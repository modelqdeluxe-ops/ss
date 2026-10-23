/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client.media;

import net.tierrasfantasticas.tfclient.crates.FSCrates;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

public final class DefaultMedia {
   private static final String MUSIC = "default_music.mp3";

   private DefaultMedia() {
   }

   private static Path defaultsFolder() {
      return MediaCache.cacheRoot().resolve("defaults");
   }

   public static void warmUp() {
      Thread thread = new Thread(() -> {
         try {
            music();
            FSCrates.LOGGER.info("[FSCrates] Musica por defecto lista en cache.");
         } catch (Throwable var1) {
            FSCrates.LOGGER.warn("[FSCrates] No se pudo precargar la musica por defecto: {}", var1.toString());
         }
      }, "FSCrates-MediaWarmup");
      thread.setDaemon(true);
      thread.setPriority(1);
      thread.start();
   }

   public static List<Path> music() {
      List<Path> out = new ArrayList<>();
      Path track = extract("default_music.mp3");
      if (track != null) {
         out.add(track);
      }

      return out;
   }

   public static synchronized Path extract(String name) {
      try {
         long size = resourceSize(name);
         int dot = name.lastIndexOf(46);
         String base = dot > 0 ? name.substring(0, dot) : name;
         String extension = dot > 0 ? name.substring(dot) : "";
         String versioned = base + "_" + (size > 0L ? Long.toString(size) : "x") + extension;
         Path target = defaultsFolder().resolve(versioned);
         if (MediaCache.isReady(target)) {
            return target;
         } else {
            Files.createDirectories(target.getParent());
            cleanOldVersions(base, extension, versioned);

            try (InputStream in = open(name)) {
               if (in == null) {
                  FSCrates.LOGGER.warn("[FSCrates] No se encontro la media por defecto '{}' dentro del JAR.", name);
                  return null;
               }

               Path part = target.resolveSibling(versioned + ".part");
               Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
               Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            }

            FSCrates.LOGGER.info("[FSCrates] Media por defecto extraida: {} ({} bytes)", target.getFileName(), size);
            return MediaCache.isReady(target) ? target : null;
         }
      } catch (Exception var13) {
         FSCrates.LOGGER.error("[FSCrates] No se pudo extraer la media por defecto '{}': {}", name, var13.toString());
         return null;
      }
   }

   private static long resourceSize(String name) {
      try {
         URL url = DefaultMedia.class.getResource("/assets/fscrates/media/" + name);
         if (url != null) {
            URLConnection connection = url.openConnection();
            long length = connection.getContentLengthLong();

            try {
               connection.getInputStream().close();
            } catch (IOException var9) {
            }

            if (length > 0L) {
               return length;
            }
         }
      } catch (Exception var10) {
      }

      try {
         long var6;
         try (InputStream in = open(name)) {
            if (in == null) {
               return -1L;
            }

            long total = 0L;
            byte[] buffer = new byte[65536];

            int read;
            while ((read = in.read(buffer)) != -1) {
               total += (long)read;
            }

            var6 = total;
         }

         return var6;
      } catch (Exception var12) {
         return -1L;
      }
   }

   private static void cleanOldVersions(String base, String extension, String keep) {
      List<Path> stale = new ArrayList<>();

      try (Stream<Path> files = Files.list(defaultsFolder())) {
         files.filter(p -> {
            String fileName = p.getFileName().toString();
            boolean versioned = fileName.startsWith(base + "_") && fileName.endsWith(extension);
            boolean legacy = fileName.equals(base + extension);
            return (versioned || legacy) && !fileName.equals(keep);
         }).forEach(stale::add);
      } catch (IOException var10) {
         return;
      }

      for (Path path : stale) {
         try {
            Files.deleteIfExists(path);
            FSCrates.LOGGER.info("[FSCrates] Borrada media por defecto obsoleta: {}", path.getFileName());
         } catch (IOException var8) {
         }
      }
   }

   private static InputStream open(String name) throws IOException {
      InputStream in = DefaultMedia.class.getResourceAsStream("/assets/fscrates/media/" + name);
      if (in != null) {
         return in;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getResourceManager() != null) {
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation("fscrates", "media/" + name));
            if (res.isPresent()) {
               return res.get().open();
            }
         }

         return null;
      }
   }
}
