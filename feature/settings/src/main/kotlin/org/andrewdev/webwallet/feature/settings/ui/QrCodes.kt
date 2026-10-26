package org.andrewdev.webwallet.feature.settings.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders the `otpauth://` URI as a QR bitmap with high error correction (docs/03 §5). */
object QrCodes {
    fun render(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(
            size * size,
        ) { index -> if (matrix.get(index % size, index / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }
}
