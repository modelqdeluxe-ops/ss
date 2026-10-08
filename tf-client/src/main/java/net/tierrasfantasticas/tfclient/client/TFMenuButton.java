package net.tierrasfantasticas.tfclient.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * Botón del menú principal con el diseño de los botones de la web: bloque redondeado con labio, letra Unbounded en
 * mayúsculas. El principal (entrar al servidor y la web) es de oro con letras oscuras; Discord, en su azul; el resto,
 * pizarra con filo cian que se llena de cian al pasar el ratón. Al pasar el ratón cruza un destello. Las texturas las
 * genera tools/gen_buttons.py.
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "unbounded");

    /** Estilo: oro (principal), Discord o secundario. */
    public enum Style { PRIMARY, DISCORD, SECONDARY }

    private final Style style;
    /** Orden en que le pasa la línea luminosa (0 = primero). */
    private int shineOrder;
    /** Desde cuándo está el ratón encima (para el destello). */
    private long hoverSince = -1;

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    private TFMenuButton shineOrder(int order) {
        this.shineOrder = order;
        return this;
    }

    /** Texto con la fuente de TF, en mayúsculas como los botones de la web. */
    public static MutableComponent label(String text) {
        return Component.literal(text.toUpperCase(java.util.Locale.ROOT)).setStyle(net.minecraft.network.chat.Style.EMPTY.withFont(FONT));
    }

    /** Botón principal: conexión directa a Tierras Fantásticas. */
    public static TFMenuButton server(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label(TFConfig.serverName()),
                button -> TFServer.join(Minecraft.getInstance().screen), Style.PRIMARY).shineOrder(0);
    }

    /** Botón de la web del servidor. */
    public static TFMenuButton web(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Web"), button -> openWeb(), Style.PRIMARY).shineOrder(1);
    }

    /** Botón del Discord del servidor (invitación del servidor, no de una persona). */
    public static TFMenuButton discord(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Discord"), button -> TFDiscord.open(), Style.DISCORD).shineOrder(2);
    }

    private static void openWeb() {
        TFDiscord.openUrl(TFConfig.webUrl());
    }

    /** Copia un botón del menú normal con el diseño de TF y el mismo efecto al pulsarlo. */
    public static TFMenuButton wrapping(Button original, int x, int y, int width, String text) {
        TFMenuButton button = new TFMenuButton(x, y, width, 20, label(text), pressed -> original.onPress(), Style.SECONDARY);
        button.active = original.active;
        return button;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TFTextures texture = switch (style) {
            case PRIMARY -> TFTextures.BUTTON_SLICE_PRIMARY;
            case DISCORD -> TFTextures.BUTTON_SLICE_DISCORD;
            default -> TFTextures.BUTTON_SLICE;
        };
        if (!texture.ready()) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        boolean hot = this.active && this.isHoveredOrFocused();
        if (hot && hoverSince < 0) hoverSince = Util.getMillis();
        if (!hot) hoverSince = -1;
        int state = !this.active ? 2 : hot ? 1 : 0;
        // Al pasar el ratón el botón sube un poco (como en la web)
        int lift = hot ? -1 : 0;
        TFButtonTheme.drawPlate(g, texture, getX(), getY() + lift, getWidth(), getHeight(), state, this.alpha);
        int color = !this.active ? 0xA0A0A0 : switch (style) {
            case PRIMARY -> 0x2A1500;
            case DISCORD -> 0xFFFFFF;
            default -> hot ? 0xFFFFFF : 0xE8FBFF;
        };
        TFButtonTheme.drawLabel(g, getMessage(), getX(), getY() + lift - 1, getWidth(), getHeight(), color, this.alpha,
                style != Style.PRIMARY);
        if (!this.active) return;
        if (style == Style.PRIMARY) TFButtonTheme.drawGoldSweep(g, getX(), getY() + lift, getWidth(), getHeight() - 2, this.alpha, shineOrder);
        if (hot) TFButtonTheme.drawHoverSweep(g, getX(), getY() + lift, getWidth(), getHeight() - 2, this.alpha, Util.getMillis() - hoverSince);
    }
}
