package com.freedomplayer.jvm.youtube

import com.freedomplayer.core.AlbumResult
import com.freedomplayer.core.AudioOption
import com.freedomplayer.core.AudioPicker
import com.freedomplayer.core.CoverPalette
import com.freedomplayer.core.ResultKind
import com.freedomplayer.core.SearchResult
import com.freedomplayer.core.SongIds
import com.freedomplayer.core.TitleCleaner
import com.freedomplayer.core.compactCount
import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.MusicSource
import com.freedomplayer.data.SourceException
import com.freedomplayer.data.StreamSource
import com.freedomplayer.jvm.AudioResolver
import com.freedomplayer.jvm.Http
import com.freedomplayer.jvm.ResolvedAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import java.util.Locale

/**
 * El buscador de verdad, con NewPipe Extractor: canciones y álbumes de YouTube Music y vídeos
 * de YouTube. También da el audio de cada resultado (para escucharlo antes y para descargarlo).
 */
class YouTubeSource(locale: Locale = Locale.getDefault()) : MusicSource, AudioResolver {
    init {
        ensureInit(locale)
    }

    private val youtube get() = ServiceList.YouTube

    override suspend fun searchSongs(query: String): List<SearchResult> =
        search(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS)
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toResult(ResultKind.Cancion) }

    override suspend fun searchVideos(query: String): List<SearchResult> =
        search(query, YoutubeSearchQueryHandlerFactory.VIDEOS)
            .filterIsInstance<StreamInfoItem>()
            .filter { it.streamType == StreamType.VIDEO_STREAM || it.streamType == StreamType.AUDIO_STREAM }
            .mapNotNull { it.toResult(ResultKind.Video) }

    override suspend fun searchAlbums(query: String): List<AlbumResult> =
        search(query, YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS)
            .filterIsInstance<PlaylistInfoItem>()
            .map { item ->
                val artist = TitleCleaner.cleanChannel(item.uploaderName.orEmpty())
                AlbumResult(
                    title = item.name,
                    artist = artist,
                    year = null,
                    coverColor = CoverPalette.forKey(item.url),
                    url = item.url,
                    cover = bestImage(item.thumbnails, Size.Row),
                )
            }

    override suspend fun albumTracks(album: AlbumResult): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val info = PlaylistInfo.getInfo(youtube, album.url)
            val items = info.relatedItems.toMutableList()
            var page = info.nextPage
            // Un álbum cabe en una página; por si acaso, como mucho 200 canciones.
            while (page != null && items.size < 200) {
                val more = PlaylistInfo.getMoreItems(youtube, album.url, page)
                items += more.items
                page = if (more.hasNextPage()) more.nextPage else null
            }
            val cover = bestImage(info.thumbnails, Size.Cover) ?: album.cover
            items.mapNotNull { item ->
                item.toResult(ResultKind.Cancion)?.let { r ->
                    r.copy(
                        album = album.title,
                        // En la lista del álbum, el «artista» de cada canción a veces viene vacío.
                        artist = r.artist.ifBlank { album.artist },
                        cover = r.cover ?: cover,
                    )
                }
            }
        } catch (e: Throwable) {
            throw classify(e, "Álbum")
        }
    }

    override suspend fun stream(result: SearchResult): StreamSource {
        val audio = resolve(result)
        return StreamSource(audio.option.url, audio.option.headers, audio.option.mimeType, audio.durationSec)
    }

    override suspend fun resolve(result: SearchResult): ResolvedAudio = withContext(Dispatchers.IO) {
        try {
            val url = result.url.ifEmpty { "https://www.youtube.com/watch?v=${SongIds.videoIdOf(result.id.removePrefix("yt:")) ?: result.id.removePrefix("yt:")}" }
            val info = StreamInfo.getInfo(youtube, url)
            val options = info.audioStreams.map { s ->
                val streamUrl = if (s.isUrl) s.content else ""
                AudioOption(
                    url = streamUrl,
                    mimeType = s.format?.mimeType ?: "audio/*",
                    extension = s.format?.suffix ?: "audio",
                    bitrate = if (s.bitrate > 0) s.bitrate else s.averageBitrate.coerceAtLeast(0) * 1000,
                    progressive = s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && s.isUrl,
                    contentLength = s.itagItem?.contentLength ?: -1L,
                    original = s.audioTrackType == null || s.audioTrackType == AudioTrackType.ORIGINAL,
                    headers = headersFor(streamUrl),
                )
            }
            val best = AudioPicker.best(options)
                ?: throw SourceException(FailureKind.Changed, "Sin pistas de audio descargables (${options.size} en total)")
            ResolvedAudio(
                option = best,
                title = info.name,
                artist = info.uploaderName.orEmpty(),
                durationSec = info.duration.toInt().coerceAtLeast(result.durationSec),
                coverUrl = bestImage(info.thumbnails, Size.Cover) ?: result.cover,
            )
        } catch (e: Throwable) {
            throw classify(e, "Audio")
        }
    }

    private suspend fun search(query: String, filter: String): List<InfoItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val extractor = youtube.getSearchExtractor(query.trim(), listOf(filter), "")
            extractor.fetchPage()
            extractor.initialPage.items
        } catch (e: Throwable) {
            throw classify(e, "Buscar")
        }
    }

    private fun StreamInfoItem.toResult(kind: ResultKind): SearchResult? {
        val videoId = SongIds.videoIdOf(url) ?: return null
        val channel = uploaderName.orEmpty()
        val views = viewCount
        return SearchResult(
            id = SongIds.youtube(videoId),
            title = name,
            artist = if (kind == ResultKind.Cancion) TitleCleaner.cleanChannel(channel) else channel,
            durationSec = duration.toInt().coerceAtLeast(0),
            coverColor = CoverPalette.forKey(videoId),
            kind = kind,
            note = if (kind == ResultKind.Video) {
                if (views >= 0) "$channel · ${compactCount(views)} visualizaciones" else channel
            } else {
                null
            },
            url = url,
            cover = bestImage(thumbnails, Size.Row),
        )
    }

    private enum class Size { Row, Cover }

    companion object {
        @Volatile
        private var initialized = false

        /** NewPipe se prepara una sola vez, con el idioma y el país del sistema. */
        @Synchronized
        fun ensureInit(locale: Locale = Locale.getDefault()) {
            if (initialized) return
            val language = locale.language.ifBlank { "es" }
            val country = locale.country.ifBlank { "ES" }
            NewPipe.init(NewPipeHttp(), Localization(language, country), ContentCountry(country))
            initialized = true
        }

        /** Cabeceras que pide YouTube según el cliente que dio la dirección del audio. */
        fun headersFor(url: String): Map<String, String> = when {
            url.isEmpty() -> emptyMap()
            YoutubeParsingHelper.isAndroidStreamingUrl(url) -> mapOf("User-Agent" to YoutubeParsingHelper.getAndroidUserAgent(null))
            YoutubeParsingHelper.isIosStreamingUrl(url) -> mapOf("User-Agent" to YoutubeParsingHelper.getIosUserAgent(null))
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> mapOf("User-Agent" to YoutubeParsingHelper.getVisionOsUserAgent(null))
            else -> mapOf(
                "User-Agent" to Http.BROWSER_UA,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/",
            )
        }

        /**
         * La imagen más grande de la lista. Las carátulas de YouTube Music llegan pequeñas
         * («=w120-h120…»), así que se piden más grandes cambiando el tamaño de la dirección.
         */
        private fun bestImage(images: List<Image>, size: Size): String? {
            val best = images.maxByOrNull { maxOf(it.height, it.width) } ?: return null
            val px = if (size == Size.Cover) 800 else 226
            return best.url.replace(Regex("""=w\d+-h\d+"""), "=w$px-h$px")
                .replace(Regex("""=s\d+"""), "=s$px")
        }

        /** Convierte los errores de NewPipe en algo que se pueda explicar en un aviso. */
        fun classify(e: Throwable, what: String): SourceException = when (e) {
            is SourceException -> e
            is ReCaptchaException, is SignInConfirmNotBotException ->
                SourceException(FailureKind.Blocked, "$what: YouTube pide verificación (${e.message})", e)
            is ContentNotAvailableException ->
                SourceException(FailureKind.Unavailable, "$what: ${e.javaClass.simpleName}: ${e.message}", e)
            is ExtractionException -> {
                // NewPipe envuelve los errores de red en excepciones de extracción.
                val io = generateSequence(e.cause) { it.cause }.firstOrNull { it is IOException }
                if (io != null) Http.failure(io, what) else SourceException(FailureKind.Changed, "$what: ${e.javaClass.simpleName}: ${e.message}", e)
            }
            is IOException -> Http.failure(e, what)
            else -> SourceException(FailureKind.Unknown, "$what: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }
}
