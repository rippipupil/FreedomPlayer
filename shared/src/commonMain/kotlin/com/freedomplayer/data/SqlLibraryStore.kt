package com.freedomplayer.data

import app.cash.sqldelight.db.SqlDriver
import com.freedomplayer.core.CustomRanking
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Song
import com.freedomplayer.db.FpDatabase

/**
 * La biblioteca en SQLite (SQLDelight). Android usa el SQLite del sistema y el PC un archivo con
 * el driver JDBC. Cada cambio se escribe al momento; al abrir la app se carga todo de una vez.
 */
class SqlLibraryStore(driver: SqlDriver) : LibraryStore {
    private val db = FpDatabase(driver)
    private val q = db.libraryQueries

    override fun load(): LibrarySnapshot {
        val counts = q.playCounts().executeAsList().associate { it.song_id to it.plays.toInt() }
        val songs = q.allSongs().executeAsList().map { r ->
            Song(
                id = r.id,
                title = r.title,
                artist = r.artist,
                durationSec = r.duration_sec.toInt(),
                coverColor = r.cover_color,
                plays = counts[r.id] ?: 0,
                score = r.score?.toInt(),
                album = r.album,
                audioPath = r.audio_path,
                cover = r.cover,
                sourceUrl = r.source_url,
                addedAt = r.added_at,
            )
        }
        val playlistSongs = q.allPlaylistSongs().executeAsList().groupBy { it.playlist_id }
        val playlists = q.allPlaylists().executeAsList().map { p ->
            val rows = playlistSongs[p.id].orEmpty()
            Playlist(
                p.id,
                p.name,
                rows.map { it.song_id },
                p.cover_color,
                addedAt = rows.filter { it.added_at > 0 }.associate { it.song_id to it.added_at },
            )
        }
        val rankingSongs = q.allRankingSongs().executeAsList().groupBy({ it.ranking_id }, { it.song_id })
        val rankings = q.allRankings().executeAsList().map { CustomRanking(it.id, it.name, rankingSongs[it.id].orEmpty()) }
        val log = q.allPlays().executeAsList().map { PlayEvent(it.song_id, it.at_millis) }
        return LibrarySnapshot(songs, playlists, rankings, log)
    }

    override fun saveSong(song: Song) {
        q.upsertSong(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            duration_sec = song.durationSec.toLong(),
            cover_color = song.coverColor,
            score = song.score?.toLong(),
            audio_path = song.audioPath,
            cover = song.cover,
            source_url = song.sourceUrl,
            added_at = song.addedAt,
        )
    }

    override fun deleteSong(id: String) {
        db.transaction {
            q.deleteSong(id)
            q.deleteSongFromPlaylists(id)
            q.deleteSongFromRankings(id)
            q.deletePlaysOf(id)
            q.deleteLyrics(id)
        }
    }

    override fun setScore(songId: String, score: Int?) {
        q.setScore(score = score?.toLong(), id = songId)
    }

    override fun savePlaylist(playlist: Playlist, position: Int) {
        db.transaction {
            q.upsertPlaylist(playlist.id, playlist.name, playlist.coverColor, position.toLong())
            q.clearPlaylistSongs(playlist.id)
            playlist.songIds.forEachIndexed { i, id -> q.insertPlaylistSong(playlist.id, id, i.toLong(), playlist.addedAt[id] ?: 0L) }
        }
    }

    override fun deletePlaylist(id: String) {
        db.transaction {
            q.clearPlaylistSongs(id)
            q.deletePlaylist(id)
        }
    }

    override fun saveRanking(ranking: CustomRanking, position: Int) {
        db.transaction {
            q.upsertRanking(ranking.id, ranking.name, position.toLong())
            q.clearRankingSongs(ranking.id)
            ranking.songIds.forEachIndexed { i, id -> q.insertRankingSong(ranking.id, id, i.toLong()) }
        }
    }

    override fun deleteRanking(id: String) {
        db.transaction {
            q.clearRankingSongs(id)
            q.deleteRanking(id)
        }
    }

    override fun addPlay(event: PlayEvent) {
        q.insertPlay(event.songId, event.atMillis)
    }

    override fun lyrics(songId: String): LyricsData? {
        val row = q.lyricsFor(songId).executeAsOneOrNull() ?: return null
        val data = LyricsData(row.synced, row.plain, row.instrumental != 0L)
        return if (data.isEmpty && !data.instrumental) null else data
    }

    override fun saveLyrics(songId: String, lyrics: LyricsData?, checkedAt: Long) {
        q.upsertLyrics(songId, lyrics?.synced, lyrics?.plain, if (lyrics?.instrumental == true) 1L else 0L, checkedAt)
    }

    override fun lyricsCheckedAt(songId: String): Long? = q.lyricsFor(songId).executeAsOneOrNull()?.checked_at

    override fun setting(key: String): String? = q.getSetting(key).executeAsOneOrNull()

    override fun setSetting(key: String, value: String?) {
        if (value == null) q.deleteSetting(key) else q.putSetting(key, value)
    }

    // ---------- Historial ----------

    override fun recentlyPlayed(limit: Int): List<String> =
        q.recentlyPlayed(limit.toLong()).executeAsList().map { it.song_id }

    override fun mostPlayedSince(sinceMillis: Long, limit: Int): List<Pair<String, Int>> =
        q.mostPlayedSince(sinceMillis, limit.toLong()).executeAsList().map { it.song_id to it.plays.toInt() }

    override fun unratedPlayed(): List<Pair<String, Int>> =
        q.unratedPlayed().executeAsList().map { it.id to it.plays.toInt() }
}
