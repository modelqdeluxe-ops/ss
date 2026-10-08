package net.tierrasfantasticas.tfclient.items;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Contador del planeo con alas, a la derecha del último hueco de la barra (sin tapar la mano secundaria ni el
 * indicador de ataque): solo mientras planeas con unas alas de los sets (o si ya gastaste el planeo y sigues en el
 * aire). Un hueco como el de la barra con los segundos que quedan y una barrita que se vacía (verde → ámbar → rojo).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TFGlideHud {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

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

        // Lo que ya ocupa el lado derecho de la barra: la mano secundaria (zurdos) y el indicador de ataque
        int x = width / 2 + 91 + 6;
        if (player.getMainArm() == HumanoidArm.LEFT && !player.getOffhandItem().isEmpty()) x += 29;
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) x += 24;
        int y = height - 23;

        RenderSystem.enableBlend();
        // Marco del hueco de la mano secundaria (29×24) como el de la barra
        g.blit(WIDGETS, x, y, 53, 22, 29, 24);
        int inner = 0xC0000000;
        g.fill(x + 3, y + 3, x + 26, y + 21, inner);

        // Segundos que quedan (o «0» en rojo si ya se gastó y sigues en el aire)
        Font font = mc.font;
        String text = spent ? "0" : left >= 9.95f * 20 ? "10" : String.format(java.util.Locale.ROOT, "%.1f", left / 20f);
        int color = spent ? 0xFF5A5A : frac > 0.5f ? 0x7CFFB2 : frac > 0.2f ? 0xFFD35A : 0xFF6B6B;
        float scale = text.length() > 3 ? 0.75f : 1f;
        g.pose().pushPose();
        g.pose().translate(x + 14.5f, y + 6f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, true);
        g.pose().popPose();

        // Barrita que se vacía
        int barX = x + 5, barY = y + 16, barW = 19;
        g.fill(barX, barY, barX + barW, barY + 2, 0xFF2A2A2A);
        int filled = Math.round(barW * frac);
        if (filled > 0) g.fill(barX, barY, barX + filled, barY + 2, 0xFF000000 | color);
        RenderSystem.disableBlend();
    }
}
