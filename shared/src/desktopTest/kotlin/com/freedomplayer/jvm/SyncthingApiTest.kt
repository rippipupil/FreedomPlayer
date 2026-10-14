package com.freedomplayer.jvm

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La API REST de Syncthing con un Syncthing de mentira: clave, vincular y cuánto falta. */
class SyncthingApiTest {
    private val pc = "MFZWI3D-BONSGYC-YLTMRWG-C43ENR5-QXGZDMM-FZWI3DP-BONSGYY-LTMRWAD"
    private val requests = mutableListOf<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            val key = ex.requestHeaders.getFirst("X-API-Key")
            val body = ex.requestBody.readBytes().decodeToString()
            synchronized(requests) { requests += "${ex.requestMethod} ${ex.requestURI} $body" }
            val (code, text) = when {
                key != "clave-secreta" -> 403 to "CSRF Error"
                ex.requestURI.path == "/rest/system/ping" -> 200 to """{"ping":"pong"}"""
                ex.requestURI.path == "/rest/system/status" -> 200 to """{"myID":"$pc","uptime":3}"""
                ex.requestURI.path == "/rest/system/connections" -> 200 to """{"connections":{"$pc":{"connected":true},"OTRO":{"connected":false}}}"""
                ex.requestURI.path == "/rest/db/completion" -> 200 to """{"completion":60,"globalItems":20,"needItems":8}"""
                ex.requestMethod == "PUT" -> 200 to ""
                else -> 404 to "no"
            }
            val bytes = text.toByteArray()
            ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        start()
    }
    private val base = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun talksToSyncthingWithTheKey() {
        runBlocking {
            val api = SyncthingApi(base, "clave-secreta")
            assertTrue(api.ping())
            assertEquals(pc, api.myId())
            assertEquals(setOf(pc), api.connected())
            assertEquals(SyncProgress(12, 20), api.completion(SyncthingApi.MUSIC_FOLDER, pc))
            val wrong = SyncthingApi(base, "otra")
            assertTrue(!wrong.ping())
            assertFailsWith<java.io.IOException> { wrong.myId() }
        }
    }

    @Test
    fun pairingAddsTheDeviceAndSharesBothFolders() {
        runBlocking {
            val pairing = SyncthingPairing(SyncthingApi(base, "clave-secreta"), File("/datos/musica"), File("/datos/biblioteca"))
            pairing.pair(pc.lowercase().replace("-", ""), "PC de casa")
            val puts = synchronized(requests) { requests.filter { it.startsWith("PUT") } }
            assertEquals(3, puts.size)
            assertTrue(puts[0].startsWith("PUT /rest/config/devices/$pc ") && "\"name\":\"PC de casa\"" in puts[0], puts[0])
            assertTrue(puts[1].startsWith("PUT /rest/config/folders/freedomplayer-musica ") && "\"deviceID\":\"$pc\"" in puts[1], puts[1])
            assertTrue(puts[2].startsWith("PUT /rest/config/folders/freedomplayer-biblioteca "), puts[2])
            assertEquals(SyncProgress(12, 20), pairing.progress(pc))
            assertFailsWith<IllegalArgumentException> { pairing.pair("hola", "x") }
        }
    }

    @Test
    fun deviceIdsKeysAndCommand() {
        assertEquals(pc, SyncthingIds.device(pc.lowercase()))
        assertEquals(pc, SyncthingIds.device(pc.replace("-", " ")))
        assertNull(SyncthingIds.device("https://example.com"))
        val key = SyncthingIds.newApiKey()
        assertEquals(40, key.length)
        assertTrue(key.all { it.isLetterOrDigit() })
        assertTrue(key != SyncthingIds.newApiKey())
        val args = SyncthingCommand.args(File("/app/syncthing"), File("/datos/st"), 8385, key)
        assertTrue("--gui-address=127.0.0.1:8385" in args && "--gui-apikey=$key" in args && "--no-browser" in args)
    }
}
