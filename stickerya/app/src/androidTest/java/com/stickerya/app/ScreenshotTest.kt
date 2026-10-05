package com.stickerya.app

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stickerya.app.data.PackRepository
import com.stickerya.app.editor.EditorState
import com.stickerya.app.editor.Renderer
import com.stickerya.app.editor.TextLayer
import com.stickerya.app.editor.newLayerId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Recorre la app como un usuario y guarda capturas (las recoge el workflow). */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val dir = File(inst.targetContext.filesDir, "screens").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(900)
        // Lo que se cargó en segundo plano durante la espera necesita otro fotograma para verse.
        rule.waitForIdle()
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun recorrido() {
        val repo = PackRepository.get(inst.targetContext)
        repo.packs.value.forEach { repo.deletePack(it.id) }
        shot("01_inicio_vacio")

        val pack = repo.createPack("Memes del grupo", "Dani")
        listOf("JAJA" to 0xFFFFCC00, "NO" to 0xFFFF2D55, "OK" to 0xFF34C759).forEach { (t, color) ->
            val st = EditorState(listOf(TextLayer(newLayerId(), t, color.toInt(), font = 1, scale = 2f)), outline = 10f)
            repo.saveSticker(pack.id, Renderer.render(st), listOf("😂"))
        }
        rule.waitForIdle()
        shot("02_inicio")

        rule.onNodeWithText("Memes del grupo").performClick()
        shot("03_paquete")

        rule.onNodeWithText("Añadir").performClick()
        shot("04_origen")
        rule.onNodeWithText("Solo texto").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("¡Buenos días!")
        shot("05_texto")
        rule.onNodeWithText("Listo").performClick()
        rule.onNodeWithText("Borde").performClick()
        shot("06_borde")
        rule.onNodeWithText("Recortar").performClick()
        shot("07_recortar")
        rule.onAllNodesWithText("Guardar").onFirst().performClick()
        shot("08_guardar")
        rule.onAllNodesWithText("Guardar").onLast().performClick()
        rule.waitForIdle()
        Thread.sleep(1500)
        shot("09_paquete_listo")
    }
}
