package net.tierrasfantasticas.tfclient.crates.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class FSButton extends AbstractButton {
   private static final float HOVER_MS = 160.0F;
   private static final int MOTES = 20;
   private final int accent;
   private final Runnable action;
   private boolean centered = true;
   private float hover;
   private long lastFrameMs = System.currentTimeMillis();
   private float clock;
   private boolean wasHovered;
   private static long lastHoverSoundMs;

   public FSButton(int x, int y, int width, int height, Component message, int accent, Runnable action) {
      super(x, y, width, height, message);
      this.accent = accent;
      this.action = action;
   }

   public FSButton leftAligned() {
      this.centered = false;
      return this;
   }

   public void onPress() {
      if (this.action != null) {
         this.action.run();
      }
   }

   public void playDownSound(SoundManager sounds) {
      sounds.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.92F, 0.9F));
   }

   private void playHoverSound() {
      long now = System.currentTimeMillis();
      if (now - lastHoverSoundMs >= 90L) {
         lastHoverSoundMs = now;
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.45F, 0.32F));
         }
      }
   }

   protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      int x = this.getX();
      int y = this.getY();
      int w = this.getWidth();
      int h = this.getHeight();
      this.updateHover();
      float lit = this.active ? this.hover : 0.0F;
      rounded(g, x, y + 2, w, h, 1426063360, 1426063360);
      if (!this.active) {
         rounded(g, x, y, w, h, -15066598, -15066598);
         rounded(g, x + 1, y + 1, w - 2, h - 2, -11842741, -12961222);
      } else {
         int border = mix(-15460838, darken(this.accent, 0.55F), 0.25F + 0.75F * lit);
         rounded(g, x, y, w, h, border, border);
         int top = mix(-11380378, mix(this.accent, -1, 0.25F), 0.22F + 0.3F * lit);
         int bottom = mix(-13881030, darken(this.accent, 0.7F), 0.22F + 0.3F * lit);
         rounded(g, x + 1, y + 1, w - 2, h - 2, top, bottom);
         if (lit > 0.0F) {
            this.renderGlow(g, x, y, w, h, lit);
         }

         g.fill(x + 3, y + 1, x + w - 3, y + 2, 872415231);
         g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, withAlpha(this.accent, (int)(130.0F + 100.0F * lit)));
      }

      int textColor = this.active ? -1 : -6645094;
      Font font = Minecraft.getInstance().font;
      int textY = y + (h - 9) / 2 + 1;
      if (this.centered) {
         g.drawCenteredString(font, this.getMessage(), x + w / 2, textY, textColor);
      } else {
         g.drawString(font, this.getMessage(), x + 8, textY, textColor, true);
      }
   }

   private void renderGlow(GuiGraphics g, int x, int y, int w, int h, float lit) {
      float breath = 0.5F + 0.13F * (float)Math.sin((double)(this.clock * 2.6F));
      float glowRows = (float)(h - 2) * breath * lit;
      int baseAlpha = (int)(255.0F * lit);

      for (int row = 0; row < h - 2; row++) {
         float up = (float)row / Math.max(1.0F, glowRows);
         if (up > 1.0F) {
            break;
         }

         float fade = (float)Math.pow((double)(1.0F - up), 1.15);
         int alpha = (int)((float)baseAlpha * fade);
         if (alpha > 0) {
            int rowY = y + h - 2 - row;
            int inset = Math.max(1, cornerInset(rowY - y, h));
            g.fill(x + inset, rowY, x + w - inset, rowY + 1, withAlpha(this.accent, alpha));
         }
      }

      int usable = w - 8;
      if (usable > 0) {
         for (int i = 0; i < 20; i++) {
            float speed = 1.6F + 0.55F * (float)(i * 5 % 7) / 7.0F;
            float phase = (this.clock * speed + (float)i * 0.7548F % 1.0F) % 1.0F;
            float travel = phase * (float)h * 0.78F;
            float moteY = (float)(y + h - 2) - travel;
            if (!(moteY <= (float)(y + 2))) {
               int moteX = x + 4 + (int)((0.13F + (float)i * 0.618F) % 1.0F * (float)usable);
               int alpha = (int)(235.0F * lit * (1.0F - phase) * Math.min(1.0F, phase * 7.0F));
               if (alpha > 4) {
                  int row = (int)moteY;
                  float frac = moteY - (float)row;
                  int upper = (int)((float)alpha * (1.0F - frac));
                  int lower = (int)((float)alpha * frac);
                  if (upper > 3) {
                     g.fill(moteX, row, moteX + 1, row + 1, withAlpha(-1, upper));
                  }

                  if (lower > 3 && row + 1 < y + h - 1) {
                     g.fill(moteX, row + 1, moteX + 1, row + 2, withAlpha(-1, lower));
                  }
               }
            }
         }
      }
   }

   private void updateHover() {
      long now = System.currentTimeMillis();
      float step = (float)(now - this.lastFrameMs) / 160.0F;
      this.lastFrameMs = now;
      step = Math.min(1.0F, Math.max(0.0F, step));
      boolean target = this.isHoveredOrFocused() && this.active;
      if (target && !this.wasHovered) {
         this.playHoverSound();
      }

      this.wasHovered = target;
      this.hover = Math.max(0.0F, Math.min(1.0F, this.hover + (target ? step : -step)));
      this.clock += step * 0.16F;
   }

   private static void rounded(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
      if (w > 4 && h > 2) {
         for (int row = 0; row < h; row++) {
            int inset = cornerInset(row, h);
            float t = h == 1 ? 0.0F : (float)row / (float)(h - 1);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, mixKeepAlpha(top, bottom, t));
         }
      } else {
         g.fill(x, y, x + w, y + h, top);
      }
   }

   private static int cornerInset(int row, int h) {
      if (row == 0 || row == h - 1) {
         return 2;
      } else {
         return row != 1 && row != h - 2 ? 0 : 1;
      }
   }

   private static int mix(int a, int b, float amount) {
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      int r = (int)((float)ar + (float)(br - ar) * amount);
      int gg = (int)((float)ag + (float)(bg - ag) * amount);
      int bl = (int)((float)ab + (float)(bb - ab) * amount);
      return 0xFF000000 | r << 16 | gg << 8 | bl;
   }

   private static int mixKeepAlpha(int a, int b, float amount) {
      int aa = a >>> 24;
      int ba = b >>> 24;
      int alpha = (int)((float)aa + (float)(ba - aa) * amount);
      return (alpha & 0xFF) << 24 | mix(a, b, amount) & 16777215;
   }

   private static int darken(int color, float factor) {
      return mix(color, -16777216, 1.0F - factor);
   }

   private static int withAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }

   protected void updateWidgetNarration(NarrationElementOutput output) {
      this.defaultButtonNarrationText(output);
   }
}
