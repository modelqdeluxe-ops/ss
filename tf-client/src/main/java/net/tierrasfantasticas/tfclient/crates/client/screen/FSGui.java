/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class FSGui {
   public static final int PANEL_TOP = -299619784;
   public static final int PANEL_BOTTOM = -300541404;
   public static final int PANEL_BG = -299619784;
   public static final int PANEL_BORDER = -11774872;
   public static final int PANEL_HIGHLIGHT = -9208429;
   public static final int INSET_BG = 1292767768;
   public static final int INSET_BORDER = -13090994;
   public static final int TEXT = -1644826;
   public static final int TEXT_DIM = -6577748;
   public static final int ACCENT_GREEN = -11746198;
   public static final int ACCENT_BLUE = -11884320;
   public static final int ACCENT_RED = -2796465;

   private FSGui() {
   }

   private static int cornerInset(int row, int height) {
      int fromEdge = Math.min(row, height - 1 - row);
      if (fromEdge == 0) {
         return 3;
      } else if (fromEdge == 1) {
         return 2;
      } else {
         return fromEdge == 2 ? 1 : 0;
      }
   }

   private static void roundedGradient(GuiGraphics g, int x, int y, int width, int height, int top, int bottom) {
      for (int row = 0; row < height; row++) {
         int inset = cornerInset(row, height);
         float t = height <= 1 ? 0.0F : (float)row / (float)(height - 1);
         g.fill(x + inset, y + row, x + width - inset, y + row + 1, lerpColor(top, bottom, t));
      }
   }

   private static int lerpColor(int a, int b, float t) {
      int aa = a >>> 24;
      int ba = b >>> 24;
      int alpha = (int)((float)aa + (float)(ba - aa) * t);
      int r = (int)((float)(a >> 16 & 0xFF) + (float)((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int gg = (int)((float)(a >> 8 & 0xFF) + (float)((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = (int)((float)(a & 0xFF) + (float)((b & 0xFF) - (a & 0xFF)) * t);
      return (alpha & 0xFF) << 24 | r << 16 | gg << 8 | bl;
   }

   public static void panel(GuiGraphics g, int x, int y, int width, int height) {
      for (int ring = 3; ring >= 1; ring--) {
         int alpha = 20 * ring;
         roundedGradient(g, x - ring, y - ring + 1, width + ring * 2, height + ring * 2, alpha << 24, alpha << 24);
      }

      roundedGradient(g, x, y, width, height, -299619784, -300541404);
      borderRounded(g, x, y, width, height, -11774872);
      g.fill(x + 4, y + 1, x + width - 4, y + 2, -9208429);
      g.fillGradient(x + 2, y + 2, x + width - 2, y + 20, 452984831, 16777215);
   }

   private static void borderRounded(GuiGraphics g, int x, int y, int width, int height, int color) {
      for (int row = 0; row < height; row++) {
         int inset = cornerInset(row, height);
         int prev = row == 0 ? -1 : cornerInset(row - 1, height);
         if (row != 0 && row != height - 1 && inset == prev) {
            g.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
            g.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
         } else {
            g.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
         }
      }

      int lastInset = cornerInset(height - 1, height);
      g.fill(x + lastInset, y + height - 1, x + width - lastInset, y + height, color);
   }

   public static void inset(GuiGraphics g, int x, int y, int width, int height) {
      roundedGradient(g, x, y, width, height, 1510673939, 1024332312);
      borderRounded(g, x, y, width, height, -13090994);
   }

   public static void scrollbar(GuiGraphics g, int x, int y, int width, int trackHeight, int thumbTop, int thumbHeight, boolean highlighted) {
      g.fill(x, y, x + width, y + trackHeight, 1610612736);
      int body = highlighted ? -7695450 : -10854030;
      int edge = highlighted ? -4800819 : -9011568;
      g.fill(x, thumbTop, x + width, thumbTop + thumbHeight, body);
      g.fill(x, thumbTop, x + width, thumbTop + 1, edge);
      g.fill(x, thumbTop + thumbHeight - 1, x + width, thumbTop + thumbHeight, -12696496);
   }

   public static void separator(GuiGraphics g, int x, int y, int width) {
      g.fill(x, y, x + width, y + 1, 822083583);
   }
}
