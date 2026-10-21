package net.tierrasfantasticas.tfclient.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.joml.Matrix4f;

/**
 * Varias animaciones a la vez sobre un modelo de ModelEngine, como las mezcla ModelEngine 4 (los jefes: la Hidra muerde
 * con una cabeza mientras gira las patas y respira con el resto). Abajo va la animación por defecto (reposo, andar o
 * la que ponga defaultstate); encima, las de state{}: cada prioridad («p») es una máquina de estados que lleva UNA
 * animación (la nueva sustituye a la que hubiera en esa prioridad, pasando de una a otra en su «li»), y las
 * prioridades se apilan de menor a mayor: una con «override» (la casilla de Blockbench) tapa a las de debajo en los
 * huesos que mueve; una sin él se suma. Una de una vez acaba sola (con su «lo») y deja libre su prioridad; una que se
 * queda («hold») sigue en su final hasta que la quitan. (Antes se apilaban todas: el «sleep» del Esqueleto de la
 * Torre seguía debajo después de despertar y se quedaba sentado en el aire.)
 *
 * <p>No depende del cliente: el servidor lleva las mismas capas para saber dónde está cada hueso (@modelpart) y
 * lanzar los scripts «mm:» de la línea de tiempo.
 */
public final class VfxLayers {
    /** Ticks que tarda en cambiar de una animación por defecto a otra (de reposo a andar). */
    private static final float DEFAULT_FADE = 4F;

    public static final class Layer {
        public final String name;
        VfxModel.Anim anim;
        public float start;
        public float speed;
        public int lerpIn;
        public int lerpOut;
        public final int priority;
        /** Cuándo empezó a irse (ticks); -1 = no se va. */
        public float stopAt = -1;
        /** modifystate{loop;ov}: bucle (0 una vez, 1 se queda, 2 bucle; -1 = el del .bbmodel) y override (null = el suyo). */
        public int loopMode = -1;
        public Boolean overrideMode;

        int loop() {
            return loopMode >= 0 ? loopMode : anim.loop;
        }

        boolean override() {
            return overrideMode != null ? overrideMode : anim.override;
        }

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
            if (loop() == 0) {
                float over = (seconds(now) - anim.length) * 20F / speed;
                if (over > 0) return lerpOut > 0 ? w * Math.max(0F, 1F - over / lerpOut) : 0F;
            }
            return w;
        }

        boolean done(float now) {
            return weight(now) <= 0F && (stopAt >= 0 || loop() == 0 && seconds(now) > anim.length);
        }

        float time(float now) {
            float t = seconds(now);
            if (loop() == 2 && anim.length > 0) return t % anim.length;
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
    /**
     * animatemodel de ModelEngine: huesos movidos a mano (sin animación), por hueso: posición (3), giro (3), escala (3),
     * override, copy, global (1/0 cada uno).
     */
    private final java.util.Map<Integer, float[]> manual = new java.util.HashMap<>();
    /** rootmotion + model{rootbone}: el hueso raíz (-1 = no), si manda, sus pesos y lo último que se movió. */
    private int rootBone = -1;
    private boolean rootOn;
    private float rootW = -1F, rootBw = -1F;
    private final float[] rootOffset = new float[3];
    private String rootKey = "";
    private float rootWeightNow;
    /** petrify: congelado en ese instante (NaN = no). */
    private float frozenAt = Float.NaN;

    /**
     * state{s;sp;li;lo;p;force}: pone una animación en su prioridad (y quita la que hubiera en esa misma prioridad,
     * fundiéndose en «li» ticks). false si el modelo no la tiene.
     */
    public boolean play(VfxModel m, String name, float now, float speed, int lerpIn, int lerpOut, int priority, boolean force) {
        VfxModel.Anim a = m.anim(name);
        if (a == null) return false;
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (!l.name.equalsIgnoreCase(name)) continue;
            // Ya está y sigue (no se va ni ha acabado): ModelEngine no la vuelve a empezar salvo con force
            if (!force && l.stopAt < 0 && (l.loop() != 0 || l.seconds(now) <= l.anim.length)) return true;
            layers.remove(i);
        }
        // Una sola animación por prioridad: la que hubiera se va mientras entra la nueva
        int fade = Math.max(0, lerpIn);
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (l.priority != priority || l.stopAt >= 0) continue;
            if (fade == 0) {
                layers.remove(i);
            } else {
                l.stopAt = now;
                l.lerpOut = fade;
            }
        }
        layers.add(new Layer(name, a, now, speed, lerpIn, lerpOut, priority));
        return true;
    }

    /**
     * La animación de aparecer de ModelEngine («spawn», al poner el modelo): una sola vez, aunque en el .bbmodel esté en
     * bucle, y luego la de reposo. (1.3.87: el Mamut del Druida la tiene en bucle y sale de debajo de la tierra; se
     * repetía toda la pelea: «aparece y desaparece en un bucle».)
     */
    public void playSpawn(VfxModel m, float now) {
        if (m.anim("spawn") == null) return;
        play(m, "spawn", now, 1F, 0, 3, 0, true);
        layers.get(layers.size() - 1).loopMode = 0;
    }

    /** state{s;r=true}: quita una animación (con su salida suave «lerpOut»; -1 = la suya). */
    public void stop(String name, float now, int lerpOut) {
        for (Layer l : layers) {
            if (!l.name.equalsIgnoreCase(name) || l.stopAt >= 0) continue;
            l.stopAt = now;
            if (lerpOut >= 0) l.lerpOut = lerpOut;
        }
    }

    /**
     * modifystate{s;p;speed;li;lo;loop;ov}: cambia una animación que ya suena sin volver a empezarla (la velocidad
     * nueva sigue desde el mismo instante). NaN / -1 / null = no tocar. loop: 0 una vez, 1 se queda, 2 bucle, 3 = la del
     * .bbmodel. false si no estaba.
     */
    public boolean modify(String name, int priority, float now, float speed, int lerpIn, int lerpOut, int loop, Boolean override) {
        boolean found = false;
        for (Layer l : layers) {
            if (!l.name.equalsIgnoreCase(name) || l.stopAt >= 0) continue;
            if (priority >= 0 && l.priority != priority) continue;
            found = true;
            if (!Float.isNaN(speed) && speed > 0 && speed != l.speed) {
                float sec = l.seconds(now);
                l.speed = speed;
                l.start = now - sec * 20F / speed;
            }
            if (lerpIn >= 0) l.lerpIn = lerpIn;
            if (lerpOut >= 0) l.lerpOut = lerpOut;
            if (loop >= 0) l.loopMode = loop == 3 ? -1 : loop;
            if (override != null) l.overrideMode = override;
        }
        return found;
    }

    /** ¿Alguna animación que suena ahora (con peso) mueve ese hueso? (las cadenas IK se encienden así) */
    public boolean moves(VfxModel m, int bone, float now) {
        VfxModel.Anim b = base == null ? null : m.anim(base);
        if (b != null && b.moves != null && bone < b.moves.length && b.moves[bone]) return true;
        for (Layer l : layers) {
            if (l.anim.moves != null && bone < l.anim.moves.length && l.anim.moves[bone] && l.weight(now) > 0F) return true;
        }
        return false;
    }

    /** ¿Suena esa animación ahora (y no se está yendo)? (statetoggle) */
    public boolean playing(String name, float now) {
        for (Layer l : layers) {
            if (l.name.equalsIgnoreCase(name) && l.stopAt < 0 && !l.done(now)) return true;
        }
        return false;
    }

    /** Quita todas al momento (al cambiar de modelo), con lo puesto a mano y la raíz. */
    public void clear() {
        layers.clear();
        manual.clear();
        rootBone = -1;
        rootOn = false;
    }

    /**
     * animatemodel{p;em;position;rotation;scale;r;o;copy;rm}: mueve esos huesos a mano, sin animación (posición en
     * píxeles y giro en grados, ya con los signos del modelo convertido). relative: se suma a lo que ya tenían a mano;
     * override: tapa a sus animaciones (si no, se suma a ellas); copy: parte de la forma del hueso en el modelo (si no,
     * su giro de reposo no cuenta); global: el giro va en los ejes del modelo, no en los del hueso.
     */
    public void animate(int[] bones, float[] pos, float[] rot, float[] scale, boolean relative, boolean override, boolean copy,
                        boolean global) {
        for (int b : bones) {
            float[] v = manual.get(b);
            if (v == null || !relative) {
                v = new float[] {0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 1, 0};
                manual.put(b, v);
            }
            for (int k = 0; k < 3; k++) {
                v[k] += pos[k];
                v[3 + k] += rot[k];
                v[6 + k] *= scale[k];
            }
            v[9] = override ? 1 : 0;
            v[10] = copy ? 1 : 0;
            v[11] = global ? 1 : 0;
        }
    }

    /**
     * rootmotion{w;bw;ov} con el hueso raíz de model{rootbone}: con «on», la entidad anda lo que mueve la animación a ese
     * hueso (lo hace el servidor con {@link #rootOffset()}) y el modelo deja de moverlo (para no verse dos veces). w: el
     * peso con las animaciones de state{} (-1 = 1); bw: con las de por defecto, reposo y andar (-1 = 0). null = no tocar.
     */
    public void rootMotion(int bone, Boolean on, float w, float bw) {
        if (bone >= -1) rootBone = bone;
        if (on != null) rootOn = on;
        if (!Float.isNaN(w)) rootW = w;
        if (!Float.isNaN(bw)) rootBw = bw;
    }

    /** Lo que mueve ahora la animación al hueso raíz ({x, y, z} en píxeles, sin peso), según la última pose. */
    public float[] rootOffset() {
        return rootOffset;
    }

    /** Qué animación mueve la raíz ahora (si cambia, el desplazamiento vuelve a empezar: no se anda «hacia atrás»). */
    public String rootKey() {
        return rootKey;
    }

    /** El peso de la raíz en la última pose (el de state{} o el de las animaciones por defecto). */
    public float rootWeight() {
        return rootWeightNow;
    }

    public boolean rootActive() {
        return rootOn && rootBone >= 0;
    }

    /**
     * El mensaje «anim» (SkillMeg.animateModel): «px,py,pz|rx,ry,rz|sx,sy,sz|banderas» (banderas: relative, override,
     * copy, global, 1/0) en los huesos con ese nombre (exact) o que lo llevan. Devuelve los huesos tocados.
     */
    public int[] animate(VfxModel m, String part, boolean exact, String spec) {
        String[] p = spec.split("\\|");
        if (p.length < 4) return new int[0];
        float[] pos = vec(p[0], 0F), rot = vec(p[1], 0F), scl = vec(p[2], 1F);
        String f = p[3] + "0000";
        int[] bones = VfxRig.match(m, part, exact, false);
        animate(bones, pos, rot, scl, f.charAt(0) == '1', f.charAt(1) == '1', f.charAt(2) == '1', f.charAt(3) == '1');
        return bones;
    }

    /** Lo puesto a mano en un hueso, como un mensaje «anim» absoluto (para quien llega tarde), o null. */
    public String manualSpec(int bone) {
        float[] v = manual.get(bone);
        if (v == null) return null;
        return v[0] + "," + v[1] + "," + v[2] + "|" + v[3] + "," + v[4] + "," + v[5] + "|" + v[6] + "," + v[7] + "," + v[8] + "|0"
                + (v[9] > 0 ? 1 : 0) + (v[10] > 0 ? 1 : 0) + (v[11] > 0 ? 1 : 0);
    }

    /** El mensaje «root» (model{rootbone} y rootmotion): hueso («» = el de antes) y «w|bw|on» («NaN»/«» = no tocar). */
    public void rootMessage(VfxModel m, String bone, String spec) {
        String[] p = (spec == null ? "" : spec).split("\\|", -1);
        int b = bone == null || bone.isEmpty() ? -2 : m.bone(bone);
        float w = p.length > 0 ? num(p[0], Float.NaN) : Float.NaN, bw = p.length > 1 ? num(p[1], Float.NaN) : Float.NaN;
        Boolean on = p.length > 2 && !p[2].isEmpty() ? "1".equals(p[2]) : null;
        rootMotion(b, on, w, bw);
    }

    /** La raíz como un mensaje «root» completo: {hueso, «w|bw|on»} (para quien llega tarde), o null si no tiene. */
    public String[] rootSpec(VfxModel m) {
        if (rootBone < 0 || rootBone >= m.bones.length) return null;
        return new String[] {m.bones[rootBone].id, rootW + "|" + rootBw + "|" + (rootOn ? "1" : "0")};
    }

    /**
     * El modelo ha cambiado en vivo (changeparent, submodel: los huesos se renumeran): las animaciones que suenan, lo
     * puesto a mano y la raíz pasan a los huesos del mismo nombre del modelo nuevo.
     */
    public void rebind(VfxModel old, VfxModel now) {
        for (Layer l : layers) {
            VfxModel.Anim a = now.anim(l.name);
            if (a != null) l.anim = a;
        }
        java.util.Map<Integer, float[]> moved = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, float[]> e : manual.entrySet()) {
            if (e.getKey() >= old.bones.length) continue;
            int b = now.bone(old.bones[e.getKey()].id);
            if (b >= 0) moved.put(b, e.getValue());
        }
        manual.clear();
        manual.putAll(moved);
        if (rootBone >= 0 && rootBone < old.bones.length) rootBone = now.bone(old.bones[rootBone].id);
    }

    private static float[] vec(String s, float def) {
        String[] p = s.split(",");
        float x = num(p[0], def);
        return new float[] {x, p.length > 1 ? num(p[1], def) : x, p.length > 2 ? num(p[2], def) : x};
    }

    private static float num(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** petrify: se queda quieto en «now» para siempre (la estatua). */
    public void freeze(float now) {
        frozenAt = now;
    }

    public float frozenAt() {
        return frozenAt;
    }

    /**
     * Una copia (la estatua de petrify) con las animaciones de ahora movidas «shift» ticks (el instante «now» de este es
     * el «now - shift» de la copia).
     */
    public VfxLayers copy(float shift) {
        VfxLayers c = new VfxLayers();
        for (Layer l : layers) {
            Layer n = new Layer(l.name, l.anim, l.start - shift, l.speed, l.lerpIn, l.lerpOut, l.priority);
            n.stopAt = l.stopAt < 0 ? -1 : l.stopAt - shift;
            n.loopMode = l.loopMode;
            n.overrideMode = l.overrideMode;
            c.layers.add(n);
        }
        c.idle = idle;
        c.walk = walk;
        c.walking = walking;
        c.base = base;
        c.prevBase = prevBase;
        c.baseStart = baseStart - shift;
        c.prevStart = prevStart - shift;
        c.baseSince = baseSince - shift;
        c.dead = dead;
        for (java.util.Map.Entry<Integer, float[]> e : manual.entrySet()) c.manual.put(e.getKey(), e.getValue().clone());
        return c;
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
        if (a != null) {
            // Se queda en su último fotograma hasta que se quita el modelo (la muerte épica dura más que la animación:
            // si no, al acabar, el jefe volvía a quedarse de pie en su pose de reposo)
            Layer l = new Layer("death", a, now, 1F, 3, 0, Integer.MAX_VALUE);
            if (a.loop == 0) l.loopMode = 1;
            layers.add(l);
        }
    }

    /** Quita las que ya han terminado del todo. */
    public void prune(float now) {
        layers.removeIf(l -> l.done(now));
    }

    public List<Layer> active() {
        return layers;
    }

    /** La animación por defecto que se ve ahora (reposo, andar o la de defaultstate); null si aún ninguna. */
    public String baseName() {
        return base;
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
        pose(m, now, headYaw, headPitch, out, m.headLimits());
    }

    /** Igual, con los límites de las cabezas de este mob (headconfig): ver VfxPose.build. */
    public void pose(VfxModel m, float now, float headYaw, float headPitch, Matrix4f[] out, float[] headLimits) {
        if (!Float.isNaN(frozenAt)) now = frozenAt;
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
        // Una animación suena una sola vez, como en ModelEngine 4 (van por su nombre): si la de reposo también la pone un
        // state{} (defaultstate idle=angry1 y state angry1: 57 NPC de escenas), no se suman las dos; sin override salían
        // con cada giro doble (el «angry 1» de la taberna, con el torso partido de las piernas). Mientras la del state
        // entra o se va, la de reposo pone el resto.
        if (prevBase != null && fade < 1F) addBase(m, m.anim(prevBase), now - prevStart, (1F - fade) * (1F - sameAs(prevBase, now)), extra, v);
        if (b != null) addBase(m, m.anim(b), now - baseStart, (prevBase != null ? fade : 1F) * (1F - sameAs(b, now)), extra, v);
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
                    boolean ov = l.override();
                    if (tr.rot != null && VfxPose.sample(tr.rot, t, v)) mix(extra, o, v, w, ov, false);
                    if (tr.pos != null && VfxPose.sample(tr.pos, t, v)) mix(extra, o + 3, v, w, ov, false);
                    if (tr.scale != null && VfxPose.sample(tr.scale, t, v)) mix(extra, o + 6, v, w, ov, true);
                }
            }
        }
        // rootmotion: lo que la animación mueve la raíz lo anda la entidad (servidor); el modelo no lo vuelve a mover
        if (rootBone >= 0 && rootBone < n) {
            int o = rootBone * 9 + 3;
            System.arraycopy(extra, o, rootOffset, 0, 3);
            String key = "";
            boolean byState = false;
            for (Layer l : layers) {
                if (l.weight(now) > 0F && l.anim.moves != null && rootBone < l.anim.moves.length && l.anim.moves[rootBone]) {
                    key += l.name + "@" + l.start + ";";
                    byState = true;
                }
            }
            rootKey = byState ? key : String.valueOf(base) + "@" + baseStart;
            if (rootOn) {
                float w = byState ? (rootW < 0 ? 1F : rootW) : (rootBw < 0 ? 0F : rootBw);
                rootWeightNow = w;
                // solo en el suelo (x, z): la altura la siguen viendo los saltos de la animación
                extra[o] *= 1F - Math.min(1F, w);
                extra[o + 2] *= 1F - Math.min(1F, w);
            }
        }
        // animatemodel: lo puesto a mano, encima de todo
        float[] global = null;
        for (java.util.Map.Entry<Integer, float[]> e : manual.entrySet()) {
            int bone = e.getKey();
            if (bone < 0 || bone >= n) continue;
            float[] mv = e.getValue();
            int o = bone * 9;
            boolean ov = mv[9] > 0, glob = mv[11] > 0;
            for (int k = 0; k < 3; k++) {
                extra[o + 3 + k] = ov ? mv[k] : extra[o + 3 + k] + mv[k];
                extra[o + 6 + k] = ov ? mv[6 + k] : extra[o + 6 + k] * mv[6 + k];
                if (ov) extra[o + k] = 0F;
                if (!glob) extra[o + k] += mv[3 + k];
            }
            // copy=false: sin el giro de reposo del hueso
            if (mv[10] <= 0) for (int k = 0; k < 3; k++) extra[o + k] -= m.bones[bone].rot[k];
            if (glob) {
                if (global == null) {
                    global = new float[n * 3];
                    java.util.Arrays.fill(global, Float.NaN);
                }
                System.arraycopy(mv, 3, global, bone * 3, 3);
            }
        }
        VfxPose.build(m, extra, headYaw, headPitch, out, headLimits, global);
    }

    /** La animación por defecto «ticks» después de empezar, con su peso. */
    /** Cuánto suena ya esa animación por un state{} (0-1): lo que no le toca a la de reposo del mismo nombre. */
    private float sameAs(String name, float now) {
        float w = 0F;
        for (Layer l : layers) if (l.name.equalsIgnoreCase(name)) w = Math.max(w, l.weight(now));
        return Math.min(1F, w);
    }

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
