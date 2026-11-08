package com.example.p2pstream

import android.os.Bundle
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Faz 1 — HLS kaynağı yakalama:
 *   urlInput (m3u8) → SourcePoller → SegmentDownloader (files/segments/*.ts)
 *                  → TorrentMetaBuilder (files/torrents/<infohash>.torrent)
 *                  → LocalManifestWriter (files/manifest.m3u8, magnet URI'li)
 *
 * Test URL'leri (urlInput'a elle yapıştırılır):
 *   Aşama A (VOD, pipeline doğrulama): https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
 *   Aşama B (canlı, kayan pencere): https://test-streams.mux.dev/pts_shift/master.m3u8
 *   Aşama B yedek: https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val TEST_URL_VOD = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private val TEST_URL_LIVE = "https://test-streams.mux.dev/pts_shift/master.m3u8"
    private val TEST_URL_LIVE_BACKUP = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
    private val DEFAULT_URL = TEST_URL_VOD // Aşama A için VOD; Aşama B'de urlInput'a canlı URL elle yapıştırılır

    // VOD'da ilk poll'da ~150 segment geldiği için en fazla 3 paralel indirme
    private val downloadSemaphore = Semaphore(3)

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var scrollView: ScrollView

    private var poller: SourcePoller? = null

    private val segmentDir by lazy { File(filesDir, "segments").apply { mkdirs() } }
    private val torrentDir by lazy { File(filesDir, "torrents").apply { mkdirs() } }
    private val manifestFile by lazy { File(filesDir, "manifest.m3u8") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val dp = (16 * resources.displayMetrics.density).toInt()
            setPadding(dp, dp, dp, dp)
        }

        urlInput = EditText(this).apply {
            setText(DEFAULT_URL)
            hint = "m3u8 URL"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(false)
            setSelection(text.length)
        }

        startBtn = Button(this).apply { text = "Yayını Yakala" }
        stopBtn = Button(this).apply {
            text = "Durdur"
            isEnabled = false
        }

        statusText = TextView(this).apply {
            text = "Hazır. URL'yi kontrol edip \"Yayını Yakala\"'ya bas."
            movementMethod = ScrollingMovementMethod.getInstance()
        }

        scrollView = ScrollView(this).apply {
            addView(statusText)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        root.addView(urlInput)
        root.addView(startBtn)
        root.addView(stopBtn)
        root.addView(scrollView)
        setContentView(root)

        startBtn.setOnClickListener {
            startCapture(urlInput.text.toString().trim())
        }
        stopBtn.setOnClickListener { stopCapture() }
    }

    private fun startCapture(url: String) {
        if (url.isBlank()) {
            statusText.text = "URL boş — m3u8 adresi gir."
            return
        }
        if (poller != null) return

        startBtn.isEnabled = false
        stopBtn.isEnabled = true

        // Segment / torrent / manifest yazarlarını hazırla (Faz 1 çıktıları)
        val downloader = SegmentDownloader(segmentDir)
        val torrentBuilder = TorrentMetaBuilder(torrentDir)
        val manifestWriter = LocalManifestWriter(manifestFile)

        appendStatus("İzleniyor: $url\n")
        Log.i(TAG, "Capture başlatıldı: $url")

        val ioScope = CoroutineScope(lifecycleScope.coroutineContext + Dispatchers.IO)
        poller = SourcePoller(
            sourceUrl = url,
            pollIntervalMs = 2_000L,
            onNewSegment = { seg ->
                lifecycleScope.launch {
                    downloadSemaphore.withPermit {
                        try {
                            appendStatus("Segment ${seg.sequence} indiriliyor (${seg.durationSeconds}s)…\n")
                            val file = withContext(Dispatchers.IO) { downloader.download(seg) }
                            val infoHash = withContext(Dispatchers.IO) { torrentBuilder.buildFor(file) }
                            manifestWriter.addSegment(seg.sequence, infoHash, seg.durationSeconds)
                            appendStatus(
                                "Segment ${seg.sequence} hazır\n" +
                                        "Infohash: $infoHash\n" +
                                        "Dosya: ${file.name} (${file.length()} byte)\n" +
                                        "→ ${torrentDir.name}/$infoHash.torrent\n" +
                                        "→ ${manifestFile.name} güncellendi (magnet)\n"
                            )
                            Log.i(TAG, "SEGMENT ${seg.sequence} → $infoHash")
                        } catch (e: Exception) {
                            Log.e(TAG, "Segment ${seg.sequence} hatası: ${e.message}", e)
                            appendStatus("Segment ${seg.sequence} hatası: ${e.message}\n")
                        }
                    }
                }
            }
        ).also { it.start(ioScope) }
    }

    private fun stopCapture() {
        poller?.stop()
        poller = null
        startBtn.isEnabled = true
        stopBtn.isEnabled = false
        appendStatus("Durduruldu. Çıktılar: files/segments, files/torrents, files/manifest.m3u8\n")
    }

    private fun appendStatus(s: String) {
        statusText.post {
            statusText.append(s)
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        poller?.stop()
        poller = null
        super.onDestroy()
    }
}
