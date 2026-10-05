package com.stickerya.app.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Guarda las capas de un sticker para poder volver a editarlo (textos editables, borrados restaurables...). */
object ProjectStore {

    fun save(state: EditorState, dir: File) {
        dir.mkdirs()
        val written = HashMap<Bitmap, String>()
        fun png(b: Bitmap, name: String): String {
            written[b]?.let { return it }
            File(dir, name).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            written[b] = name
            return name
        }
        val layers = JSONArray()
        state.layers.forEachIndexed { i, l ->
            val o = JSONObject()
                .put("cx", l.cx.toDouble()).put("cy", l.cy.toDouble())
                .put("scale", l.scale.toDouble()).put("rotation", l.rotation.toDouble()).put("flip", l.flip)
            when (l) {
                is ImageLayer -> o.put("type", "image")
                    .put("img", png(l.bitmap, "l${i}.png"))
                    .put("orig", png(l.original, "l${i}o.png"))
                    .put("brightness", l.brightness.toDouble())
                    .put("contrast", l.contrast.toDouble())
                    .put("saturation", l.saturation.toDouble())
                is TextLayer -> o.put("type", "text")
                    .put("text", l.text).put("color", l.color).put("font", l.font)
                    .put("strokeOn", l.strokeOn).put("strokeColor", l.strokeColor)
                    .put("bgOn", l.bgOn).put("bgColor", l.bgColor)
                is StrokeLayer -> o.put("type", "stroke")
                    .put("color", l.color).put("width", l.strokeWidth.toDouble())
                    .put("points", JSONArray().apply { l.points.forEach { put(it.toDouble()) } })
            }
            layers.put(o)
        }
        val root = JSONObject()
            .put("outline", state.outline.toDouble())
            .put("outlineColor", state.outlineColor)
            .put("layers", layers)
        File(dir, "project.json").writeText(root.toString())
    }

    fun load(dir: File): EditorState? = runCatching {
        val root = JSONObject(File(dir, "project.json").readText())
        val arr = root.getJSONArray("layers")
        val cache = HashMap<String, Bitmap>()
        fun bmp(name: String): Bitmap = cache.getOrPut(name) {
            val b = BitmapFactory.decodeFile(File(dir, name).path) ?: error("Falta $name")
            BitmapOps.mutableCopy(b)
        }
        val layers = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val cx = o.getDouble("cx").toFloat()
            val cy = o.getDouble("cy").toFloat()
            val scale = o.getDouble("scale").toFloat()
            val rot = o.getDouble("rotation").toFloat()
            val flip = o.optBoolean("flip")
            when (o.getString("type")) {
                "image" -> ImageLayer(
                    newLayerId(), bmp(o.getString("img")), bmp(o.getString("orig")), cx, cy, scale, rot, flip,
                    o.optDouble("brightness", 0.0).toFloat(), o.optDouble("contrast", 1.0).toFloat(), o.optDouble("saturation", 1.0).toFloat(),
                )
                "text" -> TextLayer(
                    newLayerId(), o.getString("text"), o.getInt("color"), o.optInt("font"),
                    o.optBoolean("strokeOn", true), o.optInt("strokeColor", 0xFF000000.toInt()),
                    o.optBoolean("bgOn"), o.optInt("bgColor", -1), cx, cy, scale, rot, flip,
                )
                else -> {
                    val p = o.getJSONArray("points")
                    StrokeLayer(
                        newLayerId(), FloatArray(p.length()) { p.getDouble(it).toFloat() },
                        o.getInt("color"), o.getDouble("width").toFloat(), cx, cy, scale, rot, flip,
                    )
                }
            }
        }
        EditorState(layers, root.optDouble("outline", 0.0).toFloat(), root.optInt("outlineColor", -1))
    }.getOrNull()
}
