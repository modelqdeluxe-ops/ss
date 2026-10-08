package net.tierrasfantasticas.tfclient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * Botón del menú principal con el tema de TF. Los principales (servidor y web) son de oro con letras oscuras;
 * el resto, azul noche con filete dorado. Las texturas las genera tools/gen_buttons.py.
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    private final boolean primary;
    /** Orden en que le pasa la línea luminosa (0 = primero). */
    private int shineOrder;

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.primary = primary;
    }

    private TFMenuButton shineOrder(int order) {
        this.shineOrder = order;
        return this;
    }

    /** Texto con la fuente de TF. */
    public static MutableComponent label(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    /** Botón principal: conexión directa a Tierras Fantásticas. */
    public static TFMenuButton server(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label(TFConfig.serverName()),
                button -> TFServer.join(Minecraft.getInstance().screen), true).shineOrder(0);
    }

    /** Botón de la web del servidor. */
    public static TFMenuButton web(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Web"), button -> openWeb(), true).shineOrder(1);
    }

    /** Botón del Discord del servidor (invitación del servidor, no de una persona). */
    public static TFMenuButton discord(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label("Discord"), button -> TFDiscord.open(), true).shineOrder(2);
    }

    private static void openWeb() {
        TFDiscord.openUrl(TFConfig.webUrl());
    }

    /** Copia un botón del menú normal con el diseño de TF y el mismo efecto al pulsarlo. */
    public static TFMenuButton wrapping(Button original, int x, int y, int width, String text) {
        TFMenuButton button = new TFMenuButton(x, y, width, 20, label(text), pressed -> original.onPress(), false);
        button.active = original.active;
        return button;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TFTextures texture = primary ? TFTextures.BUTTON_SLICE_PRIMARY : TFTextures.BUTTON_SLICE;
        if (!texture.ready()) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        boolean hot = this.active && this.isHoveredOrFocused();
        int state = !this.active ? 2 : hot ? 1 : 0;
        TFButtonTheme.drawPlate(g, texture, getX(), getY(), getWidth(), getHeight(), state, this.alpha);
        if (primary && this.active) {
            // Placa de oro: letras oscuras, sin sombra, y la línea luminosa del emblema (más suave) por encima
            TFButtonTheme.drawLabel(g, getMessage(), getX(), getY(), getWidth(), getHeight(), hot ? 0x241404 : 0x34200A, this.alpha, false);
            TFButtonTheme.drawGoldSweep(g, getX(), getY(), getWidth(), getHeight(), this.alpha, shineOrder);
        } else {
            int color = !this.active ? 0xA0A0A0 : hot ? 0xFFD667 : 0xF5EAD0;
            TFButtonTheme.drawLabel(g, getMessage(), getX(), getY(), getWidth(), getHeight(), color, this.alpha, true);
        }
    }
}
