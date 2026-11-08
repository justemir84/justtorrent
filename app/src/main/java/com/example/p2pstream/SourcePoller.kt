package com.example.p2pstream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SourcePoller(
    private var sourceUrl: String,
    private val pollIntervalMs: Long = 2_000L, // ~segment süresi
    private val onNewSegment: suspend (SegmentInfo) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val parser = HlsPlaylistParser()
    private val seenSequences = mutableSetOf<Long>()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch {
            while (true) {
                try {
                    pollOnce()
                } catch (e: Exception) {
                    println("Poll hatası: ${e.message}")
                }
                delay(pollIntervalMs)
            }
        }
    }

    private suspend fun pollOnce() {
        val content = fetchManifest(sourceUrl)
        val segments = try {
            parser.parse(content, sourceUrl)
        } catch (e: MasterPlaylistException) {
            // Master geldi → variant'a geç, tekrar dene
            sourceUrl = e.variantUrl
            return
        }

        for (seg in segments) {
            if (seenSequences.add(seg.sequence)) {
                onNewSegment(seg)
            }
        }

        // Çok eski sequence'ları unut (pencere dışı)
        if (seenSequences.size > 200) {
            val min = seenSequences.minOrNull() ?: 0L
            seenSequences.removeAll { it < min + 100 }
        }
    }

    private fun fetchManifest(url: String): String {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Manifest HTTP ${resp.code}")
            resp.body?.string() ?: error("Boş manifest")
        }
    }

    fun stop() { job?.cancel() }
}