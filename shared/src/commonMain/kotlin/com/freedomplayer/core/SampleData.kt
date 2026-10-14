package com.freedomplayer.core

/**
 * Datos de ejemplo (los de los tableros de Claude Design) mientras no estén la base de datos,
 * el buscador de YouTube y la descarga de letras. Todo lo que aparece aquí es inventado.
 */
object SampleData {
    val songs = listOf(
        Song("s1", "Ahora no", "Ghostline", 192, 0xFF4A2A6B, plays = 18, score = 6, album = "Calles vacías"),
        Song("s2", "Bajo la farola", "Kaori Vale", 178, 0xFF3B2F8F, plays = 12, album = "Neón"),
        Song("s3", "Callejón 04", "Night Signal", 245, 0xFF1E5C50, plays = 33, score = 9, album = "Callejón"),
        Song("s4", "Desaparecer", "Lumen Club", 220, 0xFF5B2340, plays = 22, score = 7, album = "Rosa"),
        Song("s5", "Fantasma rosa", "Lumen Club", 201, 0xFF7A1F55, plays = 84, score = 10, album = "Rosa"),
        Song("s6", "Luz de farola", "Ghostline", 167, 0xFF4A2A6B, plays = 47, score = 8, album = "Calles vacías"),
        Song("s7", "Neón de medianoche", "Kaori Vale", 235, 0xFF3B2F8F, plays = 61, score = 9, album = "Neón"),
        Song("s8", "Farolas", "Kaori Vale", 211, 0xFF2A5E3F, plays = 4, album = "Neón"),
    )

    /** Orden de la última escucha de cada canción, de más reciente a menos (lo que sale en «Recientes»). */
    private val lastPlayedOrder = listOf("s7", "s5", "s3", "s6", "s4", "s1", "s2", "s8")

    /** Escuchas de los últimos 30 días; el resto de [Song.plays] son más antiguas. */
    private val playsLast30 = mapOf("s5" to 84, "s7" to 61, "s6" to 47, "s3" to 30, "s4" to 18, "s1" to 15, "s2" to 12, "s8" to 4)

    /**
     * Historial de escuchas de ejemplo, inventado a partir de [nowMillis] para que «Recientes»
     * y «Más escuchadas (30 días)» salgan como en el diseño.
     */
    fun playLog(nowMillis: Long): List<PlayEvent> {
        val hour = 3_600_000L
        val events = ArrayList<PlayEvent>()
        for (song in songs) {
            val recent = playsLast30[song.id] ?: 0
            if (recent > 0) {
                val rank = lastPlayedOrder.indexOf(song.id).coerceAtLeast(0)
                events += PlayEvent(song.id, nowMillis - (rank + 1) * hour)
                for (i in 1 until recent) {
                    events += PlayEvent(song.id, nowMillis - 10 * hour - i * (29L * 24 - 10) * hour / recent)
                }
            }
            for (i in 0 until (song.plays - recent).coerceAtLeast(0)) {
                events += PlayEvent(song.id, nowMillis - (45L + 20L * i) * 24 * hour)
            }
        }
        return events.sortedBy { it.atMillis }
    }

    val playlists = listOf(
        Playlist("p1", "Noches de neón", listOf("s5", "s7", "s3", "s6", "s2", "s8"), 0xFF3B2F8F),
        Playlist("p2", "Gym", listOf("s3", "s4", "s5"), 0xFF7A1F55),
        Playlist("p3", "Para programar", listOf("s1", "s6", "s8", "s2"), 0xFF1E5C50),
        Playlist("p4", "Chill fantasma", listOf("s6", "s2"), 0xFF4A2A6B),
        Playlist("p5", "Importada de Spotify", listOf("s1", "s4", "s7", "s8"), 0xFF2A5E3F),
        Playlist("p6", "Favoritas 2026", listOf("s5", "s7"), 0xFF5B2340),
    )

    val customRankings = listOf(
        CustomRanking("r1", "Mejores de 2026", listOf("s3", "s5", "s7", "s6", "s4")),
        CustomRanking("r2", "Top gym", listOf("s5", "s3", "s4", "s7", "s1")),
        CustomRanking("r3", "Para noches tristes", listOf("s6", "s2", "s1", "s8")),
    )

    /** Lo que «encuentra» el buscador de ejemplo: canciones de YouTube Music y vídeos de YouTube. */
    val catalog: List<SearchResult> = songs.map {
        SearchResult(it.id, it.title, it.artist, it.durationSec, it.coverColor, ResultKind.Cancion, album = it.album)
    } + listOf(
        SearchResult("c1", "Luz de farola (versión acústica)", "Ghostline", 185, 0xFF5B2340, ResultKind.Cancion, album = "Sencillo"),
        SearchResult("c2", "La luz que queda", "Night Signal", 252, 0xFF1E5C50, ResultKind.Cancion, album = "Callejón"),
        SearchResult("c3", "Luces de ciudad", "Lumen Club", 182, 0xFF7A1F55, ResultKind.Cancion, album = "Rosa"),
        SearchResult("c4", "Señal perdida", "Night Signal", 224, 0xFF1E5C50, ResultKind.Cancion, album = "Callejón"),
        SearchResult("c5", "Pétalos de neón", "Lumen Club", 198, 0xFF7A1F55, ResultKind.Cancion, album = "Rosa"),
        SearchResult("c6", "Té de jazmín", "Kaori Vale", 171, 0xFF3B2F8F, ResultKind.Cancion, album = "Neón"),
        SearchResult("c7", "Medianoche en el corcho", "Ghostline", 207, 0xFF4A2A6B, ResultKind.Cancion, album = "Calles vacías"),
        SearchResult("v1", "Ghostline — Luz de farola (en directo)", "Ghostline", 238, 0xFF3A2550, ResultKind.Video, note = "1,2 M visualizaciones"),
        SearchResult("v2", "Luz de farola (remix nocturno)", "Canal de fans", 280, 0xFF552243, ResultKind.Video, note = "Canal de fans · 210 k"),
        SearchResult("v3", "Luz de farola — cover a piano", "Piano Ghost", 190, 0xFF2B3F5E, ResultKind.Video, note = "Piano Ghost · 88 k"),
        // Títulos tal cual vienen de YouTube: al añadirlos se limpian (TitleCleaner).
        SearchResult("v4", "Lumen Club - Fantasma rosa (en directo) (Official Music Video)", "Lumen Club", 242, 0xFF552243, ResultKind.Video, note = "640 k visualizaciones"),
        SearchResult("v5", "Neón de medianoche (slowed + reverb) [Lyric Video]", "nocturna fm", 295, 0xFF3A2550, ResultKind.Video, note = "nocturna fm · 95 k"),
        SearchResult("v6", "Callejón 04 — cover acústico (Video Oficial) HD", "Sofía Rueda", 229, 0xFF2B3F5E, ResultKind.Video, note = "Sofía Rueda · 12 k"),
    )

    val albums = listOf(
        AlbumResult("Calles vacías", "Ghostline", "2025", 0xFF4A2A6B, listOf("s1", "s6", "c7")),
        AlbumResult("Neón", "Kaori Vale", "2024", 0xFF3B2F8F, listOf("s2", "s7", "s8", "c6")),
        AlbumResult("Callejón", "Night Signal", "EP 2026", 0xFF1E5C50, listOf("s3", "c2", "c4")),
        AlbumResult("Rosa", "Lumen Club", "2025", 0xFF7A1F55, listOf("s4", "s5", "c3", "c5")),
    )

    /** Letra sincronizada de ejemplo (la del tablero del reproductor). */
    private val lyrics = mapOf(
        "s5" to listOf(
            LyricLine(60, "Bajo la luz de la farola"),
            LyricLine(72, "nadie ve lo que yo veo"),
            LyricLine(84, "un fantasma rosa me saluda"),
            LyricLine(96, "y se esconde en el neón"),
            LyricLine(108, "cuando apagan la ciudad"),
            LyricLine(120, "sigue brillando en el callejón"),
        ),
    )

    fun lyricsFor(songId: String): List<LyricLine> = lyrics[songId].orEmpty()

    /** Lo que «devuelve» Spotify al pegar cualquier enlace de playlist mientras no esté la importación real. */
    const val SPOTIFY_PLAYLIST_NAME = "Noches en el coche"

    val spotifyTracks = listOf(
        SpotifyTrack("Fantasma Rosa", "Lumen Club", 201),
        SpotifyTrack("Neón de Medianoche", "Kaori Vale", 235),
        SpotifyTrack("Callejón 04", "Night Signal", 245),
        SpotifyTrack("Señal Perdida", "Night Signal", 224),
        SpotifyTrack("Luces de la ciudad", "Lumen Club", 182),
        SpotifyTrack("Pétalos de Neón", "Lumen Club", 198),
        SpotifyTrack("Té de jazmín (Live)", "Kaori Vale", 260),
        SpotifyTrack("Medianoche en el corcho", "Ghostline", 207),
        SpotifyTrack("Lluvia en Tokio", "Kaori Vale", 190),
    )
}
