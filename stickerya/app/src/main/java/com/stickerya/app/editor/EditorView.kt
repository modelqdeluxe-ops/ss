package com.stickerya.app.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** El lienzo: dibuja el sticker y convierte los gestos en cambios del [EditorController]. */
@SuppressLint("ViewConstructor")
class EditorView(context: Context, private val controller: EditorController) : View(context) {

    /** Doble toque sobre un texto: abrir el editor de texto. */
    var onEditText: ((TextLayer) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // Vista: zoom y desplazamiento del lienzo (para borrar con precisión).
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastResetTick = -1

    private val checker: Paint = Paint().apply {
        val cell = 16
        val bmp = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(250, 250, 250))
        val p = Paint().apply { color = Color.rgb(222, 222, 228) }
        c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), p)
        c.drawRect(cell.toFloat(), cell.toFloat(), cell * 2f, cell * 2f, p)
        shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x33000000
    }
    private val selWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.WHITE
    }
    private val selDash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = 0xFF6C3BFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C3BFF.toInt() }
    private val handleIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
        strokeCap = Paint.Cap.ROUND
    }
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
        setShadowLayer(3f * density, 0f, 0f, Color.BLACK)
    }
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFF6C3BFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val lassoFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x226C3BFF }

    // Contorno: se calcula a 512 px y se guarda mientras el estado no cambie.
    private var outlineBmp: Bitmap? = null
    private var outlineFor: EditorState? = null
    private var outlineDirty = true

    init {
        controller.onChange = {
            outlineDirty = true
            invalidate()
        }
    }

    // ---------- Geometría ----------

    private val baseScale get() = (min(width, height) - 24f * density) / CANVAS
    private val k get() = baseScale * zoom

    private fun viewMatrix() = Matrix().apply {
        postTranslate(-CANVAS / 2, -CANVAS / 2)
        postScale(k, k)
        postTranslate(width / 2f + panX, height / 2f + panY)
    }

    private fun toCanvasX(vx: Float) = (vx - width / 2f - panX) / k + CANVAS / 2
    private fun toCanvasY(vy: Float) = (vy - height / 2f - panY) / k + CANVAS / 2

    /** Punto del lienzo -> coordenadas locales (centradas) de una capa. */
    private fun toLocal(l: Layer, x: Float, y: Float): FloatArray {
        val inv = Matrix()
        l.matrix().invert(inv)
        val p = floatArrayOf(x, y)
        inv.mapPoints(p)
        return p
    }

    /** Punto de la vista -> píxel de la imagen de una capa. */
    private fun toBitmap(l: ImageLayer, vx: Float, vy: Float): FloatArray {
        val p = toLocal(l, toCanvasX(vx), toCanvasY(vy))
        p[0] += l.width / 2
        p[1] += l.height / 2
        return p
    }

    private fun hit(l: Layer, x: Float, y: Float): Boolean {
        val p = toLocal(l, x, y)
        val pad = 14f / max(l.scale, 0.05f)
        return p[0] in -l.width / 2 - pad..l.width / 2 + pad && p[1] in -l.height / 2 - pad..l.height / 2 + pad
    }

    private fun corners(l: Layer): FloatArray {
        val w = l.width / 2
        val h = l.height / 2
        val pts = floatArrayOf(-w, -h, w, -h, w, h, -w, h)
        l.matrix().mapPoints(pts)
        viewMatrix().mapPoints(pts)
        return pts
    }

    private fun handlePos(l: Layer): Pair<Float, Float> {
        val c = corners(l)
        return c[4] to c[5]
    }

    private fun centerOnView(l: Layer): Pair<Float, Float> {
        val p = floatArrayOf(l.cx, l.cy)
        viewMatrix().mapPoints(p)
        return p[0] to p[1]
    }

    // ---------- Dibujo ----------

    override fun onDraw(canvas: Canvas) {
        if (controller.resetViewTick != lastResetTick) {
            lastResetTick = controller.resetViewTick
            zoom = 1f; panX = 0f; panY = 0f
        }
        val st = controller.state
        val save = canvas.save()
        canvas.concat(viewMatrix())
        canvas.clipRect(0f, 0f, CANVAS, CANVAS)
        canvas.drawRect(0f, 0f, CANVAS, CANVAS, checker)
        if (st.outline > 0f && st.layers.isNotEmpty()) {
            if (outlineDirty || outlineFor !== st || outlineBmp == null) {
                val content = Renderer.renderLayers(st, CANVAS.toInt())
                outlineBmp = Outline.make(content, st.outline, st.outlineColor)
                outlineFor = st
                outlineDirty = false
            }
            outlineBmp?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, CANVAS, CANVAS), filterPaint) }
        }
        Renderer.drawLayers(canvas, st.layers)
        if (drawing.size >= 2) {
            canvas.drawPath(StrokeLayer.smoothPath(drawing.toFloatArray()), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = controller.drawColor
                strokeWidth = controller.drawWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
        }
        canvas.restoreToCount(save)

        // Marco del lienzo
        val r = RectF(0f, 0f, CANVAS, CANVAS)
        viewMatrix().mapRect(r)
        canvas.drawRect(r, frame)

        // Selección
        val sel = controller.selected
        if (sel != null && controller.touchMode == TouchMode.MOVE) {
            val c = corners(sel)
            val p = Path().apply {
                moveTo(c[0], c[1]); lineTo(c[2], c[3]); lineTo(c[4], c[5]); lineTo(c[6], c[7]); close()
            }
            canvas.drawPath(p, selWhite)
            canvas.drawPath(p, selDash)
            val (hx, hy) = c[4] to c[5]
            canvas.drawCircle(hx, hy, 14f * density, handleFill)
            // Flechas de girar/escalar
            canvas.drawArc(RectF(hx - 6 * density, hy - 6 * density, hx + 6 * density, hy + 6 * density), 200f, 250f, false, handleIcon)
        }

        // Recorte a mano
        if (lasso.size >= 4) {
            val p = Path()
            val pts = lasso.toFloatArray()
            viewMatrix().mapPoints(pts)
            p.moveTo(pts[0], pts[1])
            for (i in 2 until pts.size step 2) p.lineTo(pts[i], pts[i + 1])
            p.close()
            canvas.drawPath(p, lassoFill)
            canvas.drawPath(p, lassoPaint)
        }

        // Cursor del pincel
        if (showCursor && (controller.touchMode == TouchMode.ERASE || controller.touchMode == TouchMode.RESTORE)) {
            canvas.drawCircle(cursorX, cursorY, controller.brushSize / 2, cursor)
        }
    }

    // ---------- Gestos ----------

    private enum class Gesture { NONE, DRAG, HANDLE, PINCH_LAYER, PINCH_VIEW, BRUSH, BRUSH_PENDING, LASSO, DRAW, TAP, IGNORE }

    private var gesture = Gesture.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var startLayer: Layer? = null
    private var startDist = 1f
    private var startAngle = 0f
    private var startMidX = 0f
    private var startMidY = 0f
    private var startZoom = 1f
    private var startPanX = 0f
    private var startPanY = 0f
    private var brush: BitmapOps.Brush? = null
    private var brushLayer: ImageLayer? = null
    private val lasso = ArrayList<Float>()
    private val drawing = ArrayList<Float>()
    private var showCursor = false
    private var cursorX = 0f
    private var cursorY = 0f
    private var lastTapTime = 0L
    private var lastTapId = -1L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (controller.busy != null) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(e)
            MotionEvent.ACTION_POINTER_DOWN -> secondFinger(e)
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_POINTER_UP -> {
                // Al levantar uno de los dos dedos se termina el gesto.
                finish(cancelled = gesture == Gesture.BRUSH_PENDING)
                gesture = Gesture.IGNORE
            }
            MotionEvent.ACTION_UP -> {
                if (gesture == Gesture.BRUSH_PENDING) {
                    startBrush()
                    brushTo(e.x, e.y)
                }
                finish(cancelled = false)
                gesture = Gesture.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                finish(cancelled = true)
                gesture = Gesture.NONE
            }
        }
        invalidate()
        return true
    }

    private fun down(e: MotionEvent) {
        downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
        val x = toCanvasX(e.x)
        val y = toCanvasY(e.y)
        when (controller.touchMode) {
            TouchMode.MOVE -> {
                val sel = controller.selected
                if (sel != null) {
                    val (hx, hy) = handlePos(sel)
                    if (hypot(e.x - hx, e.y - hy) < 28f * density) {
                        val (cx, cy) = centerOnView(sel)
                        startLayer = sel
                        startDist = max(1f, hypot(e.x - cx, e.y - cy))
                        startAngle = Math.toDegrees(atan2((e.y - cy).toDouble(), (e.x - cx).toDouble())).toFloat()
                        controller.begin()
                        gesture = Gesture.HANDLE
                        return
                    }
                }
                val hitLayer = controller.state.layers.lastOrNull { hit(it, x, y) }
                if (hitLayer != null) {
                    if (hitLayer is TextLayer && hitLayer.id == lastTapId && e.eventTime - lastTapTime < 350) {
                        onEditText?.invoke(hitLayer)
                    }
                    lastTapId = hitLayer.id
                    lastTapTime = e.eventTime
                    controller.select(hitLayer.id)
                    startLayer = hitLayer
                    controller.begin()
                    gesture = Gesture.DRAG
                } else {
                    controller.select(null)
                    gesture = Gesture.TAP
                }
            }
            TouchMode.ERASE, TouchMode.RESTORE -> {
                if (controller.targetImage == null) {
                    controller.toast("Primero añade una imagen")
                    gesture = Gesture.IGNORE
                    return
                }
                showCursor = true
                cursorX = e.x; cursorY = e.y
                gesture = Gesture.BRUSH_PENDING
            }
            TouchMode.WAND -> gesture = Gesture.TAP
            TouchMode.LASSO -> {
                if (controller.targetImage == null) {
                    controller.toast("Primero añade una imagen")
                    gesture = Gesture.IGNORE
                    return
                }
                lasso.clear()
                lasso += x; lasso += y
                gesture = Gesture.LASSO
            }
            TouchMode.DRAW -> {
                drawing.clear()
                drawing += x; drawing += y
                gesture = Gesture.DRAW
            }
            TouchMode.NONE -> gesture = Gesture.TAP
        }
    }

    private fun secondFinger(e: MotionEvent) {
        if (e.pointerCount != 2) return
        // Si ya se estaba pintando o recortando, se cierra lo hecho y se pasa a zoom.
        when (gesture) {
            Gesture.BRUSH, Gesture.LASSO, Gesture.DRAW, Gesture.HANDLE, Gesture.DRAG -> finish(cancelled = gesture != Gesture.BRUSH && gesture != Gesture.DRAG)
            else -> {}
        }
        lasso.clear(); drawing.clear(); showCursor = false
        val mx = (e.getX(0) + e.getX(1)) / 2
        val my = (e.getY(0) + e.getY(1)) / 2
        startDist = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
        startAngle = Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
        startMidX = mx; startMidY = my
        val sel = controller.selected
        if (controller.touchMode == TouchMode.MOVE && sel != null) {
            startLayer = sel
            controller.begin()
            gesture = Gesture.PINCH_LAYER
        } else {
            startZoom = zoom; startPanX = panX; startPanY = panY
            gesture = Gesture.PINCH_VIEW
        }
    }

    private fun move(e: MotionEvent) {
        when (gesture) {
            Gesture.DRAG -> {
                val l = startLayer ?: return
                val dx = (e.x - downX) / k
                val dy = (e.y - downY) / k
                controller.live(controller.state.replace(l.moved(cx = l.cx + dx, cy = l.cy + dy)))
            }
            Gesture.HANDLE -> {
                val l = startLayer ?: return
                val (cx, cy) = centerOnView(l)
                val d = max(1f, hypot(e.x - cx, e.y - cy))
                val a = Math.toDegrees(atan2((e.y - cy).toDouble(), (e.x - cx).toDouble())).toFloat()
                val s = (l.scale * d / startDist).coerceIn(0.03f, 30f)
                controller.live(controller.state.replace(l.moved(scale = s, rotation = snap(l.rotation + a - startAngle))))
            }
            Gesture.PINCH_LAYER -> {
                if (e.pointerCount < 2) return
                val l = startLayer ?: return
                val mx = (e.getX(0) + e.getX(1)) / 2
                val my = (e.getY(0) + e.getY(1)) / 2
                val d = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
                val a = Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
                val s = (l.scale * d / startDist).coerceIn(0.03f, 30f)
                controller.live(controller.state.replace(l.moved(
                    cx = l.cx + (mx - startMidX) / k,
                    cy = l.cy + (my - startMidY) / k,
                    scale = s,
                    rotation = snap(l.rotation + a - startAngle),
                )))
            }
            Gesture.PINCH_VIEW -> {
                if (e.pointerCount < 2) return
                val mx = (e.getX(0) + e.getX(1)) / 2
                val my = (e.getY(0) + e.getY(1)) / 2
                val d = max(1f, hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)))
                // Punto del lienzo que estaba bajo los dedos al empezar.
                val k0 = baseScale * startZoom
                val sx = (startMidX - width / 2f - startPanX) / k0 + CANVAS / 2
                val sy = (startMidY - height / 2f - startPanY) / k0 + CANVAS / 2
                zoom = (startZoom * d / startDist).coerceIn(1f, 8f)
                panX = mx - width / 2f - (sx - CANVAS / 2) * k
                panY = my - height / 2f - (sy - CANVAS / 2) * k
                if (zoom == 1f) { panX = 0f; panY = 0f }
            }
            Gesture.BRUSH_PENDING -> {
                cursorX = e.x; cursorY = e.y
                if (hypot(e.x - downX, e.y - downY) > slop) {
                    startBrush()
                    brushTo(e.x, e.y)
                }
            }
            Gesture.BRUSH -> {
                cursorX = e.x; cursorY = e.y
                for (h in 0 until e.historySize) brushTo(e.getHistoricalX(h), e.getHistoricalY(h))
                brushTo(e.x, e.y)
            }
            Gesture.LASSO -> {
                lasso += toCanvasX(e.x); lasso += toCanvasY(e.y)
            }
            Gesture.DRAW -> {
                for (h in 0 until e.historySize) addDrawPoint(e.getHistoricalX(h), e.getHistoricalY(h))
                addDrawPoint(e.x, e.y)
            }
            Gesture.TAP -> if (hypot(e.x - downX, e.y - downY) > slop) gesture = Gesture.IGNORE
            else -> {}
        }
    }

    /** Ayuda a dejar el giro recto: se pega a 0/90/180/270 si está cerca. */
    private fun snap(deg: Float): Float {
        var d = deg % 360f
        if (d < 0) d += 360f
        for (t in floatArrayOf(0f, 90f, 180f, 270f, 360f)) if (kotlin.math.abs(d - t) < 4f) return t % 360f
        return d
    }

    private fun addDrawPoint(vx: Float, vy: Float) {
        val x = toCanvasX(vx)
        val y = toCanvasY(vy)
        val n = drawing.size
        if (n >= 2 && hypot(x - drawing[n - 2], y - drawing[n - 1]) < 1.5f) return
        drawing += x; drawing += y
    }

    private fun startBrush() {
        val target = controller.targetImage ?: return
        controller.begin()
        val copy = BitmapOps.mutableCopy(target.bitmap)
        val layer = target.copy(bitmap = copy)
        controller.live(controller.state.replace(layer))
        brushLayer = layer
        brush = BitmapOps.Brush(copy, target.original, restore = controller.touchMode == TouchMode.RESTORE)
        lastX = downX; lastY = downY
        gesture = Gesture.BRUSH
    }

    private fun brushTo(vx: Float, vy: Float) {
        val l = brushLayer ?: return
        val b = brush ?: return
        val p0 = toBitmap(l, lastX, lastY)
        val p1 = toBitmap(l, vx, vy)
        b.segment(p0[0], p0[1], p1[0], p1[1], controller.brushSize / (k * l.scale))
        lastX = vx; lastY = vy
        outlineDirty = true
    }

    private fun finish(cancelled: Boolean) {
        when (gesture) {
            Gesture.DRAG, Gesture.HANDLE, Gesture.PINCH_LAYER, Gesture.BRUSH -> controller.end()
            Gesture.LASSO -> {
                if (!cancelled) applyLasso()
                lasso.clear()
            }
            Gesture.DRAW -> {
                if (!cancelled && drawing.size >= 2) {
                    controller.addLayer(StrokeLayer.fromCanvasPoints(drawing.toList(), controller.drawColor, controller.drawWidth), selectIt = false)
                }
                drawing.clear()
            }
            Gesture.TAP -> if (!cancelled && controller.touchMode == TouchMode.WAND) wandAt(downX, downY)
            else -> {}
        }
        brush = null
        brushLayer = null
        startLayer = null
        showCursor = false
    }

    private fun applyLasso() {
        val target = controller.targetImage ?: return
        if (lasso.size < 12) {
            controller.toast("Rodea con el dedo la parte que quieres conservar")
            return
        }
        val path = Path()
        for (i in lasso.indices step 2) {
            val p = toLocal(target, lasso[i], lasso[i + 1])
            val x = p[0] + target.width / 2
            val y = p[1] + target.height / 2
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        controller.replaceBitmap(target, BitmapOps.keepInside(target.bitmap, path))
    }

    private fun wandAt(vx: Float, vy: Float) {
        val target = controller.targetImage ?: run {
            controller.toast("Primero añade una imagen")
            return
        }
        val p = toBitmap(target, vx, vy)
        val x = p[0].toInt()
        val y = p[1].toInt()
        if (x !in 0 until target.bitmap.width || y !in 0 until target.bitmap.height) {
            controller.toast("Toca sobre el color que quieres borrar")
            return
        }
        controller.replaceBitmap(target, BitmapOps.floodErase(target.bitmap, listOf(x to y), controller.wandTolerance.toInt()))
    }
}
