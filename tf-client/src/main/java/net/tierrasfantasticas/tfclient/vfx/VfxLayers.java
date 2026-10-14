package net.tierrasfantasticas.tfclient.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.joml.Matrix4f;

/**
 * Varias animaciones a la vez sobre un modelo de ModelEngine, como las mezcla ModelEngine (los jefes: la Hidra muerde
 * con una cabeza mientras gira las patas y respira con el resto). Abajo va la animación por defecto (reposo, andar o
 * la que ponga defaultstate); encima, las de state{} por prioridad: una con «override» (la casilla de Blockbench) tapa
 * a las de debajo en los huesos que mueve; una sin él se suma. Cada una entra y sale suave («li» y «lo», en ticks).
 * Una de una vez acaba sola (con su «lo»); una que se queda («hold») sigue en su final hasta que la quitan.
 *
 * <p>No depende del cliente: el servidor lleva las mismas capas para saber dónde está cada hueso (@modelpart) y
 * lanzar los scripts «mm:» de la línea de tiempo.
 */
public final class VfxLayers {
    /** Ticks que tarda en cambiar de una animación por defecto a otra (de reposo a andar). */
    private static final float DEFAULT_FADE = 4F;

    public static final class Layer {
        public final String name;
        final VfxModel.Anim anim;
        public final float start;
        public final float speed;
        public final int lerpIn;
        public int lerpOut;
        public final int priority;
        /** Cuándo empezó a irse (ticks); -1 = no se va. */
        public float stopAt = -1;

        Layer(String name, VfxModel.Anim anim, float start, float speed, int lerpIn, int lerpOut, int priority) {
            this.name = name;
            this.anim = anim;
            this.start = start;
            this.speed = speed <= 0 ? 1F : speed;
            this.lerpIn = Math.max(0, lerpIn);
            this.lerpOut = Math.max(0, lerpOut);
            this.priority = priority;
        }

        /** Segundos de animación en el instante «now» (ticks). */
        float seconds(float now) {
            return (now - start) / 20F * speed;
        }

        /** Peso en «now» (0 = ya no cuenta). */
        float weight(float now) {
            float w = lerpIn > 0 ? Math.min(1F, Math.max(0F, (now - start) / lerpIn)) : 1F;
            if (stopAt >= 0) {
                return lerpOut > 0 ? w * Math.max(0F, 1F - (now - stopAt) / lerpOut) : 0F;
            }
            if (anim.loop == 0) {
                float over = (seconds(now) - anim.length) * 20F / speed;
                if (over > 0) return lerpOut > 0 ? w * Math.max(0F, 1F - over / lerpOut) : 0F;
            }
            return w;
        }

        boolean done(float now) {
            return weight(now) <= 0F && (stopAt >= 0 || anim.loop == 0 && seconds(now) > anim.length);
        }

        float time(float now) {
            float t = seconds(now);
            if (anim.loop == 2 && anim.length > 0) return t % anim.length;
            return Math.max(0F, Math.min(t, anim.length));
        }
    }

    private final List<Layer> layers = new ArrayList<>();
    /** Las animaciones por defecto (defaultstate{t=idle|walk;s=...}). */
    private String idle = "idle";
    private String walk = "walk";
    private boolean walking;
    /** La animación por defecto que se ve y la de antes (para pasar de una a otra suave). */
    private String base;
    private String prevBase;
    /** Cuándo empezó la de ahora y la de antes (cada una desde su principio) y desde cuándo se mezclan. */
    private float baseStart;
    private float prevStart;
    private float baseSince = -1000F;
    private boolean dead;

    /** state{s;sp;li;lo;p;force}: pone una animación encima. false si el modelo no la tiene. */
    public boolean play(VfxModel m, String name, float now, float speed, int lerpIn, int lerpOut, int priority, boolean force) {
        VfxModel.Anim a = m.anim(name);
        if (a == null) return false;
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (!l.name.equalsIgnoreCase(name)) continue;
            // Ya está y sigue (no se va ni ha acabado): ModelEngine no la vuelve a empezar salvo con force
            if (!force && l.stopAt < 0 && (l.anim.loop != 0 || l.seconds(now) <= l.anim.length)) return true;
            layers.remove(i);
        }
        layers.add(new Layer(name, a, now, speed, lerpIn, lerpOut, priority));
        return true;
    }

    /** state{s;r=true}: quita una animación (con su salida suave «lerpOut»; -1 = la suya). */
    public void stop(String name, float now, int lerpOut) {
        for (Layer l : layers) {
            if (!l.name.equalsIgnoreCase(name) || l.stopAt >= 0) continue;
            l.stopAt = now;
            if (lerpOut >= 0) l.lerpOut = lerpOut;
        }
    }

    /** Quita todas al momento (al cambiar de modelo). */
    public void clear() {
        layers.clear();
    }

    /** defaultstate{t=idle|walk;s=...}: la animación por defecto de reposo o de andar. */
    public void setDefault(String type, String name, float now) {
        if (type == null || name == null) return;
        String t = type.toLowerCase(java.util.Locale.ROOT);
        if (t.startsWith("walk")) walk = name;
        else if (t.startsWith("idle")) idle = name;
    }

    public void setWalking(boolean w) {
        walking = w;
    }

    /** Al morir: la de muerte tapa todo y deja de haber reposo y andar. */
    public void death(VfxModel m, float now) {
        if (dead) return;
        dead = true;
        for (Layer l : layers) if (l.stopAt < 0) {
            l.stopAt = now;
            l.lerpOut = Math.max(l.lerpOut, 3);
        }
        VfxModel.Anim a = m.anim("death");
        if (a != null) layers.add(new Layer("death", a, now, 1F, 3, 0, Integer.MAX_VALUE));
    }

    /** Quita las que ya han terminado del todo. */
    public void prune(float now) {
        layers.removeIf(l -> l.done(now));
    }

    public List<Layer> active() {
        return layers;
    }

    public String idleName() {
        return idle;
    }

    public String walkName() {
        return walk;
    }

    /** La animación por defecto que toca ahora (y si cambió, desde cuándo, para mezclar). */
    private String baseFor(VfxModel m, float now) {
        if (dead) return null;
        String want = walking && m.anim(walk) != null ? walk : idle;
        if (m.anim(want) == null) want = null;
        if (want == null ? base != null : !want.equals(base)) {
            prevBase = base;
            prevStart = baseStart;
            base = want;
            baseStart = now;
            baseSince = now;
        }
        return base;
    }

    /** Pone al día la animación por defecto (reposo o andar) sin calcular la pose (el servidor, cada tick). */
    public void update(VfxModel m, float now) {
        baseFor(m, now);
    }

    /**
     * Pose de todos los huesos en «now» (ticks del actor, con la parte del tick): out[i] = matriz del hueso i en el
     * espacio del modelo (píxeles). headYaw/headPitch: hacia dónde mira la cabeza respecto al cuerpo (grados).
     */
    public void pose(VfxModel m, float now, float headYaw, float headPitch, Matrix4f[] out) {
        int n = m.bones.length;
        float[] extra = new float[n * 9];
        for (int i = 0; i < n; i++) {
            extra[i * 9 + 6] = 1F;
            extra[i * 9 + 7] = 1F;
            extra[i * 9 + 8] = 1F;
        }
        float[] v = new float[3];
        // Abajo: la animación por defecto (y la anterior desvaneciéndose)
        String b = baseFor(m, now);
        float fade = Math.min(1F, Math.max(0F, (now - baseSince) / DEFAULT_FADE));
        if (prevBase != null && fade < 1F) addBase(m, m.anim(prevBase), now - prevStart, 1F - fade, extra, v);
        if (b != null) addBase(m, m.anim(b), now - baseStart, prevBase != null ? fade : 1F, extra, v);
        // Encima: las de state{}, de menos a más prioridad
        if (!layers.isEmpty()) {
            List<Layer> sorted = new ArrayList<>(layers);
            sorted.sort((x, y) -> x.priority != y.priority ? Integer.compare(x.priority, y.priority) : Float.compare(x.start, y.start));
            for (Layer l : sorted) {
                float w = l.weight(now);
                if (w <= 0F) continue;
                float t = l.time(now);
                for (VfxModel.Track tr : l.anim.tracks) {
                    if (tr.bone < 0 || tr.bone >= n) continue;
                    int o = tr.bone * 9;
                    if (tr.rot != null && VfxPose.sample(tr.rot, t, v)) mix(extra, o, v, w, l.anim.override, false);
                    if (tr.pos != null && VfxPose.sample(tr.pos, t, v)) mix(extra, o + 3, v, w, l.anim.override, false);
                    if (tr.scale != null && VfxPose.sample(tr.scale, t, v)) mix(extra, o + 6, v, w, l.anim.override, true);
                }
            }
        }
        VfxPose.build(m, extra, headYaw, headPitch, out);
    }

    /** La animación por defecto «ticks» después de empezar, con su peso. */
    private static void addBase(VfxModel m, VfxModel.Anim a, float ticks, float w, float[] extra, float[] v) {
        if (a == null || w <= 0F) return;
        float t = a.length > 0 ? (ticks / 20F) % a.length : 0F;
        if (a.loop != 2) t = Math.min(Math.max(0F, ticks / 20F), a.length);
        for (VfxModel.Track tr : a.tracks) {
            if (tr.bone < 0 || tr.bone >= m.bones.length) continue;
            int o = tr.bone * 9;
            if (tr.rot != null && VfxPose.sample(tr.rot, t, v)) mix(extra, o, v, w, false, false);
            if (tr.pos != null && VfxPose.sample(tr.pos, t, v)) mix(extra, o + 3, v, w, false, false);
            if (tr.scale != null && VfxPose.sample(tr.scale, t, v)) mix(extra, o + 6, v, w, false, true);
        }
    }

    /** Suma (o, con override, lleva hacia) el valor de una capa con su peso. */
    private static void mix(float[] extra, int o, float[] v, float w, boolean override, boolean scale) {
        for (int k = 0; k < 3; k++) {
            float now = extra[o + k];
            if (override) extra[o + k] = now + (v[k] - now) * w;
            else if (scale) extra[o + k] = now * (1F + (v[k] - 1F) * w);
            else extra[o + k] = now + v[k] * w;
        }
    }

    /**
     * Los scripts «mm:» de la línea de tiempo que tocan entre «from» (sin incluir) y «to» (incluido), en ticks: se lanzan
     * en el servidor. En las de bucle, en cada vuelta.
     */
    public void scripts(float from, float to, Consumer<String> run) {
        for (Layer l : new ArrayList<>(layers)) {
            if (l.stopAt >= 0 || l.anim.fxTimes.length == 0) continue;
            fire(l.anim, l.seconds(from), l.seconds(to), run);
        }
    }

    /** Lo mismo para la animación por defecto (los pasos del Esqueleto de la Torre van en su «walk»). */
    public void baseScripts(VfxModel m, float from, float to, Consumer<String> run) {
        if (base == null) return;
        VfxModel.Anim a = m.anim(base);
        if (a == null || a.fxTimes.length == 0) return;
        fire(a, (from - baseStart) / 20F, (to - baseStart) / 20F, run);
    }

    private static void fire(VfxModel.Anim a, float s0, float s1, Consumer<String> run) {
        if (s1 <= s0) return;
        boolean loop = a.loop == 2 && a.length > 0;
        for (int i = 0; i < a.fxTimes.length; i++) {
            float t = a.fxTimes[i];
            if (loop) {
                // En bucle, el del final es el mismo instante que el del principio de la vuelta siguiente
                if (t >= a.length - 1e-4F && hasStart(a, a.fxSkills[i])) continue;
                t = t % a.length;
                // ¿Pasa por t en alguna vuelta entre s0 y s1?
                double k0 = Math.floor((s0 - t) / a.length), k1 = Math.floor((s1 - t) / a.length);
                if (k1 > k0) run.accept(a.fxSkills[i]);
            } else if (t > s0 && t <= s1) {
                run.accept(a.fxSkills[i]);
            }
        }
    }

    private static boolean hasStart(VfxModel.Anim a, String skill) {
        for (int i = 0; i < a.fxTimes.length; i++) if (a.fxTimes[i] < 1e-4F && a.fxSkills[i].equals(skill)) return true;
        return false;
    }

    public boolean dead() {
        return dead;
    }
}
