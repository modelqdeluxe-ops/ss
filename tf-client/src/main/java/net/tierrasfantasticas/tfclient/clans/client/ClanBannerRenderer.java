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
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.clans.ClanBannerBlock;
import net.tierrasfantasticas.tfclient.clans.ClanBannerBlockEntity;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanTagFx;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * (1.3.159) El estandarte del clan: un asta de madera oscura con remate dorado, un travesaño dorado y una tela que ondea
 * con el viento (cuanto más abajo, más se mueve), del color del clan con su ribete y dos puntas abajo; la insignia del
 * clan en grande (la de ahora, de ClanLooks: si la cambian, cambia aquí) y su etiqueta con su efecto (degradado,
 * arcoíris u onda, animados). Por detrás se ve igual, sin salir del revés.
 */
public class ClanBannerRenderer implements BlockEntityRenderer<ClanBannerBlockEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation(TFClient.MOD_ID, "textures/misc/blanco.png");
    private static final int NX = 12, NY = 16;
    /** La tela: de arriba (bajo el travesaño) a abajo, ancho y dónde va respecto al asta. */
    private static final float TOP = 2.34F, BOTTOM = 0.86F, HALF = 0.5F, Z0 = 0.10F;
    private final Font font;

    public ClanBannerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(ClanBannerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ClanLooks.Look look = ClanLooks.clan(be.clan());
        int rgb = look == null ? 0x8A8F9C : look.rgb();
        double t = ((be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partial) / 20.0 + (be.getBlockPos().asLong() % 97) * 0.37;
        int lit = LightTexture.pack(Math.max(6, LightTexture.block(light)), LightTexture.sky(light));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        int rot = be.getBlockState().hasProperty(ClanBannerBlock.ROTATION) ? be.getBlockState().getValue(ClanBannerBlock.ROTATION) : 0;
        pose.mulPose(Axis.YP.rotationDegrees(-rot * 360F / 16F));
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        // el asta, el remate y el travesaño
        box(pose, solid, -0.045F, 0F, -0.045F, 0.045F, TOP + 0.24F, 0.045F, 0x5A3820, lit);
        box(pose, solid, -0.075F, TOP + 0.24F, -0.075F, 0.075F, TOP + 0.38F, 0.075F, 0xF2C14E, LightTexture.FULL_BRIGHT);
        box(pose, solid, -HALF - 0.08F, TOP + 0.01F, Z0 - 0.04F, HALF + 0.08F, TOP + 0.08F, Z0 + 0.03F, 0xC9962E, lit);
        box(pose, solid, -HALF - 0.13F, TOP - 0.015F, Z0 - 0.06F, -HALF - 0.05F, TOP + 0.10F, Z0 + 0.05F, 0xF2C14E, lit);
        box(pose, solid, HALF + 0.05F, TOP - 0.015F, Z0 - 0.06F, HALF + 0.13F, TOP + 0.10F, Z0 + 0.05F, 0xF2C14E, lit);
        // la tela: el ribete (más oscuro, con un toque dorado) y encima el paño del color del clan
        int trim = mix(darken(rgb, 0.45F), 0xC9962E, 0.25F);
        cloth(pose, solid, t, 0F, 0F, trim, lit, 0F);
        cloth(pose, solid, t, 0.05F, 0.05F, rgb, lit, 0.005F);
        cloth(pose, solid, t, 0.05F, 0.05F, rgb, lit, -0.005F);
        // la insignia, por delante y por detrás
        if (look != null) {
            VertexConsumer badge = buffers.getBuffer(RenderType.entityCutoutNoCull(look.badgeTexture()));
            float size = 0.70F, cy = TOP - 0.50F;
            badge(pose, badge, t, cy, size, lit, 0.011F, false);
            badge(pose, badge, t, cy, size, lit, -0.011F, true);
            // la etiqueta, debajo, con su efecto: letra a letra sobre la onda de la tela (si fuera plana, la tela la tapaba
            // a trozos al ondear)
            String tag = "[" + look.tag() + "]";
            double now = ClanClient.now();
            float ty = TOP - 0.98F, v = (TOP - ty) / (TOP - BOTTOM);
            float k = 0.020F;
            int[] cps = tag.codePoints().toArray();
            float total = font.width(Component.literal(tag).withStyle(s -> s.withBold(true))) * k;
            for (int side = 0; side < 2; side++) {
                float x = -total / 2F;
                for (int i = 0; i < cps.length; i++) {
                    String ch = new String(Character.toChars(cps[i]));
                    float cw = font.width(net.minecraft.network.chat.Component.literal(ch).withStyle(s -> s.withBold(true))) * k;
                    float cx = x + cw / 2F;
                    // la u de esta letra en la tela (por detrás, reflejada)
                    float u = ((side == 0 ? cx : -cx) + HALF) / (2 * HALF);
                    float dz = wave(t, u, v);
                    int col = ClanTagFx.colorAt(look.fx(), i, cps.length, now);
                    Component one = Component.literal(ch).withStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true)
                            .withColor(net.minecraft.network.chat.TextColor.fromRgb(col)));
                    // la pendiente de la tela ahí (dz/dx; por detrás sale la misma): la letra se inclina con ella
                    float slope = (wave(t, u + 0.01F, v) - wave(t, u - 0.01F, v)) / (0.02F * 2 * HALF);
                    pose.pushPose();
                    if (side == 1) pose.mulPose(Axis.YP.rotationDegrees(180));
                    pose.translate(cx, ty, side == 0 ? Z0 + dz + 0.016F : -(Z0 + dz) + 0.016F);
                    pose.mulPose(Axis.YP.rotation((float) -Math.atan(slope)));
                    pose.scale(-k, -k, k);
                    pose.mulPose(Axis.YP.rotationDegrees(180));
                    font.drawInBatch(one, -cw / k / 2F, -4F, 0xFFFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                            LightTexture.FULL_BRIGHT);
                    pose.popPose();
                    x += cw;
                }
            }
        }
        pose.popPose();
    }

    /** Lo que ondea la tela en (u de 0 a 1 de izquierda a derecha, v de 0 a 1 de arriba abajo). */
    private static float wave(double t, float u, float v) {
        double amp = 0.012 + 0.085 * v;
        return (float) (amp * Math.sin(t * 2.4 + v * 4.3 + u * 2.1) + 0.02 * v * Math.sin(t * 1.3 + u * 5.0));
    }

    /** La tela, recortada inset por los lados y arriba (y con sus dos puntas abajo), a dz delante. */
    private static void cloth(PoseStack pose, VertexConsumer vc, double t, float insetX, float insetY, int rgb, int light, float dz) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x0 = -HALF + insetX, x1 = HALF - insetX, y0 = TOP - insetY, y1 = BOTTOM + insetY * 0.6F;
        float notch = 0.20F;
        for (int j = 0; j < NY; j++) {
            float va = j / (float) NY, vb = (j + 1) / (float) NY;
            for (int i = 0; i < NX; i++) {
                float ua = i / (float) NX, ub = (i + 1) / (float) NX;
                float[] xs = {lerp(x0, x1, ua), lerp(x0, x1, ub), lerp(x0, x1, ub), lerp(x0, x1, ua)};
                float[] us = {ua, ub, ub, ua};
                float[] vs = {va, va, vb, vb};
                float[] ys = new float[4];
                for (int k = 0; k < 4; k++) {
                    // abajo, una V en el centro: las dos puntas
                    float y = lerp(y0, y1, vs[k]);
                    if (vs[k] > 0.999F) y += notch * (1 - Math.abs(us[k] - 0.5F) * 2);
                    ys[k] = y;
                }
                for (int k = 0; k < 4; k++) {
                    float shade = 1.06F - 0.22F * vs[k];
                    int c = scale(rgb, shade);
                    // la onda según el sitio de verdad en la tela (no el de esta capa): el ribete, el paño, la insignia y la
                    // etiqueta ondean igual y no se cruzan (antes el ribete asomaba en picos oscuros y tapaba letras)
                    float z = Z0 + wave(t, (xs[k] + HALF) / (2 * HALF), (TOP - ys[k]) / (TOP - BOTTOM)) + dz;
                    vc.vertex(m, xs[k], ys[k], z).color(c >> 16 & 255, c >> 8 & 255, c & 255, 255).uv(0.5F, 0.5F)
                            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
                }
            }
        }
    }

    /** La insignia sobre la tela, siguiendo su onda (back: por detrás, reflejada para que se lea bien). */
    private static void badge(PoseStack pose, VertexConsumer vc, double t, float cy, float size, int light, float dz, boolean back) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int steps = 6;
        float x0 = -size / 2, y0 = cy + size / 2;
        for (int j = 0; j < steps; j++) {
            for (int i = 0; i < steps; i++) {
                float[] fu = {i / (float) steps, (i + 1) / (float) steps, (i + 1) / (float) steps, i / (float) steps};
                float[] fv = {j / (float) steps, j / (float) steps, (j + 1) / (float) steps, (j + 1) / (float) steps};
                for (int k = 0; k < 4; k++) {
                    float x = x0 + fu[k] * size, y = y0 - fv[k] * size;
                    float u = (x + HALF) / (2 * HALF), v = (TOP - y) / (TOP - BOTTOM);
                    float z = Z0 + wave(t, u, v) + dz;
                    float tu = back ? 1 - fu[k] : fu[k];
                    vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(tu, fv[k]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                            .normal(n, 0, 0, back ? -1 : 1).endVertex();
                }
            }
        }
    }

    /** Una caja de un color. */
    private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, 0.9F},
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, 0.9F},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, 0.75F},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, 0.75F},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, 1F},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, 0.55F}};
        for (float[] f : faces) {
            int c = scale(rgb, f[15]);
            for (int k = 0; k < 4; k++) {
                vc.vertex(m, f[k * 3], f[k * 3 + 1], f[k * 3 + 2]).color(c >> 16 & 255, c >> 8 & 255, c & 255, 255).uv(0.5F, 0.5F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, f[12], f[13], f[14]).endVertex();
            }
        }
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)),
                b = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    static int darken(int rgb, float k) {
        return scale(rgb, 1 - k);
    }

    static int mix(int a, int b, float f) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * f);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * f);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return r << 16 | g << 8 | bl;
    }

    @Override
    public boolean shouldRenderOffScreen(ClanBannerBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
