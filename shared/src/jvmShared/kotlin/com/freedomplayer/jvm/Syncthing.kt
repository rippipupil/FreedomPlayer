package com.freedomplayer.jvm

import com.freedomplayer.core.SyncIds
import com.freedomplayer.data.SyncBackend
import com.freedomplayer.data.SyncCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Ids de dispositivo de Syncthing («XXXXXXX-XXXXXXX-…», 8 grupos de 7) y claves de la API. */
object SyncthingIds {
    /** El id leído de un QR (o escrito a mano): en mayúsculas y con guiones; null si no lo es. */
    fun device(text: String): String? = SyncIds.device(text)

    /** Clave para la API REST, generada en el dispositivo cada vez que se arranca Syncthing. */
    fun newApiKey(random: SecureRandom = SecureRandom()): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return String(CharArray(40) { chars[random.nextInt(chars.length)] })
    }

    /** Un puerto libre en este equipo para la API (solo escucha en 127.0.0.1). */
    fun freePort(): Int = ServerSocket(0).use { it.localPort }
}

/** Cuánto lleva sincronizado: «Sincronizando con el PC 12 de 20». */
data class SyncProgress(val done: Long, val total: Long) {
    val finished: Boolean get() = done >= total
}

/**
 * Syncthing por su API REST local (http://127.0.0.1:<puerto>), con la clave que se genera en el
 * dispositivo al arrancarlo. Solo lo que necesita FreedomPlayer: el id de este dispositivo, añadir
 * el del otro, compartir las dos carpetas (música y biblioteca) y saber cuánto falta.
 */
class SyncthingApi(
    private val base: String,
    private val apiKey: String,
    private val client: OkHttpClient = Http.client.newBuilder().proxy(java.net.Proxy.NO_PROXY).readTimeout(30, TimeUnit.SECONDS).build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    /** Responde (Syncthing ya ha arrancado). */
    suspend fun ping(): Boolean = runCatching { get("/rest/system/ping")["ping"]?.let { (it as JsonPrimitive).content } == "pong" }.getOrDefault(false)

    /** El id de este dispositivo. */
    suspend fun myId(): String = (get("/rest/system/status")["myID"] as? JsonPrimitive)?.content ?: throw IOException("Syncthing: sin id")

    /** Añade (o actualiza) el otro dispositivo. */
    suspend fun addDevice(id: String, name: String) {
        put("/rest/config/devices/$id", buildJsonObject {
            put("deviceID", id)
            put("name", name)
            put("addresses", buildJsonArray { add(JsonPrimitive("dynamic")) })
            put("autoAcceptFolders", false)
        })
    }

    /** Comparte una carpeta (con id fijo, el mismo en los dos dispositivos) con los dispositivos dados. */
    suspend fun shareFolder(folderId: String, label: String, path: String, devices: List<String>) {
        put("/rest/config/folders/$folderId", buildJsonObject {
            put("id", folderId)
            put("label", label)
            put("path", path)
            put("type", "sendreceive")
            put("fsWatcherEnabled", true)
            put("rescanIntervalS", 3600)
            put("devices", buildJsonArray { devices.forEach { add(buildJsonObject { put("deviceID", it) }) } })
        })
    }

    /** Qué dispositivos están conectados ahora. */
    suspend fun connected(): Set<String> {
        val all = get("/rest/system/connections")["connections"] as? JsonObject ?: return emptySet()
        return all.filter { (_, v) -> ((v as? JsonObject)?.get("connected") as? JsonPrimitive)?.booleanOrNull == true }.keys
    }

    /** Cuántos archivos de [folderId] tiene ya [device] de los que debería tener. */
    suspend fun completion(folderId: String, device: String): SyncProgress {
        val o = get("/rest/db/completion?folder=$folderId&device=$device")
        val total = (o["globalItems"] as? JsonPrimitive)?.longOrNull ?: 0
        val need = (o["needItems"] as? JsonPrimitive)?.longOrNull ?: 0
        return SyncProgress((total - need).coerceAtLeast(0), total)
    }

    private suspend fun get(path: String): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base + path).header("X-API-Key", apiKey).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Syncthing: HTTP ${r.code} en $path")
            json.parseToJsonElement(r.body.string()) as? JsonObject ?: JsonObject(emptyMap())
        }
    }

    private suspend fun put(path: String, body: JsonObject) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base + path).header("X-API-Key", apiKey).put(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Syncthing: HTTP ${r.code} en $path")
        }
    }

    companion object {
        const val MUSIC_FOLDER = "freedomplayer-musica"
        const val LIBRARY_FOLDER = "freedomplayer-biblioteca"
    }
}

/**
 * Vincular este dispositivo con otro (el id que se lee del QR): se añade el dispositivo y se
 * comparten con él la carpeta de la música y la de la biblioteca (los JSON de cada uno).
 */
class SyncthingPairing(private val api: SyncthingApi, private val music: File, private val library: File) {
    suspend fun pair(otherId: String, otherName: String) {
        val id = SyncthingIds.device(otherId) ?: throw IllegalArgumentException("Ese código no es de un dispositivo")
        api.addDevice(id, otherName)
        api.shareFolder(SyncthingApi.MUSIC_FOLDER, "FreedomPlayer · música", music.absolutePath, listOf(id))
        api.shareFolder(SyncthingApi.LIBRARY_FOLDER, "FreedomPlayer · biblioteca", library.absolutePath, listOf(id))
    }

    /** Lo que lleva el otro dispositivo de la música (para «Sincronizando con el PC 12 de 20»). */
    suspend fun progress(otherId: String): SyncProgress = api.completion(SyncthingApi.MUSIC_FOLDER, otherId)
}

/** Cómo se arranca Syncthing: solo en 127.0.0.1, con la clave generada aquí y sin abrir el navegador. */
object SyncthingCommand {
    fun args(executable: File, home: File, port: Int, apiKey: String): List<String> = listOf(
        executable.absolutePath,
        "serve",
        "--home=" + home.absolutePath,
        "--gui-address=127.0.0.1:$port",
        "--gui-apikey=$apiKey",
        "--no-browser",
        "--no-restart",
        "--no-upgrade",
    )

    /** Sin la carpeta «Default Folder» que Syncthing crea la primera vez. */
    val environment: Map<String, String> = mapOf("STNODEFAULTFOLDER" to "1", "STNOUPGRADE" to "1")
}

/**
 * Syncthing de verdad para la app: lo arranca (solo en 127.0.0.1, con una clave nueva cada vez),
 * espera a que responda y vincula. Lo usan el PC (con su QR) y el móvil (que lo escanea).
 */
class SyncthingBackend(
    private val executable: () -> File?,
    private val home: File,
    private val music: File,
    private val library: File,
    override val showsQr: Boolean,
    private val qrCode: (String) -> List<BooleanArray> = { emptyList() },
    private val extraEnvironment: Map<String, String> = emptyMap(),
) : SyncBackend {
    private val lock = Mutex()
    private var process: Process? = null
    private var api: SyncthingApi? = null

    override suspend fun start(): String = lock.withLock {
        api?.let { running -> if (running.ping()) return@withLock running.myId() }
        val exe = executable() ?: throw IOException("Syncthing no está en esta instalación")
        home.mkdirs()
        val port = SyncthingIds.freePort()
        val key = SyncthingIds.newApiKey()
        val builder = ProcessBuilder(SyncthingCommand.args(exe, home, port, key))
            .redirectErrorStream(true)
            .redirectOutput(File(home, "syncthing.log"))
        builder.environment().putAll(SyncthingCommand.environment + extraEnvironment)
        val started = withContext(Dispatchers.IO) { builder.start() }
        process = started
        Runtime.getRuntime().addShutdownHook(Thread { started.destroy() })
        val candidate = SyncthingApi("http://127.0.0.1:$port", key)
        repeat(60) {
            if (candidate.ping()) {
                api = candidate
                return@withLock candidate.myId()
            }
            if (!started.isAlive) throw IOException("Syncthing se ha cerrado al arrancar")
            delay(500)
        }
        started.destroy()
        throw IOException("Syncthing no responde")
    }

    override fun qr(text: String): List<BooleanArray> = qrCode(text)

    override suspend fun pair(otherId: String, otherName: String) {
        val running = api ?: throw IOException("Syncthing no está arrancado")
        library.mkdirs()
        SyncthingPairing(running, music, library).pair(otherId, otherName)
    }

    override suspend fun progress(otherId: String): SyncCount? {
        val running = api ?: return null
        if (otherId !in running.connected()) return null
        val p = running.completion(SyncthingApi.MUSIC_FOLDER, otherId)
        return SyncCount(p.done, p.total)
    }

    /** Para Syncthing (al salir de la app). */
    fun stop() {
        process?.destroy()
        process = null
        api = null
    }
}
