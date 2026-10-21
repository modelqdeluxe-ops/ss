package net.tierrasfantasticas.tfclient.crates.client.screen;

import net.tierrasfantasticas.tfclient.crates.client.color.FSTextStyle;
import net.tierrasfantasticas.tfclient.crates.client.media.CrateMedia;
import net.tierrasfantasticas.tfclient.crates.client.widget.FSButton;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.crate.LootEngine;
import net.tierrasfantasticas.tfclient.crates.item.CrateItems;
import net.tierrasfantasticas.tfclient.crates.network.FSNetwork;
import net.tierrasfantasticas.tfclient.crates.network.RequestOpenPacket;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

public class CratePreOpenScreen extends Screen {
   static int masterVolume = 100;
   static boolean muted = false;
   private static boolean volumeLoaded;
   private static final int AUDIO_SIZE = 14;
   private static final float FADE_IN_MS = 900.0F;
   private final CrateConfig config;
   private final BlockPos pos;
   private boolean handedOff;
   private long openedAt;
   private int audioX;
   private int audioY;
   private FSButton openButton;
   private FSButton poolButton;
   private static final long UI_SAFETY_MS = 15000L;
   private boolean confirming;
   private int confirmAcceptX;
   private int confirmRejectX;
   private int confirmButtonY;
   private int confirmButtonW;
   private String notice = "";
   private long noticeUntil;
   /** (1.3.99) El santuario de las hadas detrás (si la caja no tiene vídeo propio): el encuadre de la imagen del dueño. */
   private CrateCinematicScreen sanctuary;
   private boolean sanctuaryTried;

   public CratePreOpenScreen(CrateConfig config, BlockPos pos) {
      super(Component.literal("Pre-apertura"));
      this.config = config == null ? new CrateConfig() : config;
      this.pos = pos;
      loadVolumeState();
   }

   private ItemStack usableKey() {
      return this.minecraft != null && this.minecraft.player != null ? CrateItems.findUsableKey(this.minecraft.player, this.config) : ItemStack.EMPTY;
   }

   protected void init() {
      if (this.openedAt == 0L) {
         this.openedAt = System.currentTimeMillis();
      }

      if (!this.sanctuaryTried) {
         this.sanctuaryTried = true;
         boolean ownVideo = this.config.videos.stream().anyMatch(u -> u != null && !u.isBlank());
         this.sanctuary = ownVideo ? null : CrateCinematicScreen.idle(this.config);
      }

      if (!CrateMedia.isActive()) {
         CrateMedia.begin(this.config, this.sanctuary == null);
      }

      applyVolume();
      int buttonWidth = Math.max(120, Math.max(this.font.width("ABRIR"), this.font.width("VER RECOMPENSAS")) + 28);
      int gap = 10;
      int startX = (this.width - (buttonWidth * 2 + gap)) / 2;
      int buttonsY = this.height - 42;
      this.openButton = (FSButton)this.addRenderableWidget(new FSButton(startX, buttonsY, buttonWidth, 20, Component.literal("ABRIR"), -11746198, this::onOpenPressed));
      this.poolButton = (FSButton)this.addRenderableWidget(
         new FSButton(startX + buttonWidth + gap, buttonsY, buttonWidth, 20, Component.literal("VER RECOMPENSAS"), -11884320, () -> {
            if (this.minecraft != null) {
               this.minecraft.setScreen(new CratePoolScreen(this.config, this));
            }
         })
      );
      boolean ready = this.sceneReady();
      this.openButton.visible = ready;
      this.poolButton.visible = ready;
      this.audioX = this.width - 8 - 14;
      this.audioY = 8;
   }

   private void onOpenPressed() {
      if (!this.handedOff) {
         if (this.usableKey().isEmpty()) {
            this.notice = "§cTe falta la llave: " + CrateItems.requiredKeyName(this.config);
            this.noticeUntil = System.currentTimeMillis() + 4000L;
            this.confirming = false;
         } else if (!this.config.consumeKey) {
            this.requestOpen();
         } else {
            this.confirming = true;
         }
      }
   }

   private void requestOpen() {
      if (!this.handedOff) {
         this.handedOff = true;
         this.confirming = false;
         boolean skip = this.config.allowSkip && this.minecraft != null && this.minecraft.player != null && this.minecraft.player.isShiftKeyDown();
         CrateMedia.stop();
         this.stopSanctuary();
         FSNetwork.sendToServer(new RequestOpenPacket(this.pos, skip));
         if (this.minecraft != null) {
            this.minecraft.setScreen(null);
         }
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      if (!this.renderSanctuary(g, this.width, this.height)) {
         CrateMedia.renderBackground(g, this.width, this.height);
      }

      boolean ready = this.sceneReady();
      if (this.openButton != null) {
         this.openButton.visible = ready;
      }

      if (this.poolButton != null) {
         this.poolButton.visible = ready;
      }

      if (ready) {
         g.fillGradient(0, 0, this.width, 56, -1342177280, 0);
         g.fillGradient(0, this.height - 76, this.width, this.height, 0, -1073741824);
         this.renderTexts(g);
         this.renderAudioControl(g, mouseX, mouseY);
         this.renderMediaProblems(g);
      }

      if (CrateMedia.isLoading()) {
         this.renderLoadingOverlay(g);
      }

      super.render(g, mouseX, mouseY, partialTick);
      if (this.confirming) {
         this.renderConfirm(g, mouseX, mouseY);
      }

      this.renderNotice(g);
      this.renderTransitions(g);
   }

   private void renderConfirm(GuiGraphics g, int mouseX, int mouseY) {
      int boxW = Math.min(this.width - 40, 226);
      int boxH = 62;
      int x = (this.width - boxW) / 2;
      int y = (this.height - boxH) / 2;
      g.fill(0, 0, this.width, this.height, 1711276032);
      FSGui.panel(g, x, y, boxW, boxH);
      g.drawCenteredString(this.font, "§f¿Consumir 1 llave?", x + boxW / 2, y + 10, -1);
      ItemStack key = this.usableKey();
      String keyName = key.isEmpty() ? "-" : key.getHoverName().getString();
      g.drawCenteredString(this.font, "§7" + this.font.plainSubstrByWidth(keyName, boxW - 20), x + boxW / 2, y + 23, -5592406);
      int bw = (boxW - 30) / 2;
      int by = y + boxH - 26;
      this.confirmAcceptX = x + 10;
      this.confirmRejectX = x + 20 + bw;
      this.confirmButtonY = by;
      this.confirmButtonW = bw;
      drawMiniButton(g, this.font, this.confirmAcceptX, by, bw, "Aceptar", -11746198, mouseX, mouseY);
      drawMiniButton(g, this.font, this.confirmRejectX, by, bw, "Rechazar", -2796465, mouseX, mouseY);
   }

   private static void drawMiniButton(GuiGraphics g, Font font, int x, int y, int w, String label, int accent, int mouseX, int mouseY) {
      int h = 18;
      boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
      g.fill(x, y, x + w, y + h, -16777216);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hovered ? -7300954 : -9539986);
      g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered ? -4800821 : -7566196);
      g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, -12632257);
      if (hovered) {
         g.fill(x + 1, y + h - 1, x + w - 1, y + h, accent);
      }

      g.drawCenteredString(font, label, x + w / 2, y + (h - 9) / 2 + 1, -1);
   }

   private void renderNotice(GuiGraphics g) {
      if (!this.notice.isEmpty() && System.currentTimeMillis() <= this.noticeUntil) {
         int textWidth = this.font.width(this.notice);
         int x = (this.width - textWidth) / 2;
         int y = this.height - 74;
         g.fill(x - 6, y - 4, x + textWidth + 6, y + 12, -1342177280);
         g.drawString(this.font, this.notice, x, y, -1, false);
      }
   }

   private void renderTransitions(GuiGraphics g) {
      float elapsed = (float)(System.currentTimeMillis() - this.openedAt);
      if (!(elapsed >= 900.0F)) {
         float progress = elapsed / 900.0F;
         float remaining = 1.0F - progress;
         float eased = remaining * remaining * remaining;
         int alpha = (int)(eased * 255.0F) & 0xFF;
         g.fill(0, 0, this.width, this.height, alpha << 24);
      }
   }

   private void renderTexts(GuiGraphics g) {
      int centerX = this.width / 2;
      int y = 12;
      long now = System.currentTimeMillis();
      y = this.drawStyled(g, this.config.sceneHeader, centerX, y, now, 12);
      FSTextStyle name = this.config.nameStyle == null ? new FSTextStyle() : this.config.nameStyle.copy();
      name.text = LootEngine.colorize(this.config.displayName == null ? "" : this.config.displayName);
      if (!name.isBlank()) {
         g.drawCenteredString(this.font, name.toComponent(now), centerX, y, -1);
         y += 13;
      }

      y = this.drawStyled(g, this.config.sceneSubtitle, centerX, y, now, 11);

      for (FSTextStyle line : this.config.sceneLines) {
         if (line != null && !line.isBlank()) {
            y = this.drawStyled(g, line, centerX, y, now, 10);
         } else {
            y += 5;
         }
      }
   }

   private int drawStyled(GuiGraphics g, FSTextStyle style, int centerX, int y, long now, int advance) {
      if (style != null && !style.isBlank()) {
         g.drawCenteredString(this.font, style.toComponent(now), centerX, y, -1);
         return y + advance;
      } else {
         return y;
      }
   }

   private void renderAudioControl(GuiGraphics g, int mouseX, int mouseY) {
      boolean hovered = this.isOverAudio((double)mouseX, (double)mouseY);
      int x = this.audioX;
      int y = this.audioY;
      g.fill(x, y, x + 14, y + 14, hovered ? -1342177280 : 1879048192);
      if (hovered) {
         g.fill(x, y, x + 14, y + 1, 1627389951);
         g.fill(x, y + 14 - 1, x + 14, y + 14, 1627389951);
         g.fill(x, y, x + 1, y + 14, 1627389951);
         g.fill(x + 14 - 1, y, x + 14, y + 14, 1627389951);
      }

      String icon = muted ? "§8♪" : "§f♪";
      g.drawCenteredString(this.font, icon, x + 7, y + 3, 16777215);
      if (!muted) {
         int trackWidth = 10;
         int filled = Math.max(1, Math.min(trackWidth, Math.round((float)(trackWidth * Math.min(masterVolume, 100)) / 100.0F)));
         g.fill(x + 2, y + 14 - 3, x + 2 + trackWidth, y + 14 - 2, 1627389951);
         g.fill(x + 2, y + 14 - 3, x + 2 + filled, y + 14 - 2, -11746198);
      } else {
         for (int i = 2; i < 12; i++) {
            g.fill(x + i, y + 14 - 2 - i, x + i + 1, y + 14 - 1 - i, -2796465);
         }
      }
   }

   private void renderLoadingOverlay(GuiGraphics g) {
      int boxW = 132;
      int boxH = 26;
      int x = (this.width - boxW) / 2;
      int y = this.height / 2 - boxH / 2;
      g.fill(x, y, x + boxW, y + boxH, -1073741824);
      g.fill(x, y, x + boxW, y + 1, 1358954495);
      g.fill(x, y + boxH - 1, x + boxW, y + boxH, 1358954495);
      long now = System.currentTimeMillis();
      int dots = (int)(now / 350L % 4L);
      StringBuilder text = new StringBuilder("Cargando media");

      for (int i = 0; i < dots; i++) {
         text.append('.');
      }

      g.drawCenteredString(this.font, "§f" + text, x + boxW / 2, y + 5, 16777215);
      int trackX = x + 12;
      int trackW = boxW - 24;
      int trackY = y + 17;
      g.fill(trackX, trackY, trackX + trackW, trackY + 3, 1610612736);
      int sweepW = Math.max(12, trackW / 4);
      int offset = (int)(now / 6L % (long)(trackW + sweepW)) - sweepW;
      int from = Math.max(trackX, trackX + offset);
      int to = Math.min(trackX + trackW, trackX + offset + sweepW);
      if (to > from) {
         g.fill(from, trackY, to, trackY + 3, -11884320);
      }
   }

   private void renderMediaProblems(GuiGraphics g) {
      String videoError = CrateMedia.videoError();
      String musicError = CrateMedia.musicError();
      if (videoError != null || musicError != null) {
         List<String> lines = new ArrayList<>();
         if (videoError != null) {
            lines.add("§cNo se pudo cargar el video: §f" + videoError);
         }

         if (musicError != null) {
            lines.add("§cNo se pudo cargar la musica: §f" + musicError);
         }

         lines.add("§7Revisa los enlaces en el editor. Detalles en logs/latest.log");
         int maxWidth = this.width - 16;
         List<String> wrapped = new ArrayList<>();

         for (String line : lines) {
            wrapped.addAll(this.font.getSplitter().splitLines(line, maxWidth, Style.EMPTY).stream().map(FormattedText::getString).toList());
         }

         int y = this.height - 84 - wrapped.size() * (9 + 1);

         for (String line : wrapped) {
            g.drawString(this.font, line, 8, y, -1, true);
            y += 9 + 1;
         }
      }
   }

   private boolean sceneReady() {
      return CrateMedia.isSceneReady() ? true : this.openedAt != 0L && System.currentTimeMillis() - this.openedAt >= 15000L;
   }

   private boolean isOverAudio(double mouseX, double mouseY) {
      return !this.sceneReady()
         ? false
         : mouseX >= (double)this.audioX && mouseX < (double)(this.audioX + 14) && mouseY >= (double)this.audioY && mouseY < (double)(this.audioY + 14);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.confirming) {
         if (button == 0 && mouseY >= (double)this.confirmButtonY && mouseY < (double)(this.confirmButtonY + 18)) {
            if (mouseX >= (double)this.confirmAcceptX && mouseX < (double)(this.confirmAcceptX + this.confirmButtonW)) {
               this.requestOpen();
               return true;
            }

            if (mouseX >= (double)this.confirmRejectX && mouseX < (double)(this.confirmRejectX + this.confirmButtonW)) {
               this.confirming = false;
               return true;
            }
         }

         return true;
      } else if (button == 0 && this.isOverAudio(mouseX, mouseY)) {
         muted = !muted;
         applyVolume();
         saveVolumeState();
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
      if (this.isOverAudio(mouseX, mouseY) && delta != 0.0) {
         masterVolume = Math.max(0, masterVolume + (delta > 0.0 ? 1 : -1));
         applyVolume();
         saveVolumeState();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, delta);
      }
   }

   public void onClose() {
      if (this.confirming) {
         this.confirming = false;
      } else {
         CrateMedia.stop();
         this.stopSanctuary();
         if (this.minecraft != null) {
            this.minecraft.setScreen(null);
         }
      }
   }

   /** El santuario detrás (también desde «VER RECOMPENSAS»); false si no lo hay. */
   boolean renderSanctuary(GuiGraphics g, int w, int h) {
      current = this;
      return this.sanctuary != null && this.sanctuary.renderIdle(g, w, h);
   }

   /** La última que dibujó el santuario (MediaTicker lo apaga si se sale de ella por otro camino). */
   private static CratePreOpenScreen current;

   /** Si en esa pantalla va el santuario detrás (entonces el mundo no se dibuja: no se ve y cuesta). */
   public static boolean sanctuaryShown(Screen screen) {
      if (screen instanceof CratePoolScreen pool) {
         screen = pool.parentScreen();
      }

      return screen instanceof CratePreOpenScreen pre && pre.sanctuary != null;
   }

   public static void stopIdleSanctuary() {
      if (current != null) {
         current.stopSanctuary();
         current = null;
      }
   }

   private void stopSanctuary() {
      if (this.sanctuary != null) {
         this.sanctuary.removed();
         this.sanctuary = null;
      }
   }

   public void tick() {
      // (los avisos del juego —logros, recetas— no salen encima del santuario)
      if (this.sanctuary != null && this.minecraft != null) {
         this.minecraft.getToasts().clear();
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static void applyVolume() {
      CrateMedia.applyVolume(muted ? 0.0F : (float)masterVolume / 100.0F);
   }

   private static Path volumeFile() {
      try {
         return FMLPaths.CONFIGDIR.get().resolve("fscrates").resolve("media.properties");
      } catch (Throwable var1) {
         return Path.of("config", "fscrates", "media.properties");
      }
   }

   private static synchronized void loadVolumeState() {
      if (!volumeLoaded) {
         volumeLoaded = true;

         try {
            Path file = volumeFile();
            if (Files.exists(file)) {
               Properties props = new Properties();

               try (InputStream in = Files.newInputStream(file)) {
                  props.load(in);
               }

               masterVolume = Math.max(0, Integer.parseInt(props.getProperty("volumen", "100").trim()));
               muted = Boolean.parseBoolean(props.getProperty("silenciado", "false").trim().toLowerCase(Locale.ROOT));
            }
         } catch (Exception var7) {
            masterVolume = 100;
            muted = false;
         }
      }
   }

   private static void saveVolumeState() {
      try {
         Path file = volumeFile();
         Files.createDirectories(file.getParent());
         Properties props = new Properties();
         props.setProperty("volumen", Integer.toString(masterVolume));
         props.setProperty("silenciado", Boolean.toString(muted));

         try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "Fantastic Crates - audio de la pantalla de pre-apertura");
         }
      } catch (Exception var7) {
      }
   }
}
