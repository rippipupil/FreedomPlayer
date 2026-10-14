package com.freedomplayer.jvm

import com.freedomplayer.core.PlayEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * La biblioteca para sincronizar el móvil y el PC con Syncthing. Cada dispositivo escribe su copia
 * en un JSON propio («biblioteca-<dispositivo>.json») dentro de la carpeta compartida y lee las de
 * los demás; así nunca hay dos dispositivos escribiendo el mismo archivo. El archivo de SQLite no
 * se sincroniza nunca.
 *
 * Reglas para juntar dos copias:
 * - Cada canción, cada nota, cada playlist y cada ranking llevan la hora de su último cambio:
 *   gana el cambio más reciente. Si empatan, se queda lo que ya hay en este dispositivo.
 * - Lo borrado se recuerda con su hora: no vuelve si se borró después del último cambio del otro.
 * - Las escuchas se suman, sin repetir la misma (misma canción en el mismo momento).
 * - El audio no va en el JSON: va como archivo en la carpeta de música compartida, y la canción
 *   dice cómo se llama su archivo.
 */

/** Una canción tal y como se comparte (sin la ruta del audio, que es de cada dispositivo). */
data class SyncSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Int,
    val coverColor: Long,
    val sourceUrl: String?,
    val addedAt: Long,
    /** El nombre del archivo de audio en la carpeta de música compartida. */
    val audioFile: String?,
    val changedAt: Long,
)

/** La nota (1–10, o sin nota) de una canción y cuándo se puso. */
data class SyncScore(val songId: String, val score: Int?, val changedAt: Long)

/** Una playlist o un ranking propio. */
data class SyncList(val id: String, val name: String, val songIds: List<String>, val coverColor: Long?, val changedAt: Long)

/** Algo borrado y cuándo. */
data class Tombstone(val id: String, val deletedAt: Long)

data class SyncLibrary(
    val device: String,
    val exportedAt: Long,
    val songs: List<SyncSong> = emptyList(),
    val scores: List<SyncScore> = emptyList(),
    val playlists: List<SyncList> = emptyList(),
    val rankings: List<SyncList> = emptyList(),
    val plays: List<PlayEvent> = emptyList(),
    val deletedSongs: List<Tombstone> = emptyList(),
    val deletedPlaylists: List<Tombstone> = emptyList(),
    val deletedRankings: List<Tombstone> = emptyList(),
)

object LibrarySync {
    const val FORMAT = 1

    /** El nombre del JSON de cada dispositivo en la carpeta compartida. */
    fun fileName(device: String): String = "biblioteca-" + device.filter { it.isLetterOrDigit() || it == '-' }.take(80) + ".json"

    /** Junta la copia de este dispositivo con la de otro, con las reglas de arriba. */
    fun merge(local: SyncLibrary, remote: SyncLibrary): SyncLibrary {
        val deletedSongs = newest(local.deletedSongs, remote.deletedSongs)
        val deletedPlaylists = newest(local.deletedPlaylists, remote.deletedPlaylists)
        val deletedRankings = newest(local.deletedRankings, remote.deletedRankings)
        val songs = mergeById(local.songs, remote.songs, SyncSong::id, SyncSong::changedAt)
            .filter { s -> deletedSongs[s.id]?.let { it.deletedAt < s.changedAt } ?: true }
        val alive = songs.map { it.id }.toSet()
        val scores = mergeById(local.scores, remote.scores, SyncScore::songId, SyncScore::changedAt).filter { it.songId in alive }
        val playlists = mergeById(local.playlists, remote.playlists, SyncList::id, SyncList::changedAt)
            .filter { p -> deletedPlaylists[p.id]?.let { it.deletedAt < p.changedAt } ?: true }
            .map { it.copy(songIds = it.songIds.filter { id -> id in alive }) }
        val rankings = mergeById(local.rankings, remote.rankings, SyncList::id, SyncList::changedAt)
            .filter { r -> deletedRankings[r.id]?.let { it.deletedAt < r.changedAt } ?: true }
            .map { it.copy(songIds = it.songIds.filter { id -> id in alive }) }
        val plays = (local.plays + remote.plays).distinct().filter { it.songId in alive }.sortedBy { it.atMillis }
        return SyncLibrary(
            device = local.device,
            exportedAt = maxOf(local.exportedAt, remote.exportedAt),
            songs = songs,
            scores = scores,
            playlists = playlists,
            rankings = rankings,
            plays = plays,
            deletedSongs = deletedSongs.values.filter { it.id !in alive }.sortedBy { it.id },
            deletedPlaylists = deletedPlaylists.values.filter { t -> playlists.none { it.id == t.id } }.sortedBy { it.id },
            deletedRankings = deletedRankings.values.filter { t -> rankings.none { it.id == t.id } }.sortedBy { it.id },
        )
    }

    /** Por id: el más reciente; si empatan, el de este dispositivo. En el orden de aquí y luego lo nuevo. */
    private fun <T> mergeById(local: List<T>, remote: List<T>, id: (T) -> String, at: (T) -> Long): List<T> {
        val theirs = remote.associateBy(id)
        val merged = local.map { mine -> theirs[id(mine)]?.takeIf { at(it) > at(mine) } ?: mine }
        val known = local.map(id).toSet()
        return merged + remote.filter { id(it) !in known }
    }

    private fun newest(a: List<Tombstone>, b: List<Tombstone>): Map<String, Tombstone> =
        (a + b).groupBy { it.id }.mapValues { (_, all) -> all.maxBy { it.deletedAt } }

    // ---------- JSON ----------

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(lib: SyncLibrary): String = buildJsonObject {
        put("formato", FORMAT)
        put("dispositivo", lib.device)
        put("exportado", lib.exportedAt)
        put("canciones", buildJsonArray {
            lib.songs.forEach { s ->
                add(buildJsonObject {
                    put("id", s.id)
                    put("titulo", s.title)
                    put("artista", s.artist)
                    s.album?.let { put("album", it) }
                    put("duracion", s.durationSec)
                    put("color", s.coverColor)
                    s.sourceUrl?.let { put("origen", it) }
                    put("anadida", s.addedAt)
                    s.audioFile?.let { put("audio", it) }
                    put("cambio", s.changedAt)
                })
            }
        })
        put("notas", buildJsonArray {
            lib.scores.forEach { n ->
                add(buildJsonObject {
                    put("cancion", n.songId)
                    put("nota", n.score)
                    put("cambio", n.changedAt)
                })
            }
        })
        put("playlists", lists(lib.playlists))
        put("rankings", lists(lib.rankings))
        put("escuchas", buildJsonArray {
            lib.plays.forEach { p ->
                add(buildJsonObject {
                    put("cancion", p.songId)
                    put("momento", p.atMillis)
                })
            }
        })
        put("borradas", tombstones(lib.deletedSongs))
        put("playlistsBorradas", tombstones(lib.deletedPlaylists))
        put("rankingsBorrados", tombstones(lib.deletedRankings))
    }.toString()

    /** Lee el JSON de otro dispositivo. Falla si no es de FreedomPlayer o es de un formato más nuevo. */
    fun decode(text: String): SyncLibrary {
        val root = json.parseToJsonElement(text) as? JsonObject ?: error("No es una biblioteca de FreedomPlayer")
        val format = (root["formato"] as? JsonPrimitive)?.intOrNull ?: error("No es una biblioteca de FreedomPlayer")
        require(format <= FORMAT) { "Biblioteca de una versión más nueva de FreedomPlayer (formato $format)" }
        return SyncLibrary(
            device = root.text("dispositivo") ?: error("Falta el dispositivo"),
            exportedAt = root.long("exportado") ?: 0,
            songs = root.objects("canciones").mapNotNull { o ->
                SyncSong(
                    id = o.text("id") ?: return@mapNotNull null,
                    title = o.text("titulo") ?: return@mapNotNull null,
                    artist = o.text("artista").orEmpty(),
                    album = o.text("album"),
                    durationSec = o.long("duracion")?.toInt() ?: 0,
                    coverColor = o.long("color") ?: 0xFF3B2F8F,
                    sourceUrl = o.text("origen"),
                    addedAt = o.long("anadida") ?: 0,
                    audioFile = o.text("audio")?.takeIf { safeFileName(it) },
                    changedAt = o.long("cambio") ?: 0,
                )
            },
            scores = root.objects("notas").mapNotNull { o ->
                val score = (o["nota"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.int?.takeIf { it in 1..10 }
                SyncScore(o.text("cancion") ?: return@mapNotNull null, score, o.long("cambio") ?: 0)
            },
            playlists = readLists(root, "playlists"),
            rankings = readLists(root, "rankings"),
            plays = root.objects("escuchas").mapNotNull { o -> PlayEvent(o.text("cancion") ?: return@mapNotNull null, o.long("momento") ?: return@mapNotNull null) },
            deletedSongs = readTombstones(root, "borradas"),
            deletedPlaylists = readTombstones(root, "playlistsBorradas"),
            deletedRankings = readTombstones(root, "rankingsBorrados"),
        )
    }

    /** Solo un nombre de archivo, sin carpetas: lo que llega de otro dispositivo no puede salirse de la carpeta. */
    fun safeFileName(name: String): Boolean =
        name.isNotBlank() && name.length <= 200 && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == ':' || it.code < 32 }

    private fun lists(all: List<SyncList>): JsonArray = buildJsonArray {
        all.forEach { l ->
            add(buildJsonObject {
                put("id", l.id)
                put("nombre", l.name)
                put("canciones", buildJsonArray { l.songIds.forEach { add(JsonPrimitive(it)) } })
                l.coverColor?.let { put("color", it) }
                put("cambio", l.changedAt)
            })
        }
    }

    private fun tombstones(all: List<Tombstone>): JsonArray = buildJsonArray {
        all.forEach { t ->
            add(buildJsonObject {
                put("id", t.id)
                put("borrado", t.deletedAt)
            })
        }
    }

    private fun readLists(root: JsonObject, key: String): List<SyncList> = root.objects(key).mapNotNull { o ->
        SyncList(
            id = o.text("id") ?: return@mapNotNull null,
            name = o.text("nombre") ?: return@mapNotNull null,
            songIds = (o["canciones"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            coverColor = o.long("color"),
            changedAt = o.long("cambio") ?: 0,
        )
    }

    private fun readTombstones(root: JsonObject, key: String): List<Tombstone> =
        root.objects(key).mapNotNull { o -> Tombstone(o.text("id") ?: return@mapNotNull null, o.long("borrado") ?: return@mapNotNull null) }

    private fun JsonObject.objects(key: String): List<JsonObject> = (this[key] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
}
