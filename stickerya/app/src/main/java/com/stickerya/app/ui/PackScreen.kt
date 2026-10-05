package com.stickerya.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerya.app.AppViewModel
import com.stickerya.app.data.Sticker
import com.stickerya.app.data.StickerPack
import com.stickerya.app.whatsapp.WhatsApp
import com.stickerya.app.whatsapp.WhatsAppApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackScreen(vm: AppViewModel, packId: String, onNewSticker: () -> Unit) {
    val packs by vm.repo.packs.collectAsState()
    val pack = packs.firstOrNull { it.id == packId }
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Sticker?>(null) }
    var editingEmojis by remember { mutableStateOf<Sticker?>(null) }
    var sentTo by remember { mutableStateOf<WhatsAppApp?>(null) }

    if (pack == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }

    // ¿Qué WhatsApp hay instalados y cuáles tienen ya el paquete?
    val installed = remember(vm.resumeTick) { WhatsApp.installed(context) }
    val added = remember { mutableStateMapOf<WhatsAppApp, Boolean>() }
    LaunchedEffect(vm.resumeTick, pack.id, pack.version) {
        val result = withContext(Dispatchers.IO) { WhatsAppApp.entries.associateWith { WhatsApp.isPackAdded(context, it, pack.id) } }
        added.putAll(result)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val app = sentTo
        if (res.resultCode == Activity.RESULT_OK) {
            vm.snackbar = "¡Listo! Ya está en ${app?.label ?: "WhatsApp"}: ábrelo en un chat > 😊 > Stickers"
            if (app != null) added[app] = true
        } else {
            val error = res.data?.getStringExtra("validation_error")
            if (error != null) vm.snackbar = "WhatsApp no aceptó el paquete: $error"
        }
    }

    fun send(app: WhatsAppApp) {
        sentTo = app
        try {
            launcher.launch(WhatsApp.addPackIntent(app, pack.id, pack.name))
        } catch (e: ActivityNotFoundException) {
            vm.snackbar = "${app.label} no está instalado o no está actualizado"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.padding(end = 8.dp)) {
                        Text(pack.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        Text("de ${pack.publisher}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                actions = {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Cambiar nombre") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Cambiar nombre o autor") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Eliminar paquete") }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { WhatsAppBar(pack, installed, added, ::send) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(3) }) { Progress(pack) }
            if (!pack.isFull) {
                item {
                    Surface(
                        onClick = onNewSticker,
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.aspectRatio(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.Add, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Añadir", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            items(pack.stickers, key = { it.file }) { s ->
                val isCover = s == pack.stickers.first()
                Surface(
                    onClick = { selected = s },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.aspectRatio(1f)
                        .then(if (isCover) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier),
                ) {
                    Box {
                        FileThumb(vm.repo.file(pack.id, s.png), Modifier.fillMaxSize(), side = 256, checker = false)
                        Text(s.emojis.firstOrNull() ?: "", fontSize = 14.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                    }
                }
            }
        }
    }

    selected?.let { s ->
        StickerActions(
            onDismiss = { selected = null },
            onEdit = { selected = null; vm.editSticker(pack.id, s.file) },
            onEmojis = { selected = null; editingEmojis = s },
            onCover = { selected = null; vm.repo.makeCover(pack.id, s.file); WhatsApp.notifyChanged(context) },
            onLeft = { vm.repo.move(pack.id, s.file, -1); WhatsApp.notifyChanged(context) },
            onRight = { vm.repo.move(pack.id, s.file, 1); WhatsApp.notifyChanged(context) },
            onDelete = { selected = null; vm.repo.deleteSticker(pack.id, s.file); WhatsApp.notifyChanged(context) },
            isCover = s == pack.stickers.firstOrNull(),
        )
    }

    editingEmojis?.let { s ->
        var emojis by remember(s) { mutableStateOf(s.emojis) }
        AlertDialog(
            onDismissRequest = { editingEmojis = null },
            title = { Text("Emojis del sticker") },
            text = {
                Column {
                    Text("WhatsApp los usa para sugerir el sticker. Elige de 1 a 3.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    EmojiPicker(emojis, { emojis = it })
                }
            },
            confirmButton = {
                TextButton(enabled = emojis.isNotEmpty(), onClick = {
                    vm.repo.setEmojis(pack.id, s.file, emojis)
                    WhatsApp.notifyChanged(context)
                    editingEmojis = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingEmojis = null }) { Text("Cancelar") } },
        )
    }

    if (renaming) {
        PackNameDialog(
            title = "Paquete",
            initialName = pack.name,
            initialPublisher = pack.publisher,
            onDismiss = { renaming = false },
            onConfirm = { n, p ->
                renaming = false
                vm.repo.rename(pack.id, n, p)
                WhatsApp.notifyChanged(context)
            },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("¿Eliminar \"${pack.name}\"?") },
            text = { Text("Se borran sus ${pack.stickers.size} stickers de la app. Si ya estaba en WhatsApp, desaparecerá de allí también.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.repo.deletePack(pack.id)
                    WhatsApp.notifyChanged(context)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Progress(pack: StickerPack) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        val n = pack.stickers.size
        val text = when {
            n < StickerPack.MIN_STICKERS -> "$n de ${StickerPack.MIN_STICKERS} mínimo · WhatsApp pide al menos 3 stickers por paquete"
            pack.isFull -> "Paquete lleno ($n/${StickerPack.MAX_STICKERS}). Crea otro paquete para seguir"
            else -> "$n/${StickerPack.MAX_STICKERS} stickers · el primero (con borde) es el icono del paquete"
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { n / StickerPack.MAX_STICKERS.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (pack.isReady) WhatsAppGreen else MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun WhatsAppBar(pack: StickerPack, installed: List<WhatsAppApp>, added: Map<WhatsAppApp, Boolean>, onSend: (WhatsAppApp) -> Unit) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 8.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installed.isEmpty()) {
                Text("No encuentro WhatsApp en este teléfono.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhatsAppApp.entries.forEach { app ->
                        OutlinedButton(onClick = { runCatching { context.startActivity(WhatsApp.storeIntent(app)) } }, modifier = Modifier.weight(1f)) {
                            Text("Instalar ${app.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                return@Column
            }
            if (!pack.isReady) {
                Text(
                    "Añade ${StickerPack.MIN_STICKERS - pack.stickers.size} sticker(s) más para poder enviarlo a WhatsApp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            installed.forEach { app ->
                val isAdded = added[app] == true
                val color = if (app == WhatsAppApp.NORMAL) WhatsAppGreen else WhatsAppBusiness
                Button(
                    onClick = { onSend(app) },
                    enabled = pack.isReady,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                ) {
                    if (isAdded) {
                        Icon(Icons.Filled.CheckCircle, null)
                        Spacer(Modifier.size(8.dp))
                        Text("En ${app.label} · se actualiza solo", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Añadir a ${app.label}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerActions(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onEmojis: () -> Unit,
    onCover: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onDelete: () -> Unit,
    isCover: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ListItem(headlineContent = { Text("Editar") }, leadingContent = { Icon(Icons.Outlined.Edit, null) }, colors = colors, modifier = Modifier.clickableRow(onEdit))
            ListItem(headlineContent = { Text("Cambiar emojis") }, leadingContent = { Icon(Icons.Outlined.EmojiEmotions, null) }, colors = colors, modifier = Modifier.clickableRow(onEmojis))
            if (!isCover) {
                ListItem(headlineContent = { Text("Usar como icono del paquete") }, leadingContent = { Icon(Icons.Outlined.Star, null) }, colors = colors, modifier = Modifier.clickableRow(onCover))
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLeft, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null); Text("Mover antes")
                }
                OutlinedButton(onClick = onRight, modifier = Modifier.weight(1f)) {
                    Text("Mover después"); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            ListItem(
                headlineContent = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                colors = colors,
                modifier = Modifier.clickableRow(onDelete),
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)
