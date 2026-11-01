/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.tierrasfantasticas.tfclient.clans.ClanEmote;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanNet;
import org.lwjgl.glfw.GLFW;

/**
 * (1.3.160) La rueda de los emotes del clan: la tecla de los emotes (B de fábrica) la abre; se elige con el ratón (clic)
 * o con 1 a 5, y se cierra con Esc, clic derecho o la misma tecla. En medio, la insignia del clan y lo que hace el
 * emote que tienes debajo del ratón.
 */
public final class ClanEmoteWheel extends Screen {
    private static final ItemStack[] ICONS = {new ItemStack(Items.WHITE_BANNER), new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.LIGHTNING_ROD),
            new ItemStack(Items.FIREWORK_ROCKET), new ItemStack(Items.ENDER_EYE)};
    private static final String[] DESC = {"Un estandarte gigante del clan", "Una corona de oro con la insignia", "Un rayo del color del clan",
            "Confeti, baile y bola de disco", "Insignias que giran a tu alrededor"};
    private static final int[] COLORS = {0xFFD9A441, 0xFFF2C14E, 0xFF5AB4FF, 0xFFFF6FB0, 0xFFB07CFF};
    /** Para la prueba en el juego: el que se ve como «debajo del ratón» (-1: el del ratón de verdad). */
    public static volatile int testHover = -1;
    private int hover = -1;

    public ClanEmoteWheel() {
        super(Component.literal("Emotes del clan"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int radius() {
        return Math.max(56, Math.min(92, (int) (Math.min(width, height) * 0.27)));
    }

    private int slotAt(double mx, double my) {
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        double d = Math.sqrt(dx * dx + dy * dy), r = radius();
        if (d < r * 0.42 || d > r * 1.55) return -1;
        double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 36;
        ang = ((ang % 360) + 360) % 360;
        return Math.min(4, (int) (ang / 72));
    }

    /** Un círculo lleno (por filas). */
    private static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, color);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, width, height, 0x88000000);
        hover = testHover >= 0 ? testHover : slotAt(mx, my);
        int cx = width / 2, cy = height / 2, r = radius();
        Minecraft mc = Minecraft.getInstance();
        ClanLooks.Look look = mc.player == null ? null : ClanLooks.ofPlayer(mc.player.getUUID());
        for (int i = 0; i < ClanEmote.IDS.length; i++) {
            double a = Math.toRadians(-90 + i * 72);
            boolean on = i == hover;
            int sr = on ? 30 : 25;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            disc(g, x, y, sr + 2, on ? 0xFFFFE070 : 0xFF1A1F2E);
            disc(g, x, y, sr, on ? COLORS[i] : 0xFF2C3446);
            disc(g, x, y - sr / 3, (int) (sr * 0.62), on ? 0x30FFFFFF : 0x14FFFFFF);
            g.pose().pushPose();
            float k = on ? 1.9F : 1.5F;
            g.pose().translate(x, y - 2, 0);
            g.pose().scale(k, k, 1);
            g.renderItem(ICONS[i], -8, -8);
            g.pose().popPose();
            g.drawString(font, String.valueOf(i + 1), x - sr + 4, y - sr + 2, on ? 0xFF3A2000 : 0xFFB8C2D8, false);
            String name = ClanEmote.NAMES[i];
            g.drawCenteredString(font, name, x, y + sr + 5, on ? 0xFFFFE070 : 0xFFE8ECF4);
        }
        // en medio: la insignia del clan y lo que hace el elegido
        disc(g, cx, cy, 36, 0xFF10141E);
        disc(g, cx, cy, 34, 0xFF222A3A);
        if (look != null) {
            g.blit(look.badgeTexture(), cx - 22, cy - 26, 0, 0, 44, 44, 44, 44);
        }
        String l1, l2;
        if (look == null) {
            l1 = "Únete a un clan";
            l2 = "Los emotes son de los clanes";
        } else if (hover >= 0) {
            l1 = ClanEmote.NAMES[hover];
            l2 = DESC[hover];
        } else {
            l1 = "Emotes del clan";
            l2 = "Clic o 1 a 5 · Esc para salir";
        }
        int ty = cy + Math.max(r + 46, 0);
        if (ty + 22 > height) ty = height - 22;
        g.drawCenteredString(font, l1, cx, ty, look == null ? 0xFFFF6060 : 0xFFFFFFFF);
        g.drawCenteredString(font, l2, cx, ty + 11, 0xFFB8C2D8);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) {
            onClose();
            return true;
        }
        int i = slotAt(mx, my);
        if (i >= 0) {
            play(i);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_5) {
            play(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_5) {
            play(key - GLFW.GLFW_KEY_KP_1);
            return true;
        }
        if (ClanClient.EMOTE.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void play(int i) {
        ClanNet.askEmote(i);
        onClose();
    }
}
