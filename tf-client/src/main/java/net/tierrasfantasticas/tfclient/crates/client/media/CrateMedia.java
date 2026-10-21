package net.tierrasfantasticas.tfclient.crates.client.media;

import net.tierrasfantasticas.tfclient.crates.FSCrates;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gui.GuiGraphics;

public final class CrateMedia {
   private static boolean active;
   private static CompletableFuture<Path> videoFuture;
   private static CompletableFuture<Path> musicFuture;
   private static VideoPlayer video;
   private static MusicPlayer music;
   private static String videoError;
   private static String musicError;
   private static float volume = 1.0F;

   private CrateMedia() {
   }

   public static synchronized void begin(CrateConfig config) {
      stop();
      active = true;
      List<String> videoUrls = validUrls(config == null ? null : config.videos);
      List<String> musicUrls = validUrls(config == null ? null : config.music);
      boolean customVideo = !videoUrls.isEmpty();
      boolean customMusic = !musicUrls.isEmpty();
      String chosenVideo = customVideo ? pick(videoUrls) : null;
      String chosenMusic = customMusic ? pick(musicUrls) : null;
      FSCrates.LOGGER
         .info(
            "[FSCrates] Media de la escena -> video: {} | musica: {}",
            chosenVideo == null ? "ninguno (la caja no tiene video configurado: fondo negro)" : chosenVideo,
            chosenMusic == null ? "la del mod (la caja no tiene ninguna configurada)" : chosenMusic
         );
      videoFuture = chosenVideo != null ? MediaCache.obtain(chosenVideo, MediaCache.Kind.VIDEO) : null;
      musicFuture = chosenMusic != null
         ? MediaCache.obtain(chosenMusic, MediaCache.Kind.MUSIC)
         : CompletableFuture.supplyAsync(() -> pickPath(DefaultMedia.music()));
   }

   public static synchronized void ensurePlayers() {
      if (active) {
         if (video == null && videoFuture != null && videoFuture.isDone()) {
            Path file = resolve(videoFuture, "video");
            videoFuture = null;
            if (file != null) {
               video = new VideoPlayer(file);
               video.start();
            }
         }

         if (music == null && musicFuture != null && musicFuture.isDone()) {
            Path file = resolve(musicFuture, "musica");
            musicFuture = null;
            if (file != null) {
               music = new MusicPlayer(file);
               music.setVolume(volume);
               music.start();
            }
         }
      }
   }

   private static Path resolve(CompletableFuture<Path> future, String what) {
      try {
         Path file = future.getNow(null);
         if (file == null) {
            FSCrates.LOGGER.warn("[FSCrates] No hay {} disponible para la pantalla de pre-apertura.", what);
            return null;
         } else {
            try {
               FSCrates.LOGGER
                  .info("[FSCrates] {} lista: {} ({} bytes, {}).", new Object[]{what, file.getFileName(), Files.size(file), MediaCache.sniff(file)});
            } catch (Exception var5) {
               FSCrates.LOGGER.info("[FSCrates] {} lista: {}.", what, file.getFileName());
            }

            return file;
         }
      } catch (Exception var6) {
         Throwable cause = (Throwable)(var6.getCause() != null ? var6.getCause() : var6);
         String reason = cause.getMessage() == null ? cause.toString() : cause.getMessage();
         FSCrates.LOGGER.error("[FSCrates] Fallo al preparar la {}: {}", what, reason);
         noteError(what, reason);
         return null;
      }
   }

   private static void noteError(String what, String reason) {
      if ("video".equals(what)) {
         videoError = reason;
      } else {
         musicError = reason;
      }
   }

   public static String videoError() {
      return videoError;
   }

   public static String musicError() {
      return musicError;
   }

   public static boolean isLoading() {
      return active && (videoFuture != null && !videoFuture.isDone() || musicFuture != null && !musicFuture.isDone());
   }

   public static boolean isActive() {
      return active;
   }

   public static synchronized boolean isSceneReady() {
      if (!active) {
         return false;
      } else {
         boolean videoReady = video != null ? video.hasPicture() || video.hasFailed() : videoFuture == null;
         boolean musicReady = music != null || musicFuture == null;
         return videoReady && musicReady;
      }
   }

   public static synchronized void applyVolume(float linear) {
      volume = Math.max(0.0F, linear);
      if (music != null) {
         music.setVolume(volume);
      }
   }

   public static void renderBackground(GuiGraphics g, int width, int height) {
      g.fill(0, 0, width, height, -16777216);
      ensurePlayers();
      if (video != null && !video.hasFailed() && video.hasPicture()) {
         video.render(g, width, height, 1.0F);
      }
   }

   public static synchronized void stop() {
      active = false;
      videoFuture = null;
      musicFuture = null;
      videoError = null;
      musicError = null;
      if (video != null) {
         video.close();
         video = null;
      }

      if (music != null) {
         music.close();
         music = null;
      }
   }

   private static List<String> validUrls(List<String> source) {
      List<String> out = new ArrayList<>();
      if (source != null) {
         for (String url : source) {
            if (MediaCache.isValidUrl(url)) {
               out.add(url.trim());
            }
         }
      }

      return out;
   }

   private static String pick(List<String> options) {
      return options.size() == 1 ? options.get(0) : options.get(ThreadLocalRandom.current().nextInt(options.size()));
   }

   private static Path pickPath(List<Path> options) {
      if (options != null && !options.isEmpty()) {
         return options.size() == 1 ? options.get(0) : options.get(ThreadLocalRandom.current().nextInt(options.size()));
      } else {
         return null;
      }
   }
}
