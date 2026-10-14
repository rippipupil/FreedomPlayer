package com.freedomplayer.core

/** Ids de dispositivo de Syncthing (lo que lleva el QR): 8 grupos de 7 letras y números. */
object SyncIds {
    private val deviceId = Regex("""[A-Z2-7]{7}(?:-[A-Z2-7]{7}){7}""")

    /** El id leído de un QR (o escrito a mano): en mayúsculas y con guiones; null si no lo es. */
    fun device(text: String): String? {
        val compact = text.trim().uppercase().filter { it.isLetterOrDigit() }
        if (compact.length != 56) return null
        val id = compact.chunked(7).joinToString("-")
        return id.takeIf { deviceId.matches(it) }
    }
}
