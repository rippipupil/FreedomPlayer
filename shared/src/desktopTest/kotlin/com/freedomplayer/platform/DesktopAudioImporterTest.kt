package com.freedomplayer.platform

import com.freedomplayer.jvm.AppDirs
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.io.File
import java.nio.ShortBuffer
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Añadir tus archivos de audio» en el PC: se copian a la biblioteca y se leen sus etiquetas. */
class DesktopAudioImporterTest {
    private val dir: File = Files.createTempDirectory("fp-importar").toFile()
    private val library = AppDirs(File(dir, "biblioteca"))
    private val importer = DesktopAudioImporter(library) { 1_700_000_000_000 }

    @AfterTest
    fun clean() {
        dir.deleteRecursively()
    }

    /**
     * Un m4a de [seconds] segundos con las etiquetas que se den. FFmpeg lo escribe con un nombre
     * sencillo y luego se le pone el de verdad (con tildes o espacios) desde Java.
     */
    private fun m4a(name: String, seconds: Double, tags: Map<String, String> = emptyMap()): File {
        val file = File(dir, "grabando-${System.nanoTime()}.m4a")
        val rate = 44_100
        val samples = ShortArray((seconds * rate).toInt() * 2) { i -> (sin(2 * PI * 440 * (i / 2).toDouble() / rate) * 8000).toInt().toShort() }
        val recorder = FFmpegFrameRecorder(file, 2)
        recorder.format = "ipod"
        recorder.audioCodec = avcodec.AV_CODEC_ID_AAC
        recorder.sampleRate = rate
        recorder.audioBitrate = 128_000
        tags.forEach { (k, v) -> recorder.setMetadata(k, v) }
        recorder.start()
        recorder.recordSamples(rate, 2, ShortBuffer.wrap(samples))
        recorder.stop()
        recorder.release()
        return File(dir, name).also { file.copyTo(it, overwrite = true) }
    }

    @Test
    fun copiesTheFileAndReadsItsTags() {
        val source = m4a("pista 01.m4a", 2.0, mapOf("title" to "Luz de farola", "artist" to "Ghostline", "album" to "Calles vacías"))
        val (songs, failed) = runBlocking { importer.import(listOf(source)) }
        assertEquals(0, failed)
        val song = songs.single().song
        assertEquals("Luz de farola", song.title)
        assertEquals("Ghostline", song.artist)
        assertEquals("Calles vacías", song.album)
        assertEquals(2, song.durationSec)
        assertTrue(song.id.startsWith("local:"), song.id)
        assertEquals(1_700_000_000_000, song.addedAt)
        val copy = File(song.audioPath!!)
        assertTrue(copy.isFile && copy.parentFile == library.music, "se copia a la biblioteca: $copy")
        assertTrue(source.isFile, "el original no se toca")
        assertNull(song.cover, "sin carátula en el archivo, sin carátula")
        assertTrue(library.partial.listFiles().orEmpty().isEmpty(), "no quedan copias a medias")
    }

    @Test
    fun withoutTagsTheNameOfTheFileIsTheTitle() {
        val source = m4a("Mi canción.m4a", 1.0)
        val song = runBlocking { importer.import(listOf(source)) }.first.single().song
        assertEquals("Mi canción", song.title)
        assertEquals("Artista desconocido", song.artist)
    }

    @Test
    fun theSameFileTwiceIsTheSameSong() {
        val source = m4a("dos veces.m4a", 1.0)
        val first = runBlocking { importer.import(listOf(source)) }.first.single().song
        val second = runBlocking { importer.import(listOf(source)) }.first.single().song
        assertEquals(first.id, second.id)
        assertEquals(1, library.music.listFiles().orEmpty().size)
    }

    @Test
    fun whatIsNotAudioFailsAndLeavesNothingBehind() {
        val text = File(dir, "notas.mp3").apply { writeText("esto no es una canción") }
        val good = m4a("buena.m4a", 1.0)
        val (songs, failed) = runBlocking { importer.import(listOf(text, good, File(dir, "no-existe.mp3"))) }
        assertEquals(2, failed)
        assertEquals(listOf("buena"), songs.map { it.song.title })
        assertEquals(1, library.music.listFiles().orEmpty().size, "lo que falla no se queda en la biblioteca")
    }

    @Test
    fun mediaKeysOnlyOnWindows() {
        assertEquals(setOf(0xB0, 0xB1, 0xB2, 0xB3), MediaKeys.codes.values.toSet())
        assertEquals(MediaKey.entries.toSet(), MediaKeys.codes.keys)
        if (!MediaKeys.supported) assertNull(MediaKeys.listen { }, "fuera de Windows no se registra nada")
    }
}
