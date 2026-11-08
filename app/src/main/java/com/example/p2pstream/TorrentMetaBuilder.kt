package com.example.p2pstream

import android.util.Log
import org.libtorrent4j.TorrentBuilder
import org.libtorrent4j.TorrentInfo
import java.io.File
import java.io.IOException

/**
 * Bir dosyayı tek parçalı torrent haline getirir ve .torrent dosyası olarak kaydeder.
 * Infohash'i döndürür — bu, DHT/tracker üzerinde bu segmentin kimliğidir.
 *
 * libtorrent4j 2.1.0-33 API'sine göre düzeltildi (Faz 1 madde 4):
 *   - setPieceSize/setCreator/setComment/addFile/build/save/File/ti.bencode YOK
 *   - Doğrusu: TorrentBuilder.path(File).pieceSize().creator().comment().generate()
 *                -> Result.entry().bencode() -> TorrentInfo(bytes) -> infoHash().toHex()
 *                -> outFile.writeBytes(bytes)
 */
class TorrentMetaBuilder(private val torrentDir: File) {
    private val TAG = "TorrentMeta"

    init { if (!torrentDir.exists()) torrentDir.mkdirs() }

    @Throws(IOException::class)
    fun buildFor(segmentFile: File): String {
        val result = TorrentBuilder()
            .path(segmentFile)
            .pieceSize(16 * 1024)     // 16 KB piece (16 kiB'nin katı olmalı)
            .creator("P2PStream/0.1")
            .comment("live segment ${segmentFile.name}")
            .generate()

        val torrentBytes = result.entry().bencode()
        val ti = TorrentInfo(torrentBytes)
        val infoHash = ti.infoHash().toHex()

        val outFile = File(torrentDir, "${infoHash}.torrent")
        outFile.writeBytes(torrentBytes)

        Log.i(TAG, "Torrent oluşturuldu: ${segmentFile.name} → $infoHash")
        return infoHash
    }
}
