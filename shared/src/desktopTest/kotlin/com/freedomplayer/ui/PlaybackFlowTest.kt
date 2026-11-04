package com.freedomplayer.ui

import com.freedomplayer.core.Playlist
import com.freedomplayer.core.Ranking
import com.freedomplayer.core.RepeatMode
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.Song
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.LibrarySnapshot
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
import com.freedomplayer.data.SoundSettings
import com.freedomplayer.data.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La reproducción vista desde la app (no los datos de ejemplo): la cola sigue con el resto de
 * la lista, aleatorio y repetir, las escuchas que alimentan Recientes, Más escuchadas y Sin nota,
 * la vista previa que pausa y reanuda, y la cola que se recuerda al volver a abrir.
 */
class PlaybackFlowTest {
    private val ids = (1..5).map { "s$it" }

    private fun songs() = ids.mapIndexed { i, id ->
        Song(id, "Canción ${i + 1}", "Artista", durationSec = 60, coverColor = 0xFF3B2F8F, audioPath = "/musica/$id.webm")
    }

    private fun store() = MemoryStore(LibrarySnapshot(songs(), listOf(Playlist("p", "Noches", ids, 0xFF7A1F55)), emptyList(), emptyList()))

    private fun app(store: MemoryStore = store()) =
        AppState(System.currentTimeMillis(), Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), SimulatedEngine()))

    /**
     * Deja pasar [seconds] segundos de reproducción, de segundo en segundo. Antes, una pausa de
     * verdad de unos milisegundos: así cada escucha guardada lleva una hora distinta.
     */
    private fun AppState.play(seconds: Int) {
        Thread.sleep(3)
        repeat(seconds) { step(1_000) }
    }

    @Test
    fun tappingASongPlaysTheRestOfTheList() {
        val state = app()
        state.playFrom(ids, "s3", "NOCHES")
        assertEquals("s3", state.currentSong?.id)
        assertTrue(state.isPlaying)
        state.play(61)
        assertEquals("s4", state.currentSong?.id, "al acabar, la siguiente de la lista")
        state.play(60)
        assertEquals("s5", state.currentSong?.id)
        state.play(60)
        assertEquals("s1", state.currentSong?.id, "al acabar la lista, vuelve a empezar")
        assertTrue(state.isPlaying, "y no se para")
    }

    @Test
    fun seekButtonsJumpTheChosenSecondsWithinTheSong() {
        val store = store()
        val state = app(store)
        state.playFrom(ids, "s2", "NOCHES")
        state.play(20)
        assertEquals(10, state.settings.seekSeconds, "lo normal: 10 s")
        state.seekBy(forward = true)
        assertEquals(30_000L, state.positionMs)
        state.settings.seekSeconds = 5
        state.seekBy(forward = false)
        assertEquals(25_000L, state.positionMs)
        repeat(10) { state.seekBy(forward = false) }
        assertEquals(0L, state.positionMs, "no pasa del principio")
        repeat(20) { state.seekBy(forward = true) }
        assertEquals(59_000L, state.positionMs, "ni del final")
        assertEquals("s2", state.currentSong?.id, "y no cambia de canción")
        assertEquals(5, app(store).settings.seekSeconds, "el ajuste se guarda")
    }

    @Test
    fun shuffleKeepsTheSongAndRepeatOneRepeatsIt() {
        val state = app()
        state.playFrom(ids, "s2", "NOCHES")
        state.play(10)
        state.toggleShuffle()
        assertEquals("s2", state.currentSong?.id, "aleatorio no corta la que suena")
        assertEquals(ids.toSet(), state.queue.items.toSet())
        state.toggleShuffle()
        assertEquals(ids, state.queue.items, "quitar aleatorio vuelve al orden de la lista")
        assertEquals("s2", state.currentSong?.id)

        assertEquals(RepeatMode.All, state.queue.repeat, "lo normal: seguir con la lista")
        state.cycleRepeat()
        assertEquals(RepeatMode.One, state.queue.repeat)
        state.play(50)
        assertEquals("s2", state.currentSong?.id, "repetir una: vuelve a empezar la misma")
        assertEquals(0, state.positionMs)
        assertEquals(1, state.song("s2")?.plays)
        state.play(30)
        assertEquals(2, state.song("s2")?.plays, "y cuenta otra escucha")
    }

    @Test
    fun playsFeedRecentMostPlayedAndUnrated() {
        val state = app()
        state.playFrom(ids, "s1", "NOCHES")
        state.play(29)
        assertTrue(state.recentSongs(5).isEmpty(), "antes de 30 s no cuenta")
        state.play(1)
        assertEquals(listOf("s1"), state.recentSongs(5).map { it.id })
        state.next()
        state.play(30)
        assertEquals(listOf("s2", "s1"), state.recentSongs(5).map { it.id }, "lo último, primero")
        state.previous()
        state.previous()
        state.play(30)
        assertEquals(listOf("s1" to 2, "s2" to 1), state.mostPlayed(days = 30, top = 3).map { it.first.id to it.second })
        assertEquals(listOf("s1", "s2"), Ranking.unrated(state.songs).map { it.id }, "Sin nota: sonadas y sin nota")
        state.rate("s1", 8)
        assertEquals(listOf("s2"), Ranking.unrated(state.songs).map { it.id })
    }

    @Test
    fun previewPausesWhatWasPlayingAndResumesIt() {
        val state = app()
        state.playFrom(ids, "s1", "NOCHES")
        state.play(5)
        val result = SearchResult("yt:abcdefghijk", "Otra", "Otro", 200, 0xFF3B2F8F, ResultKind.Cancion)
        state.togglePreview(result)
        assertTrue(!state.isPlaying, "la vista previa pausa lo que sonaba")
        assertEquals("yt:abcdefghijk", state.preview?.id)
        val position = state.positionMs
        state.play(3)
        assertEquals(position, state.positionMs, "la cola no avanza mientras tanto")
        state.stopPreview()
        assertNull(state.preview)
        assertTrue(state.isPlaying, "al cerrarla, vuelve lo que sonaba")
        assertEquals("s1", state.currentSong?.id)
    }

    @Test
    fun theQueueIsRememberedWhenTheAppOpensAgain() {
        val store = store()
        val first = app(store)
        first.playFrom(ids, "s4", "NOCHES")
        first.cycleRepeat()
        first.play(12)
        first.togglePlay()

        val again = app(store)
        assertEquals("s4", again.currentSong?.id)
        assertEquals(ids, again.queue.items)
        assertEquals(RepeatMode.One, again.queue.repeat)
        assertEquals("NOCHES", again.queueSource)
        assertTrue(again.positionMs in 11_000..13_000, "sigue por donde iba (${again.positionMs})")
        assertTrue(!again.isPlaying, "pero en pausa")
    }

    @Test
    fun aVideoTitleCanBeCorrectedLater() {
        val store = store()
        val state = app(store)
        state.prompt = Prompt.EditSong("s3")
        state.editSong("s3", "  Luz de farola ", "Ghostline")
        assertNull(state.prompt)
        assertEquals("Luz de farola" to "Ghostline", state.song("s3")?.let { it.title to it.artist })
        assertEquals("Luz de farola", store.load().songs.first { it.id == "s3" }.title, "queda guardado")
        state.editSong("s3", "   ", "Nadie")
        assertEquals("Luz de farola", state.song("s3")?.title, "sin título no se cambia")
    }

    @Test
    fun songsWithoutAudioAreSkippedWhenPlayingAList() {
        val store = MemoryStore(
            LibrarySnapshot(songs().map { if (it.id == "s2") it.copy(audioPath = null) else it }, emptyList(), emptyList(), emptyList()),
        )
        val state = app(store)
        state.playFrom(ids, "s1", "TU BIBLIOTECA")
        assertEquals(listOf("s1", "s3", "s4", "s5"), state.queue.items, "la que aún no se ha descargado no entra")
    }

    @Test
    fun theQueueCanBeReorderedTrimmedAndJumped() {
        val state = app()
        state.playFrom(ids, "s2", "NOCHES")
        state.play(5)
        state.moveInQueue(4, 2)
        assertEquals(listOf("s1", "s2", "s5", "s3", "s4"), state.queue.items)
        assertEquals("s2", state.currentSong?.id, "la que suena sigue sonando")
        assertTrue(state.positionMs >= 5_000, "sin cortes")

        state.removeFromQueue(3)
        assertEquals(listOf("s1", "s2", "s5", "s4"), state.queue.items)
        state.runToastAction()
        assertEquals(listOf("s1", "s2", "s5", "s3", "s4"), state.queue.items, "deshacer la vuelve a poner")
        state.removeFromQueue(3)
        state.removeFromQueue(1)
        assertEquals(listOf("s1", "s2", "s5", "s4"), state.queue.items, "la que suena no se quita")

        state.play(56)
        assertEquals("s5", state.currentSong?.id, "después suena la que se ha subido")
        state.playQueueItem(3)
        assertEquals("s4", state.currentSong?.id, "tocar una de la cola la pone a sonar")
        assertEquals(0, state.positionMs)
        assertTrue(state.isPlaying)
        assertEquals(listOf("s1", "s2", "s5", "s4"), state.queue.items, "y la cola no cambia")
    }

    @Test
    fun theSleepTimerPausesAfterTheChosenMinutes() {
        val state = app()
        state.playFrom(ids, "s1", "NOCHES")
        state.startSleepTimer(15)
        assertEquals(15 * 60_000L, state.sleepTimer?.remainingMs)
        state.play(14 * 60)
        assertTrue(state.isPlaying, "a los 14 minutos sigue sonando")
        assertEquals(60_000L, state.sleepTimer?.remainingMs)
        state.play(60)
        assertTrue(!state.isPlaying, "a los 15, se para")
        assertNull(state.sleepTimer)
        assertTrue(state.toast?.text.orEmpty().startsWith("Buenas noches"))

        // Apagarlo antes de tiempo: la música sigue.
        state.togglePlay()
        state.startSleepTimer(30)
        state.play(10)
        state.cancelSleepTimer()
        assertNull(state.sleepTimer)
        state.play(31 * 60)
        assertTrue(state.isPlaying)
    }

    @Test
    fun theSleepTimerCanWaitForTheEndOfTheSong() {
        val state = app()
        state.playFrom(ids, "s1", "NOCHES")
        state.play(20)
        state.sleepAtEndOfSong()
        assertTrue(state.sleepTimer?.endOfSong == true)
        state.play(39)
        assertTrue(state.isPlaying)
        state.play(2)
        assertTrue(!state.isPlaying, "se para al acabar la canción")
        assertEquals("s1", state.currentSong?.id, "sin pasar a la siguiente")
        assertNull(state.sleepTimer)

        // Con play sigue la siguiente, y ya sin temporizador.
        state.togglePlay()
        state.play(1)
        assertEquals("s2", state.currentSong?.id)
        assertTrue(state.isPlaying)
        state.play(61)
        assertEquals("s3", state.currentSong?.id, "la siguiente ya no se para")
        assertTrue(state.isPlaying)
    }

    /** El audio simulado, apuntando lo que la app le dice de cómo sonar y qué canciones le pasa. */
    private class RecordingEngine(private val inner: SimulatedEngine = SimulatedEngine()) : AudioEngine by inner {
        var lastSound: SoundSettings? = null
        var loaded: List<Track> = emptyList()

        override fun setSound(sound: SoundSettings) {
            lastSound = sound
        }

        override fun load(tracks: List<Track>, index: Int, positionMs: Long, play: Boolean) {
            loaded = tracks
            inner.load(tracks, index, positionMs, play)
        }
    }

    @Test
    fun soundSettingsReachTheEngine() {
        val engine = RecordingEngine()
        val state = AppState(System.currentTimeMillis(), Services(store(), SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), engine))
        assertEquals(SoundSettings(normalize = true, crossfadeMs = 0), engine.lastSound, "al abrir: volumen igualado sí, fundido no")
        state.settings.crossfade = true
        state.settings.crossfadeSeconds = 9
        state.setEqBand(0, 4.5f)
        state.step(100)
        assertEquals(SoundSettings(eqGains = listOf(4.5f, 0f, 0f, 0f, 0f), normalize = true, crossfadeMs = 9_000), engine.lastSound)
        state.settings.normalizeVolume = false
        state.settings.crossfade = false
        state.step(100)
        assertEquals(SoundSettings(eqGains = listOf(4.5f, 0f, 0f, 0f, 0f), normalize = false, crossfadeMs = 0), engine.lastSound)
    }

    @Test
    fun measuredSongsGetTheirLoudnessGain() {
        val store = store()
        store.setSetting("loudness.s1", "-10.0")
        store.setSetting("loudness.s2", "-20.0")
        val engine = RecordingEngine()
        val state = AppState(System.currentTimeMillis(), Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), engine))
        state.playFrom(ids, "s1", "NOCHES")
        val gains = engine.loaded.associate { it.id to it.gainDb }
        assertEquals(-6f, gains["s1"], "−10 LUFS: se baja hasta −16")
        assertEquals(0f, gains["s2"], "las flojas no se suben")
        assertEquals(0f, gains["s3"], "sin medir, tal cual")
    }
}
