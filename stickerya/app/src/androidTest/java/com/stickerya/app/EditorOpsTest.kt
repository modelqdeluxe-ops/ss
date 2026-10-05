package com.stickerya.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stickerya.app.editor.BitmapOps
import com.stickerya.app.editor.EditorController
import com.stickerya.app.editor.EditorState
import com.stickerya.app.editor.ImageLayer
import com.stickerya.app.editor.Outline
import com.stickerya.app.editor.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorOpsTest {

    /** Un círculo rojo sobre fondo verde liso. */
    private fun photo(): Bitmap {
        val b = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(20, 200, 40))
        c.drawCircle(100f, 100f, 50f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED })
        return b
    }

    @Test
    fun fondoLisoSeBorraYLaFiguraSeQueda() {
        val out = BitmapOps.removePlainBackground(photo(), 40)
        assertEquals(0, Color.alpha(out.getPixel(5, 5)))
        assertEquals(255, Color.alpha(out.getPixel(100, 100)))
    }

    @Test
    fun varitaBorraSoloLaZonaTocada() {
        val out = BitmapOps.floodErase(photo(), listOf(100 to 100), 40)
        assertEquals(0, Color.alpha(out.getPixel(100, 100)))
        assertEquals(255, Color.alpha(out.getPixel(5, 5)))
    }

    @Test
    fun formaCirculoDejaEsquinasTransparentes() {
        val p = photo()
        val out = BitmapOps.keepInside(p, BitmapOps.shapePath(Shape.HEART, 200f, 200f))
        assertEquals(0, Color.alpha(out.getPixel(1, 1)))
        assertEquals(255, Color.alpha(out.getPixel(100, 110)))
    }

    @Test
    fun contornoRodeaLaFigura() {
        val b = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(40f, 40f, 60f, 60f, Paint().apply { color = Color.BLUE })
        val o = Outline.make(b, 6f, Color.WHITE)
        assertEquals(255, Color.alpha(o.getPixel(35, 50)))
        assertEquals(0, Color.alpha(o.getPixel(25, 50)))
    }

    @Test
    fun deshacerYRehacer() {
        val c = EditorController(EditorState())
        val l = ImageLayer.fitted(photo())
        c.addLayer(l)
        assertTrue(c.canUndo)
        c.undo()
        assertTrue(c.state.isEmpty)
        c.redo()
        assertFalse(c.state.isEmpty)
    }
}
