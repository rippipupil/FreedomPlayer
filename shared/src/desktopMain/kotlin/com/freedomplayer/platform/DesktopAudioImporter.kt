package com.freedomplayer.platform

import com.freedomplayer.core.CoverPalette
import com.freedomplayer.core.Song
import com.freedomplayer.core.SongIds
import com.freedomplayer.data.DownloadedSong
import com.freedomplayer.jvm.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

/**
 * «Añadir tus archivos de audio» en el PC: copia a la biblioteca los archivos elegidos y lee sus
 * etiquetas con FFmpeg (título, artista, álbum, duración y, si la trae, la carátula). Como en
 * Android, el mismo archivo (nombre y tamaño) tiene siempre el mismo id.
 */
class DesktopAudioImporter(private val dirs: AppDirs, private val now: () -> Long = System::currentTimeMillis) {

    /** Las que se han podido añadir y cuántas han fallado. */
    suspend fun import(files: List<File>): Pair<List<DownloadedSong>, Int> = withContext(Dispatchers.IO) {
        var failed = 0
        val songs = files.mapNotNull { file ->
            runCatching { importOne(file) }.getOrElse {
                failed++
                null
            }
        }
        songs to failed
    }

    fun importOne(file: File): DownloadedSong {
        require(file.isFile) { "No es un archivo: $file" }
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "audio"
        val key = hash("$name|${file.length()}")
        val target = File(dirs.music, "local_$key.$ext")
        val partial = File(dirs.partial, "local_$key.$ext.part")
        file.copyTo(partial, overwrite = true)
        target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
        val coverFile = File(dirs.covers, "local_$key.jpg")
        val tags = try {
            read(target, coverFile)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        val song = Song(
            id = SongIds.local(key),
            title = tags.title ?: name.substringBeforeLast('.').ifBlank { name },
            artist = tags.artist ?: "Artista desconocido",
            durationSec = ((tags.durationMs + 500) / 1000).toInt(),
            coverColor = CoverPalette.forKey(key),
            album = tags.album,
            audioPath = target.absolutePath,
            cover = coverFile.takeIf { tags.hasCover }?.absolutePath,
            addedAt = now(),
        )
        return DownloadedSong(song, null)
    }

    private class Tags(val title: String?, val artist: String?, val album: String?, val durationMs: Long, val hasCover: Boolean)

    /** Lee las etiquetas y, si hay carátula, la guarda en [cover]. Falla si no es audio. */
    private fun read(file: File, cover: File): Tags {
        val g = FFmpegFrameGrabber(file)
        try {
            g.start()
            if (g.audioChannels <= 0) error("Sin audio: ${file.name}")
            val durationMs = g.lengthInTime / 1000
            if (durationMs <= 0L) error("Sin duración: ${file.name}")
            val meta = g.metadata.orEmpty().mapKeys { it.key.lowercase() }
            fun tag(vararg keys: String) = keys.firstNotNullOfOrNull { meta[it]?.trim()?.takeIf(String::isNotEmpty) }
            // La carátula de un mp3 o m4a llega como una imagen (un «vídeo» de un fotograma).
            val hasCover = g.hasVideo() && runCatching {
                val image = g.grabImage()?.let { Java2DFrameConverter().convert(it) } ?: return@runCatching false
                val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                rgb.createGraphics().apply { drawImage(image, 0, 0, null) }.dispose()
                ImageIO.write(rgb, "jpg", cover)
            }.getOrDefault(false)
            return Tags(
                title = tag("title"),
                artist = tag("artist", "album_artist", "albumartist", "author"),
                album = tag("album"),
                durationMs = durationMs,
                hasCover = hasCover,
            )
        } finally {
            runCatching {
                g.stop()
                g.release()
            }
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
