package com.stickerya.app.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.stickerya.app.BuildConfig
import com.stickerya.app.data.PackRepository
import com.stickerya.app.data.StickerPack
import java.io.File
import java.io.FileNotFoundException

/**
 * El "contrato" de stickers de WhatsApp (el mismo para WhatsApp y WhatsApp Business):
 *  content://<autoridad>/metadata                  -> todos los paquetes
 *  content://<autoridad>/metadata/<id>             -> un paquete
 *  content://<autoridad>/stickers/<id>             -> los stickers de un paquete
 *  content://<autoridad>/stickers_asset/<id>/<arc> -> el .webp de un sticker o el icono .png
 */
class StickerContentProvider : ContentProvider() {

    private val repo get() = PackRepository.get(context!!)

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val seg = uri.pathSegments
        return when {
            seg.size == 1 && seg[0] == METADATA -> packsCursor(repo.loadAll(), uri)
            seg.size == 2 && seg[0] == METADATA -> packsCursor(listOfNotNull(repo.load(seg[1])), uri)
            seg.size == 2 && seg[0] == STICKERS -> stickersCursor(repo.load(seg[1]), uri)
            else -> throw IllegalArgumentException("URI desconocida: $uri")
        }
    }

    private fun packsCursor(packs: List<StickerPack>, uri: Uri): Cursor {
        val c = MatrixCursor(arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_download_link",
            "sticker_pack_publisher_email",
            "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website",
            "sticker_pack_license_agreement_website",
            "image_data_version",
            "whatsapp_will_not_cache_stickers",
            "animated_sticker_pack",
        ))
        for (p in packs) {
            c.addRow(arrayOf<Any>(
                p.id, p.name, p.publisher, StickerPack.TRAY_FILE,
                "", "", "", "", "", "",
                p.version.toString(), 0, 0,
            ))
        }
        c.setNotificationUri(context!!.contentResolver, uri)
        return c
    }

    private fun stickersCursor(pack: StickerPack?, uri: Uri): Cursor {
        val c = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
        pack?.stickers?.forEach { s -> c.addRow(arrayOf<Any>(s.file, s.emojis.joinToString(","), "")) }
        c.setNotificationUri(context!!.contentResolver, uri)
        return c
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val seg = uri.pathSegments
        if (seg.size != 3 || seg[0] != STICKERS_ASSET) throw FileNotFoundException(uri.toString())
        val (packId, name) = seg[1] to seg[2]
        if (!PackRepository.isSafeId(packId) || !PackRepository.isSafeFile(name)) throw FileNotFoundException(uri.toString())
        if (!name.endsWith(".webp") && name != StickerPack.TRAY_FILE) throw FileNotFoundException(uri.toString())
        val file: File = repo.file(packId, name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun getType(uri: Uri): String {
        val seg = uri.pathSegments
        return when {
            seg.size == 1 && seg[0] == METADATA -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$METADATA"
            seg.size == 2 && seg[0] == METADATA -> "vnd.android.cursor.item/vnd.$AUTHORITY.$METADATA"
            seg.size == 2 && seg[0] == STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$STICKERS"
            seg.size == 3 && seg[0] == STICKERS_ASSET -> if (seg[2].endsWith(".png")) "image/png" else "image/webp"
            else -> throw IllegalArgumentException("URI desconocida: $uri")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        const val AUTHORITY = BuildConfig.CONTENT_PROVIDER_AUTHORITY
        const val METADATA = "metadata"
        const val STICKERS = "stickers"
        const val STICKERS_ASSET = "stickers_asset"
    }
}
