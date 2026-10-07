package net.tierrasfantasticas.tfclient.vfx;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Indicador de VFX junto al último slot de la barra (a la derecha, nunca encima del slot de la mano secundaria ni del
 * indicador de ataque): un hueco con el icono del paquete de skills equipado (con su cooldown, como el de los
 * objetos) y otro con el del efecto de kill. Si no hay nada equipado no se dibuja nada.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VfxHud {
    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    private VfxHud() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "tf_vfx", (gui, g, partial, width, height) -> draw(g, width, height));
    }

    private static void draw(GuiGraphics g, int width, int height) {
        String pack = VfxClient.hudPack, kill = VfxClient.hudKill;
        if (pack == null && kill == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.getCameraEntity() instanceof Player player) || mc.player == null || mc.player.isSpectator()) return;
        int x = width / 2 + 91;
        // Lo que ya ocupa el lado derecho de la barra: la mano secundaria (zurdos) y el indicador de ataque
        boolean leftHanded = player.getMainArm() == HumanoidArm.LEFT;
        if (leftHanded && !player.getOffhandItem().isEmpty()) x += 29;
        if (!leftHanded && mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) x += 24;
        int y = height - 23;
        if (pack != null) {
            slot(g, x, y, "pack_" + pack, cooldownFraction());
            x += 24;
        }
        if (kill != null) slot(g, x, y, "kill_" + kill, 0F);
    }

    /** Lo que le falta a la última skill usada (0 = lista). */
    private static float cooldownFraction() {
        int[] left = VfxClient.hudCooldowns, totals = VfxClient.hudTotals;
        if (left.length == 0 || totals.length == 0 || totals[0] <= 0) return 0F;
        return Math.min(1F, Math.max(0F, left[0] / (float) totals[0]));
    }

    private static void slot(GuiGraphics g, int x, int y, String icon, float cooldown) {
        // El mismo marco que el slot de la mano secundaria cuando está a la derecha de la barra
        g.blit(WIDGETS, x, y, 53, 22, 29, 24);
        int ix = x + 10, iy = y + 4;
        g.blit(new ResourceLocation(TFClient.MOD_ID, "textures/gui/vfx/" + icon + ".png"), ix, iy, 0, 0, 16, 16, 16, 16);
        if (cooldown > 0F) {
            int h = Math.round(16 * cooldown);
            g.fill(ix, iy + 16 - h, ix + 16, iy + 16, 0x7FFFFFFF);
        }
    }
}
