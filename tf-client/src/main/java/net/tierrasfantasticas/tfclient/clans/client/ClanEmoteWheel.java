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
import net.tierrasfantasticas.tfclient.clans.ClanEmote;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;
import net.tierrasfantasticas.tfclient.clans.ClanNet;
import org.lwjgl.glfw.GLFW;

/**
 * Los emotes del clan: la tecla (B de fábrica) abre esta pantalla. (1.3.160, el dueño: «mete muchos más emotes» y «eso
 * sigue solapado») ya no es una rueda: una cuadrícula de tarjetas (5 por fila) sobre un fondo oscuro que tapa lo de
 * detrás (el chat y la barra no se cuelan). Cada tarjeta: su dibujo y su nombre; las de pago que el clan no tiene, con
 * candado (se compran en pad › Clanes › TIENDA). Se elige con clic o con 1 a 9; Esc, clic derecho o la misma tecla
 * cierran. Arriba, el emote que tienes debajo del ratón y lo que hace.
 */
public final class ClanEmoteWheel extends Screen {
    private static final String[] DESC = {"Un estandarte gigante del clan", "Una corona de oro con la insignia", "Un rayo del color del clan",
            "Confeti, baile y bola de disco", "Insignias que giran a tu alrededor", "Alas de fuego y un anillo de llamas", "Un torbellino de viento y hojas",
            "Cristales de hielo y nieve", "Un meteoro que cae detrás de ti", "Cohetes de los colores del clan", "Ocho espadas de luz en círculo",
            "Carga tu poder: aura y piedras que flotan", "Corazones de los colores del clan", "Guitarra, altavoces y notas", "Un portal mágico detrás de ti"};
    private static final int[] COLORS = {0xFFD9A441, 0xFFF2C14E, 0xFF5AB4FF, 0xFFFF6FB0, 0xFFB07CFF, 0xFFFF7A30, 0xFFB8D0E0, 0xFF9ADCFF, 0xFFFF8A40,
            0xFFFFD050, 0xFFC8E8FF, 0xFFFF5050, 0xFFFF6F9A, 0xFF7CF0B0, 0xFF9A4FFF};
    /** Para la prueba en el juego: la que se ve como «debajo del ratón» (-1: la del ratón de verdad). */
    public static volatile int testHover = -1;
    private int hover = -1;
    private int gx, gy, cw, ch;
    private static final int COLS = 5, GAP = 6;

    public ClanEmoteWheel() {
        super(Component.literal("Emotes del clan"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void layout() {
        int n = ClanEmote.IDS.length, rows = (n + COLS - 1) / COLS;
        cw = Math.max(54, Math.min(84, (width - 24 - (COLS - 1) * GAP) / COLS));
        ch = Math.max(40, Math.min(58, (int) (cw * 0.7)));
        int top = 40, bottom = 26;
        while (rows * ch + (rows - 1) * GAP > height - top - bottom && ch > 34) ch--;
        int gw = COLS * cw + (COLS - 1) * GAP, gh = rows * ch + (rows - 1) * GAP;
        gx = (width - gw) / 2;
        gy = top + Math.max(0, (height - top - bottom - gh) / 2);
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < ClanEmote.IDS.length; i++) {
            int x = gx + (i % COLS) * (cw + GAP), y = gy + (i / COLS) * (ch + GAP);
            if (mx >= x && mx < x + cw && my >= y && my < y + ch) return i;
        }
        return -1;
    }

    private static boolean owned(ClanLooks.Look look, int i) {
        return look != null && look.hasEmote(i);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        layout();
        // el fondo: oscuro y opaco (no se ve el chat ni la barra de objetos debajo)
        g.fill(0, 0, width, height, 0xF0101420);
        g.fillGradient(0, 0, width, 34, 0xFF1C2438, 0xF0101420);
        hover = testHover >= 0 ? testHover : cardAt(mx, my);
        Minecraft mc = Minecraft.getInstance();
        ClanLooks.Look look = mc.player == null ? null : ClanLooks.ofPlayer(mc.player.getUUID());
        // arriba: la insignia, el título y lo que hace el que está debajo del ratón
        String l1, l2;
        if (look == null) {
            l1 = "Únete a un clan";
            l2 = "Los emotes son de los clanes (pad › Clanes).";
        } else if (hover >= 0) {
            l1 = ClanEmote.NAMES[hover];
            l2 = owned(look, hover) ? DESC[hover] : DESC[hover] + " · se compra en pad › Clanes › TIENDA";
        } else {
            l1 = "EMOTES DEL CLAN";
            l2 = "Clic (o 1 a 9) para hacerlo · Esc para salir";
        }
        int tw = Math.max(font.width(l1), font.width(l2));
        if (look != null) g.blit(look.badgeTexture(), width / 2 - tw / 2 - 30, 5, 0, 0, 24, 24, 24, 24);
        g.drawCenteredString(font, l1, width / 2, 8, look == null ? 0xFFFF6060 : 0xFFFFE070);
        g.drawCenteredString(font, l2, width / 2, 20, 0xFFB8C2D8);
        for (int i = 0; i < ClanEmote.IDS.length; i++) {
            int x = gx + (i % COLS) * (cw + GAP), y = gy + (i / COLS) * (ch + GAP);
            boolean on = i == hover, have = owned(look, i);
            int base = have ? 0xFF262F45 : 0xFF1A1F2C;
            g.fill(x - 1, y - 1, x + cw + 1, y + ch + 1, on ? 0xFFFFE070 : have ? 0xFF3A4766 : 0xFF2A3042);
            g.fill(x, y, x + cw, y + ch, on && have ? COLORS[i] : base);
            g.fillGradient(x, y, x + cw, y + ch / 2, have ? 0x30FFFFFF : 0x10FFFFFF, 0x00FFFFFF);
            // el dibujo
            int iconSize = Math.max(12, Math.min(28, ch - 22));
            float k = iconSize / 16F;
            g.pose().pushPose();
            g.pose().translate(x + cw / 2F, y + 4 + iconSize / 2F, 0);
            g.pose().scale(k, k, 1);
            g.renderItem(new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(ClanEmote.ICONS[i]))),
                    -8, -8);
            g.pose().popPose();
            if (!have) {
                // sombra encima del dibujo, el candado y dónde se compra
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x, y, x + cw, y + ch, 0x90101420);
                lock(g, x + cw - 12, y + 4);
                g.drawCenteredString(font, "TIENDA", x + cw / 2, y + ch / 2 - 6, 0xFFFFC040);
                g.pose().popPose();
            }
            String name = ClanEmote.NAMES[i];
            while (font.width(name) > cw - 6 && name.length() > 3) name = name.substring(0, name.length() - 2) + "…";
            g.pose().pushPose();
            g.pose().translate(0, 0, 210);
            g.drawCenteredString(font, name, x + cw / 2, y + ch - 11, on ? (have ? 0xFF241400 : 0xFFFFE070) : have ? 0xFFE8ECF4 : 0xFF8A94AA);
            if (i < 9) g.drawString(font, String.valueOf(i + 1), x + 3, y + 3, have ? 0xFFB8C2D8 : 0xFF5A6478, false);
            g.pose().popPose();
        }
        String foot = "Los de candado se compran con Clan Coins en pad › Clanes › TIENDA (para todo el clan).";
        while (font.width(foot) > width - 10 && foot.length() > 10) foot = foot.substring(0, foot.length() - 2) + "…";
        g.drawCenteredString(font, foot, width / 2, height - 16, 0xFF7E8CA8);
        super.render(g, mx, my, partial);
    }

    /** Un candado pequeño (8 x 9). */
    private static void lock(GuiGraphics g, int x, int y) {
        g.fill(x + 2, y, x + 6, y + 1, 0xFFFFC040);
        g.fill(x + 1, y + 1, x + 2, y + 4, 0xFFFFC040);
        g.fill(x + 6, y + 1, x + 7, y + 4, 0xFFFFC040);
        g.fill(x, y + 4, x + 8, y + 9, 0xFFFFC040);
        g.fill(x + 3, y + 5, x + 5, y + 7, 0xFF241400);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) {
            onClose();
            return true;
        }
        int i = cardAt(mx, my);
        if (i >= 0) {
            play(i);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            play(key - GLFW.GLFW_KEY_1);
            return true;
        }
        if (key >= GLFW.GLFW_KEY_KP_1 && key <= GLFW.GLFW_KEY_KP_9) {
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
        Minecraft mc = Minecraft.getInstance();
        ClanLooks.Look look = mc.player == null ? null : ClanLooks.ofPlayer(mc.player.getUUID());
        if (look != null && !look.hasEmote(i)) {
            // no se cierra: que vea dónde se compra
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("«" + ClanEmote.NAMES[i] + "» se compra en pad › Clanes › TIENDA.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            return;
        }
        ClanNet.askEmote(i);
        onClose();
    }
}
