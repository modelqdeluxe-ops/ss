package com.stickerya.app.whatsapp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

enum class WhatsAppApp(val pkg: String, val label: String) {
    NORMAL("com.whatsapp", "WhatsApp"),
    BUSINESS("com.whatsapp.w4b", "WhatsApp Business"),
}

object WhatsApp {

    fun isInstalled(context: Context, app: WhatsAppApp): Boolean = try {
        context.packageManager.getApplicationInfo(app.pkg, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun installed(context: Context) = WhatsAppApp.entries.filter { isInstalled(context, it) }

    /** Intent oficial de WhatsApp para añadir un paquete; con setPackage va a la app elegida (normal o Business). */
    fun addPackIntent(app: WhatsAppApp, packId: String, packName: String): Intent =
        Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", packId)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", packName)
            setPackage(app.pkg)
        }

    /** Pregunta a WhatsApp si ya tiene el paquete añadido. */
    fun isPackAdded(context: Context, app: WhatsAppApp, packId: String): Boolean = runCatching {
        val authority = app.pkg + ".provider.sticker_whitelist_check"
        if (context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA) == null) {
            false
        } else isWhitelisted(context, authority, packId)
    }.getOrDefault(false)

    private fun isWhitelisted(context: Context, authority: String, packId: String): Boolean {
        val uri = Uri.Builder().scheme("content").authority(authority).appendPath("is_whitelisted")
            .appendQueryParameter("authority", StickerContentProvider.AUTHORITY)
            .appendQueryParameter("identifier", packId)
            .build()
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("result")) == 1
        } ?: false
    }

    fun storeIntent(app: WhatsAppApp): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.pkg}"))

    /** Avisa a WhatsApp de que el paquete cambió (vuelve a leerlo). */
    fun notifyChanged(context: Context) {
        context.contentResolver.notifyChange(
            Uri.Builder().scheme("content").authority(StickerContentProvider.AUTHORITY).appendPath(StickerContentProvider.METADATA).build(),
            null,
        )
    }
}
