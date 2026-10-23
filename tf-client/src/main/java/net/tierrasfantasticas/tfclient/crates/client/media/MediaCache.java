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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

public final class MediaCache {
   private static final String USER_AGENT = "Mozilla/5.0 (FSCrates)";
   private static final int CONNECT_TIMEOUT_MS = 10000;
   private static final int READ_TIMEOUT_MS = 20000;
   private static final int MAX_REDIRECTS = 6;
   private static final Pattern DRIVE_CONFIRM = Pattern.compile("name=\"confirm\"\\s+value=\"([^\"]+)\"");
   private static final Pattern DRIVE_FORM_ACTION = Pattern.compile("action=\"([^\"]+)\"");
   private static final Pattern DRIVE_HIDDEN_INPUT = Pattern.compile("name=\"([^\"]+)\"\\s+value=\"([^\"]*)\"");
   private static final ExecutorService DOWNLOADER = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "FSCrates-MediaDownload");
      t.setDaemon(true);
      return t;
   });
   private static final Map<String, CompletableFuture<Path>> IN_FLIGHT = new ConcurrentHashMap<>();

   private MediaCache() {
   }

   public static Path cacheRoot() {
      Minecraft mc = Minecraft.getInstance();
      Path base = mc != null && mc.gameDirectory != null ? mc.gameDirectory.toPath() : Path.of(".");
      return base.resolve("fscrates").resolve("cache");
   }

   public static Path folderFor(MediaCache.Kind kind) {
      return cacheRoot().resolve(kind.folder());
   }

   public static Path cacheFileFor(String url, MediaCache.Kind kind) {
      return folderFor(kind).resolve(hash(url) + extensionOf(url, kind));
   }

   public static Path findCached(String url, MediaCache.Kind kind) {
      if (url != null && !url.isBlank()) {
         String normalized = MediaUrls.normalize(url);
         String prefix = hash(normalized);
         Path exact = cacheFileFor(normalized, kind);
         if (isUsable(exact, kind)) {
            return exact;
         } else {
            Path folder = folderFor(kind);
            if (!Files.isDirectory(folder)) {
               return null;
            } else {
               List<Path> candidates;
               try (Stream<Path> files = Files.list(folder)) {
                  candidates = files.filter(p -> p.getFileName().toString().startsWith(prefix + "."))
                     .filter(p -> !p.getFileName().toString().endsWith(".part"))
                     .collect(Collectors.toList());
               } catch (IOException var12) {
                  return null;
               }

               Path good = null;

               for (Path candidate : candidates) {
                  if (isUsable(candidate, kind)) {
                     if (good == null) {
                        good = candidate;
                     }
                  } else {
                     discard(candidate, kind);
                  }
               }

               return good;
            }
         }
      } else {
         return null;
      }
   }

   public static boolean isReady(Path file) {
      try {
         return file != null && Files.isRegularFile(file) && Files.size(file) > 0L ? sniff(file) != MediaCache.MediaType.UNKNOWN : false;
      } catch (IOException var2) {
         return false;
      }
   }

   public static boolean isUsable(Path file, MediaCache.Kind kind) {
      try {
         return file != null && Files.isRegularFile(file) && Files.size(file) > 0L ? sniff(file).matches(kind) : false;
      } catch (IOException var3) {
         return false;
      }
   }

   private static void discard(Path file, MediaCache.Kind kind) {
      try {
         long size = Files.size(file);
         MediaCache.MediaType type = sniff(file);
         Files.deleteIfExists(file);
         FSCrates.LOGGER
            .warn(
               "[FSCrates] Se descarta '{}' de la cache: {} bytes y el contenido no es media valida para {} ({}). Se volvera a descargar.",
               new Object[]{
                  file.getFileName(),
                  size,
                  kind == MediaCache.Kind.VIDEO ? "video/imagen" : "musica",
                  type == MediaCache.MediaType.UNKNOWN ? "tipo no reconocido" : "es " + type
               }
            );
      } catch (IOException var5) {
         FSCrates.LOGGER.warn("[FSCrates] No se pudo borrar el archivo invalido '{}': {}", file, var5.toString());
      }
   }

   public static boolean isCached(String url, MediaCache.Kind kind) {
      return findCached(url, kind) != null;
   }

   public static CompletableFuture<Path> obtain(String url, MediaCache.Kind kind) {
      if (url != null && !url.isBlank()) {
         String clean = MediaUrls.normalize(url);
         if (MediaUrls.wasRewritten(url, clean)) {
            FSCrates.LOGGER.info("[FSCrates] Enlace convertido a descarga directa: {} -> {}", url.trim(), clean);
         }

         Path cached = findCached(clean, kind);
         if (cached != null) {
            FSCrates.LOGGER.debug("[FSCrates] Media ya en cache, no se descarga: {}", cached.getFileName());
            return CompletableFuture.completedFuture(cached);
         } else {
            String key = kind.name() + "|" + clean;
            return IN_FLIGHT.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
                  Path target;
                  try {
                     Path existing = findCached(clean, kind);
                     if (existing == null) {
                        target = downloadTo(clean, cacheFileFor(clean, kind));
                        FSCrates.LOGGER.info("[FSCrates] Media descargada y guardada en cache: {} ({})", target.getFileName(), sniff(target));
                        return target;
                     }

                     target = existing;
                  } catch (Exception var9) {
                     FSCrates.LOGGER.error("[FSCrates] No se pudo descargar la media '{}': {}", clean, var9.toString());
                     throw new CompletionException(var9);
                  } finally {
                     IN_FLIGHT.remove(k);
                  }

                  return target;
               }, DOWNLOADER));
         }
      } else {
         return CompletableFuture.failedFuture(new IOException("url vacio"));
      }
   }

   private static Path downloadTo(String url, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      Path part = target.resolveSibling(target.getFileName() + ".part");

      try (
         InputStream in = openStream(url);
         OutputStream out = Files.newOutputStream(part);
      ) {
         byte[] buf = new byte[65536];

         int n;
         while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
         }
      }

      if (Files.size(part) <= 0L) {
         Files.deleteIfExists(part);
         throw new IOException("la descarga quedo vacia");
      } else {
         MediaCache.MediaType type = sniff(part);
         if (type == MediaCache.MediaType.UNKNOWN) {
            long size = Files.size(part);
            boolean html = looksLikeHtml(part);
            Files.deleteIfExists(part);
            throw new IOException(
               html
                  ? "el enlace devolvio una pagina web ("
                     + size
                     + " bytes), no un archivo. Suele ser un enlace de 'ver' en vez de descarga directa, o que Drive pide confirmacion por cuota superada. Comparte el archivo como 'cualquier persona con el enlace'"
                  : "lo descargado (" + size + " bytes) no es un formato reconocible: tiene que ser MP4/MOV/M4V, PNG/JPG/GIF o MP3/OGG/WAV"
            );
         } else if (!type.matches(kindOf(target))) {
            Files.deleteIfExists(part);
            throw new IOException(
               "el archivo es "
                  + type
                  + " y no encaja en esta pestaña: "
                  + (type.isVisual() ? "los videos e imagenes van en la pestaña Videos" : "la musica va en la pestaña Musica")
            );
         } else {
            Path finalTarget = target;
            if (type != MediaCache.MediaType.UNKNOWN && !target.getFileName().toString().endsWith(type.extension())) {
               String name = target.getFileName().toString();
               int dot = name.lastIndexOf(46);
               String base = dot > 0 ? name.substring(0, dot) : name;
               finalTarget = target.resolveSibling(base + type.extension());
            }

            Files.move(part, finalTarget, StandardCopyOption.REPLACE_EXISTING);
            return finalTarget;
         }
      }
   }

   private static MediaCache.Kind kindOf(Path target) {
      Path parent = target.getParent();
      String folder = parent == null ? "" : parent.getFileName().toString();
      return MediaCache.Kind.MUSIC.folder().equals(folder) ? MediaCache.Kind.MUSIC : MediaCache.Kind.VIDEO;
   }

   private static boolean looksLikeHtml(Path file) {
      try {
         boolean var5;
         try (InputStream in = Files.newInputStream(file)) {
            byte[] head = new byte[256];
            int n = in.read(head);
            if (n <= 0) {
               return false;
            }

            String text = new String(head, 0, n, StandardCharsets.ISO_8859_1).trim().toLowerCase(Locale.ROOT);
            var5 = text.startsWith("<!doctype") || text.startsWith("<html") || text.contains("<head") || text.contains("<title") || text.startsWith("<?xml");
         }

         return var5;
      } catch (IOException var8) {
         return false;
      }
   }

   static InputStream openStream(String url) throws IOException {
      String current = url.trim();
      String cookies = "";

      for (int attempt = 0; attempt < 2; attempt++) {
         HttpURLConnection conn = null;

         for (int i = 0; i < 6; i++) {
            URL u = new URL(current);
            conn = (HttpURLConnection)u.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (FSCrates)");
            if (!cookies.isEmpty()) {
               conn.setRequestProperty("Cookie", cookies);
            }

            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            int code = conn.getResponseCode();
            cookies = mergeCookies(cookies, conn.getHeaderFields());
            if (code / 100 != 3) {
               if (code / 100 != 2) {
                  conn.disconnect();
                  throw new IOException("HTTP " + code + " al descargar " + current);
               }
               break;
            }

            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location == null) {
               throw new IOException("redireccion sin cabecera Location");
            }

            current = new URL(u, location).toString();
         }

         if (conn == null) {
            throw new IOException("demasiadas redirecciones");
         }

         String type = conn.getContentType();
         boolean html = type != null && type.toLowerCase(Locale.ROOT).contains("text/html");
         if (!html) {
            return conn.getInputStream();
         }

         String body = readLimited(conn.getInputStream(), 524288);
         conn.disconnect();
         String next = resolveDriveConfirm(current, body);
         if (next == null) {
            throw new IOException("el servidor devolvio HTML en vez del archivo (revisa que el link sea de descarga directa)");
         }

         current = next;
      }

      throw new IOException("no se pudo obtener el archivo tras la confirmacion de Google Drive");
   }

   private static String resolveDriveConfirm(String pageUrl, String html) {
      try {
         Matcher form = DRIVE_FORM_ACTION.matcher(html);
         if (form.find()) {
            String action = unescapeHtml(form.group(1));
            StringBuilder sb = new StringBuilder(new URL(new URL(pageUrl), action).toString());
            boolean first = !sb.toString().contains("?");

            for (Matcher hidden = DRIVE_HIDDEN_INPUT.matcher(html); hidden.find(); first = false) {
               sb.append((char)(first ? '?' : '&')).append(hidden.group(1)).append('=').append(unescapeHtml(hidden.group(2)));
            }

            return sb.toString();
         }

         Matcher confirm = DRIVE_CONFIRM.matcher(html);
         if (confirm.find()) {
            String sep = pageUrl.contains("?") ? "&" : "?";
            return pageUrl + sep + "confirm=" + confirm.group(1);
         }

         if (pageUrl.contains("drive.google.com") && !pageUrl.contains("confirm=")) {
            String sep = pageUrl.contains("?") ? "&" : "?";
            return pageUrl + sep + "confirm=t";
         }
      } catch (Exception var7) {
      }

      return null;
   }

   private static String unescapeHtml(String s) {
      return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'");
   }

   private static String mergeCookies(String existing, Map<String, List<String>> headers) {
      StringBuilder sb = new StringBuilder(existing);

      for (Entry<String, List<String>> e : headers.entrySet()) {
         if (e.getKey() != null && "set-cookie".equalsIgnoreCase(e.getKey())) {
            for (String raw : e.getValue()) {
               String pair = raw.split(";", 2)[0];
               if (!pair.isBlank()) {
                  if (sb.length() > 0) {
                     sb.append("; ");
                  }

                  sb.append(pair.trim());
               }
            }
         }
      }

      return sb.toString();
   }

   private static String readLimited(InputStream in, int max) throws IOException {
      String var6;
      try (InputStream stream = in) {
         ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
         byte[] buf = new byte[8192];

         int n;
         while (out.size() < max && (n = stream.read(buf)) != -1) {
            out.write(buf, 0, n);
         }

         var6 = out.toString(StandardCharsets.UTF_8);
      }

      return var6;
   }

   public static MediaCache.MediaType sniff(Path file) {
      if (file == null) {
         return MediaCache.MediaType.UNKNOWN;
      } else {
         byte[] head = new byte[32];

         int read;
         try (InputStream in = Files.newInputStream(file)) {
            read = in.readNBytes(head, 0, head.length);
         } catch (IOException var8) {
            return MediaCache.MediaType.UNKNOWN;
         }

         if (read < 12) {
            return MediaCache.MediaType.UNKNOWN;
         } else if (head[0] == -119 && head[1] == 80 && head[2] == 78 && head[3] == 71) {
            return MediaCache.MediaType.PNG;
         } else if (head[0] == -1 && head[1] == -40 && head[2] == -1) {
            return MediaCache.MediaType.JPEG;
         } else if (head[0] == 71 && head[1] == 73 && head[2] == 70) {
            return MediaCache.MediaType.GIF;
         } else if (head[0] == 26 && head[1] == 69 && head[2] == -33 && head[3] == -93) {
            return MediaCache.MediaType.WEBM;
         } else if (head[4] == 102 && head[5] == 116 && head[6] == 121 && head[7] == 112) {
            return MediaCache.MediaType.MP4;
         } else if (head[0] == 79 && head[1] == 103 && head[2] == 103 && head[3] == 83) {
            return MediaCache.MediaType.OGG;
         } else if (head[0] == 82 && head[1] == 73 && head[2] == 70 && head[3] == 70 && head[8] == 87 && head[9] == 65 && head[10] == 86 && head[11] == 69) {
            return MediaCache.MediaType.WAV;
         } else if (head[0] == 73 && head[1] == 68 && head[2] == 51) {
            return MediaCache.MediaType.MP3;
         } else {
            return head[0] == -1 && (head[1] & 224) == 224 ? MediaCache.MediaType.MP3 : MediaCache.MediaType.UNKNOWN;
         }
      }
   }

   public static String extensionOf(String url, MediaCache.Kind kind) {
      try {
         String path = new URI(url).getPath();
         if (path != null) {
            int dot = path.lastIndexOf(46);
            if (dot >= 0 && dot < path.length() - 1) {
               String ext = path.substring(dot).toLowerCase(Locale.ROOT);
               if (ext.length() <= 6 && ext.matches("\\.[a-z0-9]+")) {
                  return ext;
               }
            }
         }
      } catch (Exception var7) {
      }

      String lower = url.toLowerCase(Locale.ROOT);

      for (String ext : kind == MediaCache.Kind.VIDEO
         ? new String[]{".mp4", ".webm", ".mov", ".m4v", ".png", ".jpg", ".jpeg"}
         : new String[]{".mp3", ".ogg", ".wav"}) {
         if (lower.contains(ext)) {
            return ext;
         }
      }

      return kind.defaultExtension();
   }

   public static String hash(String url) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] digest = md.digest(url.trim().getBytes(StandardCharsets.UTF_8));
         StringBuilder sb = new StringBuilder(32);

         for (int i = 0; i < 16; i++) {
            sb.append(String.format("%02x", digest[i]));
         }

         return sb.toString();
      } catch (Exception var5) {
         return Integer.toHexString(url.trim().hashCode());
      }
   }

   public static boolean isValidUrl(String url) {
      if (url != null && !url.isBlank()) {
         try {
            String scheme = new URI(url.trim()).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
         } catch (Exception var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static enum Kind {
      VIDEO("videos", ".mp4"),
      MUSIC("music", ".mp3");

      private final String folder;
      private final String defaultExtension;

      private Kind(String folder, String defaultExtension) {
         this.folder = folder;
         this.defaultExtension = defaultExtension;
      }

      public String folder() {
         return this.folder;
      }

      public String defaultExtension() {
         return this.defaultExtension;
      }
   }

   public static enum MediaType {
      MP4(".mp4", true),
      WEBM(".webm", true),
      PNG(".png", true),
      JPEG(".jpg", true),
      GIF(".gif", true),
      MP3(".mp3", false),
      OGG(".ogg", false),
      WAV(".wav", false),
      UNKNOWN("", false);

      private final String extension;
      private final boolean visual;

      private MediaType(String extension, boolean visual) {
         this.extension = extension;
         this.visual = visual;
      }

      public String extension() {
         return this.extension;
      }

      public boolean isVisual() {
         return this.visual;
      }

      public boolean isImage() {
         return this == PNG || this == JPEG || this == GIF;
      }

      public boolean matches(MediaCache.Kind kind) {
         if (this == UNKNOWN) {
            return false;
         } else {
            return kind == MediaCache.Kind.VIDEO ? this.visual : !this.visual;
         }
      }
   }
}
