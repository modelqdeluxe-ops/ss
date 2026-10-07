package net.tierrasfantasticas.tfclient.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Un modelo animado de VFX tal como lo deja tools/build_vfx.py (assets/tfclient/vfx/models/&lt;id&gt;.json): texturas,
 * huesos con sus caras ya horneadas (posición relativa al pivote del hueso, en píxeles) y animaciones.
 * No depende del cliente: el servidor también lo puede leer (para la posición de los huesos).
 */
public final class VfxModel {
    /** Floats por cara: textura, normal (3), 4 vértices × (x, y, z, u, v), emisiva. */
    public static final int QUAD = 25;

    public record Tex(String path, int frames, int frameTime, boolean emissive, boolean body) {}

    public static final class Bone {
        public String id;
        public int parent;
        public float[] pivot;
        public float[] rot;
        public boolean head;
        public boolean phead;
        public boolean hidden;
        public boolean shade;
        public float[] quads;
        public int quadCount;
    }

    public static final class Track {
        public int bone;
        /** Fotogramas clave aplanados: tiempo, x, y, z, interpolación (0 lineal, 1 catmullrom, 2 escalón). */
        public float[] rot;
        public float[] pos;
        public float[] scale;
    }

    public static final class Anim {
        public float length;
        /** 0 una vez (luego vuelve al reposo), 1 se queda en el final, 2 en bucle. */
        public int loop;
        public Track[] tracks;
    }

    public final String name;
    public final Tex[] tex;
    public final Bone[] bones;
    public final Map<String, Anim> anims = new HashMap<>();
    private final Map<String, Integer> boneIndex = new HashMap<>();

    private VfxModel(String name, Tex[] tex, Bone[] bones) {
        this.name = name;
        this.tex = tex;
        this.bones = bones;
        for (int i = 0; i < bones.length; i++) {
            boneIndex.putIfAbsent(bones[i].id, i);
            boneIndex.putIfAbsent(bones[i].id.toLowerCase(java.util.Locale.ROOT), i);
        }
    }

    /** Índice del hueso por su id de ModelEngine (sin h_), o -1. */
    public int bone(String id) {
        if (id == null) return -1;
        Integer i = boneIndex.get(id);
        if (i == null) i = boneIndex.get(id.toLowerCase(java.util.Locale.ROOT));
        return i == null ? -1 : i;
    }

    public static VfxModel read(InputStream in) {
        JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray ta = o.getAsJsonArray("tex");
        Tex[] tex = new Tex[ta.size()];
        for (int i = 0; i < tex.length; i++) {
            JsonObject t = ta.get(i).getAsJsonObject();
            tex[i] = new Tex(t.get("path").getAsString(), Math.max(1, t.get("frames").getAsInt()),
                    Math.max(1, t.get("ft").getAsInt()), t.get("e").getAsBoolean(), t.get("body").getAsBoolean());
        }
        JsonArray ba = o.getAsJsonArray("bones");
        Bone[] bones = new Bone[ba.size()];
        for (int i = 0; i < bones.length; i++) {
            JsonObject b = ba.get(i).getAsJsonObject();
            Bone bone = new Bone();
            bone.id = b.get("id").getAsString();
            bone.parent = b.get("parent").getAsInt();
            bone.pivot = floats(b.getAsJsonArray("pivot"));
            bone.rot = floats(b.getAsJsonArray("rot"));
            bone.head = b.get("head").getAsBoolean();
            bone.phead = b.get("phead").getAsBoolean();
            bone.hidden = b.get("hidden").getAsBoolean();
            bone.shade = !b.has("shade") || b.get("shade").getAsBoolean();
            JsonArray qa = b.getAsJsonArray("quads");
            bone.quadCount = qa.size();
            bone.quads = new float[bone.quadCount * QUAD];
            for (int q = 0; q < bone.quadCount; q++) {
                JsonArray quad = qa.get(q).getAsJsonArray();
                int base = q * QUAD;
                for (int k = 0; k < 24; k++) bone.quads[base + k] = quad.get(k).getAsFloat();
                bone.quads[base + 24] = quad.size() > 24 ? quad.get(24).getAsFloat() : 0F;
            }
            bones[i] = bone;
        }
        VfxModel model = new VfxModel(o.has("name") ? o.get("name").getAsString() : "", tex, bones);
        JsonObject anims = o.getAsJsonObject("anims");
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            JsonObject a = e.getValue().getAsJsonObject();
            Anim anim = new Anim();
            anim.length = a.get("len").getAsFloat();
            String loop = a.get("loop").getAsString();
            anim.loop = "loop".equals(loop) ? 2 : "hold".equals(loop) ? 1 : 0;
            JsonArray tr = a.getAsJsonArray("tracks");
            anim.tracks = new Track[tr.size()];
            for (int i = 0; i < anim.tracks.length; i++) {
                JsonObject t = tr.get(i).getAsJsonObject();
                Track track = new Track();
                track.bone = t.get("b").getAsInt();
                track.rot = keys(t, "r");
                track.pos = keys(t, "p");
                track.scale = keys(t, "s");
                anim.tracks[i] = track;
            }
            model.anims.put(e.getKey(), anim);
        }
        return model;
    }

    private static float[] keys(JsonObject t, String key) {
        if (!t.has(key)) return null;
        JsonArray arr = t.getAsJsonArray(key);
        float[] out = new float[arr.size() * 5];
        for (int i = 0; i < arr.size(); i++) {
            JsonArray k = arr.get(i).getAsJsonArray();
            for (int j = 0; j < 5; j++) out[i * 5 + j] = k.get(j).getAsFloat();
        }
        return out;
    }

    private static float[] floats(JsonArray arr) {
        float[] out = new float[arr.size()];
        for (int i = 0; i < out.length; i++) out[i] = arr.get(i).getAsFloat();
        return out;
    }
}
