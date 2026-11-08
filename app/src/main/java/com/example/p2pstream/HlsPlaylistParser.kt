package com.example.p2pstream

import java.net.URI

data class SegmentInfo(
    val url: String,
    val sequence: Long,
    val durationSeconds: Double
)

/**
 * Basit m3u8 parser. Master playlist (alt stream listesi) DEĞİL,
 * doğrudan media playlist bekler. Master gelirse ilk variantı seçer.
 */
class HlsPlaylistParser {

    fun parse(content: String, baseUrl: String): List<SegmentInfo> {
        // Master playlist mi? (#EXT-X-STREAM-INF varsa)
        if (content.contains("#EXT-X-STREAM-INF")) {
            val variantUrl = extractFirstVariant(content, baseUrl)
                ?: throw IllegalStateException("Master playlist içinde variant bulunamadı")
            // Variant URL'ini caller indirsin, ona parse edelim
            throw MasterPlaylistException(variantUrl)
        }

        val lines = content.lines()
        var mediaSequence = 0L
        var nextDuration = 0.0
        val segments = mutableListOf<SegmentInfo>()

        for (raw in lines) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    mediaSequence = line.substringAfter(":").toLongOrNull() ?: 0L

                line.startsWith("#EXTINF:") ->
                    nextDuration = line.substringAfter(":")
                        .substringBefore(",").toDoubleOrNull() ?: 0.0

                line.isNotEmpty() && !line.startsWith("#") -> {
                    segments.add(
                        SegmentInfo(
                            url = resolveUrl(baseUrl, line),
                            sequence = mediaSequence,
                            durationSeconds = nextDuration
                        )
                    )
                    mediaSequence++
                    nextDuration = 0.0
                }
            }
        }
        return segments
    }

    private fun extractFirstVariant(content: String, baseUrl: String): String? {
        val lines = content.lines()
        for (i in lines.indices) {
            if (lines[i].trim().startsWith("#EXT-X-STREAM-INF")) {
                for (j in i + 1 until lines.size) {
                    val candidate = lines[j].trim()
                    if (candidate.isNotEmpty() && !candidate.startsWith("#"))
                        return resolveUrl(baseUrl, candidate)
                }
            }
        }
        return null
    }

    private fun resolveUrl(base: String, relative: String): String =
        try { URI(base).resolve(relative).toString() } catch (_: Exception) { relative }
}

class MasterPlaylistException(val variantUrl: String) : Exception("Master playlist")