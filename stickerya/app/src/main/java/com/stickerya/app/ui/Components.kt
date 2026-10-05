package com.stickerya.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerya.app.data.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val Palette = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF2D55, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF25D366,
    0xFF00C7BE, 0xFF32ADE6, 0xFF007AFF, 0xFF5856D6, 0xFF6C3BFF, 0xFFAF52DE, 0xFFFF6FB5, 0xFFA2845E, 0xFF8E8E93,
).map { it.toInt() }

/** Fila de colores; marca el elegido. */
@Composable
fun ColorRow(selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Palette) { c ->
            val color = Color(c)
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, if (c == selected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (c == selected) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Miniatura de un archivo de imagen (se carga en segundo plano y con fondo de cuadros). */
@Composable
fun FileThumb(file: File, modifier: Modifier = Modifier, side: Int = 256, checker: Boolean = true) {
    val bmp by produceState<android.graphics.Bitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { ImageLoader.thumb(file, side) }
    }
    Box(modifier.then(if (checker) Modifier.background(checkerBrush()) else Modifier), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp)) }
    }
}

@Composable
fun checkerBrush(): Brush {
    val a = MaterialTheme.colorScheme.surfaceContainerHigh
    val b = MaterialTheme.colorScheme.surfaceContainerLow
    return Brush.linearGradient(listOf(a, b))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit, onText: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Nuevo sticker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigChoice(Icons.Outlined.PhotoLibrary, "Galería", Modifier.weight(1f), onGallery)
                BigChoice(Icons.Outlined.CameraAlt, "Cámara", Modifier.weight(1f), onCamera)
                BigChoice(Icons.Outlined.TextFields, "Solo texto", Modifier.weight(1f), onText)
            }
        }
    }
}

@Composable
fun BigChoice(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

val QuickEmojis = listOf("😀", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤔", "😴", "😭", "😡", "🥺", "😱", "🤯", "🙄", "😏", "👍", "👏", "🙏", "💪", "❤️", "🔥", "💯", "🎉", "😅", "🤩", "😬", "🤡", "👀", "🫠", "💀", "🥳")

val AllEmojis = QuickEmojis + listOf(
    "😁", "😆", "😉", "😊", "🙂", "🙃", "😋", "😛", "😜", "🤪", "😝", "🤗", "🤭", "🤫", "😐", "😑", "😶", "😒", "😔", "😟",
    "😢", "😤", "🤬", "😳", "🥵", "🥶", "😨", "😰", "🤤", "🤢", "🤮", "🤧", "😷", "🤠", "🥸", "😇", "🤓", "🧐", "😈", "👻",
    "👽", "🤖", "💩", "😺", "😹", "😻", "🙈", "🙉", "🙊", "💋", "💔", "💖", "💕", "💥", "💫", "💦", "💨", "💤", "👌", "✌️",
    "🤞", "🤟", "🤘", "👋", "🤙", "👊", "✊", "🫶", "🙌", "🤝", "💅", "👑", "🎂", "🍕", "🍺", "☕", "⚽", "🏆", "🎶", "⭐",
    "🌈", "☀️", "🌙", "⚡", "❄️", "🌹", "🌸", "🍀", "🐶", "🐱", "🐸", "🐵", "🦄", "🐷", "🐔", "🦋", "✅", "❌", "❓", "❗",
)

/** Selector de hasta 3 emojis para el sticker (WhatsApp los usa para buscarlo). */
@Composable
fun EmojiPicker(selected: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier, itemSize: Dp = 42.dp) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(QuickEmojis) { e ->
            val on = e in selected
            Box(
                Modifier
                    .size(itemSize)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable {
                        onChange(
                            when {
                                on -> selected - e
                                selected.size >= 3 -> selected.drop(1) + e
                                else -> selected + e
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 22.sp) }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun Gap(w: Dp = 0.dp, h: Dp = 0.dp) = Spacer(Modifier.width(w).height(h))
