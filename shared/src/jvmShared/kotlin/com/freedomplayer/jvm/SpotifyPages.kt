package com.freedomplayer.jvm

import com.freedomplayer.core.SpotifyPlaylist
import com.freedomplayer.core.SpotifyTrack
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.SourceException
import com.freedomplayer.data.SpotifyReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Playlists públicas de Spotify sin cuenta ni clave. Se lee la página para insertar una playlist
 * en otra web (open.spotify.com/embed/playlist/…), que trae el nombre y las canciones en un JSON;
 * si no las trae, se prueba con la página normal de la playlist.
 */
class SpotifyPages(
    private val client: OkHttpClient = Http.client,
    private val base: String = "https://open.spotify.com",
) : SpotifyReader {

    override suspend fun playlist(id: String): SpotifyPlaylist = withContext(Dispatchers.IO) {
        if (!id.matches(Regex("[A-Za-z0-9]{10,40}"))) throw SourceException(FailureKind.Unavailable, "Spotify: id no válido")
        var problem: SourceException? = null
        for (page in listOf("$base/embed/playlist/$id", "$base/playlist/$id")) {
            try {
                parse(fetch(page))?.let { return@withContext it }
            } catch (e: SourceException) {
                // Si no existe o no hay red, la otra página tampoco servirá.
                if (e.kind == FailureKind.Unavailable || e.kind == FailureKind.Network) throw e
                if (problem == null) problem = e
            }
        }
        throw problem ?: SourceException(FailureKind.Changed, "Spotify: la página no trae las canciones")
    }

    private fun fetch(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", Http.BROWSER_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .build()
        return try {
            client.newCall(request).execute().use { r ->
                when {
                    r.code == 404 || r.code == 400 -> throw SourceException(FailureKind.Unavailable, "Spotify: HTTP ${r.code}")
                    r.code == 401 || r.code == 403 || r.code == 429 -> throw SourceException(FailureKind.Blocked, "Spotify: HTTP ${r.code}")
                    !r.isSuccessful -> throw SourceException(FailureKind.Unknown, "Spotify: HTTP ${r.code}")
                    else -> r.body.string()
                }
            }
        } catch (e: Exception) {
            throw Http.failure(e, "Spotify")
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val scripts = Regex("""<script\b([^>]*)>(.*?)</script>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val known = listOf("__NEXT_DATA__", "resource", "initialState", "initial-state")

        /**
         * La playlist que trae una página de Spotify, o null si no la trae. Entiende tres formas:
         * la página para insertar de ahora (`__NEXT_DATA__`, con «trackList»), la de antes (la
         * respuesta de la API web, a veces en base64) y la del reproductor web («itemV2»).
         */
        fun parse(html: String): SpotifyPlaylist? {
            val blocks = scripts.findAll(html).map { it.groupValues[1] to it.groupValues[2].trim() }.toList()
            val ordered = blocks.sortedBy { (attrs, _) -> known.indexOfFirst { attrs.contains("\"$it\"") || attrs.contains("'$it'") }.let { if (it < 0) known.size else it } }
            for ((_, body) in ordered) {
                if (body.isEmpty() || body.length < 20) continue
                val root = element(body) ?: continue
                (fromEmbed(root) ?: fromWebApi(root) ?: fromWebPlayer(root))?.let { return it }
            }
            return null
        }

        /** JSON tal cual o en base64. */
        private fun element(text: String): JsonElement? {
            val first = text.firstOrNull()
            if (first == '{' || first == '[') return runCatching { json.parseToJsonElement(text) }.getOrNull()
            if (!text.matches(Regex("""[A-Za-z0-9+/=\s]+"""))) return null
            val decoded = runCatching { String(Base64.getMimeDecoder().decode(text), Charsets.UTF_8) }.getOrNull() ?: return null
            return runCatching { json.parseToJsonElement(decoded.trim()) }.getOrNull()
        }

        /** La página para insertar: un objeto con «trackList» ([{title, subtitle, duration}]). */
        private fun fromEmbed(root: JsonElement): SpotifyPlaylist? {
            val entity = find(root) { it["trackList"] is JsonArray } ?: return null
            val tracks = (entity["trackList"] as JsonArray).mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                if (o.text("uri")?.startsWith("spotify:episode:") == true || o.text("entityType") == "episode") return@mapNotNull null
                val title = o.text("title") ?: o.text("name") ?: return@mapNotNull null
                SpotifyTrack(clean(title), clean(o.text("subtitle") ?: names(o["artists"]).orEmpty()), seconds(o["duration"]))
            }
            val owner = entity.text("subtitle") ?: names(entity["authors"])
            val total = listOf("trackCount", "totalTracks", "totalCount").firstNotNullOfOrNull { (entity[it] as? JsonPrimitive)?.intOrNull }
            return SpotifyPlaylist(clean(entity.text("name") ?: entity.text("title").orEmpty()), owner?.let(::clean), tracks, maxOf(total ?: 0, tracks.size))
        }

        /** Como la API web: {name, owner, tracks: {items: [{track: {name, artists, duration_ms}}], total}}. */
        private fun fromWebApi(root: JsonElement): SpotifyPlaylist? {
            val playlist = find(root) { (it["tracks"] as? JsonObject)?.get("items") is JsonArray } ?: return null
            val list = playlist["tracks"] as JsonObject
            val tracks = (list["items"] as JsonArray).mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val t = o["track"] as? JsonObject ?: o
                if (t.text("type") == "episode") return@mapNotNull null
                val title = t.text("name") ?: return@mapNotNull null
                SpotifyTrack(clean(title), clean(names(t["artists"]).orEmpty()), seconds(t["duration_ms"]))
            }
            val owner = (playlist["owner"] as? JsonObject)?.let { it.text("display_name") ?: it.text("id") }
            val total = (list["total"] as? JsonPrimitive)?.intOrNull ?: 0
            return SpotifyPlaylist(clean(playlist.text("name").orEmpty()), owner?.let(::clean), tracks, maxOf(total, tracks.size))
        }

        /** El reproductor web: {name, ownerV2: {data: {name}}, content: {totalCount, items: [{itemV2: {data}}]}}. */
        private fun fromWebPlayer(root: JsonElement): SpotifyPlaylist? {
            val playlist = find(root) { o ->
                val items = (o["content"] as? JsonObject)?.get("items") as? JsonArray
                items != null && o["name"] != null && items.any { (it as? JsonObject)?.get("itemV2") != null }
            } ?: return null
            val content = playlist["content"] as JsonObject
            val tracks = (content["items"] as JsonArray).mapNotNull { item ->
                val data = ((item as? JsonObject)?.get("itemV2") as? JsonObject)?.get("data") as? JsonObject ?: return@mapNotNull null
                val type = data.text("__typename")
                if (type != null && type != "Track") return@mapNotNull null
                val title = data.text("name") ?: return@mapNotNull null
                val artists = ((data["artists"] as? JsonObject)?.get("items") as? JsonArray)
                    ?.mapNotNull { ((it as? JsonObject)?.get("profile") as? JsonObject)?.text("name") }
                    ?.joinToString(", ")
                val length = (data["trackDuration"] as? JsonObject)?.get("totalMilliseconds") ?: data["duration"]
                SpotifyTrack(clean(title), clean(artists.orEmpty()), seconds(length))
            }
            val owner = ((playlist["ownerV2"] as? JsonObject)?.get("data") as? JsonObject)?.text("name")
            val total = (content["totalCount"] as? JsonPrimitive)?.intOrNull ?: 0
            return SpotifyPlaylist(clean(playlist.text("name").orEmpty()), owner?.let(::clean), tracks, maxOf(total, tracks.size))
        }

        /** El primer objeto (buscando en profundidad) que cumple [test]. */
        private fun find(e: JsonElement, test: (JsonObject) -> Boolean): JsonObject? = when (e) {
            is JsonObject -> if (test(e)) e else e.values.firstNotNullOfOrNull { find(it, test) }
            is JsonArray -> e.firstNotNullOfOrNull { find(it, test) }
            else -> null
        }

        private fun JsonObject.text(key: String): String? =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotBlank() }

        /** [{name: …}, …] → «A, B». */
        private fun names(e: JsonElement?): String? =
            (e as? JsonArray)?.mapNotNull { (it as? JsonObject)?.text("name") }?.takeIf { it.isNotEmpty() }?.joinToString(", ")

        /** Milisegundos → segundos. */
        private fun seconds(e: JsonElement?): Int = ((e as? JsonPrimitive)?.doubleOrNull?.div(1000.0))?.roundToInt() ?: 0

        /** Sin espacios que no se parten (U+00A0) ni espacios de más. */
        private fun clean(text: String): String = text.replace(' ', ' ').replace(Regex("""\s+"""), " ").trim()
    }
}
