package com.freedomplayer.jvm

import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.data.LibraryData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/** La huella de una cosa en la última exportación y la hora de su último cambio. */
data class Stamp(val hash: Int, val at: Long)

/**
 * La hora del último cambio de cada cosa, sin tocar el resto de la app: se compara lo que hay
 * ahora con lo de la exportación anterior. Lo que no ha cambiado conserva su hora; lo nuevo o
 * cambiado lleva la de ahora; lo que ya no está queda como borrado a esa hora.
 */
object SyncClock {
    fun stamp(previous: Map<String, Stamp>, current: Map<String, Int>, now: Long): Pair<Map<String, Stamp>, Map<String, Long>> {
        val stamps = current.mapValues { (key, hash) -> previous[key]?.takeIf { it.hash == hash } ?: Stamp(hash, now) }
        val deleted = previous.keys.filter { it !in current }.associateWith { now }
        return stamps to deleted
    }

    /** Las huellas de la biblioteca: canciones, notas, playlists y rankings (las escuchas no cambian). */
    fun fingerprints(lib: LibraryData, audioFile: (Song) -> String?): Map<String, Int> = buildMap {
        lib.songs.forEach { s ->
            put("cancion:${s.id}", listOf(s.title, s.artist, s.album, s.durationSec, s.coverColor, s.sourceUrl, s.addedAt, audioFile(s)).hashCode())
            put("nota:${s.id}", (s.score ?: 0).hashCode())
        }
        lib.playlists.forEach { p -> put("playlist:${p.id}", listOf(p.name, p.songIds, p.coverColor).hashCode()) }
        lib.rankings.forEach { r -> put("ranking:${r.id}", listOf(r.name, r.songIds).hashCode()) }
    }
}

/**
 * Intercambiar la biblioteca por la carpeta que comparte Syncthing: se escribe la de aquí
 * («biblioteca-<este dispositivo>.json»), se leen las de los demás y se juntan con las reglas de
 * [LibrarySync]. El audio de las canciones nuevas llega por la carpeta de música; una canción del
 * otro dispositivo cuyo audio aún no ha llegado se deja para la vez siguiente.
 */
class SyncExchange(
    private val device: String,
    private val folder: File,
    private val music: File,
    private val stateFile: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun exchange(local: LibraryData): LibraryData? {
        folder.mkdirs()
        val time = now()
        val (previous, previousDeleted) = readState()
        val current = SyncClock.fingerprints(local, ::audioFile)
        val (stamps, deletedNow) = SyncClock.stamp(previous, current, time)
        val deleted = (previousDeleted + deletedNow).filterKeys { it !in current }
        val mine = toSync(local, stamps, deleted, time)
        write(File(folder, LibrarySync.fileName(device)), LibrarySync.encode(mine))
        val others = folder.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("biblioteca-") && it.name.endsWith(".json") && it.name != LibrarySync.fileName(device) }
            .mapNotNull { runCatching { LibrarySync.decode(it.readText()) }.getOrNull() }
        if (others.isEmpty()) {
            writeState(stamps, deleted)
            return null
        }
        val merged = others.fold(mine) { acc, other -> LibrarySync.merge(acc, other) }
        val result = fromSync(merged, local)
        // Lo que queda aquí tras juntar, con la hora de cada cambio: así, si luego se borra o se
        // cambia algo que llegó del otro dispositivo, también se sabe.
        writeState(stampsOf(result, merged, time), tombstonesOf(merged))
        return result
    }

    private fun stampsOf(result: LibraryData, merged: SyncLibrary, time: Long): Map<String, Stamp> {
        val songAt = merged.songs.associate { it.id to it.changedAt }
        val scoreAt = merged.scores.associate { it.songId to it.changedAt }
        val playlistAt = merged.playlists.associate { it.id to it.changedAt }
        val rankingAt = merged.rankings.associate { it.id to it.changedAt }
        return SyncClock.fingerprints(result, ::audioFile).mapValues { (key, hash) ->
            val id = key.substringAfter(':')
            val at = when {
                key.startsWith("cancion:") -> songAt[id]
                key.startsWith("nota:") -> scoreAt[id]
                key.startsWith("playlist:") -> playlistAt[id]
                else -> rankingAt[id]
            }
            Stamp(hash, at ?: time)
        }
    }

    private fun tombstonesOf(merged: SyncLibrary): Map<String, Long> =
        merged.deletedSongs.associate { "cancion:${it.id}" to it.deletedAt } +
            merged.deletedPlaylists.associate { "playlist:${it.id}" to it.deletedAt } +
            merged.deletedRankings.associate { "ranking:${it.id}" to it.deletedAt }

    /** El nombre del audio si está en la carpeta de música compartida. */
    private fun audioFile(song: Song): String? {
        val file = song.audioPath?.let(::File) ?: return null
        return file.name.takeIf { file.parentFile?.absoluteFile == music.absoluteFile && LibrarySync.safeFileName(it) }
    }

    private fun toSync(lib: LibraryData, stamps: Map<String, Stamp>, deleted: Map<String, Long>, time: Long): SyncLibrary {
        fun at(key: String) = stamps[key]?.at ?: time
        fun gone(prefix: String) = deleted.filterKeys { it.startsWith(prefix) }.map { (k, v) -> Tombstone(k.removePrefix(prefix), v) }.sortedBy { it.id }
        return SyncLibrary(
            device = device,
            exportedAt = time,
            songs = lib.songs.map { s ->
                SyncSong(s.id, s.title, s.artist, s.album, s.durationSec, s.coverColor, s.sourceUrl, s.addedAt, audioFile(s), at("cancion:${s.id}"))
            },
            scores = lib.songs.map { s -> SyncScore(s.id, s.score, at("nota:${s.id}")) },
            playlists = lib.playlists.map { p -> SyncList(p.id, p.name, p.songIds, p.coverColor, at("playlist:${p.id}")) },
            rankings = lib.rankings.map { r -> SyncList(r.id, r.name, r.songIds, null, at("ranking:${r.id}")) },
            plays = lib.plays,
            deletedSongs = gone("cancion:"),
            deletedPlaylists = gone("playlist:"),
            deletedRankings = gone("ranking:"),
        )
    }

    private fun fromSync(merged: SyncLibrary, local: LibraryData): LibraryData {
        val mine = local.songs.associateBy { it.id }
        val scores = merged.scores.associate { it.songId to it.score }
        val songs = merged.songs.mapNotNull { s ->
            val here = mine[s.id]
            val arrived = s.audioFile?.let { File(music, it) }?.takeIf { it.isFile }?.absolutePath
            if (here == null && arrived == null) return@mapNotNull null
            val base = here ?: Song(s.id, s.title, s.artist, s.durationSec, s.coverColor, album = s.album, audioPath = arrived, sourceUrl = s.sourceUrl, addedAt = s.addedAt)
            base.copy(title = s.title, artist = s.artist, album = s.album, score = scores[s.id], plays = here?.plays ?: merged.plays.count { it.songId == s.id })
        }
        val ids = songs.map { it.id }.toSet()
        val colors = local.playlists.associate { it.id to it.coverColor }
        return LibraryData(
            songs = songs,
            playlists = merged.playlists.map { p -> Playlist(p.id, p.name, p.songIds.filter { it in ids }, p.coverColor ?: colors[p.id] ?: 0xFF7A1F55) },
            rankings = merged.rankings.map { r -> CustomRanking(r.id, r.name, r.songIds.filter { it in ids }) },
            plays = merged.plays.filter { it.songId in ids },
        )
    }

    /** Se escribe entero y luego se cambia de nombre: Syncthing nunca ve un JSON a medias. */
    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, ".${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun readState(): Pair<Map<String, Stamp>, Map<String, Long>> {
        val root = runCatching { json.parseToJsonElement(stateFile.readText()) as? JsonObject }.getOrNull() ?: return emptyMap<String, Stamp>() to emptyMap()
        val stamps = (root["huellas"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
            val pair = v as? JsonArray ?: return@mapNotNull null
            val hash = (pair.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val at = (pair.getOrNull(1) as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            k to Stamp(hash, at)
        }.toMap()
        val deleted = (root["borrados"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }.toMap()
        return stamps to deleted
    }

    private fun writeState(stamps: Map<String, Stamp>, deleted: Map<String, Long>) {
        stateFile.parentFile?.mkdirs()
        val text = buildJsonObject {
            put("huellas", buildJsonObject {
                stamps.forEach { (k, v) -> put(k, buildJsonArray { add(JsonPrimitive(v.hash)); add(JsonPrimitive(v.at)) }) }
            })
            put("borrados", buildJsonObject { deleted.forEach { (k, v) -> put(k, v) } })
        }.toString()
        write(stateFile, text)
    }
}
