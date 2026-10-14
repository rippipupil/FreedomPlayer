package com.freedomplayer.jvm

import com.freedomplayer.core.LyricsCandidate
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.LyricsMatch
import com.freedomplayer.data.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Letras de LRCLIB (https://lrclib.net), sin clave. Primero se pide la canción exacta
 * (`/api/get`, con título, artista, álbum y duración); si no está, se busca (`/api/search`)
 * y se elige la que mejor encaja por artista y duración.
 */
class Lrclib(
    private val client: OkHttpClient = Http.client,
    private val base: String = "https://lrclib.net",
    /** LRCLIB pide que cada app se identifique. */
    private val userAgent: String = "FreedomPlayer (https://github.com/rippipupil/FreedomPlayer)",
) : LyricsSource {

    override suspend fun find(title: String, artist: String, album: String?, durationSec: Int): LyricsData? =
        withContext(Dispatchers.IO) {
            if (title.isBlank()) return@withContext null
            val exact = get(title, artist, album, durationSec)
            if (exact != null && (exact.instrumental || !exact.synced.isNullOrBlank())) return@withContext exact.toData()
            val best = LyricsMatch.best(search(title, artist), title, artist, durationSec)
            (best ?: exact)?.toData()
        }

    private fun LyricsCandidate.toData() = LyricsData(synced?.takeIf { it.isNotBlank() }, plain?.takeIf { it.isNotBlank() }, instrumental)

    /** `/api/get`: la canción exacta (LRCLIB acepta ±2 s de duración). null si no está. */
    fun get(title: String, artist: String, album: String?, durationSec: Int): LyricsCandidate? {
        val url = "$base/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
                if (durationSec > 0) addQueryParameter("duration", durationSec.toString())
            }
            .build()
        val body = call(url) ?: return null
        return (parse(body) as? JsonObject)?.let(::candidate)
    }

    /** `/api/search`: hasta 20 posibles. */
    fun search(title: String, artist: String): List<LyricsCandidate> {
        val url = "$base/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val body = call(url) ?: return emptyList()
        return (parse(body) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::candidate) }
    }

    private fun call(url: HttpUrl): String? {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw Http.failure(java.io.IOException("LRCLIB HTTP ${response.code}"), "Letra")
                else -> response.body.string()
            }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

        /** Una respuesta de LRCLIB («trackName», «syncedLyrics»…) como candidata. */
        fun candidate(o: JsonObject): LyricsCandidate? {
            val track = o.text("trackName") ?: return null
            return LyricsCandidate(
                track = track,
                artist = o.text("artistName").orEmpty(),
                album = o.text("albumName"),
                durationSec = (o["duration"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                synced = o.text("syncedLyrics"),
                plain = o.text("plainLyrics"),
                instrumental = (o["instrumental"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }
    }
}
