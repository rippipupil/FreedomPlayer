package com.freedomplayer.ui

import com.freedomplayer.core.GhostSkin
import com.freedomplayer.core.PlayEvent
import com.freedomplayer.core.Song
import com.freedomplayer.core.Tempo
import com.freedomplayer.data.AudioEngine
import com.freedomplayer.data.LibrarySnapshot
import com.freedomplayer.data.MemoryStore
import com.freedomplayer.data.SampleLyrics
import com.freedomplayer.data.SampleSource
import com.freedomplayer.data.Services
import com.freedomplayer.data.SimulatedDownloader
import com.freedomplayer.data.SimulatedEngine
import com.freedomplayer.data.SoundSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fantasmas coleccionables, el Wrapped y la velocidad y el tono, vistos desde la app. */
class GhostsAndTempoFlowTest {
    private fun songs(n: Int) = (1..n).map { Song("s$it", "Canción $it", "Artista", 60, 0, audioPath = "/musica/s$it.webm") }

    private fun store(n: Int, log: List<PlayEvent> = emptyList()) = MemoryStore(LibrarySnapshot(songs(n), emptyList(), emptyList(), log))

    private fun app(store: MemoryStore, engine: AudioEngine = SimulatedEngine()) =
        AppState(System.currentTimeMillis(), Services(store, SampleSource(latencyMs = 0), SimulatedDownloader(), SampleLyrics(), engine))

    @Test
    fun ghostsWonBeforeUpdatingAreNotAnnounced() {
        val state = app(store(50))
        state.step(100)
        assertNull(state.toast, "sin avisos al estrenar la versión")
        assertEquals(setOf(GhostSkin.Clasico, GhostSkin.Coleccionista), state.settings.ghostsAnnounced)
        assertTrue(GhostSkin.Coleccionista in state.ownedGhosts())
    }

    @Test
    fun aNewGhostWaitsForTheRatingToastAndCanBeWorn() {
        val store = store(3)
        val state = app(store)
        state.step(100)
        state.rate("s1", 10)
        state.step(100)
        assertEquals("DESHACER", state.toast?.actionLabel, "el aviso de la nota no se pisa")
        state.dismissToast()
        state.step(100)
        val toast = assertNotNull(state.toast)
        assertTrue("Rey" in toast.text, toast.text)
        state.runToastAction()
        assertEquals(Screen.Ghosts, state.screen)

        state.chooseGhost(GhostSkin.Dorado)
        assertEquals(GhostSkin.Clasico, state.settings.ghostSkin, "uno sin ganar no se puede poner")
        state.chooseGhost(GhostSkin.Rey)
        assertEquals(GhostSkin.Rey, state.settings.ghostSkin)
        assertEquals(GhostSkin.Rey, app(store).settings.ghostSkin, "se recuerda")
    }

    @Test
    fun aWonGhostIsKeptEvenIfTheReasonGoes() {
        val store = store(3)
        val state = app(store)
        state.step(100)
        state.rate("s1", 10)
        state.dismissToast()
        state.step(100)
        state.dismissToast()
        state.rate("s1", 4)
        state.step(100)
        assertTrue(GhostSkin.Rey in state.ownedGhosts(), "ya no hay un 10, pero el Rey sigue siendo tuyo")
    }

    @Test
    fun openingTheWrappedWinsTheParty() {
        val now = System.currentTimeMillis()
        val log = listOf(PlayEvent("s1", now - 1_000), PlayEvent("s2", now))
        val state = app(store(3, log))
        state.step(100)
        state.openWrapped()
        assertEquals(Screen.Wrapped, state.screen)
        assertTrue(state.settings.wrappedSeen)
        state.step(100)
        assertTrue("Fiesta" in state.toast?.text.orEmpty(), state.toast?.text)
        val periods = state.wrappedPeriods()
        assertTrue(periods.first().month == null, "primero el año: $periods")
        assertEquals(2, state.wrappedReport(periods[1])?.plays, "el mes")
        assertEquals(2, state.wrappedReport(periods[0])?.plays, "el año")
    }

    /** El audio simulado, apuntando lo que la app le dice de cómo sonar. */
    private class RecordingEngine(private val pitchApart: Boolean, private val inner: SimulatedEngine = SimulatedEngine()) : AudioEngine by inner {
        var lastSound: SoundSettings? = null
        override val separatePitch: Boolean get() = pitchApart

        override fun setSound(sound: SoundSettings) {
            lastSound = sound
        }
    }

    @Test
    fun speedAndPitchReachTheEngineAndAreRemembered() {
        val store = store(3)
        val engine = RecordingEngine(pitchApart = true)
        val state = app(store, engine)
        state.settings.tempo = Tempo(125, 2)
        state.step(100)
        assertEquals(1.25f, engine.lastSound?.speed)
        assertEquals(Tempo(125, 2).pitch, engine.lastSound?.pitch)
        assertEquals(Tempo(125, 2), app(store).settings.tempo)
    }

    @Test
    fun onThePcThePitchGoesWithTheSpeed() {
        val engine = RecordingEngine(pitchApart = false)
        val state = app(store(3), engine)
        assertTrue(!state.separatePitch)
        state.settings.tempo = Tempo(85, 3)
        state.step(100)
        assertEquals(0.85f, engine.lastSound?.speed)
        assertEquals(0.85f, engine.lastSound?.pitch)
    }
}
