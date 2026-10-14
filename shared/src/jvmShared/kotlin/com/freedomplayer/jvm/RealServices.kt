package com.freedomplayer.jvm

import app.cash.sqldelight.db.SqlDriver
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.LoudnessAnalyzer
import com.freedomplayer.data.Services
import com.freedomplayer.data.SqlLibraryStore
import com.freedomplayer.jvm.youtube.YouTubeSource
import java.util.Locale

/**
 * Lo de verdad, igual en Android y en el PC: biblioteca en SQLite ([driver]), YouTube con
 * NewPipe Extractor, descargas en [dirs] y letras de LRCLIB. El audio (y medir su sonoridad) lo
 * pone cada plataforma.
 */
fun realServices(
    driver: SqlDriver,
    dirs: AppDirs,
    engine: AudioEngine,
    loudness: LoudnessAnalyzer? = null,
    locale: Locale = Locale.getDefault(),
): Services {
    val youtube = YouTubeSource(locale)
    val lyrics = Lrclib()
    return Services(
        store = SqlLibraryStore(driver),
        source = youtube,
        downloader = YouTubeDownloader(youtube, lyrics, dirs),
        lyrics = lyrics,
        engine = engine,
        loudness = loudness,
    )
}
