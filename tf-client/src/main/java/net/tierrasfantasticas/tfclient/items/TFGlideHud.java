package net.tierrasfantasticas.tfclient.items;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La duración del planeo con alas: solo una barrita fina bajo la mira que se vacía (verde → ámbar → rojo), mientras
 * planeas con unas alas de los sets (o en rojo apagado si ya gastaste el planeo y sigues en el aire).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TFGlideHud {
    private TFGlideHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "tf_glide", (gui, g, partial, width, height) -> draw(g, width, height, partial));
    }

    private static void draw(GuiGraphics g, int width, int height, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.getCameraEntity() instanceof Player player) || player.isSpectator()) return;
        boolean gliding = TFWings.glidingWithWings(player);
        boolean spent = !gliding && TFWings.spent(player);
        if (!gliding && !spent) return;

        float left = gliding ? Math.max(0, TFWings.GLIDE_TICKS - player.getFallFlyingTicks() - partial) : 0;
        float frac = Mth.clamp(left / TFWings.GLIDE_TICKS, 0f, 1f);

        // Una barrita fina bajo la mira (debajo también del indicador de ataque), sin números
        int barW = 30;
        int x = (width - barW) / 2, y = height / 2 + 16;
        int color = spent ? 0xFF5A5A : frac > 0.5f ? 0x7CFFB2 : frac > 0.2f ? 0xFFD35A : 0xFF6B6B;
        RenderSystem.enableBlend();
        g.fill(x - 1, y - 1, x + barW + 1, y + 3, 0x90000000);
        if (spent) g.fill(x, y, x + barW, y + 2, 0x60FF5A5A);
        int filled = Math.round(barW * frac);
        if (filled > 0) g.fill(x, y, x + filled, y + 2, 0xFF000000 | color);
        RenderSystem.disableBlend();
    }
}
