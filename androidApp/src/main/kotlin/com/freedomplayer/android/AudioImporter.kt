package com.freedomplayer.android

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.freedomplayer.core.CoverPalette
import com.freedomplayer.core.Song
import com.freedomplayer.core.SongIds
import com.freedomplayer.data.DownloadedSong
import com.freedomplayer.jvm.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * «Añadir tus archivos de audio»: copia a la biblioteca lo que se elige en el selector del
 * sistema y lee su título, artista, álbum, duración y carátula (las etiquetas del archivo).
 */
class AudioImporter(private val context: Context, private val dirs: AppDirs) {

    /** Las que se han podido añadir y cuántas han fallado. */
    suspend fun import(uris: List<Uri>): Pair<List<DownloadedSong>, Int> = withContext(Dispatchers.IO) {
        var failed = 0
        val songs = uris.mapNotNull { uri ->
            runCatching { importOne(uri) }.getOrElse {
                failed++
                null
            }
        }
        songs to failed
    }

    private fun importOne(uri: Uri): DownloadedSong {
        val resolver = context.contentResolver
        var name = "audio"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
            }
        }
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri))
            ?: "audio"
        // El mismo archivo (nombre y tamaño) se reconoce si se vuelve a añadir.
        val key = hash("$name|$size")
        val id = SongIds.local(key)
        val target = File(dirs.music, "local_$key.$ext")
        val partial = File(dirs.partial, "local_$key.$ext.part")
        (resolver.openInputStream(uri) ?: error("No se puede abrir $uri")).use { input ->
            partial.outputStream().use { input.copyTo(it) }
        }
        target.delete()
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(target.absolutePath)
            fun tag(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            val durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                target.delete()
                error("No es un archivo de audio: $name")
            }
            val cover = retriever.embeddedPicture?.let { bytes ->
                File(dirs.covers, "local_$key.jpg").apply { writeBytes(bytes) }.absolutePath
            }
            val song = Song(
                id = id,
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast('.'),
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: "Artista desconocido",
                durationSec = ((durationMs + 500) / 1000).toInt(),
                coverColor = CoverPalette.forKey(key),
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                audioPath = target.absolutePath,
                cover = cover,
                addedAt = System.currentTimeMillis(),
            )
            return DownloadedSong(song, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
