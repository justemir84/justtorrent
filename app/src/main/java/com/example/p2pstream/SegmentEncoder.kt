package com.example.p2pstream

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * Kameradan gelen frame'leri H.264+AAC olarak encode eder ve
 * her 2 saniyede bir yeni bir segment dosyası (seg_000.mp4, seg_001.mp4...) oluşturur.
 *
 * NOT: Bu sürümde ses yok (video-only), Faz 1 için yeterli.
 * Ses eklemek için ikinci bir MediaCodec (AAC) + ikinci track eklenmeli.
 */
class SegmentEncoder(
    private val outputDir: File,
    private val segmentDurationUs: Long = 2_000_000L,
    private val onSegmentReady: (File, Long) -> Unit, // (dosya, sıra no)
) {
    private val TAG = "SegmentEncoder"

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var segmentIndex = 0
    private var segmentStartUs = -1L
    private var muxerStarted = false

    fun start(width: Int, height: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // her 1sn'de keyframe
        }
        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    fun getInputSurface() = encoder!!.createInputSurface()

    /** Kamera frame'leri geldikçe çağrılır. */
    fun drainEncoder(endOfStream: Boolean = false) {
        val codec = encoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        if (endOfStream) codec.signalEndOfInputStream()

        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Yeni segment muxer'ı başlat
                    startNewSegment(codec.outputFormat)
                }
                outIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outIndex)!!
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0 // config datası zaten muxer'a format olarak geçti
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)

                        // Segment rotate kararı
                        if (segmentStartUs < 0) segmentStartUs = bufferInfo.presentationTimeUs
                        val elapsed = bufferInfo.presentationTimeUs - segmentStartUs
                        if (elapsed >= segmentDurationUs) {
                            finishCurrentSegment()
                            startNewSegment(codec.outputFormat)
                            segmentStartUs = bufferInfo.presentationTimeUs
                        }

                        muxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
            if (endOfStream && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }
    }

    private fun startNewSegment(format: MediaFormat) {
        finishCurrentSegment()
        val file = File(outputDir, "seg_%03d.mp4".format(segmentIndex))
        muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).apply {
            videoTrackIndex = addTrack(format)
            start()
        }
        muxerStarted = true
        Log.i(TAG, "Yeni segment başladı: ${file.name}")
    }

    private fun finishCurrentSegment() {
        val m = muxer ?: return
        if (muxerStarted) {
            try { m.stop() } catch (e: Exception) { Log.w(TAG, "muxer.stop: ${e.message}") }
        }
        m.release()
        muxer = null
        muxerStarted = false
        val file = File(outputDir, "seg_%03d.mp4".format(segmentIndex))
        if (file.exists()) {
            onSegmentReady(file, segmentIndex.toLong())
            segmentIndex++
        }
    }

    fun stop() {
        drainEncoder(true)
        finishCurrentSegment()
        encoder?.stop(); encoder?.release(); encoder = null
    }
}