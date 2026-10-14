package com.freedomplayer.jvm

import com.freedomplayer.core.AudioOption
import com.freedomplayer.core.AudioPicker
import com.freedomplayer.core.LyricsData
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.Song
import com.freedomplayer.core.SongIds
import com.freedomplayer.data.DownloadRequest
import com.freedomplayer.data.DownloadedSong
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.LyricsSource
import com.freedomplayer.data.MediaDownloader
import com.freedomplayer.data.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** El audio que se va a descargar de un resultado, ya elegido. */
data class ResolvedAudio(
    val option: AudioOption,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val coverUrl: String?,
    /** Todas las pistas que se pueden bajar, para elegir otra calidad; [option] es la mejor. */
    val options: List<AudioOption> = listOf(option),
)

/** Quien sabe sacar el audio de un resultado del buscador (YouTube, con NewPipe). */
fun interface AudioResolver {
    suspend fun resolve(result: SearchResult): ResolvedAudio
}

/**
 * Descarga un archivo por trozos con «Range», para ver el progreso, seguir donde se quedó si se
 * corta y no pedirle a YouTube el archivo entero de una vez (lo frena).
 */
class FileFetcher(
    private val client: OkHttpClient = Http.client,
    private val chunkBytes: Long = 1L shl 20,
    private val attemptsPerChunk: Int = 3,
) {
    /** Descarga [url] en [target]. [progress] va de 0 a 1 (si se conoce el tamaño). */
    suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        target: File,
        knownLength: Long = -1,
        progress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        var length = knownLength
        var position = if (target.exists()) target.length() else 0L
        if (length in 1..position) {
            progress(1f)
            return@withContext
        }
        RandomAccessFile(target, "rw").use { out ->
            out.seek(position)
            val buffer = ByteArray(64 * 1024)
            while (length < 0 || position < length) {
                currentCoroutineContext().ensureActive()
                val chunkEnd = position + chunkBytes - 1
                var attempt = 0
                var gotBytes = 0L
                while (true) {
                    try {
                        // Si un intento se corta a medias, el siguiente pide solo lo que falta del trozo.
                        val range = if (length > 0) "bytes=$position-${minOf(chunkEnd, length - 1)}" else "bytes=$position-$chunkEnd"
                        val request = Request.Builder().url(url).apply {
                            headers.forEach { (k, v) -> header(k, v) }
                            header("Range", range)
                        }.build()
                        client.newCall(request).execute().use { response ->
                            if (response.code == 416 && length < 0) {
                                // Ya no queda nada: el tamaño era justo lo descargado.
                                length = position
                                return@use
                            }
                            if (response.code == 403 || response.code == 429) {
                                throw SourceException(FailureKind.Blocked, "Descarga: HTTP ${response.code}")
                            }
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            if (response.code == 200 && position > 0) {
                                // El servidor no admite trozos: se empieza de cero.
                                out.setLength(0)
                                out.seek(0)
                                position = 0
                            }
                            if (length < 0) {
                                length = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                                    ?: if (response.code == 200) response.body.contentLength() else -1
                            }
                            response.body.byteStream().use { input ->
                                while (true) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    position += n
                                    gotBytes += n
                                    if (length > 0) progress((position.toFloat() / length).coerceIn(0f, 1f))
                                }
                            }
                            if (response.code == 200) length = position
                        }
                        break
                    } catch (e: SourceException) {
                        throw e
                    } catch (e: IOException) {
                        attempt++
                        if (attempt >= attemptsPerChunk) throw Http.failure(e, "Descarga")
                        currentCoroutineContext().ensureActive()
                        Thread.sleep(600L * attempt)
                    }
                }
                if (length < 0 && gotBytes < chunkBytes) length = position
                if (gotBytes == 0L && length > 0 && position < length) {
                    throw SourceException(FailureKind.Network, "Descarga: el servidor no devuelve datos")
                }
            }
        }
        progress(1f)
    }

    /** Descarga algo pequeño (una carátula) de una vez. */
    suspend fun bytes(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = 8 shl 20): ByteArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    if (body.contentLength() > maxBytes) return@use null
                    body.bytes().takeIf { it.size in 1..maxBytes }
                }
            }.getOrNull()
        }
}

/**
 * Añadir de verdad: el mejor audio (con NewPipe), la carátula y la letra sincronizada de LRCLIB.
 * Todo se guarda en [dirs]; la letra se devuelve para guardarla en la base de datos.
 */
class YouTubeDownloader(
    private val resolver: AudioResolver,
    private val lyrics: LyricsSource,
    private val dirs: AppDirs,
    private val fetcher: FileFetcher = FileFetcher(),
    private val now: () -> Long = { System.currentTimeMillis() },
) : MediaDownloader {

    override suspend fun download(request: DownloadRequest, progress: (Float) -> Unit): DownloadedSong =
        withContext(Dispatchers.IO) { downloadNow(request, progress) }

    /** Todo fuera del hilo principal: red, archivos y carátula. */
    private suspend fun downloadNow(request: DownloadRequest, progress: (Float) -> Unit): DownloadedSong {
        val result = request.result
        progress(0f)
        val audio = resolver.resolve(result)
        // La pista de la calidad elegida en Ajustes (la mejor si no hay otra).
        val option = AudioPicker.pick(audio.options, request.quality) ?: audio.option
        progress(0.04f)
        val key = fileKey(result.id)
        val partial = File(dirs.partial, "$key.${option.extension}.part")
        // Si se reintenta, se sigue donde se quedó (misma pista y mismo tamaño).
        try {
            fetcher.fetch(option.url, option.headers, partial, option.contentLength) { p ->
                progress(0.04f + p * 0.88f)
            }
        } catch (e: SourceException) {
            // Las direcciones de YouTube caducan: la siguiente vez se pide otra y se empieza de cero.
            if (e.kind == FailureKind.Blocked) partial.delete()
            throw e
        }
        if (!partial.exists() || partial.length() == 0L) {
            partial.delete()
            throw SourceException(FailureKind.Network, "Descarga vacía")
        }
        val audioFile = File(musicFolder(request.folder), "$key.${option.extension}")
        audioFile.delete()
        if (!partial.renameTo(audioFile)) {
            partial.copyTo(audioFile, overwrite = true)
            partial.delete()
        }

        val coverUrl = audio.coverUrl ?: result.cover
        val coverFile = coverUrl?.let { url ->
            fetcher.bytes(url)?.let { bytes ->
                File(dirs.covers, "$key.jpg").apply { writeBytes(bytes) }
            }
        }
        progress(0.96f)

        val durationSec = if (audio.durationSec > 0) audio.durationSec else result.durationSec
        val found: LyricsData? = runCatching { lyrics.find(request.title, request.artist, request.album ?: result.album, durationSec) }.getOrNull()
        progress(1f)

        val song = Song(
            id = result.id,
            title = request.title,
            artist = request.artist,
            durationSec = durationSec,
            coverColor = result.coverColor,
            album = request.album ?: result.album,
            audioPath = audioFile.absolutePath,
            cover = coverFile?.absolutePath ?: coverUrl,
            sourceUrl = result.url.ifEmpty { null },
            addedAt = now(),
        )
        return DownloadedSong(song, found)
    }

    /**
     * Dónde va el audio: la carpeta elegida en Ajustes, si existe (o se puede crear) y se puede
     * escribir en ella; si no (un disco que ya no está…), la de la app.
     */
    private fun musicFolder(chosen: String?): File {
        val dir = chosen?.takeIf { it.isNotBlank() }?.let(::File) ?: return dirs.music
        return if ((dir.isDirectory || dir.mkdirs()) && dir.canWrite()) dir else dirs.music
    }

    override fun deleteFiles(song: Song) {
        // Fuera de la carpeta de la app solo se borra lo que bajó la app (se llama como la canción).
        song.audioPath?.let { path -> File(path).takeIf { it.startsWith(dirs.root) || isOwnDownload(it, song) }?.delete() }
        song.cover?.let { path -> File(path).takeIf { it.isFile && it.startsWith(dirs.root) }?.delete() }
    }

    private fun isOwnDownload(file: File, song: Song): Boolean =
        file.isFile && !SongIds.isLocal(song.id) && file.nameWithoutExtension == fileKey(song.id)

    companion object {
        /** «yt:abc» → «yt_abc», válido como nombre de archivo en todos los sistemas. */
        fun fileKey(id: String): String = id.replace(Regex("""[^A-Za-z0-9_-]"""), "_")
    }
}
