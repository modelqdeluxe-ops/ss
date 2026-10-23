/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.screen;

import net.tierrasfantasticas.tfclient.crates.client.media.CrateMedia;
import net.tierrasfantasticas.tfclient.crates.client.widget.GlassButton;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.config.RewardEntry;
import net.tierrasfantasticas.tfclient.crates.crate.LootEngine;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag.Default;

/**
 * (1.3.99) Las recompensas de la caja, rehechas (el dueño: «dale rework a todo eso de ver recompensas»): el santuario
 * sigue vivo detrás; encima, un panel de cristal oscuro con el título y una cuadrícula de premios, cada uno en su casilla
 * con el color de su rareza (de la más rara a la más común); al pasar el ratón, su nombre, su rareza, su probabilidad y lo
 * que lleve (encantamientos, efectos…). Se mueve con la rueda; abajo, un botón pequeño para volver.
 */
public class CratePoolScreen extends Screen {
   private static final int CELL = 24;
   private static final int GAP = 4;
   private final CrateConfig config;
   private final Screen parent;
   private final List<CratePoolScreen.Cell> cells = new ArrayList<>();
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private int cols;
   private int gridX;
   private int gridY;
   private int gridH;
   private float scroll;
   private float scrollTarget;
   private long lastFrameMs = System.currentTimeMillis();
   private long openedAt;

   public Screen parentScreen() {
      return this.parent;
   }

   public CratePoolScreen(CrateConfig config, Screen parent) {
      super(Component.literal("Recompensas"));
      this.config = config == null ? new CrateConfig() : config;
      this.parent = parent;
   }

   protected void init() {
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }

      this.rebuild();
      this.panelW = Math.min(this.width - 32, 300);
      this.cols = Math.max(1, (this.panelW - 24 + GAP) / (CELL + GAP));
      int maxGrid = Math.max(CELL, this.height - 110);
      this.gridH = Math.min(maxGrid, this.contentH());
      this.panelH = this.gridH + 52;
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = Math.max(10, (this.height - 30 - this.panelH) / 2);
      int gridW = this.cols * (CELL + GAP) - GAP;
      this.gridX = this.panelX + (this.panelW - gridW) / 2;
      this.gridY = this.panelY + 38;
      this.scrollTarget = Math.min(this.scrollTarget, this.maxScroll());
      this.scroll = Math.min(this.scroll, this.maxScroll());
      int accent = this.config.rarity == null ? 0xE070FF : this.config.rarity.rgb();
      int backW = Math.max(64, GlassButton.widthFor(this.font, "VOLVER"));
      this.addRenderableWidget(new GlassButton((this.width - backW) / 2, this.height - 22, backW, 14, Component.literal("VOLVER"), accent, this::onClose));
   }

   private void rebuild() {
      this.cells.clear();
      List<RewardEntry> ordered = new ArrayList<>();

      for (RewardEntry entry : this.config.rewards) {
         if (entry.type == RewardEntry.Type.ITEM && entry.item != null && !entry.item.isEmpty()) {
            ordered.add(entry);
         }
      }

      ordered.sort(
         Comparator.<RewardEntry>comparingInt(e -> -e.effectiveRarity(this.config.rarity).ordinal()).thenComparing(e -> labelOf(e).toLowerCase(Locale.ROOT))
      );

      for (RewardEntry e : ordered) {
         Rarity rarity = e.effectiveRarity(this.config.rarity);
         String odds = this.config.showOddsInPool ? String.format(Locale.ROOT, "%.2f%%", this.config.normalizedPercentInPool(e)) : "";
         this.cells.add(new CratePoolScreen.Cell(e.item, labelOf(e), rarity.displayName(), rarity.rgb() & 0xFFFFFF, odds));
      }
   }

   private static String labelOf(RewardEntry entry) {
      if (entry.label != null && !entry.label.isBlank()) {
         return entry.label;
      } else {
         return entry.item != null && !entry.item.isEmpty() ? entry.item.getHoverName().getString() : "(item vacío)";
      }
   }

   private int contentH() {
      int rows = Math.max(1, (this.cells.size() + this.cols - 1) / this.cols);
      return rows * (CELL + GAP) - GAP;
   }

   private float maxScroll() {
      return Math.max(0, this.contentH() - this.gridH);
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      // detrás, el santuario en vivo (o el vídeo de la caja)
      boolean drawn = this.parent instanceof CratePreOpenScreen pre && pre.renderSanctuary(g, this.width, this.height);
      if (!drawn && CrateMedia.isActive()) {
         CrateMedia.renderBackground(g, this.width, this.height);
      }

      long now = System.currentTimeMillis();
      float dt = Math.min(1.0F, (float)(now - this.lastFrameMs) / 120.0F);
      this.lastFrameMs = now;
      this.scroll += (this.scrollTarget - this.scroll) * dt;
      float appear = Math.min(1.0F, (float)(now - this.openedAt) / 220.0F);
      appear = appear * appear * (3.0F - 2.0F * appear);
      int accent = this.config.rarity == null ? 0xE070FF : this.config.rarity.rgb() & 0xFFFFFF;
      // oscurece un poco el santuario, sobre todo abajo
      g.fillGradient(0, 0, this.width, this.height / 2, (int)(0x70 * appear) << 24, (int)(0x40 * appear) << 24);
      g.fillGradient(0, this.height / 2, this.width, this.height, (int)(0x40 * appear) << 24, (int)(0x90 * appear) << 24);
      int py = this.panelY + Math.round((1.0F - appear) * 8.0F);
      int dy = py - this.panelY;
      // el panel de cristal: oscuro y translúcido, con un borde fino del color de la caja
      glass(g, this.panelX, py, this.panelW, this.panelH, appear, accent);
      String crate = LootEngine.colorize(this.config.displayName == null ? "" : this.config.displayName).replaceAll("§.", "");
      g.drawCenteredString(this.font, "Recompensas", this.width / 2, py + 9, alpha(0xFFFFFF, appear));
      String sub = (crate.isBlank() ? "" : crate + "  ·  ") + this.cells.size() + (this.cells.size() == 1 ? " premio" : " premios");
      g.pose().pushPose();
      g.pose().translate(this.width / 2.0F, py + 21, 0.0F);
      g.pose().scale(0.8F, 0.8F, 1.0F);
      g.drawCenteredString(this.font, sub, 0, 0, alpha(0xA8B4B2, appear));
      g.pose().popPose();
      int lineW = this.panelW - 40;
      for (int i = 0; i < 20; i++) {
         float f = 1.0F - Math.abs((i + 0.5F) / 20.0F - 0.5F) * 2.0F;
         g.fill(this.panelX + 20 + lineW * i / 20, py + 31, this.panelX + 20 + lineW * (i + 1) / 20, py + 32, (int)(0x50 * f * appear) << 24 | accent);
      }

      Cell hovered = null;
      if (this.cells.isEmpty()) {
         g.drawCenteredString(this.font, "Esta caja no tiene premios", this.width / 2, this.gridY + dy + this.gridH / 2 - 4, alpha(0x8A9492, appear));
      } else {
         int top = this.gridY + dy;
         g.enableScissor(this.panelX + 2, top - 2, this.panelX + this.panelW - 2, top + this.gridH + 2);
         int off = Math.round(this.scroll);
         for (int i = 0; i < this.cells.size(); i++) {
            Cell c = this.cells.get(i);
            int cx = this.gridX + i % this.cols * (CELL + GAP);
            int cy = top + i / this.cols * (CELL + GAP) - off;
            if (cy + CELL < top - 2 || cy > top + this.gridH + 2) {
               continue;
            }

            boolean over = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL && mouseY >= top && mouseY < top + this.gridH;
            if (over) {
               hovered = c;
            }

            this.cell(g, cx, cy, c, over, appear);
         }

         g.disableScissor();
         if (this.maxScroll() > 0.0F) {
            // la barrita de la derecha: dónde estás
            int trackX = this.panelX + this.panelW - 7;
            g.fill(trackX, top, trackX + 2, top + this.gridH, 0x20FFFFFF);
            int thumbH = Math.max(12, this.gridH * this.gridH / this.contentH());
            int thumbY = top + Math.round((this.gridH - thumbH) * (this.scroll / this.maxScroll()));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x90000000 | accent);
         }
      }

      super.render(g, mouseX, mouseY, partialTick);
      if (hovered != null) {
         this.tooltip(g, hovered, mouseX, mouseY);
      }
   }

   private static void glass(GuiGraphics g, int x, int y, int w, int h, float a, int accent) {
      for (int ring = 3; ring >= 1; ring--) {
         g.fill(x - ring, y - ring + 1, x + w + ring, y + h + ring, (int)(14 * ring * a) << 24);
      }

      int top = (int)(0xB0 * a) << 24 | 0x0D1719, bottom = (int)(0xC8 * a) << 24 | 0x060B0C;
      g.fillGradient(x + 1, y, x + w - 1, y + h, top, bottom);
      g.fillGradient(x, y + 1, x + 1, y + h - 1, top, bottom);
      g.fillGradient(x + w - 1, y + 1, x + w, y + h - 1, top, bottom);
      int b = (int)(0x48 * a) << 24 | mixRgb(0xFFFFFF, accent, 0.35F);
      g.fill(x + 1, y, x + w - 1, y + 1, b);
      g.fill(x + 1, y + h - 1, x + w - 1, y + h, b);
      g.fill(x, y + 1, x + 1, y + h - 1, b);
      g.fill(x + w - 1, y + 1, x + w, y + h - 1, b);
      // un reflejo suave arriba
      g.fillGradient(x + 1, y + 1, x + w - 1, y + 14, (int)(0x14 * a) << 24 | 0xFFFFFF, 0);
   }

   private void cell(GuiGraphics g, int x, int y, Cell c, boolean over, float a) {
      int rc = c.rarityColor();
      // la casilla: oscura, teñida de su rareza, con su borde fino; al pasar el ratón, se enciende
      int fillTop = (int)((over ? 0x70 : 0x48) * a) << 24 | mixRgb(0x0A1214, rc, over ? 0.30F : 0.16F);
      int fillBot = (int)((over ? 0x90 : 0x68) * a) << 24 | mixRgb(0x050909, rc, over ? 0.18F : 0.08F);
      g.fillGradient(x + 1, y, x + CELL - 1, y + CELL, fillTop, fillBot);
      g.fillGradient(x, y + 1, x + 1, y + CELL - 1, fillTop, fillBot);
      g.fillGradient(x + CELL - 1, y + 1, x + CELL, y + CELL - 1, fillTop, fillBot);
      int ba = (int)((over ? 0xE0 : 0x70) * a);
      g.fill(x + 1, y, x + CELL - 1, y + 1, ba << 24 | rc);
      g.fill(x + 1, y + CELL - 1, x + CELL - 1, y + CELL, ba << 24 | rc);
      g.fill(x, y + 1, x + 1, y + CELL - 1, ba << 24 | rc);
      g.fill(x + CELL - 1, y + 1, x + CELL, y + CELL - 1, ba << 24 | rc);
      if (a > 0.5F) {
         g.renderItem(c.icon(), x + (CELL - 16) / 2, y + (CELL - 16) / 2);
         g.renderItemDecorations(this.font, c.icon(), x + (CELL - 16) / 2, y + (CELL - 16) / 2);
      }
   }

   private void tooltip(GuiGraphics g, Cell c, int mouseX, int mouseY) {
      List<Component> lines = new ArrayList<>();
      lines.add(Component.literal(c.name()).withStyle(s -> s.withColor(mixRgb(c.rarityColor(), 0xFFFFFF, 0.25F))));
      String info = c.rarity() + (c.odds().isEmpty() ? "" : "  ·  " + c.odds());
      lines.add(Component.literal(info).withStyle(s -> s.withColor(c.rarityColor())));
      if (hasDetails(c.icon()) && this.minecraft != null) {
         List<Component> raw = c.icon().getTooltipLines(this.minecraft.player, Default.NORMAL);
         for (int i = 1; i < raw.size() && lines.size() < 14; i++) {
            lines.add(raw.get(i));
         }
      }

      g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
   }

   private static boolean hasDetails(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (CrateItems.isKey(stack) || CrateItems.isUniqueKey(stack) || CrateItems.isCrate(stack)) {
         return false;
      } else if (stack.isEnchanted()) {
         return true;
      } else {
         CompoundTag tag = stack.getTag();
         return tag != null
            && (tag.contains("StoredEnchantments") || tag.contains("AttributeModifiers") || tag.contains("CustomPotionEffects")
               || tag.contains("Potion") || tag.getCompound("display").contains("Lore"));
      }
   }

   private static int alpha(int rgb, float a) {
      return Math.max(4, (int)(255 * a)) << 24 | rgb & 0xFFFFFF;
   }

   private static int mixRgb(int a, int b, float t) {
      int r = (int)(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
      int gg = (int)(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
      int bl = (int)((a & 255) + ((b & 255) - (a & 255)) * t);
      return r << 16 | gg << 8 | bl;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.maxScroll() > 0.0F) {
         this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget - (float)delta * (CELL + GAP)));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, delta);
      }
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static record Cell(ItemStack icon, String name, String rarity, int rarityColor, String odds) {
   }
}
