package com.freedomplayer.jvm

import com.freedomplayer.core.AudioOption
import com.freedomplayer.core.AudioQuality
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SearchResult
import com.freedomplayer.data.DownloadRequest
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.LyricsSource
import com.freedomplayer.data.SourceException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Descargas y letras contra un servidor local (sin internet): trozos con «Range», seguir donde
 * se quedó, errores de YouTube y las dos preguntas a LRCLIB.
 */
class DownloadsTest {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val tmp: File = Files.createTempDirectory("fp-test").toFile()

    @AfterTest
    fun stop() {
        server.stop(0)
        tmp.deleteRecursively()
    }

    private val audio = ByteArray(300_000) { (it * 31 % 251).toByte() }
    private val rangeRequests = AtomicInteger()

    /** Sirve [audio] con «Range» (206) y cuenta las peticiones; [failFirst] corta la primera. */
    private fun serveAudio(path: String = "/audio", failFirst: Boolean = false, status: Int = 0) {
        val calls = AtomicInteger()
        server.createContext(path) { ex ->
            val n = calls.incrementAndGet()
            if (status != 0) return@createContext ex.reply(status, ByteArray(0))
            val range = ex.requestHeaders.getFirst("Range")
            if (range == null) return@createContext ex.reply(200, audio)
            rangeRequests.incrementAndGet()
            val (from, to) = range.removePrefix("bytes=").split("-").let { it[0].toInt() to (it[1].toIntOrNull() ?: audio.lastIndex) }
            val end = minOf(to, audio.lastIndex)
            val part = audio.copyOfRange(from, end + 1)
            ex.responseHeaders.add("Content-Range", "bytes $from-$end/${audio.size}")
            if (failFirst && n == 1) {
                // Corta a la mitad: la descarga tiene que seguir desde ahí.
                ex.sendResponseHeaders(206, part.size.toLong())
                ex.responseBody.write(part, 0, part.size / 2)
                ex.close()
                return@createContext
            }
            ex.reply(206, part)
        }
    }

    private fun HttpExchange.reply(code: Int, body: ByteArray) {
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.use { it.write(body) } else close()
    }

    @Test
    fun downloadsInChunksWithProgress() = runBlocking {
        serveAudio()
        val target = File(tmp, "a.webm")
        val seen = mutableListOf<Float>()
        FileFetcher(chunkBytes = 64 * 1024).fetch("$base/audio", mapOf("User-Agent" to "prueba"), target) { seen += it }
        assertContentEquals(audio, target.readBytes())
        assertEquals(5, rangeRequests.get(), "300 KB en trozos de 64 KB")
        assertEquals(1f, seen.last())
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a }, "el progreso no va hacia atrás")
    }

    @Test
    fun aCutConnectionContinuesWhereItStopped() = runBlocking {
        serveAudio(failFirst = true)
        val target = File(tmp, "b.webm")
        FileFetcher(chunkBytes = 1 shl 20).fetch("$base/audio", emptyMap(), target, knownLength = audio.size.toLong())
        assertContentEquals(audio, target.readBytes())
    }

    @Test
    fun forbiddenMeansYouTubeBlockedIt() = runBlocking {
        serveAudio(status = 403)
        val e = assertFailsWith<SourceException> { FileFetcher().fetch("$base/audio", emptyMap(), File(tmp, "c.webm")) }
        assertEquals(FailureKind.Blocked, e.kind)
    }

    private val lrcGets = AtomicInteger()
    private val lrcSearches = AtomicInteger()
    private var lastQuery: Map<String, String> = emptyMap()

    private fun query(ex: HttpExchange): Map<String, String> =
        ex.requestURI.rawQuery.orEmpty().split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    private fun serveLrclib(get: String?, search: String) {
        server.createContext("/api/get") { ex ->
            lrcGets.incrementAndGet()
            lastQuery = query(ex)
            if (get == null) ex.reply(404, """{"code":404,"name":"TrackNotFound"}""".toByteArray()) else ex.reply(200, get.toByteArray())
        }
        server.createContext("/api/search") { ex ->
            lrcSearches.incrementAndGet()
            ex.reply(200, search.toByteArray())
        }
    }

    @Test
    fun lrclibExactMatchWithSyncedLyricsIsEnough() = runBlocking {
        serveLrclib(
            get = """{"id":1,"trackName":"Luz de farola","artistName":"Ghostline","albumName":"Calles vacías","duration":167.0,"instrumental":false,"plainLyrics":"Hola","syncedLyrics":"[00:01.00] Hola"}""",
            search = "[]",
        )
        val found = Lrclib(base = base).find("Luz de farola", "Ghostline", "Calles vacías", 167)
        assertEquals(LyricsData("[00:01.00] Hola", "Hola"), found)
        assertEquals(0, lrcSearches.get())
        assertEquals(mapOf("track_name" to "Luz de farola", "artist_name" to "Ghostline", "album_name" to "Calles vacías", "duration" to "167"), lastQuery)
    }

    @Test
    fun lrclibSearchesWhenTheExactOneIsMissing() = runBlocking {
        serveLrclib(
            get = null,
            search = """[
                {"trackName":"Luz de farola","artistName":"Otro","duration":167,"syncedLyrics":"[00:01.00] No","plainLyrics":"No","instrumental":false},
                {"trackName":"Luz de farola","artistName":"Ghostline","duration":200,"syncedLyrics":"[00:01.00] Larga","plainLyrics":"Larga","instrumental":false},
                {"trackName":"Luz de farola (en directo)","artistName":"Ghostline","duration":168.4,"syncedLyrics":"[00:02.00] Sí","plainLyrics":null,"instrumental":false}
            ]""",
        )
        val found = Lrclib(base = base).find("Luz de farola", "Ghostline", null, 167)
        assertEquals("[00:02.00] Sí", found?.synced)
        assertEquals(1, lrcGets.get())
        assertEquals(1, lrcSearches.get())
        assertNull(lastQuery["album_name"], "sin álbum no se manda")
    }

    @Test
    fun lrclibWithNothingGivesNull() = runBlocking {
        serveLrclib(get = null, search = "[]")
        assertNull(Lrclib(base = base).find("Nada", "Nadie", null, 100))
    }

    @Test
    fun theDownloaderSavesAudioCoverAndLyrics() = runBlocking {
        serveAudio()
        server.createContext("/cover.jpg") { ex -> ex.reply(200, byteArrayOf(1, 2, 3)) }
        val dirs = AppDirs(File(tmp, "app"))
        val result = SearchResult(
            id = "yt:abcdefghijk",
            title = "Ghostline — Luz de farola (Official Video)",
            artist = "Ghostline",
            durationSec = 167,
            coverColor = 0xFF4A2A6B,
            kind = ResultKind.Video,
            url = "https://www.youtube.com/watch?v=abcdefghijk",
        )
        val resolver = AudioResolver {
            ResolvedAudio(AudioOption("$base/audio", "audio/webm", "webm", 160_000, true, audio.size.toLong()), it.title, it.artist, 167, "$base/cover.jpg")
        }
        val lyrics = lyricsSource { title, artist, _, duration ->
            assertEquals("Luz de farola" to "Ghostline", title to artist)
            assertEquals(167, duration)
            LyricsData("[00:01.00] Hola", null)
        }
        val downloader = YouTubeDownloader(resolver, lyrics, dirs, FileFetcher(chunkBytes = 100_000), now = { 42L })
        val progress = mutableListOf<Float>()
        val got = downloader.download(DownloadRequest(result, "Luz de farola", "Ghostline"), progress::add)

        val song = got.song
        assertEquals("yt:abcdefghijk", song.id)
        assertEquals("Luz de farola", song.title)
        assertEquals(42L, song.addedAt)
        assertContentEquals(audio, File(assertNotNull(song.audioPath)).readBytes())
        assertTrue(song.audioPath!!.endsWith("yt_abcdefghijk.webm"))
        assertContentEquals(byteArrayOf(1, 2, 3), File(assertNotNull(song.cover)).readBytes())
        assertEquals("[00:01.00] Hola", got.lyrics?.synced)
        assertEquals(1f, progress.last())
        assertTrue(dirs.partial.listFiles().isNullOrEmpty(), "no quedan archivos a medias")

        downloader.deleteFiles(song)
        assertTrue(!File(song.audioPath!!).exists() && !File(song.cover!!).exists())
    }

    @Test
    fun theDownloaderUsesTheChosenQualityAndFolder() = runBlocking {
        serveAudio()
        val dirs = AppDirs(File(tmp, "app"))
        val music = File(tmp, "Música")
        val result = SearchResult(
            id = "yt:abcdefghijk",
            title = "Luz de farola",
            artist = "Ghostline",
            durationSec = 167,
            coverColor = 0xFF4A2A6B,
            kind = ResultKind.Video,
            url = "https://www.youtube.com/watch?v=abcdefghijk",
        )
        // La mejor no existe en el servidor: si se bajara esa, la prueba fallaría.
        val best = AudioOption("$base/no-existe", "audio/webm", "webm", 160_000, true)
        val light = AudioOption("$base/audio", "audio/mp4", "m4a", 48_000, true, audio.size.toLong())
        val resolver = AudioResolver { ResolvedAudio(best, it.title, it.artist, 167, null, listOf(best, light)) }
        val downloader = YouTubeDownloader(resolver, lyricsSource { _, _, _, _ -> null }, dirs, FileFetcher(chunkBytes = 100_000))

        val request = DownloadRequest(result, "Luz de farola", "Ghostline", quality = AudioQuality.Saver, folder = music.absolutePath)
        val song = downloader.download(request) {}.song
        assertEquals(File(music, "yt_abcdefghijk.m4a").absolutePath, song.audioPath, "en la carpeta elegida y con la pista ligera")
        assertContentEquals(audio, File(song.audioPath!!).readBytes())

        val notMine = File(music, "mi-cancion.m4a").apply { writeText("mía") }
        downloader.deleteFiles(song.copy(audioPath = notMine.absolutePath))
        assertTrue(notMine.exists(), "fuera de la carpeta de la app no se borra lo que no bajó la app")
        downloader.deleteFiles(song)
        assertTrue(!File(song.audioPath!!).exists(), "lo que bajó la app se borra aunque esté en otra carpeta")

        // Si la carpeta elegida no sirve (aquí es un archivo), se usa la de la app.
        val notAFolder = File(tmp, "no-es-carpeta").apply { writeText("x") }
        val again = downloader.download(request.copy(folder = notAFolder.absolutePath)) {}.song
        assertEquals(File(dirs.music, "yt_abcdefghijk.m4a").absolutePath, again.audioPath)
    }

    private fun lyricsSource(block: suspend (String, String, String?, Int) -> LyricsData?): LyricsSource = object : LyricsSource {
        override suspend fun find(title: String, artist: String, album: String?, durationSec: Int) = block(title, artist, album, durationSec)
    }
}
