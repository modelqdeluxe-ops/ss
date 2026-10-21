package net.tierrasfantasticas.tfclient.crates.client.screen;

import net.tierrasfantasticas.tfclient.crates.client.media.CrateMedia;
import net.tierrasfantasticas.tfclient.crates.client.widget.FSButton;
import net.tierrasfantasticas.tfclient.crates.client.widget.ScrollbarDrag;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.config.RewardEntry;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag.Default;

public class CratePoolScreen extends Screen {
   private static final int ROW_HEIGHT = 20;
   private static final int BAR_WIDTH = 5;
   private final CrateConfig config;
   private final Screen parent;
   private int scroll;
   private final List<CratePoolScreen.Row> rows = new ArrayList<>();
   private final ScrollbarDrag scrollbarDrag = new ScrollbarDrag();
   private int leftPos;
   private int topPos;
   private int panelWidth;
   private int panelHeight;
   private ItemStack detailStack = ItemStack.EMPTY;
   private int detailWidth;
   private static final int DETAIL_WIDTH = 166;
   private static final int DETAIL_GAP = 6;
   private static final int MIN_DETAIL_WIDTH = 104;
   private static final float SHIFT_MS = 170.0F;
   private int centeredLeft;
   private int shiftedLeft;
   private float shift;
   private long lastFrameMs = System.currentTimeMillis();
   private FSButton closeButton;

   private static boolean hasDetails(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else if (CrateItems.isKey(stack) || CrateItems.isUniqueKey(stack) || CrateItems.isCrate(stack)) {
         return false;
      } else if (stack.isEnchanted()) {
         return true;
      } else {
         CompoundTag tag = stack.getTag();
         return tag == null
            ? false
            : tag.contains("StoredEnchantments")
               || tag.contains("AttributeModifiers")
               || tag.contains("CustomPotionEffects")
               || tag.contains("Potion")
               || tag.getCompound("display").contains("Lore");
      }
   }

   public Screen parentScreen() {
      return this.parent;
   }

   public CratePoolScreen(CrateConfig config, Screen parent) {
      super(Component.literal("Recompensas"));
      this.config = config == null ? new CrateConfig() : config;
      this.parent = parent;
   }

   protected void init() {
      this.panelHeight = Math.min(this.height - 20, 220);
      this.topPos = (this.height - this.panelHeight) / 2;
      this.panelWidth = Math.min(this.width - 20, 330);
      this.centeredLeft = (this.width - this.panelWidth) / 2;
      int room = this.width - this.panelWidth - 6 - 8;
      this.detailWidth = room >= 104 ? Math.min(166, room) : 0;
      this.shiftedLeft = this.detailWidth > 0 ? Math.max(4, (this.width - (this.panelWidth + 6 + this.detailWidth)) / 2) : this.centeredLeft;
      this.leftPos = Math.round((float)this.centeredLeft + (float)(this.shiftedLeft - this.centeredLeft) * this.shift);
      int closeWidth = Math.max(70, this.font.width("Cerrar") + 30);
      this.closeButton = (FSButton)this.addRenderableWidget(
         new FSButton(
            this.leftPos + (this.panelWidth - closeWidth) / 2,
            this.topPos + this.panelHeight - 26,
            closeWidth,
            20,
            Component.literal("Cerrar"),
            -11884320,
            this::onClose
         )
      );
      this.rebuildRows();
   }

   private void rebuildRows() {
      this.rows.clear();
      List<RewardEntry> ordered = new ArrayList<>();

      for (RewardEntry entry : this.config.rewards) {
         if (entry.type == RewardEntry.Type.ITEM) {
            ordered.add(entry);
         }
      }

      ordered.sort(
         Comparator.<RewardEntry>comparingInt(e -> -e.effectiveRarity(this.config.rarity).ordinal()).thenComparing(e -> labelOf(e).toLowerCase(Locale.ROOT))
      );

      for (RewardEntry entryx : ordered) {
         String label = labelOf(entryx);
         Rarity rarity = entryx.effectiveRarity(this.config.rarity);
         String odds = this.config.showOddsInPool ? String.format(Locale.ROOT, "%.2f%%", this.config.normalizedPercentInPool(entryx)) : "";
         this.rows.add(new CratePoolScreen.Row(entryx.item, label, rarity.displayName(), rarity.rgb(), odds));
      }

      this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll));
   }

   private static String labelOf(RewardEntry entry) {
      if (entry.label != null && !entry.label.isBlank()) {
         return entry.label;
      } else {
         return entry.item != null && !entry.item.isEmpty() ? entry.item.getHoverName().getString() : "(item vacío)";
      }
   }

   private int listTop() {
      return this.topPos + 22;
   }

   private int listHeight() {
      return this.panelHeight - 22 - 30;
   }

   private int visibleRows() {
      return Math.max(1, this.listHeight() / 20);
   }

   private int maxScroll() {
      return Math.max(0, this.rows.size() - this.visibleRows());
   }

   private int barX() {
      return this.leftPos + this.panelWidth - 9 - 5;
   }

   private int thumbHeight() {
      return ScrollbarDrag.thumbHeight(this.listHeight(), this.visibleRows(), this.rows.size());
   }

   private int thumbTop() {
      return ScrollbarDrag.thumbTop(this.listTop(), this.listHeight(), this.thumbHeight(), this.scroll, this.maxScroll());
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      // (detrás, lo mismo que en la pantalla de antes de abrir: el santuario o el vídeo de la caja)
      boolean drawn = this.parent instanceof CratePreOpenScreen pre && pre.renderSanctuary(g, this.width, this.height);
      if (!drawn && CrateMedia.isActive()) {
         CrateMedia.renderBackground(g, this.width, this.height);
      }

      this.detailStack = this.pickDetail(mouseX, mouseY);
      this.updateShift(!this.detailStack.isEmpty());
      this.leftPos = Math.round((float)this.centeredLeft + (float)(this.shiftedLeft - this.centeredLeft) * this.shift);
      if (this.closeButton != null) {
         this.closeButton.setX(this.leftPos + (this.panelWidth - this.closeButton.getWidth()) / 2);
      }

      g.fill(0, 0, this.width, this.height, 1627389952);
      FSGui.panel(g, this.leftPos, this.topPos, this.panelWidth, this.panelHeight);
      String title = "§d✦ §fRecompensas §8(" + this.rows.size() + ")";
      g.drawString(this.font, title, this.leftPos + 9, this.topPos + 9, 16777215, false);
      this.fadeLine(g, this.leftPos + 8, this.topPos + 20, this.panelWidth - 16);
      this.renderRows(g, mouseX, mouseY);
      this.renderDetails(g);
      super.render(g, mouseX, mouseY, partialTick);
   }

   private ItemStack pickDetail(int mouseX, int mouseY) {
      if (this.detailWidth > 0 && !this.rows.isEmpty()) {
         int listTop = this.listTop();
         if (mouseY >= listTop && mouseY < listTop + this.listHeight()) {
            int bandLeft = Math.min(this.centeredLeft, this.shiftedLeft);
            int bandRight = Math.max(this.centeredLeft, this.shiftedLeft) + this.panelWidth;
            if (mouseX >= bandLeft && mouseX < bandRight) {
               int index = this.scroll + (mouseY - listTop) / 20;
               if (index >= 0 && index < this.rows.size()) {
                  ItemStack stack = this.rows.get(index).icon();
                  return hasDetails(stack) ? stack : ItemStack.EMPTY;
               } else {
                  return ItemStack.EMPTY;
               }
            } else {
               return ItemStack.EMPTY;
            }
         } else {
            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   private void updateShift(boolean wantsDetails) {
      long now = System.currentTimeMillis();
      float step = Math.min(1.0F, Math.max(0.0F, (float)(now - this.lastFrameMs) / 170.0F));
      this.lastFrameMs = now;
      this.shift = Math.max(0.0F, Math.min(1.0F, this.shift + (wantsDetails ? step : -step)));
   }

   private void renderDetails(GuiGraphics g) {
      ItemStack stack = this.detailStack;
      if (stack != null && !stack.isEmpty() && this.minecraft != null) {
         if (this.detailWidth > 0 && !(this.shift < 0.55F)) {
            List<Component> raw = stack.getTooltipLines(this.minecraft.player, Default.NORMAL);
            if (!raw.isEmpty()) {
               int padding = 7;
               int step = 9 + 1;
               int maxText = this.detailWidth - padding * 2;
               int maxLines = Math.max(4, (this.height - 16 - padding * 2) / step);
               List<FormattedCharSequence> lines = new ArrayList<>();
               boolean cut = false;

               label54:
               for (Component line : raw) {
                  Iterator x = this.font.split(line, maxText).iterator();

                  while (true) {
                     if (x.hasNext()) {
                        FormattedCharSequence part = (FormattedCharSequence)x.next();
                        if (lines.size() < maxLines) {
                           lines.add(part);
                           continue;
                        }

                        cut = true;
                     }

                     if (cut) {
                        break label54;
                     }
                     break;
                  }
               }

               if (!lines.isEmpty()) {
                  if (cut) {
                     lines.set(lines.size() - 1, Component.literal("§8...").getVisualOrderText());
                  }

                  int boxWidth = this.detailWidth;
                  int boxHeight = lines.size() * step + padding * 2 - 1;
                  int x = this.leftPos + this.panelWidth + 6;
                  int y = this.topPos + (this.panelHeight - boxHeight) / 2;
                  y = Math.max(4, Math.min(y, this.height - boxHeight - 4));
                  g.pose().pushPose();
                  g.pose().translate(0.0F, 0.0F, 400.0F);
                  FSGui.panel(g, x, y, boxWidth, boxHeight);
                  int textY = y + padding;

                  for (FormattedCharSequence line : lines) {
                     g.drawString(this.font, line, x + padding, textY, -1644826);
                     textY += step;
                  }

                  g.flush();
                  g.pose().popPose();
               }
            }
         }
      }
   }

   private void fadeLine(GuiGraphics g, int x, int y, int width) {
      int segments = 24;

      for (int i = 0; i < segments; i++) {
         int x1 = x + width * i / segments;
         int x2 = x + width * (i + 1) / segments;
         if (x2 > x1) {
            float t = ((float)i + 0.5F) / (float)segments;
            float strength = 1.0F - Math.abs(t - 0.5F) * 2.0F;
            int alpha = (int)(46.0F * strength);
            if (alpha > 1) {
               g.fill(x1, y, x2, y + 1, alpha << 24 | 16777215);
            }
         }
      }
   }

   private void renderRows(GuiGraphics g, int mouseX, int mouseY) {
      int listTop = this.listTop();
      int listBottom = listTop + this.listHeight();
      boolean scrollable = this.maxScroll() > 0;
      int rowsLeft = this.leftPos + 9;
      int rowsWide = this.panelWidth - 18 - (scrollable ? 8 : 0);
      FSGui.inset(g, rowsLeft - 1, listTop - 1, rowsWide + 2, this.listHeight() + 2);
      if (this.rows.isEmpty()) {
         g.drawCenteredString(this.font, "§8(sin items en el pool)", this.leftPos + this.panelWidth / 2, listTop + this.listHeight() / 2 - 4, 11184810);
      } else {
         g.enableScissor(rowsLeft, listTop, rowsLeft + rowsWide, listBottom);
         int visible = this.visibleRows();

         for (int i = 0; i < visible; i++) {
            int index = this.scroll + i;
            if (index < 0 || index >= this.rows.size()) {
               break;
            }

            CratePoolScreen.Row row = this.rows.get(index);
            int rowY = listTop + i * 20;
            int textY = rowY + 6;
            boolean hovered = mouseX >= rowsLeft && mouseX < rowsLeft + rowsWide && mouseY >= rowY && mouseY < rowY + 20;
            if (hovered) {
               g.fillGradient(rowsLeft, rowY, rowsLeft + rowsWide, rowY + 20, 654311423, 268435455);
               g.fill(rowsLeft, rowY, rowsLeft + 1, rowY + 20, 1724939496);
            } else if ((index & 1) == 1) {
               g.fill(rowsLeft, rowY, rowsLeft + rowsWide, rowY + 20, 184549375);
            }

            if (row.icon() != null && !row.icon().isEmpty()) {
               g.renderItem(row.icon(), rowsLeft + 2, rowY + 2);
            }

            int right = rowsLeft + rowsWide - 3;
            if (!row.odds().isEmpty()) {
               int oddsWidth = this.font.width(row.odds());
               g.drawString(this.font, row.odds(), right - oddsWidth, textY, 11184810, false);
               right -= oddsWidth + 6;
            }

            int rarityWidth = this.font.width(row.rarity());
            g.drawString(this.font, row.rarity(), right - rarityWidth, textY, row.rarityColor(), false);
            right -= rarityWidth + 6;
            int nameX = rowsLeft + 22;
            String name = this.font.plainSubstrByWidth(row.name(), Math.max(12, right - nameX));
            g.drawString(this.font, name, nameX, textY, 14737632, false);
         }

         g.disableScissor();
         if (scrollable) {
            boolean onThumb = this.scrollbarDrag.isDragging()
               || ScrollbarDrag.overThumb((double)mouseX, (double)mouseY, this.barX(), 5, this.thumbTop(), this.thumbHeight());
            FSGui.scrollbar(g, this.barX(), listTop, 5, this.listHeight(), this.thumbTop(), this.thumbHeight(), onThumb);
         }
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (ScrollbarDrag.isDragButton(button)
         && this.maxScroll() > 0
         && ScrollbarDrag.overTrack(mouseX, mouseY, this.barX(), 5, this.listTop(), this.listHeight())) {
         this.scroll = this.scrollbarDrag.beginOnTrack(mouseY, this.scroll, this.listTop(), this.listHeight(), this.thumbHeight(), this.maxScroll());
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.scrollbarDrag.isDragging()) {
         this.scroll = this.scrollbarDrag.drag(mouseY, this.listHeight(), this.thumbHeight(), this.maxScroll());
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.scrollbarDrag.isDragging()) {
         this.scrollbarDrag.end();
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.maxScroll() > 0) {
         this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int)Math.signum(delta)));
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

   private static record Row(ItemStack icon, String name, String rarity, int rarityColor, String odds) {
   }
}
