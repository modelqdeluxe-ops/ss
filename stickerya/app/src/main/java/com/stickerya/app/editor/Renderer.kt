package com.stickerya.app.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.sqrt

object Fonts {
    val names = listOf("Clásica", "Gruesa", "Meme", "Elegante", "Escrita", "Divertida", "Máquina")

    fun typeface(index: Int): Typeface = when (index) {
        1 -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        2 -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        3 -> Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
        4 -> Typeface.create("cursive", Typeface.BOLD)
        5 -> Typeface.create("casual", Typeface.BOLD)
        6 -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
}

object TextPainter {
    const val TEXT_SIZE = 72f
    private const val PAD = 18f

    private fun fillPaint(l: TextLayer) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.typeface(l.font)
        textSize = TEXT_SIZE
        textAlign = Paint.Align.CENTER
        color = l.color
    }

    private fun lines(l: TextLayer) = l.text.ifEmpty { " " }.split('\n')

    private fun strokeWidth(l: TextLayer) = if (l.strokeOn) TEXT_SIZE * 0.16f else 0f

    fun measure(l: TextLayer): Pair<Float, Float> {
        val p = fillPaint(l)
        val lineH = p.fontSpacing
        val w = lines(l).maxOf { p.measureText(it) } + strokeWidth(l) + PAD * 2
        val h = lineH * lines(l).size + strokeWidth(l) + PAD
        return w to h
    }

    fun draw(c: Canvas, l: TextLayer) {
        val p = fillPaint(l)
        val lines = lines(l)
        val lineH = p.fontSpacing
        val total = lineH * lines.size
        val fm = p.fontMetrics
        if (l.bgOn) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = l.bgColor }
            val r = RectF(-l.width / 2, -l.height / 2, l.width / 2, l.height / 2)
            c.drawRoundRect(r, 28f, 28f, bg)
        }
        val stroke = if (l.strokeOn) Paint(p).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidth(l)
            strokeJoin = Paint.Join.ROUND
            strokeMiter = 10f
            color = l.strokeColor
        } else null
        lines.forEachIndexed { i, line ->
            // Centrado vertical de cada línea en el bloque.
            val baseline = -total / 2 + lineH * i + (lineH - (fm.descent - fm.ascent)) / 2 - fm.ascent
            stroke?.let { c.drawText(line, 0f, baseline, it) }
            c.drawText(line, 0f, baseline, p)
        }
    }
}

object Renderer {

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Dibuja todas las capas en un canvas que ya está en coordenadas del lienzo (0..512). */
    fun drawLayers(c: Canvas, layers: List<Layer>) {
        for (l in layers) drawLayer(c, l)
    }

    fun drawLayer(c: Canvas, l: Layer) {
        val save = c.save()
        c.concat(l.matrix())
        when (l) {
            is ImageLayer -> {
                val paint = if (l.brightness == 0f && l.contrast == 1f && l.saturation == 1f) bitmapPaint
                else Paint(bitmapPaint).apply { colorFilter = ColorMatrixColorFilter(adjustMatrix(l.brightness, l.contrast, l.saturation)) }
                c.drawBitmap(l.bitmap, -l.width / 2, -l.height / 2, paint)
            }
            is TextLayer -> TextPainter.draw(c, l)
            is StrokeLayer -> c.drawPath(l.path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = l.color
                strokeWidth = l.strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
        c.restoreToCount(save)
    }

    /** brillo -100..100, contraste 0.5..1.5, saturación 0..2 */
    fun adjustMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(saturation)
        val t = (1f - contrast) * 128f + brightness
        val bc = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        ))
        m.postConcat(bc)
        return m
    }

    /** Solo las capas, sin contorno, a [size] px. */
    fun renderLayers(state: EditorState, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.scale(size / CANVAS, size / CANVAS)
        drawLayers(c, state.layers)
        return out
    }

    /** El sticker terminado: capas + contorno. */
    fun render(state: EditorState, size: Int = CANVAS.toInt()): Bitmap {
        val content = renderLayers(state, size)
        if (state.outline <= 0f) return content
        val out = Outline.make(content, state.outline * size / CANVAS, state.outlineColor)
        Canvas(out).drawBitmap(content, 0f, 0f, null)
        return out
    }

    fun isBlank(bmp: Bitmap): Boolean {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px.none { (it ushr 24) > 8 }
    }
}

/**
 * Contorno tipo sticker: dilata la silueta con una transformada de distancia exacta (Felzenszwalb),
 * así el borde queda redondo y uniforme sea cual sea el grosor.
 */
object Outline {
    private const val INF = 1e20f

    /** Devuelve un bitmap del mismo tamaño con solo el contorno pintado en [color]. */
    fun make(content: Bitmap, radius: Float, color: Int): Bitmap {
        val w = content.width
        val h = content.height
        val px = IntArray(w * h)
        content.getPixels(px, 0, w, 0, 0, w, h)
        val f = FloatArray(w * h) { if ((px[it] ushr 24) >= 110) 0f else INF }
        edt(f, w, h)
        val rgb = color and 0x00FFFFFF
        val baseA = color ushr 24
        for (i in px.indices) {
            val d = sqrt(f[i])
            val cov = (radius + 0.5f - d).coerceIn(0f, 1f)
            // Dentro de la figura el contorno también se pinta, para que los bordes semitransparentes no dejen huecos.
            val a = (cov * baseA).toInt()
            px[i] = (a shl 24) or rgb
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun edt(f: FloatArray, w: Int, h: Int) {
        val n = max(w, h)
        val buf = FloatArray(n)
        val d = FloatArray(n)
        val v = IntArray(n)
        val z = FloatArray(n + 1)
        for (x in 0 until w) {
            for (y in 0 until h) buf[y] = f[y * w + x]
            dt1d(buf, h, d, v, z)
            for (y in 0 until h) f[y * w + x] = d[y]
        }
        for (y in 0 until h) {
            System.arraycopy(f, y * w, buf, 0, w)
            dt1d(buf, w, d, v, z)
            System.arraycopy(d, 0, f, y * w, w)
        }
    }

    private fun dt1d(f: FloatArray, n: Int, d: FloatArray, v: IntArray, z: FloatArray) {
        var k = 0
        v[0] = 0
        z[0] = -Float.MAX_VALUE
        z[1] = Float.MAX_VALUE
        for (q in 1 until n) {
            var s = intersect(f, q, v[k])
            while (s <= z[k]) {
                k--
                s = intersect(f, q, v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.MAX_VALUE
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val dq = (q - v[k]).toFloat()
            d[q] = dq * dq + f[v[k]]
        }
    }

    private fun intersect(f: FloatArray, q: Int, p: Int): Float =
        ((f[q] + q.toFloat() * q) - (f[p] + p.toFloat() * p)) / (2f * (q - p))
}
