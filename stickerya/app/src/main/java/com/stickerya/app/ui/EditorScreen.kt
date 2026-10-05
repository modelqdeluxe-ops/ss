package com.stickerya.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.BorderOuter
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.stickerya.app.AppViewModel
import com.stickerya.app.EditSession
import com.stickerya.app.data.ImageLoader
import com.stickerya.app.data.StickerPack
import com.stickerya.app.editor.BitmapOps
import com.stickerya.app.editor.CANVAS
import com.stickerya.app.editor.CutMode
import com.stickerya.app.editor.EditorController
import com.stickerya.app.editor.EditorView
import com.stickerya.app.editor.Fonts
import com.stickerya.app.editor.ImageLayer
import com.stickerya.app.editor.Renderer
import com.stickerya.app.editor.Shape
import com.stickerya.app.editor.TextLayer
import com.stickerya.app.editor.Tool
import com.stickerya.app.editor.newLayerId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel, session: EditSession) {
    val c = session.controller
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    /** Capa de texto que se está escribiendo (null = ninguna). */
    var textId by remember { mutableStateOf<Long?>(null) }
    var textIsNew by remember { mutableStateOf(false) }
    var showEmojis by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }

    fun newText() {
        c.begin()
        val l = TextLayer(newLayerId(), "", 0xFFFFFFFF.toInt(), font = 1, strokeOn = true)
        c.live(c.state.add(l))
        c.select(l.id)
        c.tool = Tool.MOVE
        textIsNew = true
        textId = l.id
    }

    fun editText(l: TextLayer) {
        c.begin()
        c.select(l.id)
        c.tool = Tool.MOVE
        textIsNew = false
        textId = l.id
    }

    fun finishText() {
        val l = c.state.layer(textId) as? TextLayer
        if (l == null || l.text.isBlank()) {
            c.cancel()
            if (l != null && !textIsNew) c.commit(c.state.remove(l.id))
        } else {
            c.end()
        }
        textId = null
    }

    val addImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            c.busy = "Abriendo imagen…"
            val bmp = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
            c.busy = null
            if (bmp != null) {
                c.addLayer(ImageLayer.fitted(bmp, margin = 96f))
                c.tool = Tool.MOVE
            } else c.toast("No se pudo abrir la imagen")
        }
    }

    LaunchedEffect(Unit) {
        if (session.startWithText && c.state.isEmpty) newText()
    }
    LaunchedEffect(c.message) {
        c.message?.let {
            vm.snackbar = it
            c.message = null
        }
    }

    BackHandler {
        when {
            textId != null -> finishText()
            c.canUndo -> confirmExit = true
            else -> vm.back()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { if (c.canUndo) confirmExit = true else vm.back() }) { Icon(Icons.Filled.Close, "Cerrar") }
                },
                actions = {
                    IconButton(onClick = { c.undo() }, enabled = c.canUndo && textId == null) { Icon(Icons.AutoMirrored.Filled.Undo, "Deshacer") }
                    IconButton(onClick = { c.redo() }, enabled = c.canRedo && textId == null) { Icon(Icons.AutoMirrored.Filled.Redo, "Rehacer") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            if (textId != null) finishText()
                            if (c.state.isEmpty) c.toast("Añade una imagen, un texto o un emoji") else showSave = true
                        },
                        enabled = c.busy == null,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Guardar", fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 6.dp) {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                    val tid = textId
                    if (tid != null) {
                        TextPanel(c, tid, textIsNew, onDone = { finishText() })
                    } else {
                        ToolPanel(c, scope = { block -> scope.launch { block() } }, onAddImage = {
                            addImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }, onEditText = { editText(it) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ToolBar(c, onText = { newText() }, onEmoji = { showEmojis = true }, onAddImage = {
                            addImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                factory = { ctx -> EditorView(ctx, c).apply { onEditText = { editText(it) } } },
                modifier = Modifier.fillMaxSize(),
            )
            SmallFloatingActionButton(
                onClick = { c.resetViewTick++; c.changed() },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) { Icon(Icons.Outlined.ZoomOutMap, "Ajustar a la pantalla", Modifier.size(20.dp)) }
            c.busy?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color(0x55000000)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(msg, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showEmojis) {
        EmojiSheet(onDismiss = { showEmojis = false }) { e ->
            showEmojis = false
            val offset = ((c.state.layers.size % 5) - 2) * 18f
            c.addLayer(TextLayer(newLayerId(), e, 0xFF000000.toInt(), strokeOn = false, cx = CANVAS / 2 + offset, cy = CANVAS / 2 + offset, scale = 2.4f))
            c.tool = Tool.MOVE
        }
    }

    if (showSave) {
        SaveSheet(vm, session, onDismiss = { showSave = false })
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Salir sin guardar?") },
            text = { Text("Perderás los cambios de este sticker.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.back() }) { Text("Salir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir editando") } },
        )
    }
}

// ---------------- Barra de herramientas ----------------

@Composable
private fun ToolBar(c: EditorController, onText: () -> Unit, onEmoji: () -> Unit, onAddImage: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolButton(Icons.Outlined.OpenWith, "Mover", c.tool == Tool.MOVE) { c.tool = Tool.MOVE }
        ToolButton(Icons.Outlined.ContentCut, "Recortar", c.tool == Tool.CUT) { c.tool = Tool.CUT }
        ToolButton(Icons.Outlined.TextFields, "Texto", false, onText)
        ToolButton(Icons.Outlined.EmojiEmotions, "Emoji", false, onEmoji)
        ToolButton(Icons.Outlined.Brush, "Dibujar", c.tool == Tool.DRAW) { c.tool = Tool.DRAW; c.select(null) }
        ToolButton(Icons.Outlined.BorderOuter, "Borde", c.tool == Tool.BORDER) { c.tool = Tool.BORDER }
        ToolButton(Icons.Outlined.Tune, "Ajustes", c.tool == Tool.ADJUST) { c.tool = Tool.ADJUST }
        ToolButton(Icons.Outlined.AddPhotoAlternate, "Imagen", false, onAddImage)
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .width(68.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = color, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

// ---------------- Paneles de cada herramienta ----------------

@Composable
private fun ToolPanel(
    c: EditorController,
    scope: (suspend () -> Unit) -> Unit,
    onAddImage: () -> Unit,
    onEditText: (TextLayer) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (c.tool) {
            Tool.MOVE -> MovePanel(c, onAddImage, onEditText)
            Tool.CUT -> CutPanel(c, scope)
            Tool.DRAW -> {
                ColorRow(c.drawColor, { c.drawColor = it })
                LabeledSlider("Grosor", c.drawWidth, 3f..48f) { c.drawWidth = it }
            }
            Tool.BORDER -> BorderPanel(c)
            Tool.ADJUST -> AdjustPanel(c)
            else -> MovePanel(c, onAddImage, onEditText)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MovePanel(c: EditorController, onAddImage: () -> Unit, onEditText: (TextLayer) -> Unit) {
    val sel = c.selected
    if (sel == null) {
        if (c.state.isEmpty) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAddImage) { Icon(Icons.Outlined.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Añadir imagen") }
            }
        }
        Hint("Toca un elemento para moverlo. Con dos dedos lo giras y cambias de tamaño (o usa el círculo de la esquina).")
        return
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (sel is TextLayer) SmallAction(Icons.Outlined.Edit, "Editar") { onEditText(sel) }
        SmallAction(Icons.Outlined.ContentCopy, "Duplicar") { c.duplicateSelected() }
        SmallAction(Icons.Outlined.Flip, "Espejo") { c.flipSelected() }
        SmallAction(Icons.Outlined.FlipToFront, "Delante") { c.bringToFront() }
        SmallAction(Icons.Outlined.FlipToBack, "Detrás") { c.sendToBack() }
        SmallAction(Icons.Outlined.CenterFocusStrong, "Centrar") { c.centerSelected() }
        SmallAction(Icons.Outlined.Delete, "Borrar", danger = true) { c.deleteSelected() }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = color)
    }
}

@Composable
private fun CutPanel(c: EditorController, scope: (suspend () -> Unit) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                val target = c.targetImage ?: return@Button c.toast("Primero añade una imagen")
                scope {
                    c.busy = "Quitando el fondo…"
                    try {
                        val fg = BitmapOps.removeBackgroundAi(context, target.original)
                        val now = c.state.layer(target.id) as? ImageLayer ?: target
                        val out = withContext(Dispatchers.Default) { BitmapOps.intersectAlpha(fg, now.bitmap) }
                        c.replaceBitmap(now, out)
                    } catch (e: Exception) {
                        c.toast("No se pudo con la IA (hace falta Google Play Services e internet la 1ª vez). Prueba \"Fondo liso\" o la varita.")
                    } finally {
                        c.busy = null
                    }
                }
            },
            modifier = Modifier.weight(1.3f),
        ) {
            Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Quitar fondo", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        OutlinedButton(
            onClick = {
                val target = c.targetImage ?: return@OutlinedButton c.toast("Primero añade una imagen")
                scope {
                    c.busy = "Borrando el fondo liso…"
                    val out = withContext(Dispatchers.Default) { BitmapOps.removePlainBackground(target.bitmap, c.wandTolerance.toInt()) }
                    c.replaceBitmap(c.state.layer(target.id) as? ImageLayer ?: target, out)
                    c.busy = null
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.FormatColorFill, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Fondo liso", maxLines = 1)
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CutMode.entries.forEach { m ->
            FilterChip(selected = c.cutMode == m, onClick = { c.cutMode = m; c.changed() }, label = { Text(m.label) })
        }
    }
    when (c.cutMode) {
        CutMode.ERASE, CutMode.RESTORE -> {
            LabeledSlider("Pincel", c.brushSize, 12f..160f) { c.brushSize = it }
            Hint(if (c.cutMode == CutMode.ERASE) "Pasa el dedo por lo que sobra. Dos dedos: zoom para afinar." else "Pasa el dedo para recuperar partes de la foto original.")
        }
        CutMode.WAND -> {
            LabeledSlider("Tolerancia", c.wandTolerance, 5f..120f) { c.wandTolerance = it }
            Hint("Toca un color del fondo y se borra toda esa zona. Repite en cada zona.")
        }
        CutMode.LASSO -> Hint("Rodea con el dedo lo que quieres conservar; lo de fuera se borra.")
        CutMode.SHAPE -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Shape.entries.forEach { s ->
                OutlinedButton(onClick = {
                    val t = c.targetImage ?: return@OutlinedButton c.toast("Primero añade una imagen")
                    c.replaceBitmap(t, BitmapOps.keepInside(t.bitmap, BitmapOps.shapePath(s, t.width, t.height)))
                }) { Text(s.label) }
            }
        }
    }
}

@Composable
private fun BorderPanel(c: EditorController) {
    val st = c.state
    LabeledSlider("Contorno", st.outline, 0f..24f, onFinished = { c.end() }) {
        c.begin()
        c.live(c.state.copy(outline = it))
    }
    ColorRow(st.outlineColor, { color ->
        c.commit(c.state.copy(outlineColor = color, outline = if (c.state.outline == 0f) 8f else c.state.outline))
    })
    Hint("El borde blanco típico de los stickers. Ponlo a 0 para quitarlo.")
}

@Composable
private fun AdjustPanel(c: EditorController) {
    val t = c.targetImage
    if (t == null) {
        Hint("Los ajustes se aplican a una imagen. Añade una primero.")
        return
    }
    LabeledSlider("Brillo", t.brightness, -80f..80f, onFinished = { c.end() }) { v ->
        c.begin(); (c.state.layer(t.id) as? ImageLayer)?.let { c.live(c.state.replace(it.copy(brightness = v))) }
    }
    LabeledSlider("Contraste", t.contrast, 0.5f..1.7f, onFinished = { c.end() }) { v ->
        c.begin(); (c.state.layer(t.id) as? ImageLayer)?.let { c.live(c.state.replace(it.copy(contrast = v))) }
    }
    LabeledSlider("Color", t.saturation, 0f..2f, onFinished = { c.end() }) { v ->
        c.begin(); (c.state.layer(t.id) as? ImageLayer)?.let { c.live(c.state.replace(it.copy(saturation = v))) }
    }
    TextButton(onClick = { c.updateLayer(t.copy(brightness = 0f, contrast = 1f, saturation = 1f)) }) { Text("Restablecer") }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onFinished: () -> Unit = {}, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(84.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, onValueChangeFinished = onFinished, modifier = Modifier.weight(1f).height(36.dp))
    }
}

// ---------------- Texto ----------------

@Composable
private fun TextPanel(c: EditorController, id: Long, isNew: Boolean, onDone: () -> Unit) {
    val l = c.state.layer(id) as? TextLayer ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(id) { runCatching { focus.requestFocus() } }

    fun update(n: TextLayer) {
        // Un texto nuevo se ajusta solo al ancho del sticker.
        val fitted = if (isNew) n.copy(scale = (470f / n.width).coerceIn(0.35f, 1.8f)) else n
        c.live(c.state.replace(fitted))
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = l.text,
                onValueChange = { update(l.copy(text = it.take(120))) },
                placeholder = { Text("Escribe aquí…") },
                modifier = Modifier.weight(1f).focusRequester(focus),
                maxLines = 3,
                textStyle = TextStyle(fontFamily = FontFamily(Fonts.typeface(l.font)), fontSize = 18.sp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onDone) { Text("Listo") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Fonts.names.forEachIndexed { i, name ->
                FilterChip(
                    selected = l.font == i,
                    onClick = { update(l.copy(font = i)) },
                    label = { Text(name, fontFamily = FontFamily(Fonts.typeface(i))) },
                )
            }
        }
        ColorRow(l.color, { update(l.copy(color = it)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Contorno", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Switch(checked = l.strokeOn, onCheckedChange = { update(l.copy(strokeOn = it)) })
        }
        if (l.strokeOn) ColorRow(l.strokeColor, { update(l.copy(strokeColor = it)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fondo", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Switch(checked = l.bgOn, onCheckedChange = { update(l.copy(bgOn = it)) })
        }
        if (l.bgOn) ColorRow(l.bgColor, { update(l.copy(bgColor = it)) })
    }
}

// ---------------- Emojis ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(54.dp),
            modifier = Modifier.fillMaxWidth().height(420.dp).padding(horizontal = 12.dp),
        ) {
            items(AllEmojis) { e ->
                Box(
                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 30.sp) }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

// ---------------- Guardar ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(vm: AppViewModel, session: EditSession, onDismiss: () -> Unit) {
    val packs by vm.repo.packs.collectAsState()
    val preview by androidx.compose.runtime.produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.Default) { Renderer.render(session.controller.state, 384) }
    }
    var emojis by remember { mutableStateOf(session.emojis.ifEmpty { listOf("😀") }) }
    val defaultPack = session.packId ?: packs.firstOrNull { !it.isFull }?.id
    var packId by remember { mutableStateOf(defaultPack) }
    var newName by remember { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(checkerBrush()),
                    contentAlignment = Alignment.Center,
                ) {
                    preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(6.dp)) }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Guardar sticker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("512×512 · WebP · sin marcas de agua", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionLabel("Emojis (para que WhatsApp lo sugiera)")
            EmojiPicker(emojis, { emojis = it })

            SectionLabel("Paquete")
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
                packs.forEach { p ->
                    val full = p.isFull && p.id != session.packId
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = packId == p.id, enabled = !full, onClick = { packId = p.id })
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = packId == p.id, onClick = null, enabled = !full, modifier = Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                if (full) "Lleno (${StickerPack.MAX_STICKERS})" else "${p.stickers.size}/${StickerPack.MAX_STICKERS}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().selectable(selected = packId == null, onClick = { packId = null }).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = packId == null, onClick = null, modifier = Modifier.padding(8.dp))
                    Text("Paquete nuevo", fontWeight = FontWeight.SemiBold)
                }
                if (packId == null) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(60) },
                        placeholder = { Text("Mis stickers") },
                        label = { Text("Nombre del paquete") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    vm.save(session, packId, if (packId == null) newName.trim().ifEmpty { "Mis stickers" } else null, emojis)
                },
                enabled = emojis.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Guardar", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}
