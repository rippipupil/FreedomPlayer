package com.freedomplayer.jvm

import com.freedomplayer.data.FailureKind
import com.freedomplayer.data.SourceException
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Lo común a Android y al PC para hablar con YouTube y LRCLIB. */
object Http {
    /** User-Agent de navegador (el último Firefox ESR), como recomienda NewPipe Extractor. */
    const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** Un solo cliente para toda la app: comparte conexiones y hilos. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Traduce los errores de red a algo que se pueda explicar en un aviso. */
    fun failure(e: Throwable, what: String): SourceException = when (e) {
        is SourceException -> e
        is UnknownHostException, is SocketTimeoutException, is SSLException ->
            SourceException(FailureKind.Network, "$what: sin conexión (${e.javaClass.simpleName})", e)
        is IOException -> SourceException(FailureKind.Network, "$what: ${e.message ?: e.javaClass.simpleName}", e)
        else -> SourceException(FailureKind.Unknown, "$what: ${e.message ?: e.javaClass.simpleName}", e)
    }
}

/** Dónde guarda la app la música, las carátulas y la base de datos. */
class AppDirs(val root: File) {
    val music: File get() = File(root, "music").also { it.mkdirs() }
    val covers: File get() = File(root, "covers").also { it.mkdirs() }
    val partial: File get() = File(root, "partial").also { it.mkdirs() }
    val database: File get() = File(root, "freedomplayer.db")
}
