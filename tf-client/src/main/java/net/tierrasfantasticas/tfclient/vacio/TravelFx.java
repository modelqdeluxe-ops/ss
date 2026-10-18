package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * La animación del viaje en la pantalla: un fundido. Al salir ({@link VoidTravel#LEAVE_TICKS}), mientras las espirales
 * de luz suben alrededor (partículas en el mundo, {@link VoidTravel}), la pantalla se va llenando de luz blanca cálida;
 * al llegar, ese blanco se abre despacio (con un tinte lila si llegas al Void).
 *
 * <p>1.3.89, el dueño: «esas franjas o círculos que se ponen en ambas está horrible; si vas a poner eso, mejor no pongas
 * nada si no lo harás bien». Fuera los rayos que giraban, la viñeta de franjas desde los bordes y la onda que se abría:
 * solo el fundido, a pantalla entera y con curva suave (sin escalones).
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
        float white;
        int tint;
        if (phase == VoidNet.FX_LEAVE) {
            // la luz sube en la segunda mitad y al final lo cubre todo
            white = smooth(0.35F, 0.97F, k);
            tint = 0xFFF8EC;
        } else {
            white = 1F - smooth(0.0F, 0.85F, k);
            tint = TFVoid.isVoid(mc.level) ? 0xF1E6FF : 0xFFF6E4;
        }
        if (white <= 0.004F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.fill(0, 0, w, h, ((int) (white * 255) << 24) | tint);
        RenderSystem.disableBlend();
    }

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0F, 1F);
        return t * t * (3 - 2 * t);
    }
}
