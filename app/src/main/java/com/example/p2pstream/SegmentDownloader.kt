package com.example.p2pstream

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class SegmentDownloader(private val outputDir: File) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    init { if (!outputDir.exists()) outputDir.mkdirs() }

    /**
     * Segmenti indirir. Uzantıyı URL'den tahmin eder (.ts, .m4s, .mp4).
     * Dosya zaten varsa tekrar indirmez (idempotent).
     */
    fun download(segment: SegmentInfo): File {
        val ext = detectExtension(segment.url)
        val target = File(outputDir, "seg_%06d%s".format(segment.sequence, ext))

        if (target.exists() && target.length() > 0) return target

        val request = Request.Builder().url(segment.url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw IOException("HTTP ${response.code} — ${segment.url}")
            val body = response.body ?: throw IOException("Boş body")
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private fun detectExtension(url: String): String {
        val path = url.substringBefore("?")
        return when {
            path.endsWith(".ts") -> ".ts"
            path.endsWith(".m4s") -> ".m4s"
            path.endsWith(".mp4") -> ".mp4"
            path.endsWith(".aac") -> ".aac"
            else -> ".ts"
        }
    }
}