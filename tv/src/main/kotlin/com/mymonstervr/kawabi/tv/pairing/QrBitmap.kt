package com.mymonstervr.kawabi.tv.pairing

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Rendered once per [payload] (a full pairing screen shows one code for minutes at a time). */
@Composable
fun rememberQrBitmap(payload: String, sizePx: Int = 512): ImageBitmap =
    remember(payload, sizePx) { generateQrBitmap(payload, sizePx) }.asImageBitmap()

private fun generateQrBitmap(payload: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) DARK else LIGHT)
        }
    }
    return bitmap
}

private const val DARK = 0xFF1A1206.toInt()
private const val LIGHT = 0xFFEFE9E2.toInt()
