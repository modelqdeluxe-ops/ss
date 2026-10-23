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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class Mp4Sanitizer {
   private static final int MAX_HEADER_BYTES = 33554432;

   private Mp4Sanitizer() {
   }

   public static Path prepare(Path file) {
      if (file == null) {
         return null;
      } else {
         try {
            List<Long> extra = findNonVideoTracks(file);
            if (extra.isEmpty()) {
               return file;
            } else {
               Path target = writableCopy(file);
               patch(target, extra);
               FSCrates.LOGGER.info("[FSCrates] '{}': {} pista(s) sin video apartadas para que el video se pueda abrir.", file.getFileName(), extra.size());
               return target;
            }
         } catch (Throwable var3) {
            FSCrates.LOGGER.debug("[FSCrates] No se pudo revisar las pistas de '{}': {}", file, var3.toString());
            return file;
         }
      }
   }

   private static List<Long> findNonVideoTracks(Path file) throws IOException {
      List<Long> found = new ArrayList<>();

      Object moovBytes;
      try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
         long size = channel.size();
         Mp4Sanitizer.Box moov = findBox(channel, 0L, size, "moov");
         if (moov == null) {
            return found;
         }

         long moovLength = (long)(moov.end() - moov.contentStart);
         if (moovLength > 0L && moovLength <= 33554432L) {
            byte[] moovBytesx = new byte[(int)moovLength];
            read(channel, (long)moov.contentStart, ByteBuffer.wrap(moovBytesx));

            for (Mp4Sanitizer.Box trak : children(moovBytesx, 0, moovBytesx.length, "trak")) {
               String handler = handlerOf(moovBytesx, trak);
               if (!"vide".equals(handler)) {
                  found.add((long)moov.contentStart + (long)trak.start);
               }
            }

            return found;
         }

         moovBytes = found;
      }

      return (List<Long>)moovBytes;
   }

   private static String handlerOf(byte[] buffer, Mp4Sanitizer.Box trak) {
      for (Mp4Sanitizer.Box mdia : children(buffer, trak.contentStart, trak.end(), "mdia")) {
         for (Mp4Sanitizer.Box hdlr : children(buffer, mdia.contentStart, mdia.end(), "hdlr")) {
            int at = hdlr.contentStart + 8;
            if (at + 4 <= buffer.length) {
               return new String(buffer, at, 4, StandardCharsets.US_ASCII);
            }
         }
      }

      return null;
   }

   private static Path writableCopy(Path file) throws IOException {
      Path root = MediaCache.cacheRoot();
      Path absolute = file.toAbsolutePath().normalize();
      if (absolute.startsWith(root.getParent() == null ? root : root.getParent())) {
         return file;
      } else {
         Path folder = root.resolve("sinaudio");
         Files.createDirectories(folder);
         Path copy = folder.resolve(MediaCache.hash(absolute.toString()) + ".mp4");
         if (Files.isRegularFile(copy) && Files.size(copy) == Files.size(file)) {
            return copy;
         } else {
            Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
         }
      }
   }

   private static void patch(Path file, List<Long> trackOffsets) throws IOException {
      try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
         for (long offset : trackOffsets) {
            ByteBuffer free = ByteBuffer.wrap("free".getBytes(StandardCharsets.US_ASCII));
            channel.position(offset + 4L);

            while (free.hasRemaining()) {
               channel.write(free);
            }
         }
      }
   }

   private static List<Mp4Sanitizer.Box> children(byte[] buffer, int from, int to, String type) {
      List<Mp4Sanitizer.Box> out = new ArrayList<>();
      int p = from;

      while (p + 8 <= to) {
         long size = readUint32(buffer, p);
         String boxType = new String(buffer, p + 4, 4, StandardCharsets.US_ASCII);
         int header = 8;
         if (size == 1L) {
            if (p + 16 > to) {
               break;
            }

            size = readUint64(buffer, p + 8);
            header = 16;
         } else if (size == 0L) {
            size = (long)(to - p);
         }

         if (size < (long)header || (long)p + size > (long)to) {
            break;
         }

         if (boxType.equals(type)) {
            out.add(new Mp4Sanitizer.Box(p, size, p + header));
         }

         p += (int)size;
      }

      return out;
   }

   private static Mp4Sanitizer.Box findBox(FileChannel channel, long from, long to, String type) throws IOException {
      long p = from;
      ByteBuffer head = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);

      while (p + 8L <= to) {
         head.clear();
         head.limit(8);
         read(channel, p, head);
         head.flip();
         long size = (long)head.getInt() & 4294967295L;
         byte[] typeBytes = new byte[4];
         head.get(typeBytes);
         String boxType = new String(typeBytes, StandardCharsets.US_ASCII);
         int header = 8;
         if (size == 1L) {
            head.clear();
            head.limit(8);
            read(channel, p + 8L, head);
            head.flip();
            size = head.getLong();
            header = 16;
         } else if (size == 0L) {
            size = to - p;
         }

         if (size < (long)header || p + size > to) {
            return null;
         }

         if (boxType.equals(type)) {
            return new Mp4Sanitizer.Box((int)p, size, (int)(p + (long)header));
         }

         p += size;
      }

      return null;
   }

   private static void read(FileChannel channel, long position, ByteBuffer into) throws IOException {
      long at = position;

      while (into.hasRemaining()) {
         int n = channel.read(into, at);
         if (n < 0) {
            throw new IOException("archivo cortado en " + at);
         }

         at += (long)n;
      }
   }

   private static long readUint32(byte[] b, int at) {
      return (long)(b[at] & 255) << 24 | (long)((b[at + 1] & 255) << 16) | (long)((b[at + 2] & 255) << 8) | (long)(b[at + 3] & 255);
   }

   private static long readUint64(byte[] b, int at) {
      long v = 0L;

      for (int i = 0; i < 8; i++) {
         v = v << 8 | (long)(b[at + i] & 255);
      }

      return v;
   }

   private static final class Box {
      final int start;
      final long size;
      final int contentStart;

      Box(int start, long size, int contentStart) {
         this.start = start;
         this.size = size;
         this.contentStart = contentStart;
      }

      int end() {
         return (int)((long)this.start + this.size);
      }
   }
}
