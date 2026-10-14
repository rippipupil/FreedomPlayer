package com.freedomplayer.jvm

import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.SourceException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Buscar y descargar versiones nuevas contra un servidor local que imita a GitHub. */
class GitHubUpdatesTest {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val tmp: File = Files.createTempDirectory("fp-updates").toFile()

    @AfterTest
    fun stop() {
        server.stop(0)
        tmp.deleteRecursively()
    }

    private val apk = ByteArray(50_000) { (it * 7 % 251).toByte() }

    private fun HttpExchange.reply(code: Int, body: ByteArray) {
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.use { it.write(body) } else close()
    }

    /** Lista de releases como la de la API de GitHub (de más nueva a más vieja, como la da GitHub). */
    private fun releasesJson(apkSize: Int = apk.size) = """
        [
          {"tag_name": "v0.6.0", "draft": true, "body": "borrador", "html_url": "$base/r/0.6.0", "assets": [
            {"name": "FreedomPlayer-0.6.0-android.apk", "size": 1, "browser_download_url": "$base/apk"}]},
          {"tag_name": "v0.5.0", "draft": false, "prerelease": true, "body": "Notas 0.5", "html_url": "$base/r/0.5.0", "assets": [
            {"name": "FreedomPlayer-0.5.0-windows.msi", "size": 10, "browser_download_url": "$base/msi"}]},
          {"tag_name": "v0.4.1", "draft": false, "prerelease": true, "body": "Notas 0.4.1", "html_url": "$base/r/0.4.1", "assets": [
            {"name": "FreedomPlayer-0.4.1-android.apk", "size": $apkSize, "browser_download_url": "$base/apk"}]},
          {"tag_name": "capturas", "draft": false, "assets": []}
        ]
    """.trimIndent()

    private class FakeInstaller(override val assetSuffix: String?, override val downloadDir: File) : UpdateInstaller {
        val installed = mutableListOf<File>()
        override fun install(file: File): String? {
            installed += file
            return null
        }
    }

    private fun serve(json: String = releasesJson(), status: Int = 200) {
        server.createContext("/releases") { ex -> ex.reply(status, json.toByteArray()) }
        server.createContext("/apk") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            if (range == null) return@createContext ex.reply(200, apk)
            val (from, to) = range.removePrefix("bytes=").split("-").let { it[0].toInt() to (it[1].toIntOrNull() ?: apk.lastIndex) }
            val end = minOf(to, apk.lastIndex)
            ex.responseHeaders.add("Content-Range", "bytes $from-$end/${apk.size}")
            ex.reply(206, apk.copyOfRange(from, end + 1))
        }
    }

    private fun updates(installer: FakeInstaller) = GitHubUpdates(installer, apiUrl = "$base/releases")

    @Test
    fun picksTheNewestReleaseWithAnInstallerForThisPlatform() = runBlocking {
        serve()
        val android = updates(FakeInstaller("-android.apk", tmp)).latest()
        assertEquals("0.4.1", android?.version)
        assertEquals("Notas 0.4.1", android?.notes)
        val windows = updates(FakeInstaller("-windows.msi", tmp)).latest()
        assertEquals("0.5.0", windows?.version)
    }

    @Test
    fun platformsWithoutInstallerDoNotAsk() = runBlocking {
        serve()
        assertNull(updates(FakeInstaller(null, tmp)).latest())
    }

    @Test
    fun downloadsTheInstallerAndHandsItToThePlatform() = runBlocking {
        serve()
        val installer = FakeInstaller("-android.apk", File(tmp, "updates"))
        val u = updates(installer)
        File(tmp, "updates").mkdirs()
        File(tmp, "updates/FreedomPlayer-0.3.0-android.apk").writeText("vieja")
        val seen = mutableListOf<Float>()
        val path = u.download(u.latest()!!) { seen += it }
        assertContentEquals(apk, File(path).readBytes())
        assertEquals(1f, seen.last())
        assertEquals(listOf("FreedomPlayer-0.4.1-android.apk"), File(tmp, "updates").list()!!.toList(), "borra los instaladores viejos")
        assertNull(u.install(path))
        assertEquals(File(path), installer.installed.single())
    }

    @Test
    fun aShortDownloadIsRejected() = runBlocking<Unit> {
        serve(releasesJson(apkSize = apk.size + 100))
        val u = updates(FakeInstaller("-android.apk", tmp))
        // El servidor da menos de lo que GitHub dice que mide: no se da por bueno.
        assertFailsWith<SourceException> { u.download(u.latest()!!) {} }
    }

    @Test
    fun rateLimitIsExplained() = runBlocking {
        serve(status = 403)
        val e = assertFailsWith<SourceException> { updates(FakeInstaller("-android.apk", tmp)).latest() }
        assertEquals(FailureKind.Blocked, e.kind)
    }

    @Test
    fun releasesAreSortedAndFiltered() {
        val list = GitHubUpdates.parseReleases(releasesJson())
        assertEquals(listOf("0.5.0", "0.4.1"), list.map { it.version })
        assertTrue(GitHubUpdates.parseReleases("no es json").isEmpty())
    }
}
