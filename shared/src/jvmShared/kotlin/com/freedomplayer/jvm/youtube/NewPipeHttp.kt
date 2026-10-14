package com.freedomplayer.jvm.youtube

import com.freedomplayer.jvm.Http
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Las peticiones de NewPipe Extractor, con OkHttp (como hace la app de NewPipe). */
class NewPipeHttp(private val client: OkHttpClient = Http.client) : Downloader() {
    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header("User-Agent", Http.BROWSER_UA)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
            val text = response.body.string()
            return Response(response.code, response.message, response.headers.toMultimap(), text, response.request.url.toString())
        }
    }
}
