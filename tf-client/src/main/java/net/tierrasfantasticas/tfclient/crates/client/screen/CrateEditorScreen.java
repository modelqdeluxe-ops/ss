/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.screen;

import net.tierrasfantasticas.tfclient.crates.animation.AnimationRegistry;
import net.tierrasfantasticas.tfclient.crates.animation.CrateAnimation;
import net.tierrasfantasticas.tfclient.crates.client.RegistryLists;
import net.tierrasfantasticas.tfclient.crates.client.color.FSColorPickerScreen;
import net.tierrasfantasticas.tfclient.crates.client.color.FSRainbow;
import net.tierrasfantasticas.tfclient.crates.client.color.FSRainbowPickerScreen;
import net.tierrasfantasticas.tfclient.crates.client.color.FSTextStyle;
import net.tierrasfantasticas.tfclient.crates.client.color.FSTextStyleScreen;
import net.tierrasfantasticas.tfclient.crates.client.media.MediaCache;
import net.tierrasfantasticas.tfclient.crates.client.render.CrateStyles;
import net.tierrasfantasticas.tfclient.crates.client.widget.ScrollMemory;
import net.tierrasfantasticas.tfclient.crates.client.widget.ScrollSelector;
import net.tierrasfantasticas.tfclient.crates.client.widget.ScrollbarDrag;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.ParticleLayer;
import net.tierrasfantasticas.tfclient.crates.config.ParticleNames;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.config.RewardEntry;
import net.tierrasfantasticas.tfclient.crates.item.KeyModels;
import net.tierrasfantasticas.tfclient.crates.network.FSNetwork;
import net.tierrasfantasticas.tfclient.crates.network.SaveCratePacket;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class CrateEditorScreen extends Screen {
   private final CrateConfig config;
   private final BlockPos boundPos;
   private CrateEditorScreen.Tab activeTab = CrateEditorScreen.Tab.INFO;
   private final List<CrateEditorScreen.Label> labels = new ArrayList<>();
   private final List<CrateEditorScreen.TooltipZone> tooltipZones = new ArrayList<>();
   private String helpLine = "";
   private int leftPos;
   private int topPos;
   private int panelWidth;
   private int panelHeight;
   private RewardEntry selectedReward;
   private ParticleLayer selectedLayer;
   private int probScroll;
   private static final String COLOR_CHARS = "f7e6cab9d5234180";
   private static final int TAB_ROW_HEIGHT = 20;
   private static final int TAB_GAP = 2;
   private int headerRowCount = 2;
   private static final int PROB_BAR_WIDTH = 5;
   private final ScrollbarDrag probDrag = new ScrollbarDrag();
   private final Map<String, ScrollMemory> scrollMemories = new HashMap<>();
   private int lastMouseX;
   private int lastMouseY;
   private String selectedMediaUrl;
   private boolean rewardsFromInventory;
   private FSTextStyle selectedSceneLine;

   private ScrollMemory mem(String id) {
      return this.scrollMemories.computeIfAbsent(id, key -> new ScrollMemory());
   }

   private List<ItemStack> playerInventory() {
      List<ItemStack> out = new ArrayList<>();
      if (this.minecraft != null && this.minecraft.player != null) {
         for (ItemStack stack : this.minecraft.player.getInventory().items) {
            if (stack != null && !stack.isEmpty()) {
               out.add(stack);
            }
         }
      }

      return out;
   }

   public CrateEditorScreen(CrateConfig config) {
      this(config, null);
   }

   public CrateEditorScreen(CrateConfig config, BlockPos boundPos) {
      super(Component.literal("Editor de Crate"));
      this.config = config == null ? new CrateConfig() : config;
      this.boundPos = boundPos;
   }

   protected void init() {
      this.panelWidth = Math.min(this.width - 16, 540);
      this.headerRowCount = Math.max(1, this.packTabs().size());
      this.panelHeight = Math.min(this.height - 16, 320 + (this.headerRowCount - 1) * 20);
      this.leftPos = (this.width - this.panelWidth) / 2;
      this.topPos = (this.height - this.panelHeight) / 2;
      this.labels.clear();
      this.tooltipZones.clear();
      this.initHeader();
      this.initFooter();
      switch (this.activeTab) {
         case INFO:
            this.initInfo();
            break;
         case REWARDS:
            this.initRewards();
            break;
         case PROBABILITY:
            this.initProbability();
            break;
         case APPEARANCE:
            this.initAppearance();
            break;
         case STYLE:
            this.initStyle();
            break;
         case PARTICLES:
            this.initParticles();
            break;
         case KEY:
            this.initKey();
            break;
         case KEYMODEL:
            this.initKeyModel();
            break;
         case SETTINGS:
            this.initSettings();
            break;
         case VIDEOS:
            this.initVideos();
            break;
         case MUSICA:
            this.initMusica();
            break;
         case MENSAJES:
            this.initMensajes();
      }
   }

   private void initMensajes() {
      this.helpLine = "Textos de la escena. Botones de color/arcoiris a la derecha de cada campo.";
      int x = this.bodyX();
      int w = this.bodyW();
      int y = this.bodyY();
      int bottom = this.bodyY() + this.bodyH();
      int fxW = Math.max(44, this.font.width("■✦") + 26);
      int fieldW = Math.max(60, w - fxW - 4);
      this.addLabel(
         "§7Línea de arriba §8(encima del nombre)",
         x,
         y,
         desc("Sale encima del nombre de la caja.", "El estilo va aparte: usa §b■§7 para color y §d✦§7 para arcoiris.")
      );
      y += 10;
      y += this.addStyledField(x, y, fieldW, fxW, this.config.sceneHeader, "§8✦ TF Crates ✦");
      this.addLabel("§7Nombre de la caja §8(igual que en Info)", x, y, null);
      y += 10;
      FSTextStyle nameProxy = this.config.nameStyle == null ? (this.config.nameStyle = new FSTextStyle()) : this.config.nameStyle;
      nameProxy.text = this.config.displayName == null ? "" : this.config.displayName;
      y += this.addStyledField(x, y, fieldW, fxW, nameProxy, "§8Nombre de la caja", v -> this.config.displayName = v);
      this.addLabel("§7Línea de abajo §8(el \"prepárate para abrir\")", x, y, null);
      y += 10;
      y += this.addStyledField(x, y, fieldW, fxW, this.config.sceneSubtitle, "§8Prepárate para abrir tu caja");
      this.addLabel(
         "§7Mensaje extra §8(" + this.config.sceneLines.size() + " línea(s))",
         x,
         y,
         desc("Líneas libres debajo del subtítulo.", "Puedes añadir todas las que quieras.")
      );
      y += 10;
      int actionsY = bottom - 16;
      int addRowY = actionsY - 20;
      int listH = Math.max(0, addRowY - 4 - y);
      if (listH >= 14) {
         ScrollSelector<FSTextStyle> lines = new ScrollSelector<>(x, y, w, listH, 14, l -> l.text, l -> l.text, null);
         lines.remember(this.mem("lineasEscena"));
         lines.setItems(new ArrayList<>(this.config.sceneLines));
         lines.onSelect(line -> this.selectedSceneLine = line);
         this.addRenderableWidget(lines);
      }

      int addW = Math.max(60, this.font.width("Añadir") + 18);
      EditBox extra = new EditBox(this.font, x, addRowY, Math.max(50, w - addW - 4), 16, Component.empty());
      extra.setMaxLength(200);
      extra.setHint(Component.literal("§8Escribe y pulsa Añadir"));
      this.addRenderableWidget(extra);
      this.addRenderableWidget(Button.builder(Component.literal("§aAñadir"), b -> {
         String value = extra.getValue() == null ? "" : extra.getValue();
         if (!value.isBlank()) {
            this.config.sceneLines.add(new FSTextStyle(value, "#AAAAAA"));
            extra.setValue("");
            this.selectedSceneLine = null;
            this.rebuildWidgets();
         }
      }).bounds(x + w - addW, addRowY, addW, 16).build());
      int third = (w - 8) / 3;
      boolean has = this.selectedSceneLine != null;
      this.addRenderableWidget(Button.builder(Component.literal(has ? "§bColor" : "§8Color"), b -> {
         if (this.selectedSceneLine != null) {
            this.openColorPicker(this.selectedSceneLine);
         }
      }).bounds(x, actionsY, third, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal(has ? "§d✦ Arcoiris" : "§8✦ Arcoiris"), b -> {
         if (this.selectedSceneLine != null) {
            this.openRainbowPicker(this.selectedSceneLine);
         }
      }).bounds(x + third + 4, actionsY, third, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal(has ? "§cQuitar" : "§8Quitar"), b -> {
         if (this.selectedSceneLine != null) {
            this.config.sceneLines.remove(this.selectedSceneLine);
            this.selectedSceneLine = null;
            this.rebuildWidgets();
         }
      }).bounds(x + 2 * (third + 4), actionsY, w - 2 * (third + 4), 16).build());
   }

   private int addStyledField(int x, int y, int fieldW, int fxW, FSTextStyle style, String hint) {
      return this.addStyledField(x, y, fieldW, fxW, style, hint, null);
   }

   private int addStyledField(int x, int y, int fieldW, int fxW, FSTextStyle style, String hint, Consumer<String> alsoSet) {
      EditBox box = new EditBox(this.font, x, y, fieldW, 16, Component.empty());
      box.setMaxLength(200);
      box.setValue(style.text == null ? "" : style.text);
      box.setHint(Component.literal(hint));
      box.setResponder(value -> {
         style.text = value;
         if (alsoSet != null) {
            alsoSet.accept(value);
         }
      });
      this.addRenderableWidget(box);
      int half = (fxW - 2) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("§b■"), b -> this.openColorPicker(style)).bounds(x + fieldW + 4, y, half, 16).build());
      this.tooltipZones
         .add(
            new CrateEditorScreen.TooltipZone(
               x + fieldW + 4,
               y,
               half,
               16,
               desc(
                  "§bColor y estilo",
                  "§7Rueda de tono, RGB, hexadecimal, paleta",
                  "§7y negrita / cursiva / subrayado / tachado.",
                  "",
                  "§8Ahora: " + style.describe()
               )
            )
         );
      this.addRenderableWidget(
         Button.builder(Component.literal(style.rainbow ? "§d✦" : "§8✦"), b -> this.openRainbowPicker(style))
            .bounds(x + fieldW + 6 + half, y, fxW - half - 2, 16)
            .build()
      );
      this.tooltipZones
         .add(
            new CrateEditorScreen.TooltipZone(
               x + fieldW + 6 + half,
               y,
               fxW - half - 2,
               16,
               desc(
                  "§d✦ Arcoiris",
                  "§7" + FSRainbow.count() + " estilos animados con vista previa.",
                  "",
                  style.rainbow ? "§aActivo: " + FSRainbow.name(style.rainbowStyle) : "§8Desactivado"
               )
            )
         );
      return 20;
   }

   private void openColorPicker(FSTextStyle style) {
      boolean[] flags = new boolean[]{style.bold, style.italic, style.underline, style.strikethrough};
      this.minecraft.setScreen(new FSColorPickerScreen("Color y estilo", style.text, style.colorRgb(), flags, (rgb, bold, italic, underline, strike) -> {
         style.color = String.format("#%06X", rgb & 16777215);
         style.bold = bold;
         style.italic = italic;
         style.underline = underline;
         style.strikethrough = strike;
         style.rainbow = false;
      }, () -> {
         this.minecraft.setScreen(this);
         this.rebuildWidgets();
      }));
   }

   private void openRainbowPicker(FSTextStyle style) {
      this.minecraft.setScreen(new FSRainbowPickerScreen(Math.max(0, style.rainbowStyle), picked -> {
         style.rainbow = true;
         style.rainbowStyle = picked;
      }, () -> {
         this.minecraft.setScreen(this);
         this.rebuildWidgets();
      }));
   }

   private int bodyX() {
      return this.leftPos + 8;
   }

   private int bodyY() {
      return this.topPos + 62 + (this.headerRowCount - 1) * 20;
   }

   private int bodyW() {
      return this.panelWidth - 16;
   }

   private int bodyH() {
      return this.panelHeight - 62 - 28 - (this.headerRowCount - 1) * 20;
   }

   private List<List<CrateEditorScreen.Tab>> packTabs() {
      CrateEditorScreen.Tab[] tabs = CrateEditorScreen.Tab.values();
      int available = this.panelWidth - 16;
      int widest = 0;

      for (CrateEditorScreen.Tab tab : tabs) {
         widest = Math.max(widest, this.font.width(tab.label));
      }

      int minTab = Math.min(Math.max(38, widest + 10), Math.max(38, available / 4));
      int perRow = Math.max(1, Math.min(tabs.length, (available + 2) / (minTab + 2)));
      int rowCount = (tabs.length + perRow - 1) / perRow;
      perRow = (tabs.length + rowCount - 1) / rowCount;
      List<List<CrateEditorScreen.Tab>> rows = new ArrayList<>();

      for (int i = 0; i < tabs.length; i += perRow) {
         List<CrateEditorScreen.Tab> row = new ArrayList<>();

         for (int j = i; j < Math.min(tabs.length, i + perRow); j++) {
            row.add(tabs[j]);
         }

         rows.add(row);
      }

      return rows;
   }

   private void initHeader() {
      List<List<CrateEditorScreen.Tab>> rows = this.packTabs();
      int available = this.panelWidth - 16;

      for (int r = 0; r < rows.size(); r++) {
         List<CrateEditorScreen.Tab> row = rows.get(r);
         int count = row.size();
         int width = (available - 2 * (count - 1)) / count;
         int y = this.topPos + 24 + r * 20;

         for (int i = 0; i < count; i++) {
            CrateEditorScreen.Tab tab = row.get(i);
            int x = this.leftPos + 8 + i * (width + 2);
            boolean active = tab == this.activeTab;
            String label = tab.label;
            int room = width - 6;
            if (this.font.width(label) > room) {
               label = this.font.plainSubstrByWidth(label, Math.max(4, room));
            }

            String text = (active ? "§f§l" : "§7") + label;
            this.addRenderableWidget(Button.builder(Component.literal(text), b -> {
               this.activeTab = tab;
               this.rebuildWidgets();
            }).bounds(x, y, width, 18).build());
         }
      }
   }

   private void initFooter() {
      int w = 150;
      this.addRenderableWidget(Button.builder(Component.literal(this.boundPos != null ? "§aGuardar cambios" : "§aGuardar y Obtener"), b -> {
         FSNetwork.sendToServer(new SaveCratePacket(this.config.save(), this.boundPos));
         this.onClose();
      }).bounds(this.leftPos + this.panelWidth - 150 - 8, this.topPos + this.panelHeight - 24, 150, 18).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Cerrar"), b -> this.onClose())
            .bounds(this.leftPos + 8, this.topPos + this.panelHeight - 24, 80, 18)
            .build()
      );
   }

   private void initInfo() {
      this.helpLine = "Datos básicos: ID, nombre, tier y tiradas por apertura.";
      int x = this.bodyX();
      int y = this.bodyY();
      EditBox id = new EditBox(this.font, x + 170, y, 200, 16, Component.empty());
      id.setMaxLength(48);
      id.setValue(this.config.id);
      id.setResponder(s -> this.config.id = s.trim().toLowerCase().replace(' ', '_'));
      this.addRenderableWidget(id);
      this.addLabel("ID de la crate:", x, y + 4, desc("Identificador único (sin espacios).", "Se usa en /tf crates give, edit, delete.", "Ej: cofre_legendario"));
      String rawName = this.config.displayName == null ? "" : this.config.displayName;
      String plainName = stripCodes(rawName);
      int nameX = x + 170;
      int nameAvail = x + this.bodyW() - nameX;
      int styleBtnW = Math.max(84, Math.min(112, nameAvail - 150));
      int nameBoxW = Math.max(110, nameAvail - styleBtnW - 6);
      EditBox name = new EditBox(this.font, nameX, y + 24, nameBoxW, 16, Component.empty());
      name.setMaxLength(200);
      name.setValue(plainName);
      name.setResponder(s -> this.config.displayName = buildLegacy(s, colorOf(rawName), flagsOf(rawName)));
      this.addRenderableWidget(name);
      this.addLabel(
         "Nombre visible:",
         x,
         y + 28,
         desc("Escribe el nombre; pulsa «Color/estilo» para el MISMO editor de color", "y formato que usa el NBT de los items (rueda, RGB, hex, paleta).")
      );
      this.addRenderableWidget(
         Button.builder(
               Component.literal("§bColor/estilo »"),
               b -> {
                  if (this.minecraft != null) {
                     String plain = stripCodes(this.config.displayName);
                     int rgb0 = colorOf(this.config.displayName);
                     boolean[] flags0 = flagsOf(this.config.displayName);
                     this.minecraft
                        .setScreen(
                           new FSTextStyleScreen(
                              "Color y estilo del nombre",
                              plain,
                              rgb0,
                              flags0,
                              (col, bold, italic, underline, strike, obf) -> this.config.displayName = buildLegacy(
                                    plain, col, new boolean[]{bold, italic, underline, strike, obf}
                                 ),
                              () -> this.minecraft.setScreen(this)
                           )
                        );
                  }
               }
            )
            .bounds(nameX + nameBoxW + 6, y + 24, styleBtnW, 16)
            .build()
      );
      this.addIntField(
         x + 170,
         y + 48,
         60,
         this.config.rolls,
         v -> this.config.rolls = Math.max(1, v),
         "Tiradas por apertura:",
         x,
         y + 52,
         desc("Cuántas recompensas (por probabilidad) se entregan.", "Las garantizadas se suman aparte.")
      );
      this.addLabel(
         "§8La caja tiene TODAS las rarezas (config. en «Rarezas» y por item en «Premios»).",
         x,
         y + 74,
         desc(
            "Ya no hay 'rareza base': la rareza se decide al abrir",
            "según la tabla de la pestaña «Rarezas», y cada item",
            "tiene su propia rareza en «Premios»."
         )
      );
      this.addLabel(
         "§8Estilo: §b" + CrateStyles.displayName(this.config.styleId) + " §7→ pestaña «Diseño»",
         x,
         y + 96,
         desc("Diseño visual de la crate (independiente del tier).", "Cámbialo en la pestaña «Diseño».")
      );
      this.addLabel("§8Animación: §f" + AnimationRegistry.get(this.config.animationId).displayName(), x, y + 120, null);
      this.addLabel("§8Recompensas: §f" + this.config.rewards.size() + "  §8Capas de partículas: §f" + this.config.particleLayers.size(), x, y + 132, null);
   }

   private void initRewards() {
      this.helpLine = "Izquierda: busca y clic en un item. Derecha: lista (scroll) y editor de la seleccionada.";
      int x = this.bodyX();
      int y = this.bodyY();
      int colW = (this.bodyW() - 8) / 2;
      int rightX = x + colW + 8;
      EditBox search = new EditBox(this.font, x, y, colW, 16, Component.empty());
      boolean editingEffect = this.selectedReward != null
         && this.config.rewards.contains(this.selectedReward)
         && this.selectedReward.type == RewardEntry.Type.EFFECT;
      if (editingEffect) {
         RewardEntry er = this.selectedReward;
         search.setHint(Component.literal("Buscar efecto..."));
         this.addRenderableWidget(search);
         ScrollSelector<ResourceLocation> effects = new ScrollSelector<>(
            x,
            y + 20,
            colW,
            this.bodyH() - 22,
            16,
            rl -> (rl.toString().equals(er.effectId) ? "§a✔ " : "§f") + RegistryLists.effectName(rl),
            rl -> RegistryLists.effectName(rl) + " " + rl,
            rl -> ItemStack.EMPTY
         );
         effects.remember(this.mem("efectos"));
         effects.setItems(RegistryLists.effects());
         effects.onSelect(rl -> {
            er.effectId = rl.toString();
            this.rebuildWidgets();
         });
         search.setResponder(effects::setQuery);
         this.addRenderableWidget(effects);
      } else {
         int toggleW = Math.max(76, Math.max(this.font.width("Inventario"), this.font.width("Registro")) + 22);
         search = new EditBox(this.font, x, y, colW - toggleW - 4, 16, Component.empty());
         search.setHint(Component.literal(this.rewardsFromInventory ? "Buscar en tu inventario..." : "Buscar item..."));
         this.addRenderableWidget(search);
         this.addRenderableWidget(Button.builder(Component.literal(this.rewardsFromInventory ? "§e❖ Inventario" : "§b❖ Registro"), b -> {
            this.rewardsFromInventory = !this.rewardsFromInventory;
            this.rebuildWidgets();
         }).bounds(x + colW - toggleW, y, toggleW, 16).build());
         this.tooltipZones
            .add(
               new CrateEditorScreen.TooltipZone(
                  x + colW - toggleW,
                  y,
                  toggleW,
                  16,
                  desc(
                     "§bRegistro§7: todos los items del juego y de los mods.",
                     "§eInventario§7: los items que llevas encima.",
                     "",
                     "Cogerlo del inventario copia el item §fCON su NBT§7:",
                     "encantamientos, nombre, lore, atributos... tal cual."
                  )
               )
            );
         if (this.rewardsFromInventory) {
            ScrollSelector<ItemStack> inventory = new ScrollSelector<>(
               x,
               y + 20,
               colW,
               this.bodyH() - 22,
               18,
               st -> "§f" + st.getHoverName().getString() + (st.getCount() > 1 ? " §7x" + st.getCount() : ""),
               st -> st.getHoverName().getString() + " " + RegistryLists.itemId(st.getItem()),
               st -> st
            );
            inventory.remember(this.mem("inventario"));
            inventory.setItems(this.playerInventory());
            inventory.onSelect(st -> {
               RewardEntry r2 = new RewardEntry(RewardEntry.Type.ITEM);
               r2.item = st.copy();
               r2.label = st.getHoverName().getString();
               r2.minAmount = st.getCount();
               r2.maxAmount = st.getCount();
               r2.chance = 10.0;
               this.config.rewards.add(r2);
               this.selectedReward = r2;
               this.rebuildWidgets();
            });
            search.setResponder(inventory::setQuery);
            this.addRenderableWidget(inventory);
         } else {
            ScrollSelector<Item> items = new ScrollSelector<>(
               x,
               y + 20,
               colW,
               this.bodyH() - 22,
               18,
               RegistryLists::itemName,
               it -> RegistryLists.itemName(it) + " " + RegistryLists.itemId(it),
               it -> new ItemStack(it)
            );
            items.remember(this.mem("itemsRegistro"));
            items.setItems(RegistryLists.items());
            items.onSelect(it -> {
               RewardEntry r2 = new RewardEntry(RewardEntry.Type.ITEM);
               r2.item = new ItemStack(it);
               r2.label = RegistryLists.itemName(it);
               r2.chance = 10.0;
               this.config.rewards.add(r2);
               this.selectedReward = r2;
               this.rebuildWidgets();
            });
            search.setResponder(items::setQuery);
            this.addRenderableWidget(items);
         }
      }

      Object selR = null;
      ScrollSelector<RewardEntry> current = new ScrollSelector<>(
         rightX,
         y,
         colW,
         this.bodyH() - 98,
         16,
         rx -> (rx == this.selectedReward ? "§e▶ " : "§f")
               + rx.describe()
               + " "
               + rx.effectiveRarity(this.config.rarity).color()
               + "["
               + rx.effectiveRarity(this.config.rarity).displayName()
               + "] §7("
               + fmt(this.config.normalizedPercentInPool(rx))
               + "%)",
         RewardEntry::describe,
         rx -> rx.type == RewardEntry.Type.ITEM ? rx.item : ItemStack.EMPTY
      );
      current.remember(this.mem("premios"));
      current.setItems(new ArrayList<>(this.config.rewards));
      current.onSelect(rx -> {
         this.selectedReward = rx;
         this.rebuildWidgets();
      });
      this.addRenderableWidget(current);
      int addY = y + this.bodyH() - 94;
      this.addRenderableWidget(Button.builder(Component.literal("+ XP"), b -> {
         RewardEntry rx = new RewardEntry(RewardEntry.Type.XP);
         rx.xp = 100;
         this.config.rewards.add(rx);
         this.rebuildWidgets();
      }).bounds(rightX, addY, colW / 2 - 2, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal("+ Efecto"), b -> {
         this.config.rewards.add(new RewardEntry(RewardEntry.Type.EFFECT));
         this.rebuildWidgets();
      }).bounds(rightX + colW / 2, addY, colW / 2 - 2, 16).build());
      if (this.selectedReward != null && this.config.rewards.contains(this.selectedReward)) {
         RewardEntry r = this.selectedReward;
         int fy = y + this.bodyH() - 72;
         this.addDoubleField(
            rightX + 70,
            fy,
            50,
            r.chance,
            v -> r.chance = Math.max(0.0, v),
            "Prob. (%)",
            rightX,
            fy + 4,
            desc("Probabilidad en %. Se normaliza con las demás para sumar 100%.")
         );
         this.addIntField(rightX + 150, fy, 36, r.minAmount, v -> r.minAmount = Math.max(1, v), "Min", rightX + 122, fy + 4, desc("Cantidad mínima entregada."));
         this.addIntField(rightX + 235, fy, 36, r.maxAmount, v -> r.maxAmount = Math.max(1, v), "Max", rightX + 200, fy + 4, desc("Cantidad máxima entregada."));
         this.addToggle(rightX, fy + 22, colW - 70, r.guaranteed ? "Garantizada: Sí" : "Garantizada: No", r.guaranteed, () -> {
            r.guaranteed = !r.guaranteed;
            this.rebuildWidgets();
         }, desc("Si está activo, SIEMPRE se entrega (100%)."));
         this.addRenderableWidget(Button.builder(Component.literal("§cQuitar"), b -> {
            this.config.rewards.remove(r);
            this.selectedReward = null;
            this.rebuildWidgets();
         }).bounds(rightX + colW - 64, fy + 22, 64, 16).build());
         if (r.type == RewardEntry.Type.XP) {
            this.addIntField(rightX + 40, fy + 44, 80, r.xp, v -> r.xp = Math.max(0, v), "XP", rightX, fy + 48, desc("Puntos de experiencia entregados."));
         } else if (r.type == RewardEntry.Type.EFFECT) {
            this.addIntField(
               rightX + 44,
               fy + 44,
               40,
               r.effectAmplifier + 1,
               v -> r.effectAmplifier = Math.max(0, v - 1),
               "Nivel",
               rightX,
               fy + 48,
               desc("Nivel del efecto (1 = nivel I, 2 = nivel II...).", "Aumenta la potencia del efecto.")
            );
            this.addSecondsField(
               rightX + 175,
               fy + 44,
               50,
               r.effectDuration,
               v -> r.effectDuration = Math.max(1, v),
               "Duración (s)",
               rightX + 96,
               fy + 48,
               desc("Duración del efecto en segundos.", "Elige el efecto en la lista de la izquierda.")
            );
         } else if (r.type == RewardEntry.Type.ITEM) {
            int halfBtn = (colW - 4) / 2;
            Rarity itemR = r.effectiveRarity(this.config.rarity);
            String rarLabel = r.rarity != null && !r.rarity.isBlank() ? "Rareza: " + itemR.color() + itemR.displayName() : "§7Rareza: Auto";
            this.addRenderableWidget(Button.builder(Component.literal(rarLabel), b -> {
               r.rarity = cycleItemRarity(r.rarity);
               this.rebuildWidgets();
            }).bounds(rightX, fy + 44, halfBtn, 16).build());
            this.tooltipZones
               .add(
                  new CrateEditorScreen.TooltipZone(
                     rightX,
                     fy + 44,
                     halfBtn,
                     16,
                     desc(
                        "Rareza de ESTE item del pool.",
                        "Define el color de la luz, el sonido y las partículas",
                        "cuando este item es el premio. Auto = usa el tier de la crate."
                     )
                  )
               );
            this.addRenderableWidget(Button.builder(Component.literal("§b✎ NBT del item"), b -> {
               if (r.item != null && !r.item.isEmpty()) {
                  this.minecraft.setScreen(new NbtEditorScreen(this, r.item));
               }
            }).bounds(rightX + halfBtn + 4, fy + 44, colW - halfBtn - 4, 16).build());
            this.tooltipZones
               .add(
                  new CrateEditorScreen.TooltipZone(
                     rightX + halfBtn + 4,
                     fy + 44,
                     colW - halfBtn - 4,
                     16,
                     desc(
                        "Editor de NBT: nombre, lore con color,",
                        "encantamientos, atributos, irrompible, CustomModelData...",
                        "Todo manual, sin pegar comandos."
                     )
                  )
               );
         }
      }
   }

   private void initProbability() {
      this.helpLine = "Items AGRUPADOS por rareza. El % es DENTRO del pool de su rareza (cada rareza es independiente y full configurable).";
      int x = this.bodyX();
      int y = this.bodyY();
      List<Object> rows = this.probRows();
      int total = rows.size();
      int visibleRows = Math.max(1, this.bodyH() / 22);
      int maxScroll = Math.max(0, total - visibleRows);
      if (this.probScroll > maxScroll) {
         this.probScroll = maxScroll;
      }

      if (this.probScroll < 0) {
         this.probScroll = 0;
      }

      int end = Math.min(total, this.probScroll + visibleRows);

      for (int i = this.probScroll; i < end; i++) {
         Object row = rows.get(i);
         int ry = y + (i - this.probScroll) * 22;
         if (row instanceof RewardEntry) {
            RewardEntry r = (RewardEntry)row;
            if (!r.guaranteed) {
               this.addDoubleField(
                  x + 150,
                  ry,
                  50,
                  r.chance,
                  v -> r.chance = Math.max(0.0, v),
                  null,
                  0,
                  0,
                  desc("Peso del item DENTRO del pool de su rareza.", "Se normaliza SOLO con los demas items de ESA misma rareza.")
               );
            }
         }
      }

      this.addRenderableWidget(Button.builder(Component.literal("Igualar por rareza"), b -> {
         for (Rarity rar : Rarity.values()) {
            int n = 0;

            for (RewardEntry rx : this.config.rewards) {
               if (!rx.guaranteed && rx.effectiveRarity(this.config.rarity) == rar) {
                  n++;
               }
            }

            if (n > 0) {
               double each = 100.0 / (double)n;

               for (RewardEntry r2 : this.config.rewards) {
                  if (!r2.guaranteed && r2.effectiveRarity(this.config.rarity) == rar) {
                     r2.chance = each;
                  }
               }
            }
         }

         this.rebuildWidgets();
      }).bounds(this.leftPos + 92, this.topPos + this.panelHeight - 24, 130, 18).build());
   }

   private List<Object> probRows() {
      ArrayList<Object> rows = new ArrayList<>();

      for (Rarity rar : Rarity.values()) {
         boolean header = false;

         for (RewardEntry r : this.config.rewards) {
            if (r.effectiveRarity(this.config.rarity) == rar) {
               if (!header) {
                  rows.add(rar);
                  header = true;
               }

               rows.add(r);
            }
         }
      }

      return rows;
   }

   private void initAnimation() {
      this.helpLine = "Elige la animación del cofre. Ocurre EN el cofre, en el mundo, con tensión antes del premio.";
      int x = this.bodyX();
      int y = this.bodyY();
      int colW = this.bodyW();
      ScrollSelector<CrateAnimation> list = new ScrollSelector<>(
         x,
         y,
         colW,
         this.bodyH() - 28,
         14,
         a -> (a.id().equals(this.config.animationId) ? "§a✔ " : "§f") + a.displayName() + " §8(" + (double)a.durationTicks() / 20.0 + "s)",
         a -> a.displayName() + " " + a.id(),
         null
      );
      list.remember(this.mem("animaciones"));
      list.setItems(AnimationRegistry.all());
      list.onSelect(a -> {
         this.config.animationId = a.id();
         this.rebuildWidgets();
      });
      this.addRenderableWidget(list);
      CrateAnimation sel = AnimationRegistry.get(this.config.animationId);
      this.addLabel("§e" + sel.displayName() + ": §7" + sel.description(), x, y + this.bodyH() - 22, null);
   }

   private void initStyle() {
      this.helpLine = "Elige el DISEÑO visual del cofre (independiente del tier). Escribe para buscar entre todos los modelos.";
      int x = this.bodyX();
      int y = this.bodyY();
      int colW = this.bodyW();
      List<String> ids = CrateStyles.cycleIds();
      ScrollSelector<String> list = new ScrollSelector<>(
         x,
         y,
         colW,
         this.bodyH() - 28,
         14,
         id -> (id.equals(this.config.styleId) ? "§a✔ " : "§f") + CrateStyles.displayName(id),
         id -> CrateStyles.displayName(id) + " " + id,
         null
      );
      list.remember(this.mem("sonidos"));
      list.setItems(ids);
      list.onSelect(id -> {
         this.config.styleId = id;
         this.rebuildWidgets();
      });
      this.addRenderableWidget(list);
      this.addLabel("§eDiseño actual: §b" + CrateStyles.displayName(this.config.styleId) + " §7(" + ids.size() + " diseños)", x, y + this.bodyH() - 22, null);
   }

   private void initAppearance() {
      this.helpLine = "Brillo, partículas on/off, nombre flotante, color del nombre y texto flotante (color por línea).";
      int x = this.bodyX();
      int y = this.bodyY();
      int colW = (this.bodyW() - 10) / 2;
      this.addToggle(x, y, colW, this.config.glow ? "Brillo del item: Activado" : "Brillo del item: Desactivado", this.config.glow, () -> {
         this.config.glow = !this.config.glow;
         this.rebuildWidgets();
      }, desc("El item de crate brilla como encantado."));
      this.addToggle(x, y + 22, colW, this.config.particles ? "Partículas: Activado" : "Partículas: Desactivado", this.config.particles, () -> {
         this.config.particles = !this.config.particles;
         this.rebuildWidgets();
      }, desc("Partículas de reposo alrededor de la crate."));
      this.addToggle(x, y + 44, colW, this.config.floatingName ? "Nombre flotante: Sí" : "Nombre flotante: No", this.config.floatingName, () -> {
         this.config.floatingName = !this.config.floatingName;
         this.rebuildWidgets();
      }, desc("Muestra el nombre flotando sobre la crate."));
      EditBox hex = new EditBox(this.font, x + 70, y + 70, 110, 16, Component.empty());
      hex.setMaxLength(7);
      hex.setValue(this.config.nameColorHexOverride);
      hex.setHint(Component.literal("#RRGGBB"));
      hex.setResponder(s -> this.config.nameColorHexOverride = s.trim());
      this.addRenderableWidget(hex);
      this.addLabel("Color:", x, y + 74, desc("Color del nombre (#RRGGBB). Vacío = color del tier."));
      this.addToggle(x, y + 92, colW, this.config.showOdds ? "Mostrar % encima: Sí" : "Mostrar % encima: No", this.config.showOdds, () -> {
         this.config.showOdds = !this.config.showOdds;
         this.rebuildWidgets();
      }, desc("Muestra la probabilidad de cada recompensa flotando sobre el cofre.", "Útil para que los jugadores vean las posibilidades."));
      int tx = x + colW + 10;
      this.addLabel(
         "§eTexto flotante §7(clic para editar, ■ = color):",
         tx,
         y - 2,
         desc("Haz CLIC en una linea para escribir/editar.", "El boton ■ cambia el color de ESA linea.", "También aceptas códigos & dentro del texto.")
      );
      int maxLines = 8;
      char[] lineColors = new char[maxLines];
      String[] lineTexts = new String[maxLines];

      for (int i = 0; i < maxLines; i++) {
         String raw = i < this.config.floatingText.size() ? this.config.floatingText.get(i) : "";
         char col = 'f';
         String txt = raw;
         if (raw.length() >= 2 && (raw.charAt(0) == '&' || raw.charAt(0) == 167) && "f7e6cab9d5234180".indexOf(raw.charAt(1)) >= 0) {
            col = raw.charAt(1);
            txt = raw.substring(2);
         }

         lineColors[i] = col;
         lineTexts[i] = txt;
      }

      Runnable sync = () -> {
         ArrayList<String> out = new ArrayList<>();

         for (int k = 0; k < maxLines; k++) {
            out.add(lineTexts[k].isEmpty() ? "" : "&" + lineColors[k] + lineTexts[k]);
         }

         this.config.setFloatingText(String.join("\n", out));
      };

      for (int j = 0; j < maxLines; j++) {
         int idx = j;
         int ry = y + 12 + j * 20;
         this.addRenderableWidget(Button.builder(Component.literal("§" + lineColors[j] + "■"), b -> {
            int pos = "f7e6cab9d5234180".indexOf(lineColors[idx]);
            lineColors[idx] = "f7e6cab9d5234180".charAt((pos + 1) % "f7e6cab9d5234180".length());
            sync.run();
            this.rebuildWidgets();
         }).bounds(tx, ry, 18, 16).build());
         EditBox line = new EditBox(this.font, tx + 22, ry, colW - 22, 16, Component.empty());
         line.setMaxLength(128);
         line.setEditable(true);
         line.setBordered(true);
         line.setValue(lineTexts[j]);
         line.setHint(Component.literal("Línea " + (j + 1) + "…"));
         line.setResponder(s -> {
            lineTexts[idx] = s;
            sync.run();
         });
         this.addRenderableWidget(line);
      }
   }

   private void initParticles() {
      this.helpLine = "Capas ilimitadas. Anillo/Halo/Espiral/Vórtice se adaptan al cofre.";
      int x = this.bodyX();
      int y = this.bodyY();
      int listW = 118;
      int midW = 126;
      int midX = x + 118 + 6;
      int rx = midX + 126 + 8;
      int fw = this.leftPos + this.panelWidth - 8 - rx;
      if (this.selectedLayer != null && !this.config.particleLayers.contains(this.selectedLayer)) {
         this.selectedLayer = null;
      }

      ScrollSelector<ParticleLayer> layers = new ScrollSelector<>(
         x,
         y,
         118,
         this.bodyH() - 20,
         22,
         pl -> (pl == this.selectedLayer ? "§e▶ " : "") + pl.shortLabel(),
         ParticleLayer::shortLabel,
         pl -> ItemStack.EMPTY
      );
      layers.remember(this.mem("capasParticulas"));
      layers.setItems(new ArrayList<>(this.config.particleLayers));
      layers.onSelect(pl -> {
         this.selectedLayer = pl;
         this.rebuildWidgets();
      });
      this.addRenderableWidget(layers);
      this.addRenderableWidget(Button.builder(Component.literal("§a+ Capa"), b -> {
         ParticleLayer newL = new ParticleLayer();
         this.config.particleLayers.add(newL);
         this.selectedLayer = newL;
         this.rebuildWidgets();
      }).bounds(x, y + this.bodyH() - 18, 118, 16).build());
      EditBox search = new EditBox(this.font, midX, y, 126, 16, Component.empty());
      search.setHint(Component.literal("Buscar partícula..."));
      this.addRenderableWidget(search);
      ScrollSelector<ResourceLocation> types = new ScrollSelector<>(
         midX,
         y + 20,
         126,
         this.bodyH() - 22,
         13,
         rl -> (this.selectedLayer != null && rl.toString().equals(this.selectedLayer.particleId) ? "§a✔ " : "§f") + ParticleNames.spanish(rl.getPath()),
         rl -> ParticleNames.spanish(rl.getPath()) + " " + rl,
         rl -> ItemStack.EMPTY
      );
      types.remember(this.mem("tiposParticulas"));
      types.setItems(RegistryLists.particles());
      types.onSelect(rl -> {
         if (this.selectedLayer != null) {
            this.selectedLayer.particleId = rl.toString();
            this.rebuildWidgets();
         }
      });
      search.setResponder(types::setQuery);
      this.addRenderableWidget(types);
      if (this.selectedLayer == null) {
         this.addLabel("§7Selecciona o", rx, y + 4, null);
         this.addLabel("§7crea una capa ←", rx, y + 16, null);
      } else {
         ParticleLayer l = this.selectedLayer;
         ParticleLayer layerVar = this.selectedLayer;
         int half = fw / 2;
         int fieldW = 42;
         this.addLabel(
            "§e" + ParticleNames.spanish(l.particleId.contains(":") ? l.particleId.substring(l.particleId.indexOf(58) + 1) : l.particleId), rx, y, null
         );
         this.addRenderableWidget(Button.builder(Component.literal("Fase: §e" + l.phase.label), b -> {
            l.phase = l.phase.next();
            this.rebuildWidgets();
         }).bounds(rx, y + 12, fw, 16).build());
         this.tooltipZones.add(new CrateEditorScreen.TooltipZone(rx, y + 12, fw, 16, desc("Cuando emite:", "Reposo, Tensión, Apertura, Revelación, Final.")));
         this.addRenderableWidget(Button.builder(Component.literal("Forma: §b" + l.shape.label), b -> {
            l.shape = l.shape.next();
            l.applyShapeDefaults();
            this.rebuildWidgets();
         }).bounds(rx, y + 32, fw, 16).build());
         this.tooltipZones
            .add(
               new CrateEditorScreen.TooltipZone(
                  rx,
                  y + 32,
                  fw,
                  16,
                  desc(
                     "Forma/movimiento: Halo, Anillo, Espiral, Vórtice, Fuente...",
                     "Anillo/Halo/Espiral/Vórtice ENVUELVEN el cofre según su TAMAÑO",
                     "real (un cofre legendario/mítico es más grande -> radio mayor)."
                  )
               )
            );
         int r1 = y + 54;
         int r2 = y + 74;
         int r3 = y + 94;
         int fw2 = 40;
         int f1x = rx + half - fw2 - 4;
         int f2x = rx + fw - fw2;
         this.addIntField(f1x, r1, fw2, l.count, v -> l.count = Math.max(1, v), "Cant.", rx, r1 + 4, desc("Partículas por emisión."));
         this.addDoubleField(f2x, r1, fw2, l.speed, v -> l.speed = Math.max(0.0, v), "Vel.", rx + half, r1 + 4, desc("Empuje de las partículas."));
         this.addDoubleField(f1x, r2, fw2, l.spread, v -> l.spread = Math.max(0.0, v), "Disp.", rx, r2 + 4, desc("Apertura aleatoria."));
         this.addDoubleField(
            f2x,
            r2,
            fw2,
            l.radius,
            v -> l.radius = Math.max(0.0, v),
            "Radio",
            rx + half,
            r2 + 4,
            desc("Radio del anillo/halo/órbita/espiral. ~0.85 rodea el cofre.", "Se MULTIPLICA por el tamaño del cofre (legendario/mítico más grande).")
         );
         this.addDoubleField(
            f1x,
            r3,
            fw2,
            l.yOffset,
            v -> l.yOffset = v,
            "Alt.",
            rx,
            r3 + 4,
            desc("Altura sobre el bloque (se escala con el tamaño del cofre).", "~0.2 al ras del suelo, ~1.1 a la altura de la tapa.")
         );
         this.addIntField(
            f2x, r3, fw2, l.interval, v -> l.interval = Math.max(1, v), "Int.", rx + half, r3 + 4, desc("Solo en Reposo: emite cada N ticks (20 = 1s).")
         );
         int cy = y + 116;
         this.addToggle(rx, cy, fw, l.useRarityColor ? "Color: tier" : "Color: hex", l.useRarityColor, () -> {
            l.useRarityColor = !l.useRarityColor;
            this.rebuildWidgets();
         }, desc("Solo afecta a 'Polvo de color'. Tier = color de la rareza."));
         cy += 20;
         if (!l.useRarityColor) {
            EditBox hex = new EditBox(this.font, rx + 36, cy, fw - 36, 16, Component.empty());
            hex.setMaxLength(7);
            hex.setValue(l.colorHex);
            hex.setHint(Component.literal("#RRGGBB"));
            hex.setResponder(s -> l.colorHex = s.trim());
            this.addRenderableWidget(hex);
            this.addLabel("Hex:", rx, cy + 4, null);
            cy += 20;
         }

         this.addRenderableWidget(Button.builder(Component.literal("§cQuitar capa"), b -> {
            this.config.particleLayers.remove(l);
            this.selectedLayer = null;
            this.rebuildWidgets();
         }).bounds(rx, cy, fw, 16).build());
      }
   }

   private void initKey() {
      this.helpLine = "Fantastic Key (universal). Izq: probabilidad de cada RAREZA al abrir. Der: PITY (rareza asegurada) y llave.";
      int x = this.bodyX();
      int y = this.bodyY();
      int bw = this.bodyW();
      int gap = 10;
      int colW = (bw - gap) / 2;
      int lx = x;
      int rx2 = x + colW + gap;
      int leftFieldW = Math.max(38, Math.min(52, colW / 3));
      int rightFieldW = Math.max(34, Math.min(46, colW / 4));
      this.addLabel(
         "§ePROB. DE RAREZA §7(peso → 100%)",
         x,
         y,
         desc("Al abrir, la crate tira UNA rareza según estos pesos,", "y gira/entrega un item del POOL de esa rareza (pestaña Premios).")
      );
      int ly = y + 16;

      for (Rarity r : Rarity.values()) {
         int poolN = this.config.rewardCountForRarity(r);
         String lbl = r.color() + r.displayName() + " §7(" + fmt(this.config.rarityChancePercent(r)) + "%, " + poolN + ")";
         this.addLabel(
            lbl,
            lx,
            ly + 4,
            desc(
               poolN == 0
                  ? "§cSin items: si sale esta rareza, la ruleta usa la rareza con items mas cercana."
                  : "§7" + poolN + " item(s) en el pool de " + r.displayName() + "."
            )
         );
         this.addDoubleField(
            lx + colW - leftFieldW,
            ly,
            leftFieldW,
            this.config.rarityChance(r),
            v -> this.config.rarityChances.put(r, Double.valueOf(Math.max(0.0, v))),
            null,
            0,
            0,
            desc("Peso relativo de la rareza " + r.displayName() + ". (El % se recalcula al reabrir la pestaña.)")
         );
         ly += 20;
      }

      int halfL = (colW - 4) / 2;
      this.addRenderableWidget(Button.builder(Component.literal("§7Igualar"), b -> {
         double each = 100.0 / (double)Rarity.values().length;

         for (Rarity r2 : Rarity.values()) {
            this.config.rarityChances.put(r2, Double.valueOf(each));
         }

         this.rebuildWidgets();
      }).bounds(lx, ly + 2, halfL, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal("§7Preset 60/25/10/4/1"), b -> {
         this.config.rarityChances.clear();
         this.config.rarityChances.putAll(CrateConfig.defaultRarityChances());
         this.rebuildWidgets();
      }).bounds(lx + halfL + 4, ly + 2, colW - halfL - 4, 16).build());
      this.addLabel(
         "§6✦ PITY §7(rareza asegurada)",
         rx2,
         y,
         desc("Cada N aperturas (por jugador y por crate) se GARANTIZA", "la rareza elegida, ignorando la tirada normal.")
      );
      this.addToggle(rx2, y + 16, colW, this.config.pityEnabled ? "Pity: Activado" : "Pity: Desactivado", this.config.pityEnabled, () -> {
         this.config.pityEnabled = !this.config.pityEnabled;
         this.rebuildWidgets();
      }, desc("Activa/desactiva la rareza asegurada por aperturas."));
      this.addIntField(
         rx2 + colW - rightFieldW,
         y + 38,
         rightFieldW,
         this.config.pityInterval,
         v -> this.config.pityInterval = Math.max(1, v),
         "Cada (aperturas):",
         rx2,
         y + 42,
         desc("Número de aperturas para garantizar la rareza (mínimo 1).")
      );
      Rarity pr = this.config.pityRarity == null ? Rarity.LEGENDARY : this.config.pityRarity;
      this.addRenderableWidget(Button.builder(Component.literal("Rareza: " + pr.color() + pr.displayName()), b -> {
         this.config.pityRarity = pr.next();
         this.rebuildWidgets();
      }).bounds(rx2, y + 60, colW, 16).build());
      this.addToggle(rx2, y + 84, colW, this.config.consumeKey ? "Consumir llave: Sí" : "Consumir llave: No", this.config.consumeKey, () -> {
         this.config.consumeKey = !this.config.consumeKey;
         this.rebuildWidgets();
      }, desc("Si está activo, la llave se gasta al abrir (universal o única)."));
   }

   private void initKeyModel() {
      this.helpLine = "Llave única exclusiva de esta caja. La Fantastic Key universal SIEMPRE abre; se entrega al crear/dar la caja.";
      int x = this.bodyX();
      int y = this.bodyY();
      int bw = this.bodyW();
      this.addToggle(
         x,
         y,
         bw,
         this.config.uniqueKeyEnabled ? "Llave única: ACTIVADA" : "Llave única: Desactivada",
         this.config.uniqueKeyEnabled,
         () -> {
            this.config.uniqueKeyEnabled = !this.config.uniqueKeyEnabled;
            if (this.config.uniqueKeyEnabled && (this.config.uniqueKeyModel == null || this.config.uniqueKeyModel.isBlank())) {
               KeyModels.Entry f = KeyModels.first();
               if (f != null) {
                  this.config.uniqueKeyModel = f.id;
               }
            }

            this.rebuildWidgets();
         },
         desc(
            "ACTIVADA: esta caja tiene una llave única propia (se entrega al crearla/darla).",
            "La §d✦ Fantastic Key ✦§7 universal SIEMPRE abre cualquier caja.",
            "Desactivada: solo se usa la Fantastic Key universal."
         )
      );
      if (!this.config.uniqueKeyEnabled) {
         this.addLabel("§7Esta caja se abre solo con la §d✦ Fantastic Key ✦§7 universal.", x, y + 24, null);
         this.addLabel("§8Actívala para darle a esta caja su propia llave (uno de " + KeyModels.ALL.size() + " modelos).", x, y + 40, null);
      } else {
         int colW = (bw - 12) / 2;
         int rx2 = x + colW + 12;
         int headerY = y + 24;
         int rowY = y + 38;
         this.addLabel("§eModelo §7(" + KeyModels.ALL.size() + "):", x, headerY, desc("Elige el modelo de la llave. Escribe para buscar."));
         ScrollSelector<KeyModels.Entry> list = new ScrollSelector<>(
            x,
            rowY,
            colW,
            this.bodyH() - 40,
            18,
            e -> (e.id.equals(this.config.uniqueKeyModel) ? "§a✔ " : "§f") + e.defaultName,
            e -> e.defaultName + " " + e.group + " " + e.id,
            e -> this.previewKey(e)
         );
         list.remember(this.mem("modelosLlave"));
         list.setItems(KeyModels.ALL);
         list.onSelect(e -> {
            this.config.uniqueKeyModel = e.id;
            this.rebuildWidgets();
         });
         this.addRenderableWidget(list);
         KeyModels.Entry sel = KeyModels.byId(this.config.uniqueKeyModel);
         String defName = sel != null ? sel.defaultName : "✦ Llave de Crate ✦";
         this.addLabel(
            "§eNombre de la llave:",
            rx2,
            headerY,
            desc(
               "Nombre visible de la llave (editable).", "«Color/estilo» abre el editor completo (rueda, RGB, hex).", "Vacío = nombre por defecto del modelo."
            )
         );
         String rawName = this.config.uniqueKeyName == null ? "" : this.config.uniqueKeyName;
         String plainName = stripCodes(rawName);
         int styleBtnW = Math.max(80, Math.min(100, colW / 3));
         int nameBoxW = Math.max(70, colW - styleBtnW - 6);
         EditBox nameBox = new EditBox(this.font, rx2, rowY, nameBoxW, 16, Component.empty());
         nameBox.setMaxLength(64);
         nameBox.setValue(plainName);
         nameBox.setHint(Component.literal(defName));
         nameBox.setResponder(s -> {
            if (s != null && !s.isBlank()) {
               this.config.uniqueKeyName = buildLegacy(s, colorOf(this.config.uniqueKeyName), flagsOf(this.config.uniqueKeyName));
            } else {
               this.config.uniqueKeyName = "";
            }
         });
         this.addRenderableWidget(nameBox);
         this.addRenderableWidget(Button.builder(Component.literal("§bColor/estilo »"), b -> {
            if (this.minecraft != null) {
               String plainN = stripCodes(this.config.uniqueKeyName);
               String sample = plainN.isEmpty() ? defName : plainN;
               int rgb0 = colorOf(this.config.uniqueKeyName);
               boolean[] flags0 = flagsOf(this.config.uniqueKeyName);
               this.minecraft
                  .setScreen(new FSTextStyleScreen("Color y estilo de la llave", sample, rgb0, flags0, (col, bold, italic, underline, strike, obf) -> {
                     String plainNow = stripCodes(this.config.uniqueKeyName);
                     if (!plainNow.isBlank()) {
                        this.config.uniqueKeyName = buildLegacy(plainNow, col, new boolean[]{bold, italic, underline, strike, obf});
                     }
                  }, () -> this.minecraft.setScreen(this)));
            }
         }).bounds(rx2 + nameBoxW + 6, rowY, styleBtnW, 16).build());
         String modelLbl = "§8Modelo: §f" + (sel != null ? sel.defaultName : "-");
         this.addLabel(this.font.plainSubstrByWidth(modelLbl, colW), rx2, rowY + 24, null);
         this.addLabel("§8Se entrega al crear/dar la caja.", rx2, rowY + 38, null);
         this.addToggle(rx2, rowY + 52, colW, this.config.consumeKey ? "Consumir llave: Sí" : "Consumir llave: No", this.config.consumeKey, () -> {
            this.config.consumeKey = !this.config.consumeKey;
            this.rebuildWidgets();
         }, desc("Si está activo, la llave se gasta al abrir."));
      }
   }

   private ItemStack previewKey(KeyModels.Entry e) {
      ItemStack s = new ItemStack(ModRegistry.uniqueKey());
      if (e != null) {
         s.getOrCreateTag().putInt("CustomModelData", e.cmd);
      }

      return s;
   }

   private void initSettings() {
      this.helpLine = "Izq: cooldown, anuncio, saltar y permiso. Der: tamaño, altura, orientación (barritas) y apertura única por jugador.";
      int x = this.bodyX();
      int y = this.bodyY();
      int colW = (this.bodyW() - 12) / 2;
      int fieldW = 56;
      this.addIntField(
         x + colW - fieldW,
         y,
         fieldW,
         this.config.cooldownSeconds,
         v -> this.config.cooldownSeconds = Math.max(0, v),
         "Cooldown por jugador (seg):",
         x,
         y + 4,
         desc("Espera individual para reabrir ESTA crate. 0 = sin cooldown.")
      );
      this.addSecondsField(
         x + colW - fieldW,
         y + 22,
         fieldW,
         this.config.openDelayTicks,
         v -> this.config.openDelayTicks = Math.max(0, v),
         "Retraso de apertura (seg):",
         x,
         y + 26,
         desc("Espera antifraude. 0 = inmediato.")
      );
      this.addToggle(x, y + 44, colW, this.config.broadcast ? "Anuncio global: Activado" : "Anuncio global: Desactivado", this.config.broadcast, () -> {
         this.config.broadcast = !this.config.broadcast;
         this.rebuildWidgets();
      }, desc("Anuncia a todo el servidor cuando alguien gana."));
      this.addToggle(x, y + 66, colW, this.config.allowSkip ? "Saltar con SHIFT: Permitido" : "Saltar con SHIFT: Bloqueado", this.config.allowSkip, () -> {
         this.config.allowSkip = !this.config.allowSkip;
         this.rebuildWidgets();
      }, desc("Permite saltar la animación abriendo con SHIFT."));
      this.addLabel("Permiso requerido (opcional):", x, y + 92, desc("Nodo de permiso extra. Vacío = nada adicional."));
      EditBox perm = new EditBox(this.font, x, y + 104, colW, 16, Component.empty());
      perm.setMaxLength(64);
      perm.setValue(this.config.requiredPermission);
      perm.setHint(Component.literal("(opcional)"));
      perm.setResponder(s -> this.config.requiredPermission = s.trim());
      this.addRenderableWidget(perm);
      int tx = x + colW + 12;
      this.addLabel("§e✦ Tamaño y posición de la caja", tx, y, null);
      this.addSlider(
         tx,
         y + 12,
         colW,
         0.3,
         3.0,
         (double)this.config.sizeScale,
         2,
         "Tamaño",
         "x",
         v -> this.config.sizeScale = (float)v,
         desc("Escala de la caja (1.00x = tamaño por defecto).", "Arrastra la barrita. No deforma el modelo.")
      );
      this.addSlider(
         tx,
         y + 34,
         colW,
         -0.5,
         3.0,
         (double)this.config.yOffset,
         2,
         "Altura",
         " bloques",
         v -> this.config.yOffset = (float)v,
         desc("Sube o baja la caja (0.00 = sobre el bloque).", "Útil para flotarla o hundirla.")
      );
      this.addSlider(
         tx,
         y + 56,
         colW,
         0.0,
         360.0,
         (double)this.config.yawOffset,
         0,
         "Orientación",
         "°",
         v -> this.config.yawOffset = (float)v,
         desc("Rotación extra en grados sobre la dirección de colocación.")
      );
      this.addRenderableWidget(Button.builder(Component.literal("§7Restablecer tamaño/posición"), b -> {
         this.config.sizeScale = 1.0F;
         this.config.yOffset = 0.0F;
         this.config.yawOffset = 0.0F;
         this.rebuildWidgets();
      }).bounds(tx, y + 78, colW, 16).build());
      this.addToggle(
         tx,
         y + 100,
         colW,
         this.config.openOncePerPlayer ? "Apertura única por jugador: Sí" : "Apertura única por jugador: No",
         this.config.openOncePerPlayer,
         () -> {
            this.config.openOncePerPlayer = !this.config.openOncePerPlayer;
            this.rebuildWidgets();
         },
         desc(
            "Si está activo, CADA jugador solo puede abrir",
            "esta caja UNA vez. Otros jugadores aún pueden",
            "abrirla su propia vez. (Se recuerda por caja colocada.)"
         )
      );
      this.addToggle(
         tx,
         y + 122,
         colW,
         this.config.showOddsInPool ? "% en recompensas: Sí" : "% en recompensas: No",
         this.config.showOddsInPool,
         () -> {
            this.config.showOddsInPool = !this.config.showOddsInPool;
            this.rebuildWidgets();
         },
         desc(
            "El % de cada item en la pantalla «VER RECOMPENSAS».",
            "",
            "§7Es distinto de §fMostrar % encima§7 (pestaña Aspecto),",
            "§7que es la lista flotante sobre el cofre."
         )
      );
   }

   private void initVideos() {
      this.initMediaTab(
         this.config.videos,
         "Videos de la pantalla de pre-apertura",
         "Vídeo",
         desc(
            "Links DIRECTOS de descarga (Google Drive, etc.).",
            "Se admiten §fvídeos MP4 (H.264)§7 e §fimágenes PNG/JPG§7.",
            "§cLos .webm (VP8/VP9) no se pueden reproducir.",
            "",
            "Si hay varios, en cada apertura sale uno al azar.",
            "Cada archivo se descarga una vez y queda en caché."
         )
      );
   }

   private void initMusica() {
      this.initMediaTab(
         this.config.music,
         "Música de la pantalla de pre-apertura",
         "Canción",
         desc(
            "Links DIRECTOS de descarga (Google Drive, etc.).",
            "Formatos soportados: §fMP3, OGG y WAV§7.",
            "El audio del vídeo se ignora: la música sale de aquí.",
            "Si hay varias, en cada apertura suena una al azar."
         )
      );
   }

   private void initMediaTab(List<String> urls, String title, String noun, List<Component> tooltip) {
      this.helpLine = "Añade links directos. Si la caja tiene media propia, NO se usa la del mod.";
      int x = this.bodyX();
      int y = this.bodyY();
      int w = this.bodyW();
      int bottom = this.bodyY() + this.bodyH();
      this.addLabel("§e✦ " + title + " §7(" + urls.size() + ")", x, y, tooltip);
      int selectedLabelY = bottom - 8;
      int actionsY = selectedLabelY - 20;
      int fieldY = actionsY - 20;
      int listH = Math.max(14, fieldY - 4 - (y + 12));
      ScrollSelector<String> list = new ScrollSelector<>(x, y + 12, w, listH, 14, url -> url, url -> url, null);
      list.remember(this.mem("urlsMedia"));
      list.setItems(new ArrayList<>(urls));
      list.onSelect(url -> this.selectedMediaUrl = url);
      this.addRenderableWidget(list);
      EditBox input = new EditBox(this.font, x, fieldY, w - 96, 16, Component.empty());
      input.setMaxLength(512);
      input.setHint(Component.literal("§8https://... (link directo)"));
      this.addRenderableWidget(input);
      this.addRenderableWidget(Button.builder(Component.literal("§aAñadir"), b -> {
         String value = input.getValue() == null ? "" : input.getValue().trim();
         if (!value.isEmpty()) {
            if (!MediaCache.isValidUrl(value)) {
               this.helpLine = "§cEse link no es válido: debe empezar por http:// o https://";
            } else {
               if (!urls.contains(value)) {
                  urls.add(value);
               }

               input.setValue("");
               this.selectedMediaUrl = null;
               this.rebuildWidgets();
            }
         }
      }).bounds(x + w - 92, fieldY, 92, 16).build());
      int half = (w - 4) / 2;
      this.addRenderableWidget(Button.builder(Component.literal(this.selectedMediaUrl == null ? "§8Eliminar (elige uno)" : "§cEliminar seleccionado"), b -> {
         if (this.selectedMediaUrl != null) {
            urls.remove(this.selectedMediaUrl);
            this.selectedMediaUrl = null;
            this.rebuildWidgets();
         }
      }).bounds(x, actionsY, half, 16).build());
      this.addRenderableWidget(Button.builder(Component.literal(this.selectedMediaUrl == null ? "§8Copiar (elige uno)" : "§bCopiar al portapapeles"), b -> {
         if (this.selectedMediaUrl != null && this.minecraft != null) {
            this.minecraft.keyboardHandler.setClipboard(this.selectedMediaUrl);
            this.helpLine = "§aLink copiado al portapapeles.";
         }
      }).bounds(x + half + 4, actionsY, half, 16).build());
      if (this.selectedMediaUrl != null) {
         this.addLabel("§7Seleccionado: §f" + this.font.plainSubstrByWidth(this.selectedMediaUrl, w - 90), x, actionsY + 20, null);
      } else {
         this.addLabel("§8Haz click en un " + noun.toLowerCase(Locale.ROOT) + " de la lista para elegirlo.", x, actionsY + 20, null);
      }
   }

   private static String fmt(double v) {
      return String.format(Locale.ROOT, "%.1f", v);
   }

   private static String stripCodes(String s) {
      if (s == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c == '&' || c == 167) && i + 1 < s.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(s.charAt(i + 1)) >= 0) {
               i++;
            } else {
               sb.append(c);
            }
         }

         return sb.toString();
      }
   }

   private static int colorOf(String s) {
      if (s != null) {
         for (int i = 0; i + 1 < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' || c == 167) {
               ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(s.charAt(i + 1)));
               if (f != null && f.isColor() && f.getColor() != null) {
                  return f.getColor();
               }
            }
         }
      }

      return 16777215;
   }

   private static boolean[] flagsOf(String s) {
      boolean[] fl = new boolean[5];
      if (s != null) {
         for (int i = 0; i + 1 < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' || c == 167) {
               switch (Character.toLowerCase(s.charAt(i + 1))) {
                  case 'k':
                     fl[4] = true;
                     break;
                  case 'l':
                     fl[0] = true;
                     break;
                  case 'm':
                     fl[3] = true;
                     break;
                  case 'n':
                     fl[2] = true;
                     break;
                  case 'o':
                     fl[1] = true;
               }
            }
         }
      }

      return fl;
   }

   private static String buildLegacy(String plain, int rgb, boolean[] flags) {
      StringBuilder sb = new StringBuilder();
      sb.append('&').append(nearestColorChar(rgb));
      if (flags != null && flags.length >= 5) {
         if (flags[0]) {
            sb.append("&l");
         }

         if (flags[1]) {
            sb.append("&o");
         }

         if (flags[2]) {
            sb.append("&n");
         }

         if (flags[3]) {
            sb.append("&m");
         }

         if (flags[4]) {
            sb.append("&k");
         }
      }

      sb.append(plain == null ? "" : plain);
      return sb.toString();
   }

   private static char nearestColorChar(int rgb) {
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      char best = 'f';
      long bestD = Long.MAX_VALUE;

      for (ChatFormatting f : ChatFormatting.values()) {
         if (f.isColor() && f.getColor() != null) {
            int col = f.getColor();
            int cr = col >> 16 & 0xFF;
            int cg = col >> 8 & 0xFF;
            int cb = col & 0xFF;
            long d = (long)((r - cr) * (r - cr)) + (long)((g - cg) * (g - cg)) + (long)((b - cb) * (b - cb));
            if (d < bestD) {
               bestD = d;
               best = f.getChar();
            }
         }
      }

      return best;
   }

   private static String cycleItemRarity(String current) {
      if (current != null && !current.isBlank()) {
         Rarity r = Rarity.byName(current);
         return r == Rarity.MYTHIC ? "" : r.next().name();
      } else {
         return Rarity.COMMON.name();
      }
   }

   private static List<Component> desc(String... lines) {
      ArrayList<Component> out = new ArrayList<>();

      for (String s : lines) {
         out.add(Component.literal(s));
      }

      return out;
   }

   private void addLabel(String text, int x, int y, List<Component> tooltip) {
      this.labels.add(new CrateEditorScreen.Label(text, x, y, 14737632));
      if (tooltip != null) {
         this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y - 2, Math.max(200, this.font.width(text) + 8), 14, tooltip));
      }
   }

   private void addIntField(int x, int y, int w, int value, IntConsumer setter, String label, int labelX, int labelY, List<Component> tooltip) {
      EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
      box.setMaxLength(10);
      box.setValue(Integer.toString(value));
      box.setResponder(s -> {
         try {
            setter.accept(Integer.parseInt(s.trim()));
         } catch (NumberFormatException var3x) {
         }
      });
      this.addRenderableWidget(box);
      if (label != null) {
         this.labels.add(new CrateEditorScreen.Label(label, labelX, labelY, 14737632));
         if (tooltip != null) {
            this.tooltipZones.add(new CrateEditorScreen.TooltipZone(labelX, labelY - 2, x + w - labelX, 14, tooltip));
         }
      }
   }

   private void addDoubleField(int x, int y, int w, double value, DoubleConsumer setter, String label, int labelX, int labelY, List<Component> tooltip) {
      EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
      box.setMaxLength(8);
      box.setValue(fmt(value));
      box.setResponder(s -> {
         try {
            setter.accept(Double.parseDouble(s.trim()));
         } catch (NumberFormatException var3x) {
         }
      });
      this.addRenderableWidget(box);
      if (label != null) {
         this.labels.add(new CrateEditorScreen.Label(label, labelX, labelY, 14737632));
      }

      if (tooltip != null) {
         this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y, w, 16, tooltip));
      }
   }

   private void addSecondsField(int x, int y, int w, int ticks, IntConsumer setterTicks, String label, int labelX, int labelY, List<Component> tooltip) {
      EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
      box.setMaxLength(8);
      box.setValue(Long.toString(Math.round((double)ticks / 20.0)));
      box.setResponder(s -> {
         String t = s.trim();
         if (!t.isEmpty()) {
            try {
               double seconds = Double.parseDouble(t);
               setterTicks.accept((int)Math.round(Math.max(0.0, seconds) * 20.0));
            } catch (NumberFormatException var5x) {
            }
         }
      });
      this.addRenderableWidget(box);
      this.labels.add(new CrateEditorScreen.Label(label, labelX, labelY, 14737632));
      if (tooltip != null) {
         this.tooltipZones.add(new CrateEditorScreen.TooltipZone(labelX, labelY - 2, x + w - labelX, 14, tooltip));
      }
   }

   private void addSlider(
      int x, int y, int w, double min, double max, double value, int decimals, String label, String suffix, DoubleConsumer setter, List<Component> tooltip
   ) {
      this.addRenderableWidget(new CrateEditorScreen.FSSlider(x, y, w, min, max, value, decimals, label, suffix, setter));
      if (tooltip != null) {
         this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y, w, 18, tooltip));
      }
   }

   private void addToggle(int x, int y, int w, String text, boolean state, Runnable onToggle, List<Component> tooltip) {
      String prefix = state ? "§a" : "§7";
      this.addRenderableWidget(Button.builder(Component.literal(prefix + text), b -> onToggle.run()).bounds(x, y, w, 16).build());
      if (tooltip != null) {
         this.tooltipZones.add(new CrateEditorScreen.TooltipZone(x, y, w, 16, tooltip));
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.lastMouseX = mouseX;
      this.lastMouseY = mouseY;
      this.renderBackground(g);
      g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -535291870);
      g.fill(this.leftPos, this.topPos, this.leftPos + this.panelWidth, this.topPos + 20, -14408646);
      g.fill(this.leftPos, this.topPos + this.panelHeight - 1, this.leftPos + this.panelWidth, this.topPos + this.panelHeight, -12961206);
      int separatorY = this.topPos + 24 + this.headerRowCount * 20 + 2;
      g.fill(this.leftPos + 6, separatorY, this.leftPos + this.panelWidth - 6, separatorY + 1, -12961206);
      g.drawString(
         this.font,
         "§d✦ §fTF Crates §d✦ §7- " + this.config.rarity.color() + this.config.rarity.displayName(),
         this.leftPos + 8,
         this.topPos + 6,
         16777215,
         false
      );
      if (this.helpLine != null && !this.helpLine.isEmpty()) {
         String trimmed = this.font.plainSubstrByWidth("§7" + this.helpLine, this.panelWidth - 16);
         g.drawString(this.font, trimmed, this.leftPos + 8, separatorY + 4, 10133680, false);
      }

      if (this.activeTab == CrateEditorScreen.Tab.PROBABILITY) {
         this.renderProbabilityBars(g);
      }

      super.render(g, mouseX, mouseY, partialTick);

      for (CrateEditorScreen.Label l : this.labels) {
         g.drawString(this.font, l.text(), l.x(), l.y(), l.color(), false);
      }

      for (CrateEditorScreen.TooltipZone z : this.tooltipZones) {
         if (mouseX >= z.x() && mouseX < z.x() + z.w() && mouseY >= z.y() && mouseY < z.y() + z.h()) {
            g.renderComponentTooltip(this.font, z.lines(), mouseX, mouseY);
            break;
         }
      }
   }

   private void renderProbabilityBars(GuiGraphics g) {
      int x = this.bodyX();
      int y = this.bodyY();
      List<Object> rows = this.probRows();
      int total = rows.size();
      if (total == 0) {
         g.drawString(this.font, "§7No hay recompensas. Añádelas en Premios.", x, y, 9474192, false);
      } else {
         int maxBar = this.bodyW() - 250;
         int visibleRows = Math.max(1, this.bodyH() / 22);
         int maxScroll = Math.max(0, total - visibleRows);
         int scroll = Math.max(0, Math.min(this.probScroll, maxScroll));
         int end = Math.min(total, scroll + visibleRows);

         for (int i = scroll; i < end; i++) {
            Object row = rows.get(i);
            int ry = y + (i - scroll) * 22;
            if (row instanceof Rarity rar) {
               int n = this.config.rewardCountForRarity(rar);
               g.drawString(this.font, rar.color() + "◆ " + rar.displayName().toUpperCase() + " §8(" + n + " items en el pool)", x, ry + 5, 16777215, false);
               g.fill(x, ry + 17, x + this.bodyW() - 16, ry + 18, 1090519039);
            } else {
               RewardEntry r = (RewardEntry)row;
               double pct = this.config.normalizedPercentInPool(r);
               int barLen = (int)((double)maxBar * pct / 100.0);
               int color = r.guaranteed ? -11141291 : -13800225;
               String nameStr = this.font.plainSubstrByWidth(r.describe(), 132);
               g.drawString(this.font, nameStr, x + 10, ry + 4, 14737632, false);
               int barX = x + 210;
               g.fill(barX, ry + 2, barX + Math.max(2, barLen), ry + 14, color);
               String pctStr = r.guaranteed ? "§a100% fija" : fmt(pct);
               g.drawString(this.font, pctStr, barX + Math.max(2, barLen) + 4, ry + 4, 16777215, false);
            }
         }

         if (maxScroll > 0) {
            boolean hot = this.probDrag.isDragging()
               || ScrollbarDrag.overTrack((double)this.lastMouseX, (double)this.lastMouseY, this.probBarX(), 5, y, this.probTrackHeight());
            FSGui.scrollbar(g, this.probBarX(), y, 5, this.probTrackHeight(), this.probThumbTop(), this.probThumbHeight(), hot);
         }
      }
   }

   private int probVisibleRows() {
      return Math.max(1, this.bodyH() / 22);
   }

   private int probMaxScroll() {
      return Math.max(0, this.probRows().size() - this.probVisibleRows());
   }

   private int probTrackHeight() {
      return this.probVisibleRows() * 22;
   }

   private int probBarX() {
      return this.leftPos + this.panelWidth - 8 - 5;
   }

   private int probThumbHeight() {
      return ScrollbarDrag.thumbHeight(this.probTrackHeight(), this.probVisibleRows(), this.probRows().size());
   }

   private int probThumbTop() {
      return ScrollbarDrag.thumbTop(this.bodyY(), this.probTrackHeight(), this.probThumbHeight(), this.probScroll, this.probMaxScroll());
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.activeTab == CrateEditorScreen.Tab.PROBABILITY
         && ScrollbarDrag.isDragButton(button)
         && this.probMaxScroll() > 0
         && ScrollbarDrag.overTrack(mouseX, mouseY, this.probBarX(), 5, this.bodyY(), this.probTrackHeight())) {
         this.probScroll = this.probDrag
            .beginOnTrack(mouseY, this.probScroll, this.bodyY(), this.probTrackHeight(), this.probThumbHeight(), this.probMaxScroll());
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.probDrag.isDragging()) {
         this.probScroll = this.probDrag.drag(mouseY, this.probTrackHeight(), this.probThumbHeight(), this.probMaxScroll());
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.probDrag.isDragging()) {
         this.probDrag.end();
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (super.mouseScrolled(mouseX, mouseY, delta)) {
         return true;
      } else {
         if (this.activeTab == CrateEditorScreen.Tab.PROBABILITY) {
            int total = this.probRows().size();
            int maxScroll = Math.max(0, total - Math.max(1, this.bodyH() / 22));
            if (maxScroll > 0) {
               int before = this.probScroll;
               this.probScroll = Math.max(0, Math.min(maxScroll, this.probScroll - (int)Math.signum(delta)));
               if (this.probScroll != before) {
                  this.rebuildWidgets();
               }

               return true;
            }
         }

         return false;
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static final class FSSlider extends AbstractSliderButton {
      private final double min;
      private final double max;
      private final int decimals;
      private final String label;
      private final String suffix;
      private final DoubleConsumer setter;

      FSSlider(int x, int y, int w, double min, double max, double value, int decimals, String label, String suffix, DoubleConsumer setter) {
         super(x, y, w, 18, Component.empty(), max > min ? (value - min) / (max - min) : 0.0);
         this.min = min;
         this.max = max;
         this.decimals = decimals;
         this.label = label;
         this.suffix = suffix;
         this.setter = setter;
         this.updateMessage();
      }

      private double current() {
         return this.min + this.value * (this.max - this.min);
      }

      protected void updateMessage() {
         this.setMessage(Component.literal(this.label + ": §a" + String.format(Locale.ROOT, "%." + this.decimals + "f", this.current()) + this.suffix));
      }

      protected void applyValue() {
         this.setter.accept(this.current());
      }
   }

   static record Label(String text, int x, int y, int color) {
   }

   private static enum Tab {
      INFO("Info"),
      REWARDS("Premios"),
      PROBABILITY("Prob."),
      APPEARANCE("Aspecto"),
      STYLE("Diseño"),
      PARTICLES("Part."),
      KEY("Rarezas"),
      KEYMODEL("Llave"),
      SETTINGS("Ajustes"),
      VIDEOS("Videos"),
      MUSICA("Música"),
      MENSAJES("Mensajes");

      final String label;

      private Tab(String label) {
         this.label = label;
      }
   }

   static record TooltipZone(int x, int y, int w, int h, List<Component> lines) {
   }
}
