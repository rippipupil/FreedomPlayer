package com.freedomplayer.jvm

import com.freedomplayer.core.Spotify
import com.freedomplayer.core.SpotifyPlaylist
import com.freedomplayer.core.YouTubeLinks
import com.freedomplayer.jvm.youtube.YouTubeSource
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Prueba de red con YouTube, LRCLIB y Spotify de verdad: buscar canciones, vídeos, álbumes y
 * listas de otra gente (también por enlace), sacar el audio y bajar un trozo, pedir una letra y leer una playlist pública de Spotify y
 * emparejar sus canciones con YouTube Music. No es una prueba obligatoria (YouTube a veces
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
    var otherUrl: String? = null
    step("Listas de otra gente y sus canciones (YouTube Music)") {
        val lists = youtube.searchPlaylists("reggaeton 2026")
        check(lists.isNotEmpty()) { "sin resultados" }
        val first = lists.first()
        val list = youtube.otherList(first.url)
        check(list.tracks.isNotEmpty()) { "lista sin canciones" }
        otherUrl = first.url
        "${lists.size} listas; «${list.info.title}» de ${list.info.owner}: ${list.tracks.size} canciones; " +
            list.tracks.take(2).joinToString(" | ") { "${it.title} — ${it.artist} (${it.durationSec} s)" }
    }
    step("Lista de YouTube pegada como enlace") {
        val music = checkNotNull(otherUrl) { "no hay lista" }
        val id = music.substringAfter("list=").substringBefore('&')
        val pasted = "https://youtube.com/playlist?list=$id&si=compartido"
        val url = checkNotNull(YouTubeLinks.playlistUrl(pasted)) { "el enlace no se reconoce" }
        val list = youtube.otherList(url)
        check(list.tracks.isNotEmpty()) { "lista sin vídeos" }
        "$url: «${list.info.title}», ${list.tracks.size} vídeos; " + list.tracks.take(2).joinToString(" | ") { it.title }
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

    // Spotify sin clave. La playlist es editorial (la app no las importa, solo las de usuario),
    // pero su página pública es igual que la de cualquier playlist y siempre existe.
    val spotifyId = "37i9dQZF1DXcBWIGoYBM5M"
    var spotifyList: SpotifyPlaylist? = null
    step("Playlist pública de Spotify (sin cuenta ni clave)") {
        val list = SpotifyPages().playlist(spotifyId)
        check(list.tracks.isNotEmpty()) { "sin canciones" }
        spotifyList = list
        "«${list.name}» de ${list.owner}: ${list.tracks.size} de ${list.total} canciones; " +
            list.tracks.take(2).joinToString(" | ") { "${it.title} — ${it.artist} (${it.durationSec} s)" }
    }
    if (spotifyList == null) {
        step("Spotify: qué trae la página para insertar") { pageSummary("https://open.spotify.com/embed/playlist/$spotifyId") }
        step("Spotify: qué trae la página normal") { pageSummary("https://open.spotify.com/playlist/$spotifyId") }
    }
    step("Spotify → YouTube Music (6 canciones)") {
        val list = checkNotNull(spotifyList) { "no se ha podido leer la playlist" }
        val matches = list.tracks.take(6).map { t -> Spotify.match(t, youtube.searchSongs(Spotify.query(t))) }
        val kinds = matches.groupingBy { it.kind }.eachCount().entries.joinToString { "${it.value} ${it.key}" }
        "$kinds; " + matches.take(3).joinToString(" | ") { m ->
            "${m.track.title} — ${m.track.artist}: ${m.kind}" + (m.result?.let { " → ${it.title} — ${it.artist}" } ?: "")
        }
    }

    println(if (failures == 0) "Todo bien." else "$failures pasos han fallado.")
    exitProcess(if (failures == 0) 0 else 1)
}

/** Qué devuelve una página (para saber qué ha cambiado si no se entiende). */
private fun pageSummary(url: String): String {
    val request = Request.Builder().url(url)
        .header("User-Agent", Http.BROWSER_UA)
        .header("Accept-Language", "es-ES,es;q=0.9")
        .build()
    return Http.client.newCall(request).execute().use { r ->
        val html = r.body.string()
        val ids = Regex("""<script\b[^>]*\bid="([^"]+)"""").findAll(html).map { it.groupValues[1] }.toList()
        val title = Regex("<title>(.*?)</title>").find(html)?.groupValues?.get(1)
        "HTTP ${r.code}, ${html.length} caracteres, título «$title», scripts con id $ids, " +
            "trackList: ${html.contains("trackList")}, itemV2: ${html.contains("itemV2")}"
    }
}
