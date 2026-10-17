package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix4f;

/**
 * La animación del viaje en la pantalla. Al salir ({@link VoidTravel#LEAVE_TICKS}): rayos de luz dorada que giran desde
 * el centro, una viñeta de oro que se cierra desde los bordes y, al final, todo se llena de luz blanca. Al llegar: el
 * blanco se abre (teñido del morado del vacío si llegas al Void) con una onda de luz que se expande.
 */
@OnlyIn(Dist.CLIENT)
public final class TravelFx {
    private static int phase = -1, ticks = 1;
    private static long startTick;

    private TravelFx() {}

    static void start(int ph, int t) {
        Minecraft mc = Minecraft.getInstance();
        if (ph == VoidNet.FX_CANCEL) {
            phase = -1;
            return;
        }
        phase = ph;
        ticks = Math.max(1, t);
        startTick = mc.level == null ? 0 : mc.level.getGameTime();
        // El diálogo se cierra para que se vea
        if (ph == VoidNet.FX_ARRIVE && mc.screen instanceof KnightScreen) mc.setScreen(null);
    }

    /** ¿Está saliendo ahora? (para la prueba en el juego) */
    public static boolean active() {
        return phase >= 0;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (phase < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            phase = -1;
            return;
        }
        float k = (mc.level.getGameTime() - startTick + partial) / ticks;
        if (k < 0) k = 0;
        if (k >= 1F && phase == VoidNet.FX_ARRIVE) {
            phase = -1;
            return;
        }
        k = Math.min(k, 1F);
        float time = mc.level.getGameTime() + partial;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (phase == VoidNet.FX_LEAVE) {
            float rays = smooth(0.05F, 0.7F, k);
            rays(g, w, h, time, rays * 0.55F, 0xFFE6A8);
            vignette(g, w, h, smooth(0F, 0.9F, k), 0xFFD27A);
            float white = smooth(0.62F, 0.97F, k);
            g.fill(0, 0, w, h, ((int) (white * 255) << 24) | 0xFFF8EC);
        } else {
            boolean toVoid = net.tierrasfantasticas.tfclient.vacio.TFVoid.isVoid(mc.level);
            float white = 1F - smooth(0.05F, 0.75F, k);
            int tint = toVoid ? 0xE9D6FF : 0xFFF4DA;
            g.fill(0, 0, w, h, ((int) (white * 255) << 24) | tint);
            ring(g, w, h, k, toVoid ? 0xB98CFF : 0xFFD98A);
            vignette(g, w, h, (1F - k) * 0.8F, toVoid ? 0x8A4DFF : 0xFFC86A);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /** Rayos que salen del centro y giran despacio. */
    private static void rays(GuiGraphics g, int w, int h, float time, float alpha, int rgb) {
        if (alpha <= 0.01F) return;
        Matrix4f m = g.pose().last().pose();
        float cx = w / 2F, cy = h / 2F, len = (float) Math.hypot(w, h);
        int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int n = 18;
        for (int i = 0; i < n; i++) {
            float a = time * 0.012F + i * Mth.TWO_PI / n;
            float wid = 0.05F + 0.035F * Mth.sin(i * 2.3F + time * 0.05F);
            float x1 = cx + Mth.cos(a - wid) * len, y1 = cy + Mth.sin(a - wid) * len;
            float x2 = cx + Mth.cos(a + wid) * len, y2 = cy + Mth.sin(a + wid) * len;
            buf.vertex(m, cx, cy, 0).color(r, gg, b, (int) (alpha * 255)).endVertex();
            buf.vertex(m, x1, y1, 0).color(r, gg, b, 0).endVertex();
            buf.vertex(m, x2, y2, 0).color(r, gg, b, 0).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
        RenderSystem.defaultBlendFunc();
    }

    /** Una viñeta de color que entra desde los bordes. */
    private static void vignette(GuiGraphics g, int w, int h, float amount, int rgb) {
        if (amount <= 0.01F) return;
        int a = (int) (Mth.clamp(amount, 0, 1) * 235) << 24;
        int edge = a | rgb, none = rgb;
        int bh = (int) (h * (0.18F + 0.32F * amount)), bw = (int) (w * (0.14F + 0.3F * amount));
        g.fillGradient(0, 0, w, bh, edge, none);
        g.fillGradient(0, h - bh, w, h, none, edge);
        // los lados (girados 90°)
        g.pose().pushPose();
        g.pose().translate(0, h, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90F));
        g.fillGradient(0, 0, h, bw, edge, none);
        g.fillGradient(0, w - bw, h, w, none, edge);
        g.pose().popPose();
    }

    /** Una onda de luz que se abre desde el centro. */
    private static void ring(GuiGraphics g, int w, int h, float k, int rgb) {
        float radius = (float) Math.hypot(w, h) * 0.55F * smooth(0F, 1F, k);
        float thick = 10F + 30F * k;
        float alpha = (1F - k) * 0.8F;
        if (alpha <= 0.01F) return;
        Matrix4f m = g.pose().last().pose();
        float cx = w / 2F, cy = h / 2F;
        int r = (rgb >> 16) & 255, gg = (rgb >> 8) & 255, b = rgb & 255;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int n = 64;
        for (int i = 0; i <= n; i++) {
            float a = i * Mth.TWO_PI / n;
            float c = Mth.cos(a), s = Mth.sin(a);
            buf.vertex(m, cx + c * radius, cy + s * radius, 0).color(r, gg, b, (int) (alpha * 255)).endVertex();
            buf.vertex(m, cx + c * (radius + thick), cy + s * (radius + thick), 0).color(r, gg, b, 0).endVertex();
        }
        BufferUploader.drawWithShader(buf.end());
    }
}
