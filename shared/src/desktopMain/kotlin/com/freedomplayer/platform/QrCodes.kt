package com.freedomplayer.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Códigos QR con ZXing: los cuadros del QR (true = negro), sin margen; el margen lo pinta la pantalla. */
object QrCodes {
    fun matrix(text: String): List<BooleanArray> {
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        return List(bits.height) { y -> BooleanArray(bits.width) { x -> bits[x, y] } }
    }
}
