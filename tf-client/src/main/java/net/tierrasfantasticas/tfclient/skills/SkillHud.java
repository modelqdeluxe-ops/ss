package net.tierrasfantasticas.tfclient.skills;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.lwjgl.glfw.GLFW;

/**
 * La barra de la clase: una columna a la derecha de la pantalla con el icono de cada skill, su tecla y lo que le
 * falta para estar lista (se oscurece de abajo arriba y cuenta los segundos). Las teclas son propias y se cambian en
 * Opciones → Controles → «TF Skills» (por defecto R, G, Z, X, V, B, N y M).
 */
public final class SkillHud {
    public static final int SLOTS = 8;
    private static final int[] DEFAULT_KEYS = {GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_V,
            GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_M};
    public static final KeyMapping[] KEYS = new KeyMapping[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) {
            KEYS[i] = new KeyMapping("key.tfclient.skill." + (i + 1), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                    DEFAULT_KEYS[i], "key.categories.tfclient.skills");
        }
    }

    private static final int SIZE = 20;
    private static final int GAP = 3;

    private SkillHud() {}

    /** Mira las teclas cada tick y manda al servidor las que se pulsaron. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean has = !SkillClient.classId.isEmpty() && mc.player != null && mc.screen == null;
        for (int i = 0; i < SLOTS; i++) {
            while (KEYS[i].consumeClick()) {
                if (!has || i >= SkillClient.left.length) continue;
                if (SkillClient.left[i] > 0) {
                    if (i < SkillClient.denied.length) SkillClient.denied[i] = SkillClient.clientTicks;
                    continue;
                }
                SkillNet.CHANNEL.sendToServer(new SkillNet.Cast(i));
            }
        }
    }

    private static void draw(GuiGraphics g, int width, int height, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || SkillClient.classId.isEmpty() || !(mc.getCameraEntity() instanceof Player player) || player.isSpectator()) return;
        SkillDefs.ClassDef def = SkillDefs.get(SkillClient.classId);
        if (def == null) return;
        List<SkillDefs.SkillDef> actives = def.actives();
        int n = Math.min(Math.min(actives.size(), SkillClient.left.length), SLOTS);
        if (n == 0) return;
        Font font = mc.font;
        int colH = n * SIZE + (n - 1) * GAP;
        int x = width - SIZE - 6;
        int y0 = Math.max(4, (height - colH) / 2);
        RenderSystem.enableBlend();
        for (int i = 0; i < n; i++) {
            SkillDefs.SkillDef s = actives.get(i);
            int y = y0 + i * (SIZE + GAP);
            int left = SkillClient.left[i];
            int total = Math.max(1, SkillClient.total.length > i ? SkillClient.total[i] : 1);
            boolean ready = left <= 0;
            boolean flash = i < SkillClient.denied.length && SkillClient.clientTicks - SkillClient.denied[i] < 6;
            // Marco: dorado si está lista, apagado si no, rojo un momento si se pulsó sin estar lista
            int border = flash ? 0xFFE0524A : ready ? 0xFFE8C46A : 0xFF6B6250;
            g.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xB0000000);
            g.fill(x - 1, y - 1, x + SIZE + 1, y, border);
            g.fill(x - 1, y + SIZE, x + SIZE + 1, y + SIZE + 1, border);
            g.fill(x - 1, y, x, y + SIZE, border);
            g.fill(x + SIZE, y, x + SIZE + 1, y + SIZE, border);
            String icon = def.icons.get(s.id());
            if (icon != null) {
                ResourceLocation rl = ResourceLocation.tryParse(icon);
                if (rl != null) g.blit(rl, x + 1, y + 1, 0, 0, SIZE - 2, SIZE - 2, SIZE - 2, SIZE - 2);
            } else {
                g.drawCenteredString(font, s.name().substring(0, 1), x + SIZE / 2, y + 6, 0xFFFFFF);
            }
            if (!ready) {
                // Se oscurece lo que falta (de arriba abajo se va aclarando) y los segundos encima
                float frac = Math.min(1F, (left - partial) / total);
                int h = Math.round((SIZE - 2) * frac);
                g.fill(x + 1, y + 1 + (SIZE - 2 - h), x + SIZE - 1, y + SIZE - 1, 0xA0000000);
                String secs = left >= 20 ? String.valueOf((left + 19) / 20) : String.format(java.util.Locale.ROOT, "%.1f", left / 20F);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawCenteredString(font, secs, x + SIZE / 2, y + 6, 0xFFFFFF);
                g.pose().popPose();
            }
            // La tecla, a la izquierda
            String key = KEYS[i].getTranslatedKeyMessage().getString();
            if (key.length() > 3) key = key.substring(0, 3);
            int kw = font.width(key);
            g.fill(x - kw - 7, y + 5, x - 3, y + 15, 0x90000000);
            g.drawString(font, key, x - kw - 5, y + 6, ready ? 0xFFE8C46A : 0xFF9A9080, false);
        }
        RenderSystem.disableBlend();
    }

    /** Lo que se enseña de la clase al entrar (nombre y teclas). */
    static Component intro(SkillDefs.ClassDef def) {
        return Component.literal("✦ " + def.name).withStyle(ChatFormatting.AQUA);
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            for (KeyMapping k : KEYS) event.register(k);
        }

        @SubscribeEvent
        public static void onOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "tf_skills", (gui, g, partial, width, height) -> draw(g, width, height, partial));
        }

        /** Los modelos de los efectos no son de ningún objeto: se cargan aparte. */
        @SubscribeEvent
        public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
            try {
                for (ResourceLocation rl : SkillClient.itemModels()) event.register(rl);
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF Skills: no se pudieron registrar los modelos de los efectos", t);
            }
            SkillClient.clearCache();
        }
    }
}
