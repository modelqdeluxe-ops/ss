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

    /** Huesos del «muñeco» de los efectos de kill: raíz y cabeza, cuerpo, brazo der., brazo izq., pierna der., pierna izq. */
    public static final int RIG_ROOT = 0, RIG_HEAD = 1, RIG_BODY = 2, RIG_RIGHT_ARM = 3, RIG_LEFT_ARM = 4, RIG_RIGHT_LEG = 5,
            RIG_LEFT_LEG = 6;
    /** Marca de las caras que son el muñeco (en el juego las hace el mob de verdad). */
    public static final int FLAG_ACTOR = 2;

    public final String name;
    public final Tex[] tex;
    /**
     * Una figura del muñeco, que en el juego es el propio mob: los huesos que sigue (rig) y con qué texturas se dibuja
     * (null = la suya; si no, el material del efecto: óxido, piedra, holograma...). Puede haber varias (el cuerpo y su
     * fantasma, por ejemplo).
     */
    public record Figure(int[] rig, String[] tex) {}

    /** Figuras del muñeco de los efectos de kill (null si el modelo no tiene). */
    public Figure[] figures;
    private org.joml.Matrix4f[] rest;
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

    /** Pose de reposo (sin animación) de cada hueso, para saber cuánto se ha movido el muñeco. */
    public org.joml.Matrix4f[] rest() {
        if (rest == null) {
            org.joml.Matrix4f[] r = new org.joml.Matrix4f[bones.length];
            for (int i = 0; i < r.length; i++) r[i] = new org.joml.Matrix4f();
            VfxPose.compute(this, null, 0F, 0F, r);
            rest = r;
        }
        return rest;
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
        if (o.has("figures")) {
            JsonArray fa = o.getAsJsonArray("figures");
            String[] keys = {"root", "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg"};
            model.figures = new Figure[fa.size()];
            for (int f = 0; f < fa.size(); f++) {
                JsonObject fo = fa.get(f).getAsJsonObject();
                JsonObject r = fo.getAsJsonObject("rig");
                int[] rig = new int[keys.length];
                for (int i = 0; i < keys.length; i++) rig[i] = r.has(keys[i]) ? r.get(keys[i]).getAsInt() : -1;
                JsonArray ta2 = fo.getAsJsonArray("tex");
                String[] ft = new String[ta2.size()];
                for (int i = 0; i < ft.length; i++) ft[i] = ta2.get(i).isJsonNull() ? null : ta2.get(i).getAsString();
                model.figures[f] = new Figure(rig, ft);
            }
        }
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
