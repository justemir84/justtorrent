package com.example.p2pstream

import java.io.File

/**
 * İzleyicilere dağıtılacak "yerel m3u8". İçinde standart m3u8 segment URL'i değil,
 * her segment için magnet URI var. Faz 3'te TorrentDataSource bunları çözecek.
 */
class LocalManifestWriter(private val outputFile: File, private val windowSize: Int = 15) {

    private val entries = LinkedHashMap<Long, Pair<String, Double>>() // seq -> (infohash, süre)

    fun addSegment(sequence: Long, infoHash: String, durationSeconds: Double) {
        entries[sequence] = infoHash to durationSeconds
        trimWindow()
        write()
    }

    private fun trimWindow() {
        while (entries.size > windowSize) {
            val oldest = entries.keys.first()
            entries.remove(oldest)
        }
    }

    private fun write() {
        if (entries.isEmpty()) return
        val firstSeq = entries.keys.first()
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:6")
        sb.appendLine("#EXT-X-TARGETDURATION:${entries.values.maxOf { it.second }.toInt() + 1}")
        sb.appendLine("#EXT-X-MEDIA-SEQUENCE:$firstSeq")
        sb.appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        for ((_, value) in entries) {
            val (infoHash, dur) = value
            sb.appendLine("#EXTINF:%.3f,".format(dur))
            sb.appendLine("magnet:?xt=urn:btih:$infoHash")
        }
        // #EXT-X-ENDLIST YOK — canlı
        outputFile.writeText(sb.toString())
    }

    fun currentFile() = outputFile
}