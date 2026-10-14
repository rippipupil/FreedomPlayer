package com.freedomplayer.jvm

import com.freedomplayer.core.SpotifyTrack
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.SourceException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Leer playlists públicas de Spotify sin clave: la página para insertar (la de ahora y la de
 * antes), la página normal como plan B, y qué pasa si no existe, si Spotify bloquea o si su
 * página cambia. Las páginas son de mentira pero con la forma de las de verdad.
 */
class SpotifyPagesTest {
    private val embed = """
        <!DOCTYPE html><html><head><meta charSet="utf-8"/><title>Spotify Embed</title>
        <script src="https://embed-cdn.spotifycdn.com/_next/static/chunks/main.js" defer=""></script></head>
        <body><div id="__next"></div><script>window.dataLayer = [];</script>
        <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{
        "type":"playlist","name":"Noches en el coche","uri":"spotify:playlist:1a2B3c4D5e6F7g8H9i0J","id":"1a2B3c4D5e6F7g8H9i0J",
        "title":"Noches en el coche","subtitle":"noa","authors":[{"name":"noa"}],"trackList":[
        {"uri":"spotify:track:1","uid":"a","title":"LA COMBI VERSACE","subtitle":"ROSALÍA, Tokischa","isExplicit":true,"duration":162000,"isPlayable":true,"entityType":"track"},
        {"uri":"spotify:track:2","uid":"b","title":"Here Comes The Sun - Remastered 2009","subtitle":"The Beatles","duration":185733,"entityType":"track"},
        {"uri":"spotify:episode:3","uid":"c","title":"Un pódcast","subtitle":"Alguien","duration":1800000,"entityType":"episode"}
        ]}},"settings":{"session":{"isAnonymous":true}}}},"__N_SSP":true},"page":"/playlist/[id]","query":{"id":"1a2B3c4D5e6F7g8H9i0J"},
        "buildId":"b","isFallback":false,"gssp":true,"scriptLoader":[]}</script></body></html>
    """.trimIndent()

    private val webApi = """{"name":"Viejos tiempos","owner":{"display_name":"noa","id":"noa"},"tracks":{"items":[
        {"track":{"name":"Malamente - Cap.1: Augurio","artists":[{"name":"ROSALÍA"}],"duration_ms":150000,"type":"track"}}],"total":120}}"""

    private val oldEmbed = """<html><body><script id="resource" type="application/json">""" +
        Base64.getEncoder().encodeToString(webApi.toByteArray()) + "</script></body></html>"

    private val webPlayerState = """{"entities":{"items":{"spotify:playlist:FALLBACKFALLBACK01":{"__typename":"Playlist","name":"Para correr",
        "ownerV2":{"data":{"__typename":"User","name":"noa"}},"content":{"__typename":"PlaylistItemsPage","totalCount":2,"items":[
        {"itemV2":{"__typename":"TrackResponseWrapper","data":{"__typename":"Track","name":"Malamente","artists":{"items":[{"profile":{"name":"ROSALÍA"}},{"profile":{"name":"Otra"}}]},"trackDuration":{"totalMilliseconds":150400}}}},
        {"itemV2":{"__typename":"EpisodeOrChapterResponseWrapper","data":{"__typename":"Episode","name":"Pódcast"}}}]}}}}}"""

    private val webPlayer = """<html><head><script id="initialState" type="text/plain">""" +
        Base64.getEncoder().encodeToString(webPlayerState.toByteArray()) + "</script></head><body></body></html>"

    @Test
    fun theEmbedPageGivesNameAndSongs() {
        val list = assertNotNull(SpotifyPages.parse(embed))
        assertEquals("Noches en el coche", list.name)
        assertEquals("noa", list.owner)
        assertEquals(
            listOf(SpotifyTrack("LA COMBI VERSACE", "ROSALÍA, Tokischa", 162), SpotifyTrack("Here Comes The Sun - Remastered 2009", "The Beatles", 186)),
            list.tracks,
            "sin el pódcast, con los artistas separados por «, » y la duración en segundos",
        )
        assertEquals(2, list.total)
        assertTrue(!list.editorial)
    }

    @Test
    fun theOldEmbedAndTheWebPlayerPageToo() {
        val old = assertNotNull(SpotifyPages.parse(oldEmbed))
        assertEquals("Viejos tiempos", old.name)
        assertEquals(listOf(SpotifyTrack("Malamente - Cap.1: Augurio", "ROSALÍA", 150)), old.tracks)
        assertEquals(120, old.total, "la página dice cuántas tiene aunque enseñe menos")

        val player = assertNotNull(SpotifyPages.parse(webPlayer))
        assertEquals("Para correr", player.name)
        assertEquals("noa", player.owner)
        assertEquals(listOf(SpotifyTrack("Malamente", "ROSALÍA, Otra", 150)), player.tracks)

        assertNull(SpotifyPages.parse("<html><body><script>var a = {\"b\": 1};</script>Nada por aquí</body></html>"))
    }

    // ---------- Con un servidor de mentira ----------

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val agents = mutableListOf<String>()

    @AfterTest
    fun stop() = server.stop(0)

    private fun serve(path: String, code: Int, body: String) {
        server.createContext(path) { ex: HttpExchange ->
            agents += ex.requestHeaders.getFirst("User-Agent").orEmpty()
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            ex.sendResponseHeaders(code, bytes.size.toLong().coerceAtLeast(1))
            ex.responseBody.use { if (bytes.isNotEmpty()) it.write(bytes) else it.write(' '.code) }
        }
    }

    private fun pages() = SpotifyPages(base = "http://127.0.0.1:${server.address.port}")

    @Test
    fun readsAPlaylistFromItsPages() = runBlocking {
        serve("/embed/playlist/1a2B3c4D5e6F7g8H9i0J", 200, embed)
        serve("/embed/playlist/FALLBACKFALLBACK01", 200, "<html><body>Cargando…</body></html>")
        serve("/playlist/FALLBACKFALLBACK01", 200, webPlayer)

        val list = pages().playlist("1a2B3c4D5e6F7g8H9i0J")
        assertEquals(2, list.tracks.size)
        assertTrue(agents.all { it.startsWith("Mozilla/5.0") }, "como un navegador")

        val fallback = pages().playlist("FALLBACKFALLBACK01")
        assertEquals("Para correr", fallback.name, "si la página para insertar no trae las canciones, la normal")
    }

    @Test
    fun saysWhyItCannotRead() = runBlocking {
        serve("/embed/playlist/PRIVATEPRIVATE0001", 404, "")
        serve("/embed/playlist/BLOCKEDBLOCKED0001", 429, "")
        serve("/playlist/BLOCKEDBLOCKED0001", 429, "")
        serve("/embed/playlist/CHANGEDCHANGED0001", 200, "<html><body>Otra cosa</body></html>")
        serve("/playlist/CHANGEDCHANGED0001", 200, "<html><body>Otra cosa</body></html>")

        suspend fun kindOf(id: String, reader: SpotifyPages = pages()): FailureKind? =
            try {
                reader.playlist(id)
                null
            } catch (e: SourceException) {
                e.kind
            }
        assertEquals(FailureKind.Unavailable, kindOf("PRIVATEPRIVATE0001"), "no existe o es privada")
        assertEquals(FailureKind.Blocked, kindOf("BLOCKEDBLOCKED0001"))
        assertEquals(FailureKind.Changed, kindOf("CHANGEDCHANGED0001"))
        assertEquals(FailureKind.Unavailable, kindOf("no/vale"))
        // Nadie escucha en el puerto 1: sin conexión.
        assertEquals(FailureKind.Network, kindOf("1a2B3c4D5e6F7g8H9i0J", SpotifyPages(base = "http://127.0.0.1:1")))
    }
}
