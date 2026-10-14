package net.tierrasfantasticas.tfclient.skills;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Lo que las mecánicas de MythicMobs ponen en la pantalla del jugador (lo manda {@link SkillNet.Screen}): la pantalla
 * negra (blackscreen), el borde rojo de «cerca del borde del mundo» (bloodyscreen, como lo dibuja Minecraft: solo con
 * gráficos detallados) y los avisos de logro (sendtoast). Se dibuja encima de todo el HUD (ver SkillHud).
 */
public final class SkillScreen {
    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    /** Hasta qué tick del mundo (del cliente) dura cada efecto. */
    private static long blackUntil;
    private static long bloodUntil;

    private SkillScreen() {}

    static void handle(SkillNet.Screen m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (m.kind()) {
            case "black" -> blackUntil = m.n() <= 0 ? 0 : now + m.n();
            case "blood" -> bloodUntil = m.n() <= 0 ? 0 : now + m.n();
            case "toast" -> toast(mc, m.a(), m.b(), m.n());
            default -> { }
        }
    }

    /** El aviso de logro de Minecraft con ese icono y ese texto (como el logro falso que manda MythicMobs). */
    private static void toast(Minecraft mc, String icon, String text, int frame) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(icon);
            Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            ItemStack stack = new ItemStack(item == null || item == Items.AIR ? Items.DIAMOND_SWORD : item);
            FrameType type = frame == 0 ? FrameType.TASK : frame == 2 ? FrameType.GOAL : FrameType.CHALLENGE;
            DisplayInfo info = new DisplayInfo(stack, Component.literal(text.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")),
                    Component.empty(), null, type, true, false, false);
            Advancement adv = new Advancement(new ResourceLocation(TFClient.MOD_ID, "aviso"), null, info, AdvancementRewards.EMPTY,
                    Map.of(), new String[0][], false);
            mc.getToasts().addToast(new AdvancementToast(adv));
        } catch (RuntimeException e) {
            TFClient.LOGGER.debug("TF Skills: no se pudo enseñar el aviso", e);
        }
    }

    /** Encima del HUD: el borde rojo y la pantalla negra, si tocan. */
    static void draw(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now < bloodUntil && Minecraft.useFancyGraphics()) {
            // Como Gui.renderVignette cuando el jugador está pegado al borde del mundo: el viñeteado en rojo del todo
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            g.setColor(0F, 1F, 1F, 1F);
            g.blit(VIGNETTE, 0, 0, -90, 0F, 0F, width, height, width, height);
            g.setColor(1F, 1F, 1F, 1F);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        }
        if (now < blackUntil) g.fill(0, 0, width, height, 0xFF000000);
    }
}
