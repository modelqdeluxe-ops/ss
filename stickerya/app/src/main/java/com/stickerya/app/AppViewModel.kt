package com.stickerya.app

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stickerya.app.data.ImageLoader
import com.stickerya.app.data.PackRepository
import com.stickerya.app.data.StickerPack
import com.stickerya.app.editor.BitmapOps
import com.stickerya.app.editor.EditorController
import com.stickerya.app.editor.EditorState
import com.stickerya.app.editor.ImageLayer
import com.stickerya.app.editor.ProjectStore
import com.stickerya.app.editor.Renderer
import com.stickerya.app.editor.Tool
import com.stickerya.app.whatsapp.WhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Una edición abierta: a qué paquete va y qué sticker reemplaza (si se está re-editando). */
class EditSession(
    val controller: EditorController,
    val packId: String?,
    val replaceFile: String? = null,
    val emojis: List<String> = emptyList(),
    /** Abrir directamente el cuadro de texto (sticker "solo texto"). */
    val startWithText: Boolean = false,
)

sealed interface Screen {
    data object Home : Screen
    data class Pack(val id: String) : Screen
    data class Editor(val session: EditSession) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = PackRepository.get(app)
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen get() = stack.last()

    var snackbar by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    /** Sube cada vez que la app vuelve al frente (para comprobar si WhatsApp ya tiene el paquete). */
    var resumeTick by mutableIntStateOf(0)

    fun push(s: Screen) {
        stack.add(s)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Abre el editor con una foto. */
    fun openImage(uri: Uri, packId: String?) {
        loading = true
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageLoader.load(getApplication(), uri) }
            loading = false
            if (bmp == null) {
                snackbar = "No se pudo abrir la imagen"
                return@launch
            }
            val controller = EditorController(EditorState(listOf(ImageLayer.fitted(bmp))))
            controller.tool = Tool.CUT
            push(Screen.Editor(EditSession(controller, packId)))
        }
    }

    fun openBlank(packId: String?) {
        push(Screen.Editor(EditSession(EditorController(EditorState(outline = 0f)), packId, startWithText = true)))
    }

    /** Vuelve a editar un sticker guardado: con sus capas si las hay, o con la imagen final. */
    fun editSticker(packId: String, file: String) {
        val pack = repo.get(packId) ?: return
        val sticker = pack.stickers.firstOrNull { it.file == file } ?: return
        loading = true
        viewModelScope.launch {
            val state = withContext(Dispatchers.IO) {
                ProjectStore.load(repo.file(packId, sticker.projectDir)) ?: run {
                    val b = BitmapFactory.decodeFile(repo.file(packId, sticker.png).path) ?: return@run null
                    EditorState(listOf(ImageLayer.fitted(BitmapOps.mutableCopy(b), margin = 0f)))
                }
            }
            loading = false
            if (state == null) {
                snackbar = "No se pudo abrir el sticker"
                return@launch
            }
            push(Screen.Editor(EditSession(EditorController(state), packId, file, sticker.emojis)))
        }
    }

    /** Guarda el sticker en un paquete (o en uno nuevo si [newPackName] no es null) y abre ese paquete. */
    fun save(session: EditSession, packId: String?, newPackName: String?, emojis: List<String>) {
        val state = session.controller.state
        loading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val image = Renderer.render(state)
                if (Renderer.isBlank(image)) return@withContext null
                val target = packId ?: repo.createPack(newPackName ?: "Mis stickers", lastPublisher()).id
                val replace = if (target == session.packId) session.replaceFile else null
                repo.saveSticker(target, image, emojis, replace) { dir -> ProjectStore.save(state, dir) }
                target
            }
            loading = false
            if (result == null) {
                snackbar = "El sticker está vacío"
                return@launch
            }
            WhatsApp.notifyChanged(getApplication())
            // Quita el editor y deja abierto el paquete.
            stack.removeAll { it is Screen.Editor }
            if (screen != Screen.Pack(result)) {
                stack.removeAll { it is Screen.Pack }
                push(Screen.Pack(result))
            }
            val pack = repo.get(result)
            snackbar = when {
                pack == null -> "Sticker guardado"
                !pack.isReady -> "Sticker guardado. Añade ${StickerPack.MIN_STICKERS - pack.stickers.size} más para enviarlo a WhatsApp"
                else -> "Sticker guardado"
            }
        }
    }

    /** El autor que se usó la última vez (así no hay que escribirlo en cada paquete). */
    fun lastPublisher(): String =
        repo.packs.value.firstOrNull()?.publisher ?: "Yo"

    fun createPack(name: String, publisher: String): StickerPack = repo.createPack(name, publisher)

    fun cameraFile(): File {
        val dir = File(getApplication<Application>().cacheDir, "camera").apply { mkdirs() }
        return File(dir, "foto_${System.currentTimeMillis()}.jpg")
    }
}
