package net.tierrasfantasticas.tfclient.crates.client.color;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FSColorPickerScreen extends Screen {
   private static final int SWATCH = 13;
   private static final int PER_ROW = 8;
   private final String titleText;
   private final String sample;
   private final FSColorPickerScreen.Sink onApply;
   private final Runnable onBack;
   private final List<Integer> palette = new ArrayList<>();
   private int current;
   private boolean bold;
   private boolean italic;
   private boolean underline;
   private boolean strike;
   private boolean suppress;
   private ColorWheelWidget wheel;
   private RgbSliderWidget rgb;
   private EditBox hex;
   private int leftX;
   private int paletteX;
   private int paletteY;

   public FSColorPickerScreen(String titleText, String sample, int initial, boolean[] flags, FSColorPickerScreen.Sink onApply, Runnable onBack) {
      super(Component.literal(titleText));
      this.titleText = titleText;
      this.sample = sample != null && !sample.isBlank() ? sample : "Texto de ejemplo";
      this.current = initial & 16777215;
      this.onApply = onApply;
      this.onBack = onBack;
      if (flags != null && flags.length >= 4) {
         this.bold = flags[0];
         this.italic = flags[1];
         this.underline = flags[2];
         this.strike = flags[3];
      }

      for (ChatFormatting f : ChatFormatting.values()) {
         if (f.isColor() && f.getColor() != null) {
            this.palette.add(f.getColor() & 16777215);
         }
      }
   }

   protected void init() {
      this.leftX = this.width / 2 - 120;
      int y = 64;
      this.wheel = (ColorWheelWidget)this.addRenderableWidget(new ColorWheelWidget(this.leftX, y, 120, 60, c -> this.apply(c, true)));
      this.rgb = (RgbSliderWidget)this.addRenderableWidget(new RgbSliderWidget(this.leftX, y + 66, 120, 34, c -> this.apply(c, true)));
      int rx = this.leftX + 130;
      this.hex = (EditBox)this.addRenderableWidget(new EditBox(this.font, rx, y, 100, 16, Component.literal("HEX")));
      this.hex.setMaxLength(7);
      this.hex.setFilter(s -> s.matches("#?[0-9a-fA-F]{0,6}"));
      this.hex.setResponder(s -> {
         if (!this.suppress) {
            String v = s.startsWith("#") ? s.substring(1) : s;
            if (v.length() == 6) {
               this.apply(FSColors.parse(s, this.current), false);
            }
         }
      });
      this.paletteX = this.leftX;
      this.paletteY = y + 108;
      int sy = y + 46;
      int sw = 49;
      this.addStyleToggle(rx, sy, sw, "§lNegrita", this.bold, () -> this.bold = !this.bold);
      this.addStyleToggle(rx + sw + 2, sy, sw, "§oCursiva", this.italic, () -> this.italic = !this.italic);
      this.addStyleToggle(rx, sy + 20, sw, "§nSubray.", this.underline, () -> this.underline = !this.underline);
      this.addStyleToggle(rx + sw + 2, sy + 20, sw, "§mTachado", this.strike, () -> this.strike = !this.strike);
      this.addRenderableWidget(Button.builder(Component.literal("§aHecho"), b -> {
         if (this.onApply != null) {
            this.onApply.apply(this.current, this.bold, this.italic, this.underline, this.strike);
         }

         this.onClose();
      }).bounds(this.width / 2 + 20, 8, 90, 18).build());
      this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), b -> this.onClose()).bounds(this.width / 2 - 110, 8, 90, 18).build());
      this.apply(this.current, true);
   }

   private void addStyleToggle(int x, int y, int w, String label, boolean state, Runnable toggle) {
      this.addRenderableWidget(Button.builder(Component.literal((state ? "§a✔ " : "§7") + label), b -> {
         toggle.run();
         this.rebuildWidgets();
      }).bounds(x, y, w, 18).build());
   }

   private void apply(int color, boolean syncHex) {
      this.current = color & 16777215;
      if (this.wheel != null) {
         this.wheel.setColor(this.current);
      }

      if (this.rgb != null) {
         this.rgb.setColor(this.current);
      }

      if (syncHex && this.hex != null) {
         this.suppress = true;
         this.hex.setValue(FSColors.toHex(this.current));
         this.suppress = false;
      }
   }

   public void onClose() {
      if (this.onBack != null) {
         this.onBack.run();
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0) {
         for (int i = 0; i < this.palette.size(); i++) {
            int sx = this.paletteX + i % 8 * 13;
            int sy = this.paletteY + i / 8 * 13;
            if (mouseX >= (double)sx && mouseX < (double)(sx + 13 - 1) && mouseY >= (double)sy && mouseY < (double)(sy + 13 - 1)) {
               this.apply(this.palette.get(i), true);
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.renderBackground(g);
      int px = this.leftX - 14;
      int pw = 268;
      g.fill(px, 28, px + pw, 232, -400942558);
      g.fill(px, 28, px + pw, 48, -233959916);
      g.fill(px, 231, px + pw, 232, -13412950);
      g.drawString(this.font, "§b" + this.titleText, this.leftX, 34, 16777215, false);
      super.render(g, mouseX, mouseY, partialTick);

      for (int i = 0; i < this.palette.size(); i++) {
         int sx = this.paletteX + i % 8 * 13;
         int sy = this.paletteY + i / 8 * 13;
         g.fill(sx, sy, sx + 13 - 1, sy + 13 - 1, 0xFF000000 | this.palette.get(i));
         g.renderOutline(sx, sy, 12, 12, -16777216);
      }

      int rx = this.leftX + 130;
      g.fill(rx, 30, rx + 100, 46, 0xFF000000 | this.current);
      g.renderOutline(rx, 30, 100, 16, -16777216);
      g.drawString(this.font, FSColors.toHex(this.current), rx + 104, 34, -1, false);
      StringBuilder codes = new StringBuilder();
      if (this.bold) {
         codes.append("§l");
      }

      if (this.italic) {
         codes.append("§o");
      }

      if (this.underline) {
         codes.append("§n");
      }

      if (this.strike) {
         codes.append("§m");
      }

      String preview = codes + this.font.plainSubstrByWidth(this.sample, 120);
      g.drawString(this.font, "§7Vista previa:", rx, 196, -5592406, false);
      g.fill(rx - 2, 206, rx + 128, 224, 1610612736);
      g.drawString(this.font, preview, rx, 211, 0xFF000000 | this.current, true);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public interface Sink {
      void apply(int var1, boolean var2, boolean var3, boolean var4, boolean var5);
   }
}
