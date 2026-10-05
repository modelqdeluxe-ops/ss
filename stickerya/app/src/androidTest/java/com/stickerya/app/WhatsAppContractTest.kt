package com.stickerya.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stickerya.app.data.PackRepository
import com.stickerya.app.editor.EditorState
import com.stickerya.app.editor.ImageLayer
import com.stickerya.app.editor.ProjectStore
import com.stickerya.app.editor.Renderer
import com.stickerya.app.editor.StrokeLayer
import com.stickerya.app.editor.TextLayer
import com.stickerya.app.editor.newLayerId
import com.stickerya.app.whatsapp.StickerContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Comprueba que lo que lee WhatsApp cumple sus reglas. */
@RunWith(AndroidJUnit4::class)
class WhatsAppContractTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val repo = PackRepository.get(ctx)

    private fun noise(w: Int, h: Int): Bitmap {
        val r = Random(7)
        val px = IntArray(w * h) { (0xFF shl 24) or r.nextInt(0xFFFFFF) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun state(i: Int) = EditorState(
        listOf(
            ImageLayer.fitted(noise(900, 700)),
            TextLayer(newLayerId(), "Hola $i", 0xFFFFCC00.toInt(), font = i % 7, cy = 400f),
            StrokeLayer.fromCanvasPoints(listOf(40f, 40f, 120f, 90f, 200f, 60f), 0xFFFF2D55.toInt(), 12f),
        ),
        outline = 8f,
    )

    @Test
    fun packCumpleLasReglasDeWhatsApp() {
        val pack = repo.createPack("Prueba", "Yo")
        repeat(3) { i ->
            val st = state(i)
            repo.saveSticker(pack.id, Renderer.render(st), listOf("😀", "🔥")) { ProjectStore.save(st, it) }
        }
        val cr = ctx.contentResolver
        val base = Uri.parse("content://${StickerContentProvider.AUTHORITY}")

        cr.query(base.buildUpon().appendPath("metadata").build(), null, null, null, null)!!.use { c ->
            var found = false
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("sticker_pack_identifier")) != pack.id) continue
                found = true
                assertEquals("Prueba", c.getString(c.getColumnIndexOrThrow("sticker_pack_name")))
                assertEquals("Yo", c.getString(c.getColumnIndexOrThrow("sticker_pack_publisher")))
                assertEquals("tray.png", c.getString(c.getColumnIndexOrThrow("sticker_pack_icon")))
                assertEquals(0, c.getInt(c.getColumnIndexOrThrow("animated_sticker_pack")))
                assertTrue(c.getString(c.getColumnIndexOrThrow("image_data_version")).toInt() >= 4)
            }
            assertTrue("El paquete no aparece en metadata", found)
        }

        val files = ArrayList<String>()
        cr.query(base.buildUpon().appendPath("stickers").appendPath(pack.id).build(), null, null, null, null)!!.use { c ->
            while (c.moveToNext()) {
                files += c.getString(c.getColumnIndexOrThrow("sticker_file_name"))
                assertEquals("😀,🔥", c.getString(c.getColumnIndexOrThrow("sticker_emoji")))
            }
        }
        assertEquals(3, files.size)

        for (f in files) {
            val bytes = cr.openAssetFileDescriptor(base.buildUpon().appendPath("stickers_asset").appendPath(pack.id).appendPath(f).build(), "r")!!
                .createInputStream().use { it.readBytes() }
            assertTrue("$f pesa ${bytes.size} bytes (máx 100 KB)", bytes.size <= 100 * 1024)
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WEBP", String(bytes, 8, 4, Charsets.US_ASCII))
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertEquals(512, bmp.width)
            assertEquals(512, bmp.height)
            assertTrue("Las esquinas deben ser transparentes", (bmp.getPixel(0, 0) ushr 24) == 0)
        }

        val tray = cr.openAssetFileDescriptor(base.buildUpon().appendPath("stickers_asset").appendPath(pack.id).appendPath("tray.png").build(), "r")!!
            .createInputStream().use { it.readBytes() }
        assertTrue("Icono: ${tray.size} bytes (máx 50 KB)", tray.size <= 50 * 1024)
        val t = BitmapFactory.decodeByteArray(tray, 0, tray.size)
        assertEquals(96, t.width)
        assertEquals(96, t.height)

        // Las capas se recuperan para volver a editar.
        val sticker = repo.get(pack.id)!!.stickers.first()
        val loaded = ProjectStore.load(repo.file(pack.id, sticker.projectDir))
        assertNotNull(loaded)
        assertEquals(3, loaded!!.layers.size)
        assertTrue(loaded.layers[1] is TextLayer)

        repo.deletePack(pack.id)
    }
}
