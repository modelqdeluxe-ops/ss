package com.stickerya.app.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF

/** Lado del lienzo del sticker, en unidades del editor (igual que el archivo final: 512 px). */
const val CANVAS = 512f

private var nextId = 1L
fun newLayerId() = nextId++

/**
 * Una capa con su contenido centrado en (0,0) y su transformación: posición (cx, cy) en el lienzo,
 * escala, giro en grados y espejo. Son inmutables: deshacer/rehacer solo guarda listas de capas.
 */
sealed interface Layer {
    val id: Long
    val cx: Float
    val cy: Float
    val scale: Float
    val rotation: Float
    val flip: Boolean

    /** Ancho y alto del contenido sin transformar. */
    val width: Float
    val height: Float

    fun moved(cx: Float = this.cx, cy: Float = this.cy, scale: Float = this.scale, rotation: Float = this.rotation, flip: Boolean = this.flip): Layer
    fun withId(id: Long): Layer

    fun matrix(): Matrix = Matrix().apply {
        postScale(if (flip) -scale else scale, scale)
        postRotate(rotation)
        postTranslate(cx, cy)
    }
}

data class ImageLayer(
    override val id: Long,
    /** La imagen tal como se ve (con borrados y recortes). Nunca se modifica: cada cambio crea una copia. */
    val bitmap: Bitmap,
    /** La foto original, del mismo tamaño, para el pincel "Restaurar". */
    val original: Bitmap,
    override val cx: Float = CANVAS / 2,
    override val cy: Float = CANVAS / 2,
    override val scale: Float = 1f,
    override val rotation: Float = 0f,
    override val flip: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
) : Layer {
    override val width get() = bitmap.width.toFloat()
    override val height get() = bitmap.height.toFloat()
    override fun moved(cx: Float, cy: Float, scale: Float, rotation: Float, flip: Boolean) = copy(cx = cx, cy = cy, scale = scale, rotation = rotation, flip = flip)
    override fun withId(id: Long) = copy(id = id)

    companion object {
        /** Ajusta una foto nueva al lienzo con un pequeño margen. */
        fun fitted(bitmap: Bitmap, original: Bitmap = bitmap, margin: Float = 24f) =
            ImageLayer(newLayerId(), bitmap, original, scale = (CANVAS - margin * 2) / maxOf(bitmap.width, bitmap.height))
    }
}

data class TextLayer(
    override val id: Long,
    val text: String,
    val color: Int,
    val font: Int = 0,
    val strokeOn: Boolean = true,
    val strokeColor: Int = 0xFF000000.toInt(),
    val bgOn: Boolean = false,
    val bgColor: Int = 0xFFFFFFFF.toInt(),
    override val cx: Float = CANVAS / 2,
    override val cy: Float = CANVAS / 2,
    override val scale: Float = 1f,
    override val rotation: Float = 0f,
    override val flip: Boolean = false,
) : Layer {
    private val size by lazy { TextPainter.measure(this) }
    override val width get() = size.first
    override val height get() = size.second
    override fun moved(cx: Float, cy: Float, scale: Float, rotation: Float, flip: Boolean) = copy(cx = cx, cy = cy, scale = scale, rotation = rotation, flip = flip)
    override fun withId(id: Long) = copy(id = id)
}

/** Un trazo de pincel. [points] son pares x,y relativos al centro de la capa. */
data class StrokeLayer(
    override val id: Long,
    val points: FloatArray,
    val color: Int,
    val strokeWidth: Float,
    override val cx: Float,
    override val cy: Float,
    override val scale: Float = 1f,
    override val rotation: Float = 0f,
    override val flip: Boolean = false,
) : Layer {
    val path: Path by lazy { smoothPath(points) }
    private val bounds by lazy {
        RectF().also { r ->
            path.computeBounds(r, true)
            r.inset(-strokeWidth / 2, -strokeWidth / 2)
        }
    }
    override val width get() = 2 * maxOf(-bounds.left, bounds.right)
    override val height get() = 2 * maxOf(-bounds.top, bounds.bottom)
    override fun moved(cx: Float, cy: Float, scale: Float, rotation: Float, flip: Boolean) = copy(cx = cx, cy = cy, scale = scale, rotation = rotation, flip = flip)
    override fun withId(id: Long) = copy(id = id)

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)

    companion object {
        /** Crea la capa a partir de puntos en coordenadas del lienzo. */
        fun fromCanvasPoints(pts: List<Float>, color: Int, width: Float): StrokeLayer {
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            for (i in pts.indices step 2) {
                minX = minOf(minX, pts[i]); maxX = maxOf(maxX, pts[i])
                minY = minOf(minY, pts[i + 1]); maxY = maxOf(maxY, pts[i + 1])
            }
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            val local = FloatArray(pts.size) { i -> if (i % 2 == 0) pts[i] - cx else pts[i] - cy }
            return StrokeLayer(newLayerId(), local, color, width, cx, cy)
        }

        fun smoothPath(p: FloatArray): Path {
            val path = Path()
            if (p.size < 2) return path
            path.moveTo(p[0], p[1])
            if (p.size == 2) {
                path.lineTo(p[0] + 0.1f, p[1])
                return path
            }
            var i = 2
            while (i < p.size - 2) {
                val mx = (p[i] + p[i + 2]) / 2
                val my = (p[i + 1] + p[i + 3]) / 2
                path.quadTo(p[i], p[i + 1], mx, my)
                i += 2
            }
            path.lineTo(p[p.size - 2], p[p.size - 1])
            return path
        }
    }
}

data class EditorState(
    val layers: List<Layer> = emptyList(),
    /** Grosor del contorno (en px del sticker final); 0 = sin contorno. */
    val outline: Float = 0f,
    val outlineColor: Int = 0xFFFFFFFF.toInt(),
) {
    fun layer(id: Long?) = layers.firstOrNull { it.id == id }
    fun replace(layer: Layer) = copy(layers = layers.map { if (it.id == layer.id) layer else it })
    fun remove(id: Long) = copy(layers = layers.filterNot { it.id == id })
    fun add(layer: Layer) = copy(layers = layers + layer)
    val isEmpty get() = layers.isEmpty()
}
