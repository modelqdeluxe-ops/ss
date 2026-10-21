package net.tierrasfantasticas.tfclient.crates.client.scene;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Lo que se mueve en el Santuario de las Hadas de las crates (1.3.99; la imagen del dueño), en el mundo de la escena: el
 * haz de sol que entra por el hueco de las copas, su foco y sus rayos, el polvo que brilla en él, el halo de las luces
 * mágicas, las hadas y las chispas, la bruma baja, la espuma y las ondas de las cascadas (el agua tiene su shader),
 * los pétalos, el anillo de runas, el pilar de luz de la crate abierta, la onda de la apertura y del premio y los
 * destellos. Todo determinista con el tiempo (en ticks), sin estado: cada partícula sale de su índice.
 */
public final class CinematicFx {
    private CinematicFx() {}

    /** Lo que necesita cada efecto: la matriz de la cámara, los ejes de la vista y dónde está la cámara. */
    public static final class View {
        public final Matrix4f m;
        public final Vector3f eye, right, up;

        public View(Matrix4f m, Vector3f eye, Vector3f right, Vector3f up) {
            this.m = m;
            this.eye = eye;
            this.right = right;
            this.up = up;
        }
    }

    // ------------------------------------------------------------------------------------------------------ ayudas
    static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    static float smooth(float a, float b, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static float frac(float x) {
        return x - (float)Math.floor(x);
    }

    /** Un cuadrado de luz (la textura del brillo) de cara a la cámara. */
    public static void glow(VertexConsumer vc, View v, float x, float y, float z, float size, float r, float g, float b, float a) {
        if (a <= 0.003F || size <= 0.0F) return;
        // lo que pasa pegado a la cámara se apaga (se veía como un disco blanco enorme tapando la imagen)
        a *= nearFade(v.eye, x, y, z, size);
        if (a <= 0.003F) return;
        float rx = v.right.x * size, ry = v.right.y * size, rz = v.right.z * size;
        float ux = v.up.x * size, uy = v.up.y * size, uz = v.up.z * size;
        vc.vertex(v.m, x - rx - ux, y - ry - uy, z - rz - uz).color(r, g, b, a).uv(0, 1).endVertex();
        vc.vertex(v.m, x + rx - ux, y + ry - uy, z + rz - uz).color(r, g, b, a).uv(1, 1).endVertex();
        vc.vertex(v.m, x + rx + ux, y + ry + uy, z + rz + uz).color(r, g, b, a).uv(1, 0).endVertex();
        vc.vertex(v.m, x - rx + ux, y - ry + uy, z - rz + uz).color(r, g, b, a).uv(0, 0).endVertex();
    }

    /** 0 pegado a la cámara, 1 a partir de ~2,5 bloques (más lejos cuanto más grande es lo que se dibuja). */
    static float nearFade(Vector3f eye, float x, float y, float z, float size) {
        float dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        float k = Math.max(0.0F, Math.min(1.0F, (d - 1.0F - size) / 1.5F));
        return k * k * (3.0F - 2.0F * k);
    }

    /**
     * Una banda de luz de a hasta b, girada hacia la cámara alrededor de su eje (los haces de sol, el pilar de luz),
     * con el perfil suave del brillo a lo ancho y su color en cada punta.
     */
    public static void band(VertexConsumer vc, View v, Vector3f a, Vector3f b, float wa, float wb, float r, float g, float bl,
                            float alphaA, float alphaB) {
        band(vc, v, a, b, wa, wb, r, g, bl, alphaA, alphaB, 0.0F, 1.0F);
    }

    /**
     * Lo mismo con solo el trozo {@code u0..u1} del perfil del brillo a lo ancho: con 0,16..0,84 el borde queda marcado
     * (la columna del haz de sol, «bien definido»).
     */
    public static void band(VertexConsumer vc, View v, Vector3f a, Vector3f b, float wa, float wb, float r, float g, float bl,
                            float alphaA, float alphaB, float u0, float u1) {
        float ax = b.x - a.x, ay = b.y - a.y, az = b.z - a.z;
        float mx = (a.x + b.x) * 0.5F - v.eye.x, my = (a.y + b.y) * 0.5F - v.eye.y, mz = (a.z + b.z) * 0.5F - v.eye.z;
        float sx = ay * mz - az * my, sy = az * mx - ax * mz, sz = ax * my - ay * mx;
        float l = (float)Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5F) return;
        sx /= l;
        sy /= l;
        sz /= l;
        vc.vertex(v.m, a.x - sx * wa, a.y - sy * wa, a.z - sz * wa).color(r, g, bl, alphaA).uv(u0, 0.5F).endVertex();
        vc.vertex(v.m, a.x + sx * wa, a.y + sy * wa, a.z + sz * wa).color(r, g, bl, alphaA).uv(u1, 0.5F).endVertex();
        vc.vertex(v.m, b.x + sx * wb, b.y + sy * wb, b.z + sz * wb).color(r, g, bl, alphaB).uv(u1, 0.5F).endVertex();
        vc.vertex(v.m, b.x - sx * wb, b.y - sy * wb, b.z - sz * wb).color(r, g, bl, alphaB).uv(u0, 0.5F).endVertex();
    }

    /** Un anillo plano (en y) hecho de segmentos con el perfil del brillo a lo ancho (ondas del agua, la onda de luz). */
    public static void ring(VertexConsumer vc, View v, float cx, float y, float cz, float radius, float width, int seg, float r, float g,
                            float b, float a) {
        if (a <= 0.003F || radius <= 0.0F) return;
        float r0 = Math.max(0.0F, radius - width), r1 = radius + width;
        for (int i = 0; i < seg; i++) {
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float)Math.cos(t0), s0 = (float)Math.sin(t0), c1 = (float)Math.cos(t1), s1 = (float)Math.sin(t1);
            vc.vertex(v.m, cx + c0 * r0, y, cz + s0 * r0).color(r, g, b, a).uv(0, 0.5F).endVertex();
            vc.vertex(v.m, cx + c1 * r0, y, cz + s1 * r0).color(r, g, b, a).uv(0, 0.5F).endVertex();
            vc.vertex(v.m, cx + c1 * r1, y, cz + s1 * r1).color(r, g, b, a).uv(1, 0.5F).endVertex();
            vc.vertex(v.m, cx + c0 * r1, y, cz + s0 * r1).color(r, g, b, a).uv(1, 0.5F).endVertex();
        }
    }

    /** Un disco de luz plano (en y): la luz del haz en el agua y en el suelo, la mancha bajo el premio. */
    public static void flatGlow(VertexConsumer vc, View v, float cx, float y, float cz, float rad, float r, float g, float b, float a) {
        if (a <= 0.003F) return;
        vc.vertex(v.m, cx - rad, y, cz - rad).color(r, g, b, a).uv(0, 0).endVertex();
        vc.vertex(v.m, cx - rad, y, cz + rad).color(r, g, b, a).uv(0, 1).endVertex();
        vc.vertex(v.m, cx + rad, y, cz + rad).color(r, g, b, a).uv(1, 1).endVertex();
        vc.vertex(v.m, cx + rad, y, cz - rad).color(r, g, b, a).uv(1, 0).endVertex();
    }

    /** Un cuadradito de color (SOFT) girado en 3D: pétalos, alas. */
    static void quad(VertexConsumer vc, Matrix4f m, float x, float y, float z, float ax, float ay, float az, float bx, float by, float bz,
                     float r, float g, float b, float a) {
        vc.vertex(m, x - ax - bx, y - ay - by, z - az - bz).color(r, g, b, a).endVertex();
        vc.vertex(m, x + ax - bx, y + ay - by, z + az - bz).color(r, g, b, a).endVertex();
        vc.vertex(m, x + ax + bx, y + ay + by, z + az + bz).color(r, g, b, a).endVertex();
        vc.vertex(m, x - ax + bx, y - ay + by, z - az + bz).color(r, g, b, a).endVertex();
    }

    // ------------------------------------------------------------------------------------------------ el sol
    /**
     * El haz de sol del santuario (como en la imagen del dueño): una columna ancha que baja del hueco de las copas sobre
     * el altar, con el borde marcado y el núcleo claro, más fuerte arriba que abajo (que no tape la crate), con vetas
     * finas que se mueven dentro; el foco blanco cálido donde entra el sol, arriba en el centro (sus rayos en abanico, en
     * el shader final); y el charco de luz en el altar y en el agua.
     * {@code power} sube con la apertura (y baja mientras gira la ruleta, para no tapar los premios); {@code tint}, el
     * color de la rareza que se mezcla al abrir. (Lo mismo en santuario_render.effects.)
     */
    public static void sunShafts(VertexConsumer vc, View v, SanctuaryScene sc, float t, float power, int tint, float tintAmt) {
        Vector3f sun = sc.sunDir();
        float[] c = sc.anchor("crate");
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        float r = mix(1.0F, tr, tintAmt), g = mix(0.97F, tg, tintAmt), b = mix(0.84F, tb, tintAmt);
        Vector3f base = new Vector3f(c[0], c[1] - 0.3F, c[2]);
        Vector3f mid = new Vector3f(base).add(new Vector3f(sun).mul(9.0F));
        Vector3f top = new Vector3f(base).add(new Vector3f(sun).mul(26.0F));
        float br = 0.88F + 0.12F * (float)Math.sin(t * 0.05F);
        // el halo ancho, la columna con el borde marcado y el núcleo claro (más fuertes arriba)
        band(vc, v, base, mid, 3.4F, 3.4F, r, g, b, 0.02F * power * br, 0.03F * power * br);
        band(vc, v, mid, top, 3.4F, 3.0F, r, g, b, 0.03F * power * br, 0.05F * power * br);
        band(vc, v, base, mid, 2.2F, 2.3F, r, g, b, 0.03F * power * br, 0.05F * power * br, 0.16F, 0.84F);
        band(vc, v, mid, top, 2.3F, 2.3F, r, g, b, 0.05F * power * br, 0.08F * power * br, 0.16F, 0.84F);
        band(vc, v, mid, top, 1.0F, 1.2F, mix(r, 1, 0.5F), mix(g, 1, 0.5F), mix(b, 1, 0.5F), 0.03F * power, 0.08F * power, 0.12F, 0.88F);
        // el foco del sol, donde entra por el hueco, y los rayos que bajan en abanico desde él
        float[] spot = sc.anchor("sunspot");
        glow(vc, v, spot[0], spot[1], spot[2], 3.0F, 1.0F, 0.90F, 0.55F, 0.8F * power);
        glow(vc, v, spot[0], spot[1], spot[2], 1.5F, 1.0F, 0.95F, 0.70F, 1.0F * power);
        glow(vc, v, spot[0], spot[1], spot[2], 0.7F, 1.0F, 0.98F, 0.85F, 1.0F * power);
        // (los rayos que salen del sol en abanico los pone el shader final, crate_post.godRays: como bandas de geometría
        // salían «tablones» de borde duro en el juego)
        // vetas finas que se mueven dentro del haz
        Vector3f p1 = new Vector3f(sun).cross(0, 1, 0).normalize();
        Vector3f p2 = new Vector3f(sun).cross(p1).normalize();
        for (int i = 0; i < 9; i++) {
            float ang = hash(i, 3) * 6.2832F + t * 0.004F * (hash(i, 5) - 0.5F);
            float rad = 0.3F + hash(i, 7) * 1.7F;
            float ox = (float)Math.cos(ang) * rad, oy = (float)Math.sin(ang) * rad;
            Vector3f b0 = new Vector3f(base).add(new Vector3f(p1).mul(ox)).add(new Vector3f(p2).mul(oy));
            Vector3f b1 = new Vector3f(b0).add(new Vector3f(sun).mul(24.0F));
            float a = (0.06F + 0.05F * (float)Math.sin(t * (0.03F + hash(i, 9) * 0.04F) + i * 2.1F)) * power;
            band(vc, v, b0, b1, 0.10F + hash(i, 11) * 0.15F, 0.2F + hash(i, 13) * 0.3F, mix(r, 1, 0.6F), mix(g, 1, 0.6F),
                    mix(b, 1, 0.6F), a * 0.5F, a);
        }
        // donde cae el haz: el charco de luz en el altar y en el agua
        flatGlow(vc, v, c[0], c[1] + 0.01F, c[2], 2.6F, r, g, b, 0.12F * power);
        float[] pool = sc.anchor("pool");
        flatGlow(vc, v, c[0], pool[1] + 0.02F, c[2] - 1.0F, 4.8F, r, g, b, 0.16F * power);
    }

    /** El polvo que brilla en el haz de sol (y algo de polen por el santuario). */
    public static void motes(VertexConsumer vc, View v, SanctuaryScene sc, float t, float power) {
        Vector3f sun = sc.sunDir();
        float[] c = sc.anchor("crate");
        Vector3f p1 = new Vector3f(sun).cross(0, 1, 0).normalize();
        Vector3f p2 = new Vector3f(sun).cross(p1).normalize();
        for (int i = 0; i < 130; i++) {
            float along = 0.3F + hash(i, 31) * 13.0F;
            float ang = hash(i, 33) * 6.2832F + t * 0.004F * (hash(i, 35) - 0.5F);
            float rad = (float)Math.sqrt(hash(i, 37)) * 2.4F;
            float drift = (float)Math.sin(t * 0.02F + i) * 0.25F;
            float x = c[0] + sun.x * along + p1.x * ((float)Math.cos(ang) * rad + drift) + p2.x * (float)Math.sin(ang) * rad;
            float y = c[1] + sun.y * along + p1.y * (float)Math.cos(ang) * rad + p2.y * (float)Math.sin(ang) * rad
                    + frac(t * 0.0015F * (0.5F + hash(i, 39)) + hash(i, 41)) * 1.2F - 0.6F;
            float z = c[2] + sun.z * along + p1.z * ((float)Math.cos(ang) * rad + drift) + p2.z * (float)Math.sin(ang) * rad;
            float tw = 0.5F + 0.5F * (float)Math.sin(t * (0.15F + hash(i, 43) * 0.2F) + i * 1.7F);
            glow(vc, v, x, y, z, 0.035F + hash(i, 45) * 0.03F, 1.0F, 0.95F, 0.78F, (0.25F + 0.55F * tw) * power);
        }
        for (int i = 0; i < 45; i++) {
            float x = -9.0F + hash(i, 51) * 18.0F + (float)Math.sin(t * 0.01F + i) * 1.2F + t * 0.006F;
            x = -9.0F + frac((x + 9.0F) / 18.0F) * 18.0F;
            float z = -14.0F + hash(i, 53) * 22.0F + (float)Math.cos(t * 0.012F + i * 0.7F) * 1.0F;
            float y = 0.6F + hash(i, 55) * 5.0F + (float)Math.sin(t * 0.03F + i) * 0.3F;
            float tw = 0.5F + 0.5F * (float)Math.sin(t * 0.2F + i * 2.3F);
            glow(vc, v, x, y, z, 0.03F, 1.0F, 0.97F, 0.85F, 0.25F * tw);
        }
    }

    // ------------------------------------------------------------------------------------------------- el agua
    private static TextureAtlasSprite flow;

    private static TextureAtlasSprite flow() {
        if (flow == null) {
            flow = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft",
                    "block/water_flow"));
        }
        return flow;
    }

    /** Dónde está el agua de un caño en s (0 = la boca, 1 = donde cae al estanque): un arco que sale y cae. */
    static Vector3f streamAt(float[] spout, float[] crate, float poolY, float s, float surge) {
        float dx = crate[0] - spout[0], dz = crate[2] - spout[2];
        float L = (float)Math.sqrt(dx * dx + dz * dz);
        float reach = (L - 2.75F + surge * 0.35F) / L;
        float x = spout[0] + dx * reach * s, z = spout[2] + dz * reach * s;
        float lift = 0.45F + surge * 0.5F;
        float y = spout[1] + lift * s - (spout[1] - poolY + lift) * s * s;
        return new Vector3f(x, y, z);
    }

    /**
     * Los cuatro chorros (la textura del agua que corre, en una banda de cara a la cámara), la espuma donde caen, las
     * gotas que saltan y las ondas que se abren en el estanque. {@code glow} (0..1) los ilumina con el color de la
     * rareza al girar la llave; {@code surge}, más agua y más lejos (la apertura y el premio).
     */
    public static void streams(MultiBufferSource buf, View v, SanctuaryScene sc, float t, float surge) {
        TextureAtlasSprite sp = flow();
        VertexConsumer vc = buf.getBuffer(SceneRenderTypes.WATER);
        float[] crate = sc.anchor("crate"), pool = sc.anchor("pool");
        int wc = SceneLevel.WATER;
        float wr = ((wc >> 16) & 255) / 255.0F, wg = ((wc >> 8) & 255) / 255.0F, wb = (wc & 255) / 255.0F;
        int light = LightTexture.FULL_BRIGHT;
        for (int k = 0; k < 4; k++) {
            float[] spout = sc.anchor("spout" + k);
            if (spout[1] <= 0) continue;
            int segs = 18;
            Vector3f prev = streamAt(spout, crate, pool[1], 0, surge);
            float w = 0.11F + surge * 0.05F;
            for (int i = 1; i <= segs; i++) {
                float s = i / (float)segs;
                Vector3f p = streamAt(spout, crate, pool[1], s, surge);
                float ax = p.x - prev.x, ay = p.y - prev.y, az = p.z - prev.z;
                float mx = p.x - v.eye.x, my = p.y - v.eye.y, mz = p.z - v.eye.z;
                float sx = ay * mz - az * my, sy = az * mx - ax * mz, sz = ax * my - ay * mx;
                float l = (float)Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (l < 1e-6F) continue;
                float w0 = w * (1.0F + (i - 1) / (float)segs * 0.6F), w1 = w * (1.0F + s * 0.6F);
                sx /= l;
                sy /= l;
                sz /= l;
                float v0 = sp.getV0() + (sp.getV1() - sp.getV0()) * ((i - 1) % 3) / 3.0F;
                float v1 = v0 + (sp.getV1() - sp.getV0()) / 3.0F;
                float u0 = sp.getU0() + (sp.getU1() - sp.getU0()) * 0.25F, u1 = sp.getU0() + (sp.getU1() - sp.getU0()) * 0.75F;
                float a = 0.82F;
                vc.vertex(v.m, prev.x - sx * w0, prev.y - sy * w0, prev.z - sz * w0).color(wr * 1.15F, wg * 1.1F, wb, a).uv(u0, v0)
                        .uv2(light).normal(0, 1, 0).endVertex();
                vc.vertex(v.m, prev.x + sx * w0, prev.y + sy * w0, prev.z + sz * w0).color(wr * 1.15F, wg * 1.1F, wb, a).uv(u1, v0)
                        .uv2(light).normal(0, 1, 0).endVertex();
                vc.vertex(v.m, p.x + sx * w1, p.y + sy * w1, p.z + sz * w1).color(wr * 1.15F, wg * 1.1F, wb, a).uv(u1, v1).uv2(light)
                        .normal(0, 1, 0).endVertex();
                vc.vertex(v.m, p.x - sx * w1, p.y - sy * w1, p.z - sz * w1).color(wr * 1.15F, wg * 1.1F, wb, a).uv(u0, v1).uv2(light)
                        .normal(0, 1, 0).endVertex();
                prev = p;
            }
        }
    }

    /** El brillo de los chorros (la luz que les da) y la espuma, las gotas y las ondas (luz que suma). */
    public static void streamLights(VertexConsumer vc, View v, SanctuaryScene sc, float t, float glowAmt, int tint, float surge) {
        float[] crate = sc.anchor("crate"), pool = sc.anchor("pool");
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        for (int k = 0; k < 4; k++) {
            float[] spout = sc.anchor("spout" + k);
            if (spout[1] <= 0) continue;
            // el reflejo del sol a lo largo del chorro (y el color de la rareza al girar la llave)
            Vector3f prev = streamAt(spout, crate, pool[1], 0, surge);
            for (int i = 1; i <= 12; i++) {
                float s = i / 12.0F;
                Vector3f p = streamAt(spout, crate, pool[1], s, surge);
                float shimmer = 0.5F + 0.5F * (float)Math.sin(t * 0.9F - s * 9.0F + k);
                band(vc, v, prev, p, 0.05F, 0.06F, mix(1.0F, tr, glowAmt), mix(0.98F, tg, glowAmt), mix(0.92F, tb, glowAmt),
                        (0.10F + 0.10F * shimmer) * (1.0F + glowAmt * 2.0F), (0.10F + 0.10F * shimmer) * (1.0F + glowAmt * 2.0F));
                prev = p;
            }
            Vector3f land = streamAt(spout, crate, pool[1], 1.0F, surge);
            // espuma
            for (int f = 0; f < 6; f++) {
                float ph = frac(t * 0.09F + hash(f + k * 7, 61));
                float ang = hash(f + k * 7, 63) * 6.2832F;
                float rr = 0.1F + ph * 0.25F;
                glow(vc, v, land.x + (float)Math.cos(ang) * rr, land.y + 0.05F + ph * 0.12F, land.z + (float)Math.sin(ang) * rr,
                        0.16F * (1.0F - ph * 0.5F), 1.0F, 1.0F, 1.0F, 0.35F * (1.0F - ph));
            }
            // gotas que saltan
            for (int d = 0; d < 8; d++) {
                float ph = frac(t * 0.06F + hash(d + k * 11, 65));
                float ang = hash(d + k * 11, 67) * 6.2832F;
                float sp = 0.4F + hash(d + k * 11, 69) * 0.5F;
                float x = land.x + (float)Math.cos(ang) * sp * ph, z = land.z + (float)Math.sin(ang) * sp * ph;
                float y = land.y + 0.05F + (0.55F + surge * 0.3F) * (4.0F * ph * (1.0F - ph)) * sp;
                glow(vc, v, x, y, z, 0.035F, 0.92F, 0.97F, 1.0F, 0.6F * (1.0F - ph));
            }
            // ondas
            for (int o = 0; o < 3; o++) {
                float ph = frac(t * 0.022F + o / 3.0F + k * 0.13F);
                ring(vc, v, land.x, pool[1] + 0.012F, land.z, 0.15F + ph * 1.3F, 0.035F, 28, 0.9F, 0.96F, 1.0F, 0.32F * (1.0F - ph));
            }
        }
        // ondas sueltas por el estanque (gotas, un pez)
        for (int i = 0; i < 5; i++) {
            float cycle = 90.0F + hash(i, 71) * 80.0F;
            float ph = frac(t / cycle + hash(i, 73));
            if (ph > 0.5F) continue;
            float p = ph / 0.5F;
            int n = (int)Math.floor(t / cycle + hash(i, 73));
            float ang = hash(i * 31 + n, 75) * 6.2832F, rad = 3.0F + hash(i * 31 + n, 77) * 2.0F;
            ring(vc, v, crate[0] + (float)Math.cos(ang) * rad, pool[1] + 0.012F, crate[2] + (float)Math.sin(ang) * rad, 0.1F + p * 1.0F,
                    0.03F, 24, 0.9F, 0.96F, 1.0F, 0.16F * (1.0F - p));
        }
    }

    // ---------------------------------------------------------------------------------------- pétalos y criaturas
    /**
     * Los pétalos de los cerezos: caen dando vueltas y el viento los lleva; {@code burst} (0..1, la apertura) suelta
     * una ráfaga que sale de la crate hacia fuera y hacia arriba.
     */
    public static void petals(VertexConsumer vc, View v, SanctuaryScene sc, float t, float burstT, float burstPower) {
        for (int tree = 0; tree < 4; tree++) {
            float[] ch = sc.anchor("cherry" + tree);
            if (ch[3] <= 0) continue;
            for (int i = 0; i < 26; i++) {
                int id = tree * 100 + i;
                float speed = 0.012F + hash(id, 81) * 0.012F;
                float height = ch[1] + 1.0F;
                float period = (height + 0.5F) / speed;
                float ph = frac(t / period + hash(id, 83));
                float fall = ph * period * speed;
                float ang = hash(id, 85) * 6.2832F, rad = (float)Math.sqrt(hash(id, 87)) * ch[3];
                float x = ch[0] + (float)Math.cos(ang) * rad + fall * 0.55F + (float)Math.sin(t * 0.05F + id) * 0.35F;
                float z = ch[2] + (float)Math.sin(ang) * rad + (float)Math.cos(t * 0.04F + id) * 0.3F;
                float y = height - 1.0F - fall;
                if (y < 0.05F) continue;
                petal(vc, v, x, y, z, t, id, nearFade(v.eye, x, y, z, 0.1F));
            }
        }
        if (burstPower > 0.0F && burstT >= 0.0F) {
            float[] c = sc.anchor("crate");
            for (int i = 0; i < 70; i++) {
                float delay = hash(i, 91) * 6.0F;
                float s = (burstT - delay) / 20.0F;
                if (s < 0 || s > 6.0F) continue;
                float ang = hash(i, 93) * 6.2832F, sp = 1.5F + hash(i, 95) * 3.5F;
                float out = sp * (1.0F - (float)Math.exp(-s * 1.6F));
                float x = c[0] + (float)Math.cos(ang) * out + (float)Math.sin(s * 3.0F + i) * 0.2F;
                float z = c[2] + (float)Math.sin(ang) * out;
                float y = c[1] + 0.9F + (2.5F + hash(i, 97) * 2.0F) * (1.0F - (float)Math.exp(-s * 2.2F)) - s * s * 0.25F;
                if (y < 0.1F) continue;
                // (los pétalos no vuelan hacia la cámara: los que salían hacia ella pasaban pegados, enormes)
                petal(vc, v, x, y, z, t, 900 + i, burstPower * Math.min(1.0F, (6.0F - s) * 0.8F) * nearFade(v.eye, x, y, z, 0.1F));
            }
        }
    }

    private static void petal(VertexConsumer vc, View v, float x, float y, float z, float t, int id, float alpha) {
        // (siempre de cara a la cámara, girando en su plano y aplastándose un poco: de canto se veían como rayas blancas
        // finas sobre lo oscuro; el dueño, «líneas blancas feas»)
        float spin = t * (0.1F + hash(id, 101) * 0.15F) + hash(id, 103) * 6.0F;
        float squash = 0.55F + 0.45F * Math.abs((float)Math.cos(t * (0.07F + hash(id, 105) * 0.1F) + hash(id, 107) * 6.0F));
        float cs = (float)Math.cos(spin), sn = (float)Math.sin(spin);
        float s = 0.05F + hash(id, 109) * 0.03F;
        float ax = (v.right.x * cs + v.up.x * sn) * s, ay = (v.right.y * cs + v.up.y * sn) * s, az = (v.right.z * cs + v.up.z * sn) * s;
        float bx = (-v.right.x * sn + v.up.x * cs) * s * 0.6F * squash, by = (-v.right.y * sn + v.up.y * cs) * s * 0.6F * squash,
                bz = (-v.right.z * sn + v.up.z * cs) * s * 0.6F * squash;
        float shade = 0.85F + 0.15F * hash(id, 111);
        quad(vc, v.m, x, y, z, ax, ay, az, bx, by, bz, 0.98F * shade, 0.72F * shade, 0.84F * shade, 0.95F * alpha);
    }

    /** Las mariposas: vuelan sobre las flores, con las alas que baten. */
    public static void butterflies(VertexConsumer vc, Matrix4f m, SanctuaryScene sc, float t) {
        int[] colors = {0xF2A23A, 0xF5F2E8, 0x5B9BF0, 0xF3D34A, 0xF2A23A, 0xE884C8};
        for (int i = 0; i < 6; i++) {
            float[] f = sc.anchor("flowers" + (i % 3));
            if (f[3] <= 0) continue;
            float a = t * (0.018F + hash(i, 121) * 0.01F) + hash(i, 123) * 6.0F;
            float x = f[0] + (float)Math.sin(a) * f[3] + (float)Math.sin(a * 2.3F) * 0.6F;
            float z = f[2] + (float)Math.cos(a * 0.8F) * f[3] * 0.8F;
            float y = f[1] + 0.5F + 0.45F * (float)Math.sin(a * 1.7F + i) + 0.2F * (float)Math.sin(t * 0.3F + i);
            float dx = (float)Math.cos(a) * f[3] + (float)Math.cos(a * 2.3F) * 1.4F, dz = -(float)Math.sin(a * 0.8F) * f[3] * 0.64F;
            float l = (float)Math.sqrt(dx * dx + dz * dz) + 1e-4F;
            dx /= l;
            dz /= l;
            float flap = (float)Math.sin(t * 2.6F + i * 2.0F) * 1.1F;
            float sx = -dz, sz = dx;          // a un lado
            float s = 0.085F;
            int c = colors[i];
            float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
            for (int side = -1; side <= 1; side += 2) {
                float wx = sx * side * (float)Math.cos(flap) * s, wy = (float)Math.sin(flap) * s, wz = sz * side * (float)Math.cos(flap) * s;
                float cx = x + wx, cy = y + wy, cz = z + wz;
                quad(vc, m, cx, cy, cz, wx, wy, wz, dx * s * 0.75F, 0, dz * s * 0.75F, r, g, b, 1.0F);
            }
        }
    }

    /** Pájaros a lo lejos, cruzando el cielo. */
    public static void birds(VertexConsumer vc, Matrix4f m, float t) {
        for (int i = 0; i < 4; i++) {
            float period = 520.0F + hash(i, 131) * 300.0F;
            float ph = frac(t / period + hash(i, 133));
            float x = -70.0F + ph * 140.0F, z = -55.0F - hash(i, 135) * 20.0F, y = 22.0F + hash(i, 137) * 10.0F + (float)Math.sin(t * 0.02F + i) * 1.5F;
            float flap = (float)Math.sin(t * (0.5F + hash(i, 139) * 0.3F) + i) * 0.5F;
            float s = 0.55F;
            for (int side = -1; side <= 1; side += 2) {
                float wy = (float)Math.sin(flap) * s * 0.6F, wz = side * s * (float)Math.cos(flap);
                quad(vc, m, x, y + wy * 0.5F, z + wz * 0.5F, 0.0F, wy * 0.5F, wz * 0.5F, 0.16F, 0.0F, 0.0F, 0.18F, 0.17F, 0.2F, 0.55F);
            }
        }
    }

    // ------------------------------------------------------------------------------------- runas, pilar y ondas
    /**
     * El anillo de runas sobre el agua, alrededor del pedestal: se enciende runa a runa (de {@code from} a 1) y
     * brilla con el color de la rareza.
     */
    public static void runes(VertexConsumer vc, View v, SanctuaryScene sc, float t, float lit, float flare, int tint) {
        if (lit <= 0.0F) return;
        float[] c = sc.anchor("crate"), pool = sc.anchor("pool");
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        float y = pool[1] + 0.02F, R = 3.35F;
        float pulse = 0.85F + 0.15F * (float)Math.sin(t * 0.25F);
        ring(vc, v, c[0], y, c[2], R, 0.10F + flare * 0.1F, 64, tr, tg, tb, Math.min(1.0F, lit) * 0.55F * pulse + flare * 0.5F);
        ring(vc, v, c[0], y, c[2], R + 0.55F, 0.05F, 64, tr, tg, tb, Math.min(1.0F, lit) * 0.3F * pulse + flare * 0.3F);
        int n = 16;
        for (int i = 0; i < n; i++) {
            float on = smooth(i / (float)n, i / (float)n + 0.12F, lit);
            if (on <= 0) continue;
            float ang = (float)(Math.PI * 2 * i / n) + t * 0.003F;
            float x = c[0] + (float)Math.cos(ang) * (R + 0.28F), z = c[2] + (float)Math.sin(ang) * (R + 0.28F);
            glyph(vc, v, x, y + 0.005F, z, ang, i, tr, tg, tb, on * (0.75F + 0.25F * pulse) + flare * 0.6F);
            if (on < 1.0F) glow(vc, v, x, y + 0.15F, z, 0.45F, tr, tg, tb, (1.0F - on) * on * 3.0F);
        }
    }

    /** Una runa: unos trazos (de su semilla) dentro de un cuadrado de 0,4, tumbada en el agua y mirando al centro. */
    private static void glyph(VertexConsumer vc, View v, float x, float y, float z, float ang, int id, float r, float g, float b, float a) {
        float ux = (float)-Math.sin(ang), uz = (float)Math.cos(ang);   // a lo largo del anillo
        float rx = (float)Math.cos(ang), rz = (float)Math.sin(ang);    // hacia fuera
        int strokes = 3 + (int)(hash(id, 141) * 3);
        for (int k = 0; k < strokes; k++) {
            float a0 = hash(id * 13 + k, 143) - 0.5F, b0 = hash(id * 13 + k, 145) - 0.5F;
            float a1 = hash(id * 13 + k, 147) - 0.5F, b1 = hash(id * 13 + k, 149) - 0.5F;
            if (k % 2 == 0) a1 = a0; else b1 = b0;
            float s = 0.36F;
            Vector3f p0 = new Vector3f(x + (ux * a0 + rx * b0) * s, y, z + (uz * a0 + rz * b0) * s);
            Vector3f p1 = new Vector3f(x + (ux * a1 + rx * b1) * s, y, z + (uz * a1 + rz * b1) * s);
            flatStroke(vc, v, p0, p1, 0.035F, r, g, b, a);
        }
    }

    private static void flatStroke(VertexConsumer vc, View v, Vector3f a, Vector3f b, float w, float r, float g, float bl, float al) {
        float dx = b.x - a.x, dz = b.z - a.z;
        float l = (float)Math.sqrt(dx * dx + dz * dz) + 1e-5F;
        float nx = -dz / l * w, nz = dx / l * w;
        vc.vertex(v.m, a.x - nx - dx / l * w, a.y, a.z - nz - dz / l * w).color(r, g, bl, al).uv(0, 0.5F).endVertex();
        vc.vertex(v.m, a.x + nx - dx / l * w, a.y, a.z + nz - dz / l * w).color(r, g, bl, al).uv(1, 0.5F).endVertex();
        vc.vertex(v.m, b.x + nx + dx / l * w, b.y, b.z + nz + dz / l * w).color(r, g, bl, al).uv(1, 0.5F).endVertex();
        vc.vertex(v.m, b.x - nx + dx / l * w, b.y, b.z - nz + dz / l * w).color(r, g, bl, al).uv(0, 0.5F).endVertex();
    }

    /** El pilar de luz que sale de la crate abierta (blanco en el centro, del color de la rareza por fuera). */
    public static void pillar(VertexConsumer vc, View v, float x, float y, float z, float height, float power, float t, int tint) {
        if (power <= 0.0F) return;
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        float pulse = 0.85F + 0.15F * (float)Math.sin(t * 0.4F);
        Vector3f a = new Vector3f(x, y, z), b = new Vector3f(x, y + height, z);
        band(vc, v, a, b, 0.5F, 1.1F, tr, tg, tb, 0.40F * power * pulse, 0.0F);
        band(vc, v, a, b, 0.2F, 0.35F, mix(tr, 1, 0.6F), mix(tg, 1, 0.6F), mix(tb, 1, 0.6F), 0.6F * power * pulse, 0.0F);
        band(vc, v, a, new Vector3f(x, y + height * 0.6F, z), 0.07F, 0.12F, 1, 1, 1, 0.8F * power, 0.0F);
        glow(vc, v, x, y + 0.1F, z, 0.9F, tr, tg, tb, 0.6F * power);
        // chispas que suben por el pilar
        for (int i = 0; i < 30; i++) {
            float ph = frac(t * (0.012F + hash(i, 151) * 0.01F) + hash(i, 153));
            float ang = hash(i, 155) * 6.2832F + t * 0.05F, rad = 0.15F + hash(i, 157) * 0.45F;
            glow(vc, v, x + (float)Math.cos(ang) * rad, y + ph * height * 0.7F, z + (float)Math.sin(ang) * rad, 0.05F, mix(tr, 1, 0.5F),
                    mix(tg, 1, 0.5F), mix(tb, 1, 0.5F), power * (1.0F - ph) * 0.9F);
        }
    }

    /** Una onda de luz que se abre por el agua y el suelo (la apertura, el premio). */
    public static void shockwave(VertexConsumer vc, View v, float x, float y, float z, float since, float dur, float maxR, int tint) {
        if (since < 0 || since > dur) return;
        float p = since / dur;
        float e = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        ring(vc, v, x, y, z, e * maxR, 0.25F + p * 0.4F, 72, mix(tr, 1, 0.4F), mix(tg, 1, 0.4F), mix(tb, 1, 0.4F), (1.0F - p) * 0.8F);
        ring(vc, v, x, y + 0.01F, z, e * maxR * 0.8F, 0.12F, 72, 1, 1, 1, (1.0F - p) * 0.5F);
    }

    /** Destellos que salen de un punto en todas direcciones (al disolverse la llave, el premio). */
    public static void sparkBurst(VertexConsumer vc, View v, float x, float y, float z, float since, float dur, int count, float speed,
                                  int tint, int salt) {
        if (since < 0 || since > dur) return;
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        float p = since / dur;
        for (int i = 0; i < count; i++) {
            float th = hash(i, salt) * 6.2832F, ph = (float)Math.acos(2 * hash(i, salt + 1) - 1);
            float sp = speed * (0.4F + hash(i, salt + 2) * 0.8F);
            float d = sp * (1.0F - (float)Math.exp(-since * 0.18F)) * 4.0F;
            float px = x + (float)(Math.sin(ph) * Math.cos(th)) * d, py = y + (float)Math.cos(ph) * d - p * p * 0.6F,
                    pz = z + (float)(Math.sin(ph) * Math.sin(th)) * d;
            float white = hash(i, salt + 3) < 0.35F ? 1.0F : 0.0F;
            glow(vc, v, px, py, pz, 0.06F + hash(i, salt + 4) * 0.05F, mix(tr, 1, white), mix(tg, 1, white), mix(tb, 1, white),
                    (1.0F - p) * 0.95F);
        }
    }

    /** Rayos que giran detrás del premio (abanico de triángulos alargados, luz que suma). */
    public static void rays(VertexConsumer vc, View v, float x, float y, float z, float t, float power, float len, int tint) {
        if (power <= 0.0F) return;
        float tr = ((tint >> 16) & 255) / 255.0F, tg = ((tint >> 8) & 255) / 255.0F, tb = (tint & 255) / 255.0F;
        int n = 14;
        for (int i = 0; i < n; i++) {
            float ang = (float)(Math.PI * 2 * i / n) + t * 0.012F;
            float l = len * (0.7F + 0.3F * hash(i, 161)) * (0.85F + 0.15F * (float)Math.sin(t * 0.1F + i));
            float w = 0.09F * l;
            float cx = (float)Math.cos(ang), sy = (float)Math.sin(ang);
            float ex = (v.right.x * cx + v.up.x * sy), ey = (v.right.y * cx + v.up.y * sy), ez = (v.right.z * cx + v.up.z * sy);
            float nx = (-v.right.x * sy + v.up.x * cx), ny = (-v.right.y * sy + v.up.y * cx), nz = (-v.right.z * sy + v.up.z * cx);
            float a = power * (i % 2 == 0 ? 0.32F : 0.2F);
            vc.vertex(v.m, x, y, z).color(mix(tr, 1, 0.5F), mix(tg, 1, 0.5F), mix(tb, 1, 0.5F), a).endVertex();
            vc.vertex(v.m, x + ex * l + nx * w, y + ey * l + ny * w, z + ez * l + nz * w).color(tr, tg, tb, 0.0F).endVertex();
            vc.vertex(v.m, x + ex * l * 1.02F, y + ey * l * 1.02F, z + ez * l * 1.02F).color(tr, tg, tb, 0.0F).endVertex();
            vc.vertex(v.m, x + ex * l - nx * w, y + ey * l - ny * w, z + ez * l - nz * w).color(tr, tg, tb, 0.0F).endVertex();
        }
    }

    static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------------------------- el bosque de las hadas
    /** El halo de cada luz mágica (faroles, setas lámpara, liquen, bayas, linternas de mar...), que respira un poco. */
    public static void lightHalos(VertexConsumer vc, View v, SanctuaryScene sc, float t) {
        int i = 0;
        for (float[] l : sc.lights) {
            i++;
            float dx = l[0] - v.eye.x, dy = l[1] - v.eye.y, dz = l[2] - v.eye.z;
            if (dx * dx + dy * dy + dz * dz > 70 * 70) continue;
            float flick = 0.82F + 0.18F * (float)Math.sin(t * (0.07F + hash(i, 171) * 0.08F) + i);
            float s = l[6];
            // un halo pequeño y suave (en la imagen del dueño el brillo de los faroles es del tamaño del farol, no un disco)
            glow(vc, v, l[0], l[1], l[2], 0.42F * s + 0.12F, l[3], l[4], l[5], 0.26F * flick * Math.min(1.0F, s + 0.3F));
            glow(vc, v, l[0], l[1], l[2], 0.18F * s + 0.06F, mix(l[3], 1, 0.5F), mix(l[4], 1, 0.5F), mix(l[5], 1, 0.5F), 0.5F * flick);
            // los faroles: su resplandor, grande y muy suave (el dueño: «que se note el resplandor de los faroles»)
            if (s >= 1.2F) glow(vc, v, l[0], l[1], l[2], 1.15F * s, l[3], l[4], l[5], 0.17F * flick);
        }
    }

    private static final int[] FAIRY = {0xFFB8E8, 0x9FF3FF, 0xFFE59A, 0xD8B4FF, 0xB8FFC8, 0xFFFFFF, 0xFF9FD0, 0xA8D8FF, 0xFFD2A0, 0xE0C0FF};

    /** Dónde está el hada i en t: vuela en lazos por el claro, sube y baja, a veces cruza cerca de la crate. */
    static Vector3f fairyAt(SanctuaryScene sc, int i, float t) {
        float[] c = sc.anchor("crate");
        float sp = 0.010F + hash(i, 173) * 0.012F;
        float a = t * sp + hash(i, 175) * 6.2832F;
        float R = 3.2F + hash(i, 177) * 5.0F + 1.4F * (float)Math.sin(t * 0.007F + i);
        float x = c[0] + (float)Math.cos(a) * R + (float)Math.sin(a * 2.7F + i) * 0.9F;
        float z = c[2] + (float)Math.sin(a) * R * 0.85F - 1.5F + (float)Math.cos(a * 1.9F) * 0.8F;
        float y = 1.6F + hash(i, 179) * 3.6F + 0.8F * (float)Math.sin(a * 3.1F + i) + 0.25F * (float)Math.sin(t * 0.21F + i * 1.3F);
        return new Vector3f(x, y, z);
    }

    /**
     * Las hadas: lucecitas de colores que vuelan con alas que baten, dejando una estela de chispas. Dos pasadas: la luz
     * (GLOW_ADD, {@code wings} false) y las alas (SOFT, {@code wings} true); {@code excite} (la apertura) las aviva.
     */
    public static void fairies(VertexConsumer vc, View v, SanctuaryScene sc, float t, float excite, boolean wings) {
        for (int i = 0; i < 10; i++) {
            Vector3f p = fairyAt(sc, i, t);
            int c = FAIRY[i];
            float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
            if (wings) {
                float flap = (float)Math.sin(t * 3.4F + i) * 0.9F;
                // (las alas de un hada que pasa pegada a la cámara se veían como cuadriláteros blancos enormes)
                float near = nearFade(v.eye, p.x, p.y, p.z, 0.3F);
                if (near <= 0.01F) continue;
                // (las alas, de cara a la cámara: de canto se veían como rayas claras finas)
                for (int side = -1; side <= 1; side += 2) {
                    float s = 0.11F;
                    float c0 = side * (0.35F + 0.65F * Math.abs((float)Math.cos(flap))) * s, c1 = (float)Math.sin(flap) * s * 0.5F + 0.03F;
                    float wx = v.right.x * c0 + v.up.x * c1, wy = v.right.y * c0 + v.up.y * c1, wz = v.right.z * c0 + v.up.z * c1;
                    quad(vc, v.m, p.x + wx, p.y + wy, p.z + wz, wx, wy, wz, v.up.x * s * 0.55F, v.up.y * s * 0.55F, v.up.z * s * 0.55F,
                            mix(r, 1, 0.6F), mix(g, 1, 0.6F), mix(b, 1, 0.6F), 0.42F * near);
                }
                continue;
            }
            VertexConsumer glowVc = vc;
            // (chispitas, no orbes: en la imagen del dueño solo hay puntitos de luz)
            float pulse = 0.8F + 0.2F * (float)Math.sin(t * 0.6F + i * 2.0F);
            glow(glowVc, v, p.x, p.y, p.z, 0.22F * pulse * (1 + excite * 0.4F), r, g, b, 0.45F);
            glow(glowVc, v, p.x, p.y, p.z, 0.07F, 1, 1, 1, 0.9F);
            for (int k = 1; k <= 12; k++) {
                Vector3f q = fairyAt(sc, i, t - k * 0.9F);
                float f = 1.0F - k / 13.0F;
                float jx = (hash(i * 31 + k, 181) - 0.5F) * 0.12F, jy = (hash(i * 31 + k, 183) - 0.5F) * 0.12F - k * 0.012F;
                glow(glowVc, v, q.x + jx, q.y + jy, q.z, 0.05F * f + 0.02F, mix(r, 1, 0.4F), mix(g, 1, 0.4F), mix(b, 1, 0.4F), 0.6F * f);
            }
        }
    }

    /** Chispas mágicas (como luciérnagas): puntitos que se encienden y se apagan por todo el santuario. */
    public static void fireflies(VertexConsumer vc, View v, float t) {
        for (int i = 0; i < 110; i++) {
            float period = 60.0F + hash(i, 191) * 90.0F;
            float ph = frac(t / period + hash(i, 193));
            float on = (float)Math.pow(Math.max(0.0F, (float)Math.sin(ph * Math.PI * 2)), 3.0);
            if (on < 0.02F) continue;
            float x = -11.0F + hash(i, 195) * 22.0F + (float)Math.sin(t * 0.013F + i) * 1.2F;
            float z = -16.0F + hash(i, 197) * 26.0F + (float)Math.cos(t * 0.011F + i * 0.7F) * 1.2F;
            float y = 0.6F + hash(i, 199) * 6.0F + (float)Math.sin(t * 0.03F + i) * 0.35F;
            glow(vc, v, x, y, z, 0.13F, 0.85F, 1.0F, 0.6F, 0.40F * on);
            glow(vc, v, x, y, z, 0.035F, 1, 1, 0.85F, 0.85F * on);
        }
    }

    /** Niebla baja: manchas suaves y frías a ras del suelo y del agua, que se mueven despacio (más al pie de la cascada). */
    public static void mist(VertexConsumer vc, View v, SanctuaryScene sc, float t) {
        float[] pond = sc.anchor("pond");
        for (int i = 0; i < 26; i++) {
            float x = -9.0F + hash(i, 201) * 18.0F + t * 0.006F * (0.5F + hash(i, 203));
            x = -9.0F + frac((x + 9.0F) / 18.0F) * 18.0F;
            float z = -14.0F + hash(i, 205) * 24.0F;
            float y = 0.4F + hash(i, 207) * 0.6F;
            float a = 0.03F * (0.6F + 0.4F * (float)Math.sin(t * 0.02F + i));
            glow(vc, v, x, y, z, 2.6F + hash(i, 209) * 2.0F, 0.55F, 0.95F, 0.95F, a);
        }
        for (int i = 0; i < 10; i++) {
            float ph = frac(t * 0.008F + hash(i, 211));
            glow(vc, v, pond[0] + (hash(i, 213) - 0.5F) * 4.0F, pond[1] + 0.3F + ph * 2.2F, pond[2] + (hash(i, 215) - 0.5F) * 2.0F,
                    1.2F + ph * 1.6F, 0.75F, 0.88F, 1.0F, 0.10F * (1.0F - ph));
        }
    }

    /** La luz de las cascadas (luz que suma; el agua tiene su shader): la espuma al pie y las ondas del rellano y del estanque. */
    public static void waterfallLights(VertexConsumer vc, View v, SanctuaryScene sc, float t) {
        float[] f = sc.anchor("fall");
        float fz = sc.anchor("fall_z")[0] + 0.3F;
        float[] pond = sc.anchor("pond");
        if (f[2] <= f[3]) return;
        // la bruma del pie de cada cascada (el dueño: que la unión con el agua de abajo no se vea cortada): nubecitas
        // blancas que suben delante del pie, en la cascada alta, en cada salto de la escalera y en los chorros de los lados
        for (int i = 0; i < 18; i++) {
            float ph = frac(t * 0.07F + hash(i, 241));
            float x = mix(f[0], f[1], hash(i, 243));
            glow(vc, v, x, f[3] - 0.05F + ph * 0.6F, fz + 0.2F + ph * 0.6F, 0.40F + ph * 0.5F, 0.85F, 1.0F, 1.0F,
                    0.40F * (1.0F - ph) * Math.min(1.0F, ph * 5.0F));
        }
        float[] st = sc.anchor("stairs");
        for (int k = 0; k <= (int)st[2]; k++) {
            for (int i = 0; i < 10; i++) {
                float ph = frac(t * 0.09F + hash(i + k * 17, 245));
                float x = mix(-st[3], st[3] + 1.0F, hash(i + k * 17, 247));
                glow(vc, v, x, k + 0.80F + ph * 0.4F, st[0] - k + 0.15F + ph * 0.35F, 0.22F + ph * 0.3F, 0.85F, 1.0F,
                        1.0F, 0.32F * (1.0F - ph) * Math.min(1.0F, ph * 5.0F));
            }
        }
        for (int o = 0; o < 4; o++) {
            float ph = frac(t * 0.02F + o / 4.0F);
            ring(vc, v, (f[0] + f[1]) * 0.5F, pond[1] + 0.012F, fz + 0.9F, 0.4F + ph * 2.2F, 0.04F, 32, 0.9F, 0.97F, 1.0F, 0.2F * (1.0F - ph));
        }
        // las fuentes de las peñas: espuma y ondas donde cae su chorro
        for (int k = 0; k < 2; k++) {
            float[] s = sc.anchor("spring" + k);
            if (s[3] <= 0) continue;
            for (int i = 0; i < 16; i++) {
                float ph = frac(t * 0.08F + hash(i + k * 13, 251));
                float ang = hash(i + k * 13, 253) * 6.2832F;
                float x = s[0] + (hash(i + k * 13, 255) - 0.5F) * 1.6F;
                glow(vc, v, x + (float)Math.cos(ang) * 0.4F * ph, s[1] - 0.05F + ph * 0.6F, s[2] + 0.4F + (float)Math.sin(ang) * 0.3F * ph
                        + ph * 0.3F, 0.40F + ph * 0.5F, 0.85F, 1.0F, 1.0F, 0.42F * (1.0F - ph) * Math.min(1.0F, ph * 5.0F));
            }
            for (int o = 0; o < 3; o++) {
                float ph = frac(t * 0.025F + o / 3.0F + k * 0.3F);
                ring(vc, v, s[0], s[1] + 0.012F, s[2], 0.25F + ph * 1.1F, 0.035F, 24, 0.9F, 0.97F, 1.0F, 0.18F * (1.0F - ph));
            }
        }
    }


    /** El tipo de capa de cada efecto, para quien los junte. */
    public static RenderType glowType() {
        return SceneRenderTypes.GLOW_ADD;
    }

    public static RenderType raysType() {
        return SceneRenderTypes.RAYS;
    }

    public static RenderType softType() {
        return SceneRenderTypes.SOFT;
    }

    public static RenderType waterType() {
        return SceneRenderTypes.WATER;
    }
}
