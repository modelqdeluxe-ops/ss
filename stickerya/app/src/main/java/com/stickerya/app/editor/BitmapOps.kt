package com.stickerya.app.editor

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class Shape(val label: String) { CIRCLE("Círculo"), ROUNDED("Redondeado"), HEART("Corazón"), STAR("Estrella") }

object BitmapOps {

    fun mutableCopy(b: Bitmap): Bitmap = b.copy(Bitmap.Config.ARGB_8888, true)

    /** Pincel: borra (transparente) o restaura (vuelve a la foto original) a lo largo de un segmento. */
    class Brush(private val target: Bitmap, original: Bitmap, restore: Boolean) {
        private val canvas = Canvas(target)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (restore) {
                shader = BitmapShader(original, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
            } else {
                color = 0
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        }

        fun segment(x0: Float, y0: Float, x1: Float, y1: Float, width: Float) {
            paint.strokeWidth = width
            if (x0 == x1 && y0 == y1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x0, y0, width / 2, paint)
                paint.style = Paint.Style.STROKE
            } else {
                canvas.drawLine(x0, y0, x1, y1, paint)
            }
        }
    }

    /** Deja solo lo que hay dentro de [path] (en píxeles de la imagen). */
    fun keepInside(src: Bitmap, path: Path): Bitmap {
        val out = mutableCopy(src)
        val p = Path(path).apply { fillType = Path.FillType.INVERSE_WINDING }
        Canvas(out).drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        return out
    }

    fun shapePath(shape: Shape, w: Float, h: Float): Path {
        val s = minOf(w, h)
        val cx = w / 2
        val cy = h / 2
        val r = s / 2
        val path = Path()
        when (shape) {
            Shape.CIRCLE -> path.addCircle(cx, cy, r, Path.Direction.CW)
            Shape.ROUNDED -> path.addRoundRect(RectF(cx - r, cy - r, cx + r, cy + r), s * 0.18f, s * 0.18f, Path.Direction.CW)
            Shape.HEART -> {
                val top = cy - r * 0.55f
                path.moveTo(cx, cy + r * 0.95f)
                path.cubicTo(cx - r * 1.35f, cy + r * 0.1f, cx - r * 0.95f, cy - r * 1.05f, cx, top)
                path.cubicTo(cx + r * 0.95f, cy - r * 1.05f, cx + r * 1.35f, cy + r * 0.1f, cx, cy + r * 0.95f)
                path.close()
            }
            Shape.STAR -> {
                for (i in 0 until 10) {
                    val a = Math.toRadians((-90 + i * 36).toDouble())
                    val rr = if (i % 2 == 0) r else r * 0.48f
                    val x = cx + (rr * cos(a)).toFloat()
                    val y = cy + (rr * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
        }
        return path
    }

    /**
     * Varita mágica: borra la zona contigua de color parecido al del punto (x, y).
     * Las zonas ya transparentes se atraviesan, así se puede limpiar un fondo por partes.
     */
    fun floodErase(src: Bitmap, seeds: List<Pair<Int, Int>>, tolerance: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        val tol = tolerance * 3
        for ((sx, sy) in seeds) {
            if (sx !in 0 until w || sy !in 0 until h) continue
            val seedIdx = sy * w + sx
            if (visited[seedIdx]) continue
            val ref = px[seedIdx]
            if ((ref ushr 24) < 16) continue
            var head = 0
            var tail = 0
            queue[tail++] = seedIdx
            visited[seedIdx] = true
            while (head < tail) {
                val i = queue[head++]
                px[i] = 0
                val x = i % w
                val y = i / w
                if (x > 0) tail = visit(i - 1, px, visited, queue, tail, ref, tol)
                if (x < w - 1) tail = visit(i + 1, px, visited, queue, tail, ref, tol)
                if (y > 0) tail = visit(i - w, px, visited, queue, tail, ref, tol)
                if (y < h - 1) tail = visit(i + w, px, visited, queue, tail, ref, tol)
            }
        }
        softenEdges(px, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun visit(j: Int, px: IntArray, visited: BooleanArray, queue: IntArray, tail: Int, ref: Int, tol: Int): Int {
        if (visited[j]) return tail
        val c = px[j]
        val transparent = (c ushr 24) < 16
        if (!transparent && dist(c, ref) > tol) return tail
        visited[j] = true
        queue[tail] = j
        return tail + 1
    }

    private fun dist(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))

    /** Suaviza un poco el borde que deja la varita (los píxeles opacos pegados a huecos quedan semitransparentes). */
    private fun softenEdges(px: IntArray, w: Int, h: Int) {
        val alpha = IntArray(w * h) { px[it] ushr 24 }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val a = alpha[i]
            if (a == 0) continue
            val sum = alpha[i - 1] + alpha[i + 1] + alpha[i - w] + alpha[i + w] + a * 4
            val na = sum / 8
            if (na < a) px[i] = (na shl 24) or (px[i] and 0x00FFFFFF)
        }
    }

    /** Mantiene los borrados ya hechos: el resultado solo es opaco donde lo son [fg] y [current]. */
    fun intersectAlpha(fg: Bitmap, current: Bitmap): Bitmap {
        val w = current.width
        val h = current.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        fg.getPixels(a, 0, w, 0, 0, w, h)
        current.getPixels(b, 0, w, 0, 0, w, h)
        for (i in a.indices) {
            val fa = a[i] ushr 24
            val ca = b[i] ushr 24
            val na = fa * ca / 255
            a[i] = (na shl 24) or (a[i] and 0x00FFFFFF)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    /** "Fondo liso": varita desde todo el borde de la imagen. */
    fun removePlainBackground(src: Bitmap, tolerance: Int): Bitmap {
        val w = src.width
        val h = src.height
        val seeds = ArrayList<Pair<Int, Int>>()
        val step = maxOf(1, minOf(w, h) / 64)
        for (x in 0 until w step step) { seeds += x to 0; seeds += x to h - 1 }
        for (y in 0 until h step step) { seeds += 0 to y; seeds += w - 1 to y }
        return floodErase(src, seeds, tolerance)
    }

    /**
     * Quita el fondo con IA (ML Kit, en el teléfono). Si el modelo aún no está descargado, lo pide a
     * Google Play Services y vuelve a intentarlo.
     */
    suspend fun removeBackgroundAi(context: android.content.Context, src: Bitmap): Bitmap {
        val segmenter = SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        )
        try {
            val first = runCatching { segmenter.process(InputImage.fromBitmap(src, 0)).await() }
            val result = first.getOrNull() ?: run {
                val install = ModuleInstall.getClient(context)
                val request = ModuleInstallRequest.newBuilder().addApi(segmenter).build()
                withTimeout(90_000) { install.installModules(request).await() }
                var retry: Result<com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult>? = null
                for (attempt in 0 until 15) {
                    retry = runCatching { segmenter.process(InputImage.fromBitmap(src, 0)).await() }
                    if (retry.isSuccess) break
                    kotlinx.coroutines.delay(2000)
                }
                retry!!.getOrThrow()
            }
            val fg = result.foregroundBitmap ?: throw IllegalStateException("No se encontró ninguna figura")
            val sized = if (fg.width == src.width && fg.height == src.height) fg else Bitmap.createScaledBitmap(fg, src.width, src.height, true)
            return mutableCopy(sized)
        } finally {
            segmenter.close()
        }
    }
}
