/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanTagFx;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * (1.3.160) Lo que dibujan los emotes del clan (ClanEmotes): luz que se suma (brillos, rayos, haces, estrellas, aros),
 * piezas de color (confeti, la corona, el estandarte), la insignia del clan y su etiqueta (solo las letras, con una
 * estrella que titila a cada lado: el dueño, «los corchetes son para el chat y el TAB; en el emote, solo las letras con
 * un efecto a los lados»).
 * <p>Cada función pide su búfer justo antes de escribir: con MultiBufferSource, pedir otro tipo de dibujo cierra el
 * anterior (en la 1.3.159 los brillos se escribían en un búfer ya cerrado y salían como manchas oscuras).
 */
final class ClanEmoteFx {
    static final ResourceLocation GLOW = tex("brillo");
    static final ResourceLocation RAY = tex("rayo");
    static final ResourceLocation STAR = tex("estrella");
    static final ResourceLocation BEAM = tex("haz");
    static final ResourceLocation WHITE = tex("blanco");
    static final ResourceLocation HEART = tex("corazon");

    private ClanEmoteFx() {}

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(TFClient.MOD_ID, "textures/misc/" + name + ".png");
    }

    /** Luz que se suma (el negro no pinta). */
    static VertexConsumer light(MultiBufferSource b, ResourceLocation tex) {
        return b.getBuffer(RenderType.eyes(tex));
    }

    /** Color liso, a plena luz (confeti, corona, tela). */
    static VertexConsumer solid(MultiBufferSource b) {
        return b.getBuffer(RenderType.entityCutoutNoCull(WHITE));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Colores
    // ---------------------------------------------------------------------------------------------------------------

    static int scale(int rgb, float k) {
        k = Math.max(0, k);
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)),
                b = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * f);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * f);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return r << 16 | g << 8 | bl;
    }

    /** Los colores del clan para los efectos (los de su etiqueta; en arcoíris, el arcoíris; y dorado y blanco). */
    static int[] palette(ClanLooks.Look look) {
        if (look == null) return new int[] {0xFFD24A, 0xFFFFFF, 0xFFB030};
        if (look.fx().mode().equals(ClanTagFx.RAINBOW)) return new int[] {0xFF5050, 0xFFC040, 0x60FF70, 0x50C8FF, 0xB070FF, 0xFFFFFF};
        int[] c = look.fx().colors();
        int[] out = new int[c.length + 2];
        System.arraycopy(c, 0, out, 0, c.length);
        out[c.length] = 0xFFD24A;
        out[c.length + 1] = 0xFFFFFF;
        return out;
    }

    static int main(ClanLooks.Look look) {
        return look == null ? 0xFFD24A : look.rgb();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Piezas
    // ---------------------------------------------------------------------------------------------------------------

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int rgb, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }

    /** Un cuadrado de w x h centrado en (x, y, z), en el plano XY del sistema actual, por las dos caras. */
    static void quad(PoseStack pose, VertexConsumer vc, float x, float y, float z, float w, float h, int rgb) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = x - w / 2, x1 = x + w / 2, y0 = y - h / 2, y1 = y + h / 2;
        vertex(vc, m, n, x0, y0, z, 0, 1, rgb, 0, 0, 1);
        vertex(vc, m, n, x1, y0, z, 1, 1, rgb, 0, 0, 1);
        vertex(vc, m, n, x1, y1, z, 1, 0, rgb, 0, 0, 1);
        vertex(vc, m, n, x0, y1, z, 0, 0, rgb, 0, 0, 1);
        vertex(vc, m, n, x0, y1, z, 0, 0, rgb, 0, 0, -1);
        vertex(vc, m, n, x1, y1, z, 1, 0, rgb, 0, 0, -1);
        vertex(vc, m, n, x1, y0, z, 1, 1, rgb, 0, 0, -1);
        vertex(vc, m, n, x0, y0, z, 0, 1, rgb, 0, 0, -1);
    }

    /** Un cuadrado tumbado (en el suelo o paralelo a él) a la altura y, de lado size, girado ang grados. */
    static void flat(PoseStack pose, VertexConsumer vc, double x, double y, double z, float size, float ang, int rgb) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(ang));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        quad(pose, vc, 0, 0, 0, size, size, rgb);
        pose.popPose();
    }

    /** De cara a la cámara en (x, y, z) del sistema actual (que no debe estar girado). */
    static void billboard(PoseStack pose, VertexConsumer vc, double x, double y, double z, float size, int rgb) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        quad(pose, vc, 0, 0, 0, size, size, rgb);
        pose.popPose();
    }

    /** Una estrella que titila (cara a la cámara), girada ang grados. */
    static void star(PoseStack pose, MultiBufferSource b, double x, double y, double z, float size, float ang, int rgb) {
        VertexConsumer vc = light(b, STAR);
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.mulPose(Axis.ZP.rotationDegrees(ang));
        quad(pose, vc, 0, 0, 0, size, size, rgb);
        pose.popPose();
    }

    /** Un aro en el plano horizontal a la altura y (radio, grosor), en seg trozos. */
    static void ring(PoseStack pose, VertexConsumer vc, double y, double radius, double thick, int rgb, int seg) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float yy = (float) y;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            double ri = Math.max(0, radius - thick / 2), ro = radius + thick / 2;
            float[][] p = {
                    {(float) (Math.cos(a0) * ri), (float) (Math.sin(a0) * ri), 0.5F, 0.5F},
                    {(float) (Math.cos(a0) * ro), (float) (Math.sin(a0) * ro), 0.5F, 0F},
                    {(float) (Math.cos(a1) * ro), (float) (Math.sin(a1) * ro), 0.5F, 0F},
                    {(float) (Math.cos(a1) * ri), (float) (Math.sin(a1) * ri), 0.5F, 0.5F}};
            for (int k = 0; k < 4; k++) vertex(vc, m, n, p[k][0], yy, p[k][1], p[k][2], p[k][3], rgb, 0, 1, 0);
            for (int k = 3; k >= 0; k--) vertex(vc, m, n, p[k][0], yy, p[k][1], p[k][2], p[k][3], rgb, 0, -1, 0);
        }
    }

    /** Un trozo de luz de a a b (dos cuadrados cruzados a lo largo), de ancho w. Para rayos, chispas y haces. */
    static void segment(PoseStack pose, VertexConsumer vc, double ax, double ay, double az, double bx, double by, double bz, float w, int rgb) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) return;
        pose.pushPose();
        pose.translate((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
        // del eje Y a la dirección del trozo
        org.joml.Vector3f dir = new org.joml.Vector3f((float) (dx / len), (float) (dy / len), (float) (dz / len));
        pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), dir));
        quad(pose, vc, 0, 0, 0, w, (float) len, rgb);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        quad(pose, vc, 0, 0, 0, w, (float) len, rgb);
        pose.popPose();
    }

    /** Un rayo zigzagueante de a a b (n tramos; cambia de forma con seed), con su halo del color y el núcleo blanco. */
    static void bolt(PoseStack pose, MultiBufferSource b, double ax, double ay, double az, double bx, double by, double bz, int n, long seed,
                     float width, int rgb, float k) {
        RandomSource r = RandomSource.create(seed);
        double[] px = new double[n + 1], py = new double[n + 1], pz = new double[n + 1];
        double spread = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az)) * 0.06;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double j = i == 0 || i == n ? 0 : spread * (1 - Math.abs(f - 0.5));
            px[i] = ax + (bx - ax) * f + (r.nextDouble() - 0.5) * 2 * j;
            py[i] = ay + (by - ay) * f + (r.nextDouble() - 0.5) * j * 0.5;
            pz[i] = az + (bz - az) * f + (r.nextDouble() - 0.5) * 2 * j;
        }
        VertexConsumer halo = light(b, RAY);
        for (int i = 0; i < n; i++) segment(pose, halo, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], width * 3.2F, scale(rgb, 0.9F * k));
        VertexConsumer core = light(b, RAY);
        for (int i = 0; i < n; i++) segment(pose, core, px[i], py[i], pz[i], px[i + 1], py[i + 1], pz[i + 1], width, scale(0xFFFFFF, k));
    }

    /** Una caja de un color (para la corona, el asta…), con sombra suave por cara. */
    static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, 0.92F},
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, 0.80F},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, 0.74F},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, 0.74F},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, 1F},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, 0.55F}};
        for (float[] f : faces) {
            int c = scale(rgb, f[15]);
            for (int k = 0; k < 4; k++) vertex(vc, m, n, f[k * 3], f[k * 3 + 1], f[k * 3 + 2], 0.5F, 0.5F, c, f[12], f[13], f[14]);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La insignia y la etiqueta
    // ---------------------------------------------------------------------------------------------------------------

    /** La insignia del clan de lado size, en el plano XY del sistema actual (por las dos caras, sin verse al revés). */
    static void badge(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, float size) {
        if (look == null) return;
        VertexConsumer vc = b.getBuffer(RenderType.entityTranslucentEmissive(look.badgeHd()));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float h = size / 2;
        int w = 0xFFFFFF;
        vertex(vc, m, n, -h, -h, 0, 1, 1, w, 0, 0, 1);
        vertex(vc, m, n, h, -h, 0, 0, 1, w, 0, 0, 1);
        vertex(vc, m, n, h, h, 0, 0, 0, w, 0, 0, 1);
        vertex(vc, m, n, -h, h, 0, 1, 0, w, 0, 0, 1);
        // la otra cara con la misma textura en el mismo sitio (si el dibujo no descarta caras, no pelean entre ellas)
        vertex(vc, m, n, -h, h, 0, 1, 0, w, 0, 0, -1);
        vertex(vc, m, n, h, h, 0, 0, 0, w, 0, 0, -1);
        vertex(vc, m, n, h, -h, 0, 0, 1, w, 0, 0, -1);
        vertex(vc, m, n, -h, -h, 0, 1, 1, w, 0, 0, -1);
    }

    /** La insignia de cara a la cámara en (x, y, z) (sistema sin girar). */
    static void badgeFacing(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, double x, double y, double z, float size, float spinDeg) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        if (spinDeg != 0) pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
        badge(pose, b, look, size);
        pose.popPose();
    }

    /**
     * La etiqueta del clan de cara a la cámara en (x, y, z) (sistema sin girar): solo las letras (sin corchetes), con su
     * efecto en este instante, un borde oscuro para que se lea sobre cualquier cosa, y una estrella que titila a cada lado.
     * k es el tamaño (0,025 como el nombre de encima de la cabeza).
     */
    static void tag(PoseStack pose, MultiBufferSource b, ClanLooks.Look look, double x, double y, double z, float k, double now) {
        if (look == null || k <= 0.0005F) return;
        Font font = Minecraft.getInstance().font;
        String tag = look.tag();
        int[] cps = tag.codePoints().toArray();
        net.minecraft.network.chat.MutableComponent text = Component.empty();
        for (int i = 0; i < cps.length; i++) {
            int c = ClanTagFx.colorAt(look.fx(), i, cps.length, now);
            text.append(Component.literal(new String(Character.toChars(cps[i]))).withStyle(Style.EMPTY.withBold(true).withColor(TextColor.fromRgb(c))));
        }
        float tw = font.width(text);
        int outline = 0xFF000000 | scale(look.rgb(), 0.18F);
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        // las estrellas de los lados (del color de la letra de ese lado)
        for (int end = 0; end < 2; end++) {
            int col = mix(ClanTagFx.colorAt(look.fx(), end == 0 ? 0 : cps.length - 1, cps.length, now), 0xFFFFFF, 0.3F);
            float tw2 = (float) (0.75 + 0.25 * Math.sin(now * 5.0 + end * 2.3));
            // en este sistema (el de la cámara) +x es la izquierda de la pantalla: la primera letra queda a ese lado
            float sx = (end == 0 ? 1 : -1) * (tw * k / 2 + 9 * k);
            VertexConsumer vc = light(b, STAR);
            pose.pushPose();
            pose.translate(sx, 0, 0);
            pose.mulPose(Axis.ZP.rotationDegrees((float) (now * 40 * (end == 0 ? 1 : -1))));
            quad(pose, vc, 0, 0, 0, 16 * k * tw2, 16 * k * tw2, col);
            quad(pose, vc, 0, 0, 0.001F, 7 * k * tw2, 7 * k * tw2, 0xFFFFFF);
            pose.popPose();
        }
        pose.scale(-k, -k, k);
        font.drawInBatch8xOutline(text.getVisualOrderText(), -tw / 2F, -4F, 0xFFFFFFFF, outline, pose.last().pose(), b, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
