package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.TFConfig;

/**
 * Botón del menú con aspecto de placa de piedra de templo con marco dorado y medallones, y texto en la fuente Cinzel.
 * Las texturas las genera tools/gen_buttons.py.
 */
public class TFMenuButton extends Button {
    public static final ResourceLocation FONT = new ResourceLocation(TFClient.MOD_ID, "cinzel");
    private final boolean primary;

    public TFMenuButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.primary = primary;
    }

    /** Texto con la fuente de TF. */
    public static MutableComponent label(String text) {
        return Component.literal(text).setStyle(Style.EMPTY.withFont(FONT));
    }

    /** Botón principal: conexión directa a Tierras Fantásticas. */
    public static TFMenuButton server(int x, int y, int width) {
        return new TFMenuButton(x, y, width, 20, label(TFConfig.serverName()),
                button -> TFServer.join(Minecraft.getInstance().screen), true);
    }

    /** Copia un botón del menú normal con el diseño de TF y el mismo efecto al pulsarlo. */
    public static TFMenuButton wrapping(Button original, int x, int y, int width, String text) {
        TFMenuButton button = new TFMenuButton(x, y, width, 20, label(text), pressed -> original.onPress(), false);
        button.active = original.active;
        return button;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        TFTextures texture = primary ? TFTextures.BUTTON_PRIMARY : getWidth() >= 150 ? TFTextures.BUTTON_WIDE : TFTextures.BUTTON_HALF;
        if (!texture.ready()) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            return;
        }
        boolean hot = this.active && this.isHoveredOrFocused();
        int state = !this.active ? 2 : hot ? 1 : 0;
        int artHeight = texture.height() / 3;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1f, 1f, 1f, this.alpha);
        g.blit(texture.id(), getX(), getY(), getWidth(), getHeight(), 0f, state * artHeight,
                texture.width(), artHeight, texture.width(), texture.height());
        g.setColor(1f, 1f, 1f, 1f);

        Font font = Minecraft.getInstance().font;
        int color = !this.active ? 0xA0A0A0 : hot ? 0xFFD667 : 0xFFF3DC;
        int alpha = Mth.ceil(Mth.clamp(this.alpha, 0.02f, 1f) * 255f) << 24;
        g.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color | alpha);
    }
}
