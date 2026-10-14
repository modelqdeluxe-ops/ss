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
    /** Cuánto se meten las esquinas de cada cara en la textura (en unidades 0-1: medio píxel de una de 1024). */
    public static final float UV_INSET = 0.0005F;

    /**
     * Una coordenada de textura movida un poco hacia el centro de su cara (como hace Minecraft al hornear modelos): así
     * en los bordes no se cuela el píxel de al lado ni, en las texturas animadas, una raya del fotograma siguiente.
     */
    public static float insetUv(float uv, float center) {
        float d = center - uv;
        return uv + Math.signum(d) * Math.min(UV_INSET, Math.abs(d) * 0.25F);
    }

    /** El centro de la cara «q» en la textura: {u, v}. */
    public static float[] uvCenter(float[] quads, int o) {
        float cu = 0, cv = 0;
        for (int k = 0; k < 4; k++) {
            cu += quads[o + 4 + k * 5 + 3] * 0.25F;
            cv += quads[o + 4 + k * 5 + 4] * 0.25F;
        }
        return new float[] {cu, cv};
    }

    /** tint: se tiñe del color del jefe (la grieta). */
    public record Tex(String path, int frames, int frameTime, boolean emissive, boolean body, boolean tint) {}

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
        /** Asiento de ModelEngine (hueso p_: ahí se monta a quien agarra el jefe). */
        public boolean seat;
        /** Caja de golpe de ModelEngine (hueso ob_): esquinas mín. y máx. respecto a su pivote, en píxeles; null = no. */
        public float[] hitbox;
        /** Caja b_ (alineada con los ejes del mundo: sigue al hueso sin girar con él); si no, ob_ (gira con él). */
        public boolean aabb;
        /**
         * Comportamiento de ModelEngine 4 (la etiqueta del nombre): seg, segf, tl, tlf (colas y segmentos), jig (física),
         * ik, nametag, leash, item_head, item_right, item_left, limb, head, head_inherit, seat, ghost, hitbox_obb,
         * hitbox_aabb; null = hueso normal.
         */
        public String beh;
        /** Parámetros de la etiqueta («h[yaw=60;speed=10]_», «jig[stiff=0.3;angle=30,10,5]_»...): nombre → valores. */
        public Map<String, float[]> params = Map.of();
        /** limb[type=X] / prarm_...: qué trozo de la skin del jugador. */
        public String limb;

        /** ¿Cola o segmento de ModelEngine (seg_, segf_, tl_, tlf_)? */
        public boolean segment() {
            return beh != null && (beh.equals("seg") || beh.equals("segf") || beh.equals("tl") || beh.equals("tlf"));
        }

        /** El pivote va delante (segf_, tlf_): el trozo visible cuelga hacia atrás. */
        public boolean frontPivot() {
            return "segf".equals(beh) || "tlf".equals(beh);
        }

        public float param(String key, float def) {
            float[] v = params.get(key);
            return v == null || v.length == 0 ? def : v[0];
        }
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
        /** Tapa a las animaciones de menos prioridad en los huesos que mueve (el «override» de Blockbench). */
        public boolean override;
        /** Scripts de la línea de tiempo («mm:skill» de ModelEngine): en qué segundo y qué skill. */
        public float[] fxTimes = new float[0];
        public String[] fxSkills = new String[0];
        /** Huesos que mueve (para mezclar animaciones por huesos). */
        public boolean[] moves;
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
    /** Una cadena IK de Blockbench (objeto nulo con objetivo): sus huesos de la raíz a la punta, el nulo y el polo. */
    public record IkChain(int[] bones, int nullBone, int pole, boolean lockRotation) {}

    /** Las cadenas IK del modelo (vacío si no tiene). */
    public IkChain[] ik = new IkChain[0];
    /** Tamaño del mob según el hueso «hitbox» de ModelEngine (bloques; 0 = no tiene). */
    public float hitW, hitH;
    /** Lo más lejos que llega en reposo (bloques; -1 = sin calcular, ver VfxHitboxes.reach). */
    public double reachCache = -1;
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

    /** La animación con ese nombre (sin distinguir mayúsculas), o null. */
    public Anim anim(String name) {
        if (name == null) return null;
        Anim a = anims.get(name);
        if (a != null) return a;
        for (Map.Entry<String, Anim> e : anims.entrySet()) if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        return null;
    }

    private float[] headLimits;

    /**
     * Límites de giro de cada cabeza según su etiqueta (h[yaw;pitch;minyaw;maxyaw;minpitch;maxpitch]_): 4 por hueso
     * (mín./máx. yaw, mín./máx. pitch), NaN = libre. Una copia de esto es lo que cambia headconfig en cada mob.
     */
    public float[] headLimits() {
        if (headLimits == null) {
            float[] out = new float[bones.length * 4];
            java.util.Arrays.fill(out, Float.NaN);
            for (int i = 0; i < bones.length; i++) {
                Bone b = bones[i];
                if (!b.head || b.params.isEmpty()) continue;
                float yaw = b.param("yaw", Float.NaN), pitch = b.param("pitch", Float.NaN);
                if (!Float.isNaN(yaw)) {
                    out[i * 4] = -Math.abs(yaw);
                    out[i * 4 + 1] = Math.abs(yaw);
                }
                if (!Float.isNaN(pitch)) {
                    out[i * 4 + 2] = -Math.abs(pitch);
                    out[i * 4 + 3] = Math.abs(pitch);
                }
                out[i * 4] = b.param("minyaw", out[i * 4]);
                out[i * 4 + 1] = b.param("maxyaw", out[i * 4 + 1]);
                out[i * 4 + 2] = b.param("minpitch", out[i * 4 + 2]);
                out[i * 4 + 3] = b.param("maxpitch", out[i * 4 + 3]);
            }
            headLimits = out;
        }
        return headLimits;
    }

    /** La velocidad de giro más lenta de sus cabezas (grados por tick; 0 = al momento), según sus etiquetas. */
    public float headSpeed() {
        float sp = 0F;
        for (Bone b : bones) {
            float v = b.head ? b.param("speed", 0F) : 0F;
            if (v > 0F && (sp == 0F || v < sp)) sp = v;
        }
        return sp;
    }

    /** Índice del hueso por su id de ModelEngine (sin h_), o -1. */
    public int bone(String id) {
        if (id == null) return -1;
        Integer i = boneIndex.get(id);
        if (i == null) i = boneIndex.get(id.toLowerCase(java.util.Locale.ROOT));
        if (i == null) {
            // ModelEngine quita los prefijos de comportamiento del nombre (ef_suelo → «suelo»)
            String tail = "_" + id.toLowerCase(java.util.Locale.ROOT);
            for (int b = 0; b < bones.length; b++) {
                if (bones[b].id.toLowerCase(java.util.Locale.ROOT).endsWith(tail) && bones[b].id.indexOf('_') == bones[b].id.length() - tail.length()) {
                    return b;
                }
            }
        }
        return i == null ? -1 : i;
    }

    public static VfxModel read(InputStream in) {
        JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray ta = o.getAsJsonArray("tex");
        Tex[] tex = new Tex[ta.size()];
        for (int i = 0; i < tex.length; i++) {
            JsonObject t = ta.get(i).getAsJsonObject();
            tex[i] = new Tex(t.get("path").getAsString(), Math.max(1, t.get("frames").getAsInt()),
                    Math.max(1, t.get("ft").getAsInt()), t.get("e").getAsBoolean(), t.get("body").getAsBoolean(),
                    t.has("tint") && t.get("tint").getAsBoolean());
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
            bone.seat = b.has("seat") && b.get("seat").getAsBoolean();
            if (b.has("hb")) bone.hitbox = floats(b.getAsJsonArray("hb"));
            bone.aabb = b.has("aabb") && b.get("aabb").getAsBoolean();
            if (b.has("beh")) {
                bone.beh = b.get("beh").getAsString();
            } else {
                // Modelos convertidos antes de la 1.3.80: la etiqueta sigue en el nombre (tl_tail_2)
                String low = bone.id.toLowerCase(java.util.Locale.ROOT);
                for (String tag : new String[] {"segf_", "seg_", "tlf_", "tl_"}) {
                    if (low.startsWith(tag) && bone.quadCount == 0 && b.getAsJsonArray("quads").size() == 0) {
                        bone.beh = tag.substring(0, tag.length() - 1);
                        break;
                    }
                }
            }
            if (b.has("bp")) {
                Map<String, float[]> ps = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : b.getAsJsonObject("bp").entrySet()) {
                    JsonElement v = e.getValue();
                    if (v.isJsonArray()) ps.put(e.getKey(), floats(v.getAsJsonArray()));
                    else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) ps.put(e.getKey(), new float[] {v.getAsFloat()});
                    else if (e.getKey().equals("type")) bone.limb = v.getAsString();
                }
                bone.params = ps;
            }
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
        if (o.has("hitbox")) {
            JsonArray hb = o.getAsJsonArray("hitbox");
            model.hitW = hb.get(0).getAsFloat();
            model.hitH = hb.get(1).getAsFloat();
        }
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
        if (o.has("ik")) {
            JsonArray ia = o.getAsJsonArray("ik");
            model.ik = new IkChain[ia.size()];
            for (int i = 0; i < ia.size(); i++) {
                JsonObject c = ia.get(i).getAsJsonObject();
                JsonArray ch = c.getAsJsonArray("chain");
                int[] bs = new int[ch.size()];
                for (int k = 0; k < bs.length; k++) bs[k] = ch.get(k).getAsInt();
                model.ik[i] = new IkChain(bs, c.get("null").getAsInt(), c.has("pole") ? c.get("pole").getAsInt() : -1,
                        c.has("lock") && c.get("lock").getAsBoolean());
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
            anim.override = a.has("ov") && a.get("ov").getAsBoolean();
            anim.moves = new boolean[bones.length];
            for (Track t : anim.tracks) if (t.bone >= 0 && t.bone < bones.length) anim.moves[t.bone] = true;
            if (a.has("fx")) {
                JsonArray fx = a.getAsJsonArray("fx");
                anim.fxTimes = new float[fx.size()];
                anim.fxSkills = new String[fx.size()];
                for (int i = 0; i < fx.size(); i++) {
                    anim.fxTimes[i] = fx.get(i).getAsJsonArray().get(0).getAsFloat();
                    anim.fxSkills[i] = fx.get(i).getAsJsonArray().get(1).getAsString();
                }
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
