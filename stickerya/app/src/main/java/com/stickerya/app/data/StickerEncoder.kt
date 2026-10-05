package com.stickerya.app.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import java.io.ByteArrayOutputStream

/** Reglas de WhatsApp: 512x512, WebP y como mucho 100 KB por sticker. */
object StickerEncoder {
    const val SIZE = 512
    const val MAX_BYTES = 100 * 1024 - 512

    fun toWhatsAppWebp(source: Bitmap): ByteArray {
        val bmp = if (source.width == SIZE && source.height == SIZE) source else scaled(source)
        // Primero sin pérdida (si cabe, queda perfecto); si no, con pérdida bajando la calidad poco a poco.
        val lossless = encode(bmp, lossless = true, quality = 100)
        if (lossless.size <= MAX_BYTES) return lossless
        for (q in intArrayOf(95, 90, 85, 80, 75, 70, 60, 50, 40, 30, 20, 10, 5)) {
            val bytes = encode(bmp, lossless = false, quality = q)
            if (bytes.size <= MAX_BYTES) return bytes
        }
        // Último recurso (casi imposible a 512x512): reducir el detalle y volver a ampliar.
        val small = Bitmap.createScaledBitmap(bmp, SIZE / 2, SIZE / 2, true)
        return encode(Bitmap.createScaledBitmap(small, SIZE, SIZE, true), lossless = false, quality = 30)
    }

    @Suppress("DEPRECATION")
    private fun encode(bmp: Bitmap, lossless: Boolean, quality: Int): ByteArray {
        val format = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.WEBP // calidad 100 = sin pérdida en Android < 11
        }
        val q = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !lossless) quality.coerceAtMost(99) else quality
        return ByteArrayOutputStream().use { out ->
            bmp.compress(format, q, out)
            out.toByteArray()
        }
    }

    private fun scaled(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}
