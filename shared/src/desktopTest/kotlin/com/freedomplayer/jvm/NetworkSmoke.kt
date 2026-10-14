package com.freedomplayer.jvm

import com.freedomplayer.jvm.youtube.YouTubeSource
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Prueba de red con YouTube y LRCLIB de verdad: buscar canciones, vídeos y álbumes, sacar el
 * audio y bajar un trozo, y pedir una letra. No es una prueba obligatoria (YouTube a veces
 * bloquea los servidores de GitHub): el workflow la lanza aparte y solo avisa si falla.
 *
 * ./gradlew -Pfp.android=false :shared:networkSmoke
 */
fun main() {
    var failures = 0

    fun step(name: String, block: suspend () -> String) {
        val started = System.currentTimeMillis()
        val result = runCatching { runBlocking { block() } }
        val ms = System.currentTimeMillis() - started
        result.onSuccess { println("OK    $name (${ms} ms): $it") }
        result.onFailure { e ->
            failures++
            println("FALLO $name (${ms} ms): ${e.javaClass.simpleName}: ${e.message}")
            e.cause?.let { println("      causa: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    val youtube = YouTubeSource(Locale("es", "ES"))
    var firstSong: com.freedomplayer.core.SearchResult? = null

    step("Canciones (YouTube Music)") {
        val songs = youtube.searchSongs("Rosalía Malamente")
        check(songs.isNotEmpty()) { "sin resultados" }
        firstSong = songs.first()
        songs.take(3).joinToString(" | ") { "${it.title} — ${it.artist} (${it.durationSec} s)" }
    }
    step("Vídeos (YouTube)") {
        val videos = youtube.searchVideos("Rick Astley Never Gonna Give You Up")
        check(videos.isNotEmpty()) { "sin resultados" }
        videos.take(2).joinToString(" | ") { "${it.title} · ${it.note}" }
    }
    step("Álbumes y sus canciones (YouTube Music)") {
        val albums = youtube.searchAlbums("El mal querer Rosalía")
        check(albums.isNotEmpty()) { "sin resultados" }
        val tracks = youtube.albumTracks(albums.first())
        check(tracks.isNotEmpty()) { "álbum sin canciones" }
        "«${albums.first().title}» de ${albums.first().artist}: ${tracks.size} canciones"
    }
    step("Audio de una canción (y sus primeros 64 KB)") {
        val song = checkNotNull(firstSong) { "no hay canción" }
        val audio = youtube.resolve(song)
        val request = Request.Builder().url(audio.option.url).apply {
            audio.option.headers.forEach { (k, v) -> header(k, v) }
            header("Range", "bytes=0-65535")
        }.build()
        val code = Http.client.newCall(request).execute().use { r ->
            check(r.isSuccessful) { "HTTP ${r.code}" }
            check(r.body.bytes().size == 65_536) { "trozo incompleto" }
            r.code
        }
        "${audio.option.mimeType}, ${audio.option.bitrate / 1000} kbps, ${audio.option.contentLength} bytes; HTTP $code"
    }
    step("Letra sincronizada (LRCLIB)") {
        val lyrics = checkNotNull(Lrclib().find("Malamente", "ROSALÍA", "El Mal Querer", 150)) { "sin letra" }
        "${lyrics.lines().size} líneas, sincronizada: ${lyrics.synced != null}"
    }

    println(if (failures == 0) "Todo bien." else "$failures pasos han fallado.")
    exitProcess(if (failures == 0) 0 else 1)
}
