package com.stickerya.app.editor

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tool(val label: String) {
    MOVE("Mover"),
    CUT("Recortar"),
    TEXT("Texto"),
    EMOJI("Emoji"),
    DRAW("Dibujar"),
    BORDER("Borde"),
    ADJUST("Ajustes"),
}

enum class CutMode(val label: String) {
    ERASE("Borrar"),
    RESTORE("Restaurar"),
    WAND("Varita"),
    LASSO("A mano"),
    SHAPE("Formas"),
}

/** Lo que hace un dedo sobre el lienzo. */
enum class TouchMode { MOVE, ERASE, RESTORE, WAND, LASSO, DRAW, NONE }

/**
 * Estado del editor y su historial. La interfaz (Compose) lee los campos observables;
 * la vista del lienzo se repinta con [onChange].
 */
class EditorController(initial: EditorState = EditorState()) {

    var state by mutableStateOf(initial)
        private set
    var selectedId by mutableStateOf<Long?>(null)
    var tool by mutableStateOf(Tool.MOVE)
    var cutMode by mutableStateOf(CutMode.ERASE)
    var brushSize by mutableStateOf(48f)
    var wandTolerance by mutableStateOf(40f)
    var drawColor by mutableStateOf(0xFFFF2D55.toInt())
    var drawWidth by mutableStateOf(14f)
    var busy by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    /** Cambia cada vez que hay que volver a centrar el lienzo. */
    var resetViewTick by mutableStateOf(0)

    var onChange: (() -> Unit)? = null

    private val undoStack = ArrayDeque<EditorState>()
    private val redoStack = ArrayDeque<EditorState>()
    private var pending: EditorState? = null

    val selected: Layer? get() = state.layer(selectedId)

    val touchMode: TouchMode
        get() = when (tool) {
            Tool.CUT -> when (cutMode) {
                CutMode.ERASE -> TouchMode.ERASE
                CutMode.RESTORE -> TouchMode.RESTORE
                CutMode.WAND -> TouchMode.WAND
                CutMode.LASSO -> TouchMode.LASSO
                CutMode.SHAPE -> TouchMode.NONE
            }
            Tool.DRAW -> TouchMode.DRAW
            else -> TouchMode.MOVE
        }

    /** La imagen sobre la que actúan las herramientas de recorte: la seleccionada o la de más arriba. */
    val targetImage: ImageLayer?
        get() = (selected as? ImageLayer) ?: state.layers.lastOrNull { it is ImageLayer } as? ImageLayer

    /** Cambio con entrada en el historial. */
    fun commit(newState: EditorState) {
        if (newState == state) return
        pushUndo(pending ?: state)
        pending = null
        state = newState
        changed()
    }

    /** Para gestos y deslizadores: [begin] guarda el estado de antes, [live] cambia sin historial y [end] lo apunta. */
    fun begin() {
        if (pending == null) pending = state
    }

    fun live(newState: EditorState) {
        state = newState
        changed()
    }

    fun end() {
        val before = pending ?: return
        pending = null
        if (before != state) pushUndo(before)
    }

    /** Deshace lo hecho desde [begin] sin apuntarlo. */
    fun cancel() {
        val before = pending ?: return
        pending = null
        state = before
        fixSelection()
        changed()
    }

    val hasPending get() = pending != null

    private fun pushUndo(s: EditorState) {
        undoStack.addLast(s)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        updateFlags()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state)
        state = prev
        fixSelection()
        updateFlags()
        changed()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        state = next
        fixSelection()
        updateFlags()
        changed()
    }

    private fun fixSelection() {
        if (state.layer(selectedId) == null) selectedId = null
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun changed() {
        onChange?.invoke()
    }

    fun select(id: Long?) {
        selectedId = id
        changed()
    }

    // ---- Acciones sobre capas ----

    fun addLayer(layer: Layer, selectIt: Boolean = true) {
        commit(state.add(layer))
        if (selectIt) select(layer.id)
    }

    fun updateLayer(layer: Layer) = commit(state.replace(layer))

    fun deleteSelected() {
        val id = selectedId ?: return
        commit(state.remove(id))
        select(null)
    }

    fun duplicateSelected() {
        val l = selected ?: return
        val copy = l.withId(newLayerId()).moved(cx = l.cx + 24f, cy = l.cy + 24f)
        addLayer(copy)
    }

    fun flipSelected() {
        val l = selected ?: return
        updateLayer(l.moved(flip = !l.flip))
    }

    fun bringToFront() {
        val l = selected ?: return
        commit(state.copy(layers = state.layers.filterNot { it.id == l.id } + l))
    }

    fun sendToBack() {
        val l = selected ?: return
        commit(state.copy(layers = listOf(l) + state.layers.filterNot { it.id == l.id }))
    }

    fun centerSelected() {
        val l = selected ?: return
        updateLayer(l.moved(cx = CANVAS / 2, cy = CANVAS / 2, rotation = 0f))
    }

    /** Sustituye la imagen de una capa (borrados, recortes, IA...). */
    fun replaceBitmap(layer: ImageLayer, bitmap: Bitmap) {
        updateLayer(layer.copy(bitmap = bitmap))
    }

    fun toast(text: String) {
        message = text
    }

    companion object {
        const val MAX_HISTORY = 20
    }
}
