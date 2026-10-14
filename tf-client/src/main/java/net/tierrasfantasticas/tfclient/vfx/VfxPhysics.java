package net.tierrasfantasticas.tfclient.vfx;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Lo que ModelEngine 4 anima solo, sin animación de Blockbench, en un modelo concreto (uno por mob dibujado):
 * <ul>
 *   <li><b>Colas y segmentos</b> (huesos sin cubos seg_, segf_, tl_, tlf_): cada eslabón sigue a su padre «con el menor
 *   movimiento posible», como una cuerda que se arrastra: su extremo trasero (el pivote del hueso) se queda donde estaba
 *   en el mundo y solo se mueve lo justo para no separarse del eslabón de delante. Con límite de ángulo respecto al
 *   eslabón anterior (bounded, anglelimit y x/y/minx...), vuelta poco a poco a su forma animada (extendrate, por tick) y
 *   rebote (elasticity). Se configura con la mecánica segment{} del pack; sin ella, con los valores de ModelEngine
 *   (bounded, 50°, extend 0,01).</li>
 *   <li><b>Física</b> (jig_ / jig[stiff;damp;grav;angle]_): el hueso se balancea con inercia alrededor de su pose
 *   animada, como un muelle amortiguado con gravedad.</li>
 * </ul>
 * Trabaja en el mundo (así el movimiento del mob arrastra la cola) y deja las matrices de los huesos (espacio del
 * modelo, en píxeles) con el resultado; los hijos de cada hueso movido se recolocan con él.
 */
public final class VfxPhysics {
    /** La configuración de segment{} de un eslabón. */
    public static final class SegCfg {
        public boolean bounded = true;
        public float minX = -50, maxX = 50, minY = -50, maxY = 50;
        public float extend = 0.01F;
        public float elastic = 0F;

        SegCfg copy() {
            SegCfg c = new SegCfg();
            c.bounded = bounded;
            c.minX = minX;
            c.maxX = maxX;
            c.minY = minY;
            c.maxY = maxY;
            c.extend = extend;
            c.elastic = elastic;
            return c;
        }
    }

    private final VfxModel model;
    private final int n;
    private final boolean[] seg;
    /** Eslabón de delante de cada segmento: el hueso segmento más cercano hacia la raíz o, si no hay, su padre. */
    private final int[] anchor;
    /** Primer segmento que cuelga de cada segmento (o -1) y si el trozo visible del hueso va hacia él (hacia atrás). */
    private final int[] next;
    private final boolean[] outgoing;
    private final SegCfg[] cfg;
    private final boolean[] jig;
    private final Vector3f[] tipLocal;
    private final boolean any;

    // Estado (en el mundo)
    private final Vector3f[] joint;
    private final Vector3f[] vel;
    private final boolean[] solved;
    private final Quaternionf[] link;
    private float last = Float.NaN;

    public VfxPhysics(VfxModel m) {
        this.model = m;
        this.n = m.bones.length;
        seg = new boolean[n];
        anchor = new int[n];
        next = new int[n];
        outgoing = new boolean[n];
        cfg = new SegCfg[n];
        jig = new boolean[n];
        tipLocal = new Vector3f[n];
        joint = new Vector3f[n];
        vel = new Vector3f[n];
        solved = new boolean[n];
        link = new Quaternionf[n];
        boolean found = false;
        for (int i = 0; i < n; i++) {
            VfxModel.Bone b = m.bones[i];
            anchor[i] = -1;
            next[i] = -1;
            if (b.segment() && b.parent >= 0) {
                seg[i] = true;
                cfg[i] = new SegCfg();
                int a = b.parent;
                while (a >= 0 && !m.bones[a].segment()) a = m.bones[a].parent;
                anchor[i] = a >= 0 ? a : b.parent;
                found = true;
            }
            if ("jig".equals(b.beh)) {
                jig[i] = true;
                found = true;
            }
            joint[i] = new Vector3f();
            vel[i] = new Vector3f();
            link[i] = new Quaternionf();
        }
        any = found;
        if (!any) return;
        Matrix4f[] rest = m.rest();
        for (int i = 0; i < n; i++) {
            if (seg[i] && anchor[i] >= 0 && seg[anchor[i]] && next[anchor[i]] < 0) next[anchor[i]] = i;
        }
        for (int i = 0; i < n; i++) {
            if (seg[i]) outgoing[i] = decideOutgoing(i, rest);
            if (jig[i]) tipLocal[i] = tipOf(i, rest);
        }
    }

    /** ¿Tiene algo que animar? */
    public boolean active() {
        return any;
    }

    /**
     * segment{p;bounded;anglelimit;x;y;minx;...;extendrate;elasticity;em}: «part» null = todos los segmentos; con
     * exact=false, los que lo llevan en el nombre. Lo que no viene se queda como estaba (como ModelEngine). Se aplica al
     * eslabón y a los que cuelgan de él.
     */
    public void configure(String part, Map<String, Float> v, boolean exact) {
        if (!any) return;
        String want = part == null ? null : part.toLowerCase(Locale.ROOT);
        for (int i = 0; i < n; i++) {
            if (!seg[i]) continue;
            boolean hit = want == null || want.isEmpty();
            if (!hit) {
                String id = model.bones[i].id.toLowerCase(Locale.ROOT);
                hit = exact ? id.equals(want) || id.endsWith("_" + want) && id.indexOf('_') == id.length() - want.length() - 1
                        : id.contains(want);
            }
            if (!hit) continue;
            apply(cfg[i], v);
            // y los eslabones que cuelgan de él
            for (int j = i + 1; j < n; j++) if (seg[j] && descends(j, i)) apply(cfg[j], v);
        }
    }

    private boolean descends(int b, int ancestor) {
        for (int p = model.bones[b].parent; p >= 0; p = model.bones[p].parent) if (p == ancestor) return true;
        return false;
    }

    private static void apply(SegCfg c, Map<String, Float> v) {
        Float f;
        if ((f = v.get("bounded")) != null) c.bounded = f != 0F;
        if ((f = v.get("angle")) != null) {
            c.minX = c.minY = -Math.abs(f);
            c.maxX = c.maxY = Math.abs(f);
        }
        if ((f = v.get("x")) != null) {
            c.minX = -Math.abs(f);
            c.maxX = Math.abs(f);
        }
        if ((f = v.get("y")) != null) {
            c.minY = -Math.abs(f);
            c.maxY = Math.abs(f);
        }
        if ((f = v.get("minx")) != null) c.minX = f;
        if ((f = v.get("maxx")) != null) c.maxX = f;
        if ((f = v.get("miny")) != null) c.minY = f;
        if ((f = v.get("maxy")) != null) c.maxY = f;
        if ((f = v.get("extend")) != null) c.extend = Math.max(0F, Math.min(1F, f));
        if ((f = v.get("elastic")) != null) c.elastic = Math.max(0F, Math.min(0.95F, f));
    }

    /**
     * ¿El trozo visible de este eslabón va hacia atrás (hacia el siguiente eslabón)? Entonces se orienta con el
     * siguiente eslabón; si va hacia delante (lo normal: el pivote atrás), con el suyo.
     */
    private boolean decideOutgoing(int i, Matrix4f[] rest) {
        if (model.bones[i].frontPivot()) return next[i] >= 0;
        if (next[i] < 0) return false;
        Vector3f p = rest[i].getTranslation(new Vector3f());
        Vector3f toNext = rest[next[i]].getTranslation(new Vector3f()).sub(p);
        Vector3f c = new Vector3f();
        int count = 0;
        Vector3f v = new Vector3f();
        for (int j = i; j < n; j++) {
            if (j != i && !descends(j, i)) continue;
            if (j == next[i] || j != i && seg[j] && descends(j, next[i])) continue;
            if (j != i && descends(j, next[i])) continue;
            VfxModel.Bone b = model.bones[j];
            for (int q = 0; q < b.quadCount; q++) {
                for (int k = 0; k < 4; k++) {
                    int o = q * VfxModel.QUAD + 4 + k * 5;
                    v.set(b.quads[o], b.quads[o + 1], b.quads[o + 2]);
                    rest[j].transformPosition(v);
                    c.add(v);
                    count++;
                }
            }
        }
        if (count == 0) return false;
        c.div(count).sub(p);
        return c.dot(toNext) > 0;
    }

    /** Hacia dónde cuelga un hueso jig_ (su punta, en su espacio): el centro de lo que lleva o, si nada, 8 px abajo. */
    private Vector3f tipOf(int i, Matrix4f[] rest) {
        Vector3f c = new Vector3f();
        int count = 0;
        Matrix4f inv = new Matrix4f(rest[i]).invert();
        Vector3f v = new Vector3f();
        for (int j = i; j < n; j++) {
            if (j != i && !descends(j, i)) continue;
            VfxModel.Bone b = model.bones[j];
            for (int q = 0; q < b.quadCount; q++) {
                for (int k = 0; k < 4; k++) {
                    int o = q * VfxModel.QUAD + 4 + k * 5;
                    v.set(b.quads[o], b.quads[o + 1], b.quads[o + 2]);
                    rest[j].transformPosition(v);
                    inv.transformPosition(v);
                    c.add(v);
                    count++;
                }
            }
        }
        if (count > 0) c.div(count);
        if (c.lengthSquared() < 0.25F) c.set(0, -8, 0);
        return c;
    }

    /**
     * jiggleimpulse{p;strength;x;y;z}: empuja los huesos jig_ (todos o los de ese nombre): «push» en bloques/s del mundo
     * y «strength» lejos del punto «from» (mundo).
     */
    public void impulse(String part, boolean exact, Vector3f push, float strength, Vector3f from) {
        String want = part == null ? "" : part.toLowerCase(Locale.ROOT);
        for (int i = 0; i < n; i++) {
            if (!jig[i]) continue;
            String id = model.bones[i].id.toLowerCase(Locale.ROOT);
            if (!want.isEmpty() && !(exact ? id.equals(want) : id.contains(want))) continue;
            Vector3f t = tip.get(i);
            Vector3f v = new Vector3f(push).div(20F);
            if (strength != 0F && t != null && from != null) {
                Vector3f away = new Vector3f(t).sub(from);
                if (away.lengthSquared() > 1e-8F) v.add(away.normalize().mul(strength / 20F));
            }
            vel[i].add(v);
        }
    }

    /** Olvida dónde estaba todo (al cambiar de modelo o tras un salto): la próxima vez sale en su pose. */
    public void reset() {
        last = Float.NaN;
    }

    /**
     * Aplica la física a la pose. mats: las matrices de los huesos (espacio del modelo, ya animadas): se cambian.
     * world: del modelo (píxeles) al mundo. ticks: el instante (ticks con la fracción del fotograma).
     */
    public void apply(Matrix4f[] mats, Matrix4f world, float ticks) {
        if (!any) return;
        float dt = Float.isNaN(last) ? 0F : ticks - last;
        boolean fresh = Float.isNaN(last) || dt < 0F || dt > 20F;
        if (fresh) dt = 0F;
        dt = Math.min(dt, 3F);
        last = ticks;

        Matrix4f[] h = new Matrix4f[n];
        for (int i = 0; i < n; i++) h[i] = new Matrix4f(mats[i]);
        Quaternionf wq = world.getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf wqi = new Quaternionf(wq).conjugate();
        float wscale = world.getScale(new Vector3f()).x;
        Matrix4f worldInv = new Matrix4f(world).invert();
        boolean[] moved = new boolean[n];
        java.util.Arrays.fill(solved, false);

        for (int i = 0; i < n; i++) {
            int p = model.bones[i].parent;
            if (seg[i]) {
                if (!solved[i]) solve(i, h, mats, world, wq, wqi, wscale, dt, fresh);
                int use = outgoing[i] && next[i] >= 0 ? next[i] : i;
                if (use != i && !solved[use]) solve(use, h, mats, world, wq, wqi, wscale, dt, fresh);
                // Matriz final: en su sitio resuelto, con el giro de su eslabón sobre el animado
                Vector3f pos = worldInv.transformPosition(new Vector3f(joint[i]));
                Quaternionf rot = h[i].getUnnormalizedRotation(new Quaternionf()).normalize();
                Vector3f scl = h[i].getScale(new Vector3f());
                Quaternionf r = new Quaternionf(link[use]).mul(rot);
                mats[i].identity().translate(pos).rotate(r).scale(scl);
                moved[i] = true;
            } else if (p >= 0 && moved[p]) {
                mats[i].set(mats[p]).mul(new Matrix4f(h[p]).invert()).mul(h[i]);
                moved[i] = true;
            }
            if (jig[i]) {
                jiggle(i, mats, world, wq, wqi, wscale, dt, fresh);
                moved[i] = true;
            }
        }
    }

    /** Resuelve el eslabón «i» (su sitio en el mundo y el giro de su eslabón en el espacio del modelo). */
    private void solve(int i, Matrix4f[] h, Matrix4f[] mats, Matrix4f world, Quaternionf wq, Quaternionf wqi, float wscale,
                       float dt, boolean fresh) {
        solved[i] = true;
        int a = anchor[i];
        SegCfg c = cfg[i];
        // De dónde cuelga: el eslabón de delante ya resuelto o su padre tal como está
        Vector3f anchorW;
        Quaternionf anchorD;
        if (seg[a]) {
            if (!solved[a]) solve(a, h, mats, world, wq, wqi, wscale, dt, fresh);
            anchorW = new Vector3f(joint[a]);
            anchorD = new Quaternionf(link[a]);
        } else {
            anchorW = world.transformPosition(mats[a].getTranslation(new Vector3f()));
            Quaternionf now = mats[a].getUnnormalizedRotation(new Quaternionf()).normalize();
            Quaternionf was = h[a].getUnnormalizedRotation(new Quaternionf()).normalize();
            anchorD = now.mul(was.conjugate());
        }
        // El eslabón animado (del de delante a este), con lo que ya giró el de delante
        Vector3f linkM = h[i].getTranslation(new Vector3f()).sub(h[a].getTranslation(new Vector3f()));
        float len = linkM.length() * wscale;
        Vector3f refM = anchorD.transform(new Vector3f(linkM));
        Vector3f ref = wq.transform(new Vector3f(refM));
        if (len < 1e-5F || ref.lengthSquared() < 1e-12F) {
            joint[i].set(anchorW);
            link[i].set(anchorD);
            return;
        }
        ref.normalize();
        Vector3f dir;
        if (fresh) {
            dir = new Vector3f(ref);
            vel[i].zero();
        } else {
            // Donde estaba (con su inercia si es elástico): el eslabón se gira lo justo hacia allí
            Vector3f prev = new Vector3f(joint[i]);
            if (c.elastic > 0F) prev.add(new Vector3f(vel[i]).mul(c.elastic * Math.min(1F, dt)));
            dir = prev.sub(anchorW);
            if (dir.lengthSquared() < 1e-12F) dir.set(ref);
            dir.normalize();
            // Vuelve poco a poco a su forma animada (extendrate por tick)
            float k = c.extend <= 0F ? 0F : 1F - (float) Math.pow(1F - c.extend, dt);
            if (k > 0F) dir.lerp(ref, k).normalize();
        }
        if (c.bounded) dir = clamp(dir, ref, i, h, anchorD, wq, c);
        Vector3f nj = new Vector3f(dir).mul(len).add(anchorW);
        if (!fresh && dt > 0F) vel[i].set(nj).sub(joint[i]).div(Math.max(dt, 1e-3F)).mul(Math.min(1F, dt));
        joint[i].set(nj);
        // Giro del eslabón: del animado (ref) al resuelto (dir), en el espacio del modelo, encima del de delante
        Vector3f dirM = new Quaternionf(wq).conjugate().transform(new Vector3f(dir));
        Quaternionf q = new Quaternionf().rotationTo(refM.normalize(new Vector3f()), dirM);
        link[i].set(q).mul(anchorD);
    }

    /**
     * Límite de ángulo: lo que el eslabón se aparta de su forma animada, en sus ejes (x = arriba/abajo, y = a los
     * lados), dentro de min/max.
     */
    private Vector3f clamp(Vector3f dir, Vector3f ref, int i, Matrix4f[] h, Quaternionf anchorD, Quaternionf wq, SegCfg c) {
        Quaternionf frame = new Quaternionf(wq).mul(anchorD).mul(h[i].getUnnormalizedRotation(new Quaternionf()).normalize());
        Quaternionf inv = new Quaternionf(frame).conjugate();
        Vector3f d = inv.transform(new Vector3f(dir));
        Vector3f r = inv.transform(new Vector3f(ref));
        double yawD = Math.atan2(d.x, d.z), yawR = Math.atan2(r.x, r.z);
        double pitD = Math.atan2(d.y, Math.hypot(d.x, d.z)), pitR = Math.atan2(r.y, Math.hypot(r.x, r.z));
        double dy = Math.toDegrees(wrap(yawD - yawR)), dp = Math.toDegrees(pitD - pitR);
        double cy = Math.max(c.minY, Math.min(c.maxY, dy)), cp = Math.max(c.minX, Math.min(c.maxX, dp));
        if (cy == dy && cp == dp) return dir;
        double yaw = yawR + Math.toRadians(cy), pit = pitR + Math.toRadians(cp);
        Vector3f out = new Vector3f((float) (Math.cos(pit) * Math.sin(yaw)), (float) Math.sin(pit), (float) (Math.cos(pit) * Math.cos(yaw)));
        return frame.transform(out).normalize();
    }

    private static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    // ------------------------------------------------------------------------------------------- jig_

    private final Map<Integer, Vector3f> tip = new HashMap<>();
    /** ik{stiffness;damping;gravity;maxangle} de este mob para sus huesos jig_ (encima de la etiqueta). */
    private final Map<Integer, Map<String, Float>> jigCfg = new HashMap<>();

    /** ik{p;stiffness;damping;gravity;maxangle;em}: la física de esos huesos jig_ (lo que no venga, igual). */
    public void configureJig(String part, Map<String, Float> v, boolean exact) {
        String want = part == null ? "" : part.toLowerCase(Locale.ROOT);
        for (int i = 0; i < n; i++) {
            if (!jig[i]) continue;
            String id = model.bones[i].id.toLowerCase(Locale.ROOT);
            if (!want.isEmpty() && !(exact ? id.equals(want) : id.contains(want))) continue;
            Map<String, Float> c = jigCfg.computeIfAbsent(i, k -> new HashMap<>());
            for (String k : new String[] {"stiff", "damp", "grav", "angle"}) if (v.containsKey(k)) c.put(k, v.get(k));
        }
    }

    private float jigParam(int i, String key, float def) {
        Map<String, Float> c = jigCfg.get(i);
        Float v = c == null ? null : c.get(key);
        return v != null ? v : model.bones[i].param(key, def);
    }

    /** Un muelle amortiguado: la punta del hueso persigue la de su pose animada, con gravedad y ángulo máximo. */
    private void jiggle(int i, Matrix4f[] mats, Matrix4f world, Quaternionf wq, Quaternionf wqi, float wscale, float dt,
                        boolean fresh) {
        VfxModel.Bone b = model.bones[i];
        float stiff = clamp01(jigParam(i, "stiff", 0.15F), 0.01F);
        float damp = clamp01(jigParam(i, "damp", 0.25F), 0.01F);
        float grav = jigParam(i, "grav", 0F);
        float[] ang = b.params.get("angle");
        Map<String, Float> over = jigCfg.get(i);
        float ax = ang == null ? 45F : ang[0], ay = ang == null ? 45F : ang.length > 1 ? ang[1] : ang[0];
        if (over != null && over.containsKey("angle")) ax = ay = Math.min(180F, Math.abs(over.get("angle")));
        Vector3f pivotW = world.transformPosition(mats[i].getTranslation(new Vector3f()));
        Vector3f target = world.transformPosition(mats[i].transformPosition(new Vector3f(tipLocal[i])));
        float len = target.distance(pivotW);
        Vector3f t = tip.computeIfAbsent(i, k -> new Vector3f(target));
        if (fresh || len < 1e-5F) {
            t.set(target);
            vel[i].zero();
            return;
        }
        // Integración por ticks (fracciones incluidas): muelle hacia la pose, amortiguado, con gravedad (bloques/s²)
        Vector3f acc = new Vector3f(target).sub(t).mul(stiff);
        acc.y -= grav / 400F;
        vel[i].mul((float) Math.pow(1F - damp, dt)).add(acc.mul(dt));
        t.add(new Vector3f(vel[i]).mul(dt));
        // Siempre a la misma distancia del pivote y dentro del ángulo
        Vector3f dir = new Vector3f(t).sub(pivotW);
        if (dir.lengthSquared() < 1e-12F) dir.set(target).sub(pivotW);
        dir.normalize();
        Vector3f ref = new Vector3f(target).sub(pivotW).normalize();
        Quaternionf frame = new Quaternionf(wq).mul(mats[i].getUnnormalizedRotation(new Quaternionf()).normalize());
        SegCfg lim = new SegCfg();
        lim.minX = -ax;
        lim.maxX = ax;
        lim.minY = -ay;
        lim.maxY = ay;
        Quaternionf inv = new Quaternionf(frame).conjugate();
        Vector3f d = inv.transform(new Vector3f(dir)), r = inv.transform(new Vector3f(ref));
        double yawD = Math.atan2(d.x, d.z), yawR = Math.atan2(r.x, r.z);
        double pitD = Math.atan2(d.y, Math.hypot(d.x, d.z)), pitR = Math.atan2(r.y, Math.hypot(r.x, r.z));
        double dy = Math.max(-ay, Math.min(ay, Math.toDegrees(wrap(yawD - yawR))));
        double dp = Math.max(-ax, Math.min(ax, Math.toDegrees(pitD - pitR)));
        double yaw = yawR + Math.toRadians(dy), pit = pitR + Math.toRadians(dp);
        dir = frame.transform(new Vector3f((float) (Math.cos(pit) * Math.sin(yaw)), (float) Math.sin(pit),
                (float) (Math.cos(pit) * Math.cos(yaw)))).normalize();
        t.set(dir).mul(len).add(pivotW);
        // Giro: de la punta animada a la de la física
        Vector3f refM = new Quaternionf(wqi).transform(new Vector3f(ref));
        Vector3f dirM = new Quaternionf(wqi).transform(new Vector3f(dir));
        Quaternionf q = new Quaternionf().rotationTo(refM, dirM);
        Vector3f pos = mats[i].getTranslation(new Vector3f());
        Quaternionf rot = mats[i].getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f scl = mats[i].getScale(new Vector3f());
        mats[i].identity().translate(pos).rotate(q.mul(rot)).scale(scl);
    }

    private static float clamp01(float v, float min) {
        return Math.max(min, Math.min(1F, v));
    }

    /** Para quien dibuja: la normal no cambia de escala. */
    static Matrix3f normalOf(Matrix4f m) {
        return new Matrix3f(m).normal();
    }
}
