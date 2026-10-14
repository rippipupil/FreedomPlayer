package com.freedomplayer.jvm

import com.freedomplayer.core.AppRelease
import com.freedomplayer.core.AppVersions
import com.freedomplayer.core.ReleaseAsset
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.SourceException
import com.freedomplayer.data.UpdateService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Lo que cambia de una plataforma a otra al actualizarse: qué archivo bajar y cómo abrirlo. */
interface UpdateInstaller {
    /** Final del nombre del archivo de la release para esta plataforma; null si no se actualiza sola. */
    val assetSuffix: String?

    /** Carpeta donde dejar el instalador descargado. */
    val downloadDir: File

    /** Abre el instalador. null si se ha abierto; si no, por qué (para el aviso). */
    fun install(file: File): String?
}

/**
 * Busca versiones nuevas en GitHub Releases del repositorio de la app (es público: no hace
 * falta clave). Se usa la lista de releases y no «latest» porque las v0.* salen como pre-release.
 */
class GitHubUpdates(
    private val installer: UpdateInstaller,
    private val client: OkHttpClient = Http.client,
    private val fetcher: FileFetcher = FileFetcher(client),
    private val apiUrl: String = "https://api.github.com/repos/$REPO/releases?per_page=10",
) : UpdateService {

    override suspend fun latest(): AppRelease? {
        val suffix = installer.assetSuffix ?: return null
        val text = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(apiUrl)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "FreedomPlayer")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 403 || response.code == 429) {
                        throw SourceException(FailureKind.Blocked, "GitHub: demasiadas consultas (HTTP ${response.code})")
                    }
                    if (!response.isSuccessful) throw SourceException(FailureKind.Unknown, "GitHub: HTTP ${response.code}")
                    response.body.string()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw Http.failure(e, "Actualizaciones")
            }
        }
        return parseReleases(text).firstOrNull { it.asset(suffix) != null }
    }

    override suspend fun download(release: AppRelease, progress: (Float) -> Unit): String {
        val suffix = installer.assetSuffix ?: throw SourceException(FailureKind.Unknown, "Esta plataforma no se actualiza sola")
        val asset = release.asset(suffix) ?: throw SourceException(FailureKind.Unavailable, "La versión ${release.version} no trae instalador")
        val dir = installer.downloadDir.also { it.mkdirs() }
        // Se borran instaladores viejos; el de esta versión se conserva (si se cortó, sigue donde iba).
        dir.listFiles()?.filter { it.name != asset.name }?.forEach { it.delete() }
        val target = File(dir, asset.name)
        val size = asset.sizeBytes
        if (size > 0 && target.length() > size) target.delete()
        fetcher.fetch(asset.url, mapOf("User-Agent" to "FreedomPlayer"), target, knownLength = size, progress = progress)
        if (target.length() == 0L || (size > 0 && target.length() != size)) {
            target.delete()
            throw SourceException(FailureKind.Network, "La descarga de la versión ${release.version} ha quedado incompleta")
        }
        return target.absolutePath
    }

    override fun install(path: String): String? = installer.install(File(path))

    companion object {
        const val REPO = "rippipupil/FreedomPlayer"

        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Las releases de la respuesta de GitHub, de más nueva a más vieja: sin borradores y solo
         * con etiqueta vX.Y.Z.
         */
        fun parseReleases(text: String): List<AppRelease> {
            val list = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return emptyList()
            return list.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                if (o.bool("draft") == true) return@mapNotNull null
                val version = o.str("tag_name")?.removePrefix("v") ?: return@mapNotNull null
                if (!AppVersions.isValid(version)) return@mapNotNull null
                val assets = (o["assets"] as? JsonArray).orEmpty().mapNotNull { a ->
                    val ao = a as? JsonObject ?: return@mapNotNull null
                    ReleaseAsset(
                        name = ao.str("name") ?: return@mapNotNull null,
                        url = ao.str("browser_download_url") ?: return@mapNotNull null,
                        sizeBytes = (ao["size"] as? JsonPrimitive)?.longOrNull ?: -1,
                    )
                }
                AppRelease(version, o.str("body").orEmpty(), o.str("html_url").orEmpty(), assets)
            }.sortedWith { a, b -> AppVersions.compare(b.version, a.version) ?: 0 }
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    }
}
