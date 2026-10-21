package net.tierrasfantasticas.tfclient.crates.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * (1.3.99) Un botón pequeño de cristal oscuro, encima del santuario de las hadas (la pantalla de antes de abrir y la de
 * las recompensas): translúcido, con un borde fino que se enciende del color de la rareza al pasar el ratón y sin sonido
 * (el dueño: «más pequeños esos botones… no quiero su sonido de amatista, nada»).
 */
public class GlassButton extends AbstractButton {
   private static final float HOVER_MS = 140.0F;
   private final int accent;
   private final Runnable action;
   private float hover;
   private long lastFrameMs = System.currentTimeMillis();

   public GlassButton(int x, int y, int width, int height, Component message, int accent, Runnable action) {
      super(x, y, width, height, message);
      this.accent = accent & 0xFFFFFF;
      this.action = action;
   }

   /** El ancho justo para su texto. */
   public static int widthFor(Font font, String text) {
      return font.width(text) + 22;
   }

   public void onPress() {
      if (this.action != null) {
         this.action.run();
      }
   }

   public void playDownSound(SoundManager sounds) {
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      long now = System.currentTimeMillis();
      float step = Math.min(1.0F, Math.max(0.0F, (float)(now - this.lastFrameMs) / HOVER_MS));
      this.lastFrameMs = now;
      boolean target = this.active && this.isHoveredOrFocused();
      this.hover = Math.max(0.0F, Math.min(1.0F, this.hover + (target ? step : -step)));
      int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
      float lit = this.hover;
      // el cristal: oscuro y translúcido, algo más claro arriba
      int top = (int)(0x70 + 0x30 * lit) << 24 | 0x0E1A1C;
      int bottom = (int)(0x90 + 0x30 * lit) << 24 | 0x050A0B;
      g.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
      g.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
      g.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
      // el borde fino: blanco tenue, y del color de la rareza al pasar el ratón
      int border = mix(0xFFFFFF, this.accent, lit);
      int ba = (int)(0x38 + 0x90 * lit);
      g.fill(x + 1, y, x + w - 1, y + 1, ba << 24 | border);
      g.fill(x + 1, y + h - 1, x + w - 1, y + h, ba << 24 | border);
      g.fill(x, y + 1, x + 1, y + h - 1, ba << 24 | border);
      g.fill(x + w - 1, y + 1, x + w, y + h - 1, ba << 24 | border);
      if (lit > 0.01F) {
         // un brillo suave que sube desde abajo
         g.fillGradient(x + 1, y + h / 2, x + w - 1, y + h - 1, 0, (int)(0x40 * lit) << 24 | this.accent);
      }

      Font font = Minecraft.getInstance().font;
      int text = this.active ? mix(0xD8DEDC, 0xFFFFFF, lit) : 0x707070;
      g.drawCenteredString(font, this.getMessage(), x + w / 2, y + (h - 8) / 2, 0xFF000000 | text);
   }

   private static int mix(int a, int b, float t) {
      int r = (int)(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
      int gg = (int)(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
      int bl = (int)((a & 255) + ((b & 255) - (a & 255)) * t);
      return r << 16 | gg << 8 | bl;
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }
}
