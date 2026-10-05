package com.stickerya.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Sticker(
    /** Archivo .webp de 512x512 que lee WhatsApp. */
    val file: String,
    val emojis: List<String>,
) {
    /** Copia sin pérdidas (PNG) para miniaturas y para volver a editar. */
    val png: String get() = file.removeSuffix(".webp") + ".png"
    /** Carpeta con las capas, para editar el sticker como se dejó. */
    val projectDir: String get() = "edit_" + file.removeSuffix(".webp")
}

data class StickerPack(
    val id: String,
    val name: String,
    val publisher: String,
    val version: Int,
    val stickers: List<Sticker>,
    val created: Long,
) {
    val isFull get() = stickers.size >= MAX_STICKERS
    val isReady get() = stickers.size >= MIN_STICKERS

    companion object {
        const val MIN_STICKERS = 3
        const val MAX_STICKERS = 30
        const val TRAY_FILE = "tray.png"
    }
}

/**
 * Guarda los paquetes en la memoria interna de la app: packs/<id>/pack.json, los .webp, los .png y el icono.
 * Lo usan la interfaz y el proveedor de contenido de WhatsApp, por eso todo está sincronizado.
 */
class PackRepository private constructor(context: Context) {

    private val root = File(context.filesDir, "packs").apply { mkdirs() }
    private val lock = Any()
    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packs: StateFlow<List<StickerPack>> = _packs

    init {
        reload()
    }

    fun dir(packId: String) = File(root, packId)
    fun file(packId: String, name: String) = File(dir(packId), name)

    fun get(id: String): StickerPack? = _packs.value.firstOrNull { it.id == id }

    /** Lee siempre del disco: lo usa WhatsApp desde otro hilo. */
    fun loadAll(): List<StickerPack> = synchronized(lock) {
        root.listFiles()?.filter { it.isDirectory }?.mapNotNull { read(it) }?.sortedBy { it.created } ?: emptyList()
    }

    fun load(id: String): StickerPack? = synchronized(lock) {
        if (!isSafeId(id)) null else read(dir(id))
    }

    private fun reload() {
        _packs.value = loadAll().sortedByDescending { it.created }
    }

    fun createPack(name: String, publisher: String): StickerPack = synchronized(lock) {
        val id = "pack_" + System.currentTimeMillis().toString(36) + (100..999).random()
        val pack = StickerPack(id, name.take(128).ifBlank { "Mis stickers" }, publisher.take(128).ifBlank { "Yo" }, 1, emptyList(), System.currentTimeMillis())
        dir(id).mkdirs()
        write(pack)
        reload()
        pack
    }

    fun rename(id: String, name: String, publisher: String) = update(id) {
        it.copy(name = name.take(128).ifBlank { it.name }, publisher = publisher.take(128).ifBlank { it.publisher })
    }

    fun deletePack(id: String) = synchronized(lock) {
        if (isSafeId(id)) dir(id).deleteRecursively()
        reload()
    }

    /**
     * Añade un sticker (o reemplaza uno si [replaceFile] no es null). [image] debe ser de 512x512.
     * Devuelve el nombre del archivo nuevo.
     */
    fun saveSticker(packId: String, image: Bitmap, emojis: List<String>, replaceFile: String? = null, project: (File) -> Unit = {}): String {
        val base = "s_" + System.currentTimeMillis().toString(36) + (100..999).random()
        val sticker = Sticker("$base.webp", emojis.take(3).ifEmpty { listOf(DEFAULT_EMOJI) })
        val webp = StickerEncoder.toWhatsAppWebp(image)
        synchronized(lock) {
            val d = dir(packId)
            File(d, sticker.file).writeBytes(webp)
            File(d, sticker.png).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val projectDir = File(d, sticker.projectDir)
            projectDir.mkdirs()
            runCatching { project(projectDir) }.onFailure { projectDir.deleteRecursively() }
        }
        update(packId) { pack ->
            val list = pack.stickers.toMutableList()
            val index = list.indexOfFirst { it.file == replaceFile }
            if (index >= 0) {
                deleteFiles(packId, list[index])
                list[index] = sticker
            } else {
                list.add(sticker)
            }
            pack.copy(stickers = list)
        }
        return sticker.file
    }

    fun setEmojis(packId: String, file: String, emojis: List<String>) = update(packId) { pack ->
        pack.copy(stickers = pack.stickers.map { if (it.file == file) it.copy(emojis = emojis.take(3).ifEmpty { listOf(DEFAULT_EMOJI) }) else it })
    }

    fun deleteSticker(packId: String, file: String) = update(packId) { pack ->
        pack.stickers.firstOrNull { it.file == file }?.let { deleteFiles(packId, it) }
        pack.copy(stickers = pack.stickers.filterNot { it.file == file })
    }

    /** Pone el sticker el primero: su imagen será el icono del paquete en WhatsApp. */
    fun makeCover(packId: String, file: String) = update(packId) { pack ->
        val s = pack.stickers.firstOrNull { it.file == file } ?: return@update pack
        pack.copy(stickers = listOf(s) + pack.stickers.filterNot { it.file == file })
    }

    fun move(packId: String, file: String, delta: Int) = update(packId) { pack ->
        val list = pack.stickers.toMutableList()
        val i = list.indexOfFirst { it.file == file }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@update pack
        list.add(j, list.removeAt(i))
        pack.copy(stickers = list)
    }

    private fun deleteFiles(packId: String, s: Sticker) {
        val d = dir(packId)
        File(d, s.file).delete()
        File(d, s.png).delete()
        File(d, s.projectDir).deleteRecursively()
    }

    /** Cada cambio sube la versión de imagen: así WhatsApp vuelve a leer el paquete. */
    private fun update(id: String, change: (StickerPack) -> StickerPack) = synchronized(lock) {
        val old = read(dir(id)) ?: return@synchronized
        val new = change(old)
        if (new != old) {
            val bumped = new.copy(version = old.version + 1)
            write(bumped)
            writeTray(bumped)
        }
        reload()
    }

    private fun writeTray(pack: StickerPack) {
        val trayFile = File(dir(pack.id), StickerPack.TRAY_FILE)
        val first = pack.stickers.firstOrNull() ?: run { trayFile.delete(); return }
        val src = BitmapFactory.decodeFile(File(dir(pack.id), first.png).path) ?: return
        val tray = Bitmap.createBitmap(TRAY_SIZE, TRAY_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(tray).drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, TRAY_SIZE, TRAY_SIZE), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        trayFile.outputStream().use { tray.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun read(d: File): StickerPack? = runCatching {
        val o = JSONObject(File(d, "pack.json").readText())
        val arr = o.getJSONArray("stickers")
        val stickers = (0 until arr.length()).map { i ->
            val s = arr.getJSONObject(i)
            val e = s.getJSONArray("emojis")
            Sticker(s.getString("file"), (0 until e.length()).map { e.getString(it) })
        }.filter { File(d, it.file).exists() }
        StickerPack(d.name, o.getString("name"), o.getString("publisher"), o.optInt("version", 1), stickers, o.optLong("created"))
    }.getOrNull()

    private fun write(pack: StickerPack) {
        val o = JSONObject()
            .put("name", pack.name)
            .put("publisher", pack.publisher)
            .put("version", pack.version)
            .put("created", pack.created)
            .put("stickers", JSONArray().apply {
                pack.stickers.forEach { s -> put(JSONObject().put("file", s.file).put("emojis", JSONArray(s.emojis))) }
            })
        val target = File(dir(pack.id), "pack.json")
        val tmp = File(dir(pack.id), "pack.json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(target)) {
            target.writeText(o.toString())
            tmp.delete()
        }
    }

    companion object {
        const val DEFAULT_EMOJI = "😀"
        const val TRAY_SIZE = 96

        fun isSafeId(id: String) = id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        fun isSafeFile(name: String) = name.isNotEmpty() && !name.contains('/') && !name.startsWith(".")

        @Volatile private var instance: PackRepository? = null
        fun get(context: Context): PackRepository =
            instance ?: synchronized(this) { instance ?: PackRepository(context.applicationContext).also { instance = it } }
    }
}
