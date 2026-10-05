package com.stickerya.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerya.app.AppViewModel
import com.stickerya.app.Screen
import com.stickerya.app.data.StickerPack

@Composable
fun HomeScreen(vm: AppViewModel, onNewSticker: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    val packs by vm.repo.packs.collectAsState()
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header() }
            item {
                // Directo al grano: los tres caminos para crear un sticker, a un toque.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigChoice(Icons.Outlined.PhotoLibrary, "Galería", Modifier.weight(1f), onGallery)
                    BigChoice(Icons.Outlined.CameraAlt, "Cámara", Modifier.weight(1f), onCamera)
                    BigChoice(Icons.Outlined.TextFields, "Texto", Modifier.weight(1f)) { vm.openBlank(null) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mis paquetes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Filled.Add, null)
                        Text("Nuevo paquete")
                    }
                }
            }
            if (packs.isEmpty()) {
                item { EmptyPacks() }
            }
            items(packs, key = { it.id }) { pack ->
                PackCard(vm, pack) { vm.push(Screen.Pack(pack.id)) }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewSticker,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Crear sticker", fontWeight = FontWeight.Bold) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
        )
    }

    if (creating) {
        PackNameDialog(
            title = "Nuevo paquete",
            initialName = "",
            initialPublisher = vm.lastPublisher(),
            onDismiss = { creating = false },
            onConfirm = { name, publisher ->
                creating = false
                val p = vm.createPack(name, publisher)
                vm.push(Screen.Pack(p.id))
            },
        )
    }
}

@Composable
private fun Header() {
    Column(Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Brand, BrandPink))),
                contentAlignment = Alignment.Center,
            ) { Text("😜", fontSize = 24.sp) }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("StickerYa", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Stickers para WhatsApp, sin marcas de agua",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyPacks() {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✂️  →  😎  →  💬", fontSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            Text("Aún no tienes stickers", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Elige una foto, quita el fondo con un toque, añade texto y guárdalo. Con 3 stickers ya puedes añadir el paquete a WhatsApp o WhatsApp Business.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PackCard(vm: AppViewModel, pack: StickerPack, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pack.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${pack.stickers.size}/${StickerPack.MAX_STICKERS} stickers · ${pack.publisher}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!pack.isReady) {
                    Text(
                        "Faltan ${StickerPack.MIN_STICKERS - pack.stickers.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val preview = pack.stickers.take(5)
                preview.forEach { s ->
                    FileThumb(
                        vm.repo.file(pack.id, s.png),
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                        side = 160,
                    )
                }
                repeat(5 - preview.size) {
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)))
                }
            }
        }
    }
}

@Composable
fun PackNameDialog(
    title: String,
    initialName: String,
    initialPublisher: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var publisher by remember { mutableStateOf(initialPublisher) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nombre del paquete") },
                    placeholder = { Text("Mis stickers") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it.take(60) },
                    label = { Text("Autor (se ve en WhatsApp)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().ifEmpty { "Mis stickers" }, publisher.trim().ifEmpty { "Yo" }) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        iconContentColor = Color.Unspecified,
    )
}
