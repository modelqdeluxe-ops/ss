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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.lwjgl.glfw.GLFW;

/**
 * La barra de la clase: una fila pequeña a la derecha de la barra de objetos con el icono de cada skill, su tecla y lo
 * que le falta para estar lista.
 *
 * <p>Las skills van en las teclas 5, 6, 7, 8, 9 y 0 (las de los huecos de la barra que casi nadie usa): con una clase,
 * esas teclas ya no cambian de hueco, sino que <b>eligen</b> la skill (no la lanzan). La elegida sale con el siguiente
 * clic izquierdo (las clases de arco: al soltar una flecha con el arco) y deja de estar elegida. Pulsar otra vez la
 * misma tecla o cambiar de hueco (1-4, rueda) la suelta. Se cambian en Opciones → Controles → «TF Skills».
 */
public final class SkillHud {
    public static final int SLOTS = 6;
    private static final int[] DEFAULT_KEYS = {GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_9,
            GLFW.GLFW_KEY_0};
    public static final KeyMapping[] KEYS = new KeyMapping[SLOTS];

    /**
     * Activas en el juego como las de la barra, pero sin marcarse en rojo por compartir tecla con «Hueco 5»...: con una
     * clase las de skills tapan a las de la barra (ver {@link #preTick()}).
     */
    private static final IKeyConflictContext SKILL_CONTEXT = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };

    static {
        for (int i = 0; i < SLOTS; i++) {
            // Nombres nuevos (antes eran R, G, Z...): así a todos les salen las teclas 5-0 aunque ya hubieran guardado otras
            KEYS[i] = new KeyMapping("key.tfclient.skillslot." + (i + 1), SKILL_CONTEXT, InputConstants.Type.KEYSYM,
                    DEFAULT_KEYS[i], "key.categories.tfclient.skills");
        }
    }

    private static final int SIZE = 16;
    private static final int GAP = 2;

    /** La skill elegida (-1 = ninguna) y el hueco de la barra cuando se eligió (al cambiar de hueco se suelta). */
    static int selected = -1;
    private static int selectedHotbar = -1;
    /** ¿Estaba pulsado el clic izquierdo al acabar el tick anterior? (mantenerlo picando no lanza skills) */
    private static boolean attackWasDown;

    private SkillHud() {}

    private static boolean hasClass(Minecraft mc) {
        return !SkillClient.classId.isEmpty() && mc.player != null && SkillDefs.get(SkillClient.classId) != null;
    }

    /**
     * Antes de que el juego mire las teclas: las de skills eligen skill y, si comparten tecla con un hueco de la barra,
     * ese hueco no se cambia (se le quita la pulsación).
     */
    static void preTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean has = hasClass(mc) && mc.screen == null;
        for (int i = 0; i < SLOTS; i++) {
            boolean pressed = false;
            while (KEYS[i].consumeClick()) pressed = true;
            if (!has) continue;
            for (KeyMapping hot : mc.options.keyHotbarSlots) {
                if (hot.same(KEYS[i])) {
                    while (hot.consumeClick()) {
                        // se la queda la skill
                    }
                }
            }
            if (pressed) press(mc, i);
        }
        if (!has && selected >= 0) setSelected(-1);
        // Cambiar de hueco (1-4, rueda) suelta la skill elegida
        if (selected >= 0 && mc.player != null && mc.player.getInventory().selected != selectedHotbar) setSelected(-1);
    }

    /** Al acabar el tick. */
    static void postTick() {
        attackWasDown = Minecraft.getInstance().options.keyAttack.isDown();
    }

    private static void press(Minecraft mc, int i) {
        SkillDefs.ClassDef def = SkillDefs.get(SkillClient.classId);
        List<SkillDefs.SkillDef> actives = def.actives();
        if (i >= actives.size()) return;
        if (selected == i) { // la misma tecla: suelta
            setSelected(-1);
            return;
        }
        if (i < SkillClient.left.length && SkillClient.left[i] > 0) {
            if (i < SkillClient.denied.length) SkillClient.denied[i] = SkillClient.clientTicks;
            return;
        }
        setSelected(i);
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.6F);
        SkillDefs.SkillDef s = actives.get(i);
        mc.gui.setOverlayMessage(Component.literal("✦ " + s.name() + " ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(def.bow() ? "— dispara con tu arco" : "— clic izquierdo para usarla").withStyle(ChatFormatting.GRAY)),
                false);
    }

    private static void setSelected(int i) {
        Minecraft mc = Minecraft.getInstance();
        selected = i;
        selectedHotbar = mc.player == null ? -1 : mc.player.getInventory().selected;
        SkillNet.CHANNEL.sendToServer(new SkillNet.Select(i));
    }

    /** Lo que diga el servidor (al lanzarse o si no estaba lista). */
    static void serverSelected(int i) {
        selected = i;
        Minecraft mc = Minecraft.getInstance();
        if (i >= 0 && mc.player != null) selectedHotbar = mc.player.getInventory().selected;
    }

    /**
     * Clic izquierdo: con una skill elegida y lista (y la clase no es de arco) se lanza; si no, es un golpe normal (que
     * también avisa a las auras onSwing de la clase, como las lanzas de la Bruja que salen a cada golpe).
     */
    static void attack() {
        Minecraft mc = Minecraft.getInstance();
        if (!hasClass(mc) || attackWasDown) return;
        SkillDefs.ClassDef def = SkillDefs.get(SkillClient.classId);
        if (selected >= 0 && !def.bow() && (selected >= SkillClient.left.length || SkillClient.left[selected] <= 0)) {
            selected = -1;
            SkillNet.CHANNEL.sendToServer(new SkillNet.Use());
        } else {
            SkillNet.CHANNEL.sendToServer(new SkillNet.Swing());
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
        // A la derecha de la barra de objetos (y de la mano izquierda si va a ese lado); si no cabe, en dos filas
        int x0 = width / 2 + 91 + 6;
        if (mc.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT) x0 += 29;
        int perRow = x0 + n * (SIZE + GAP) <= width - 2 ? n : (n + 1) / 2;
        RenderSystem.enableBlend();
        for (int i = 0; i < n; i++) {
            int col = i % perRow, row = i / perRow;
            int rows = (n + perRow - 1) / perRow;
            int x = x0 + col * (SIZE + GAP);
            int y = height - 3 - SIZE - (rows - 1 - row) * (SIZE + GAP);
            SkillDefs.SkillDef s = actives.get(i);
            int left = SkillClient.left[i];
            int total = Math.max(1, SkillClient.total.length > i ? SkillClient.total[i] : 1);
            boolean ready = left <= 0;
            boolean sel = selected == i;
            boolean flash = i < SkillClient.denied.length && SkillClient.clientTicks - SkillClient.denied[i] < 6;
            if (sel) y -= 2;
            // Marco: dorado y brillante si está elegida, claro si está lista, apagado si no, rojo si se pulsó sin estar lista
            int border = flash ? 0xFFE0524A : sel ? 0xFFFFD54A : ready ? 0xFFB8A57A : 0xFF4A4438;
            g.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, border);
            g.fill(x, y, x + SIZE, y + SIZE, 0xC0101014);
            if (sel) {
                int a = (int) (90 + 60 * Math.sin((SkillClient.clientTicks + partial) * 0.3));
                g.fill(x - 2, y - 2, x + SIZE + 2, y - 1, (a << 24) | 0xFFD54A);
                g.fill(x - 2, y + SIZE + 1, x + SIZE + 2, y + SIZE + 2, (a << 24) | 0xFFD54A);
            }
            String icon = def.icons.get(s.id());
            ResourceLocation rl = icon == null ? null : ResourceLocation.tryParse(icon);
            if (rl != null) {
                g.blit(rl, x + 1, y + 1, 0, 0, SIZE - 2, SIZE - 2, SIZE - 2, SIZE - 2);
            } else {
                g.drawCenteredString(font, s.name().substring(0, 1), x + SIZE / 2, y + 4, 0xFFFFFF);
            }
            if (!ready) {
                float frac = Math.min(1F, (left - partial) / total);
                int h = Math.round((SIZE - 2) * frac);
                g.fill(x + 1, y + 1 + (SIZE - 2 - h), x + SIZE - 1, y + SIZE - 1, 0xB0000000);
                String secs = left >= 20 ? String.valueOf((left + 19) / 20) : String.format(java.util.Locale.ROOT, "%.1f", left / 20F);
                small(g, font, secs, x + SIZE / 2F, y + SIZE / 2F - 2, 0xFFFFFFFF, true);
            }
            // La tecla, pequeña en la esquina de abajo
            String key = KEYS[i].getTranslatedKeyMessage().getString();
            if (key.length() > 2) key = key.substring(0, 2);
            small(g, font, key, x + SIZE - 2.5F, y + SIZE - 4.5F, sel ? 0xFFFFD54A : ready ? 0xFFE8DCC0 : 0xFF8A8070, true);
        }
        RenderSystem.disableBlend();
    }

    /** Texto a media escala, centrado en (cx, y), con sombra. */
    private static void small(GuiGraphics g, Font font, String s, float cx, float y, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 200);
        g.pose().scale(0.5F, 0.5F, 1F);
        g.drawString(font, s, -font.width(s) / 2, 0, color, shadow);
        g.pose().popPose();
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
