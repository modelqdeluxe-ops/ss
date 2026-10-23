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
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.FrameGrab;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.api.PictureWithMetadata;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.DemuxerTrackMeta;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class GopDecoder implements AutoCloseable {
   private final Path file;
   private final int workerCount;
   private final int[] keyFrames;
   private final int totalFrames;
   private volatile int window;
   private static final long FRAME_BUDGET_BYTES = 75497472L;
   private boolean windowAdjusted;
   private final AtomicInteger nextGop = new AtomicInteger();
   private int nextToEmit;
   private final Map<Integer, GopDecoder.YuvFrame> pending = new HashMap<>();
   private final Deque<GopDecoder.YuvFrame> pool = new ArrayDeque<>();
   private final Object lock = new Object();
   private volatile boolean stopped;
   private volatile Throwable failure;
   private Thread[] workers;
   private int finishedWorkers;
   private volatile long startedAtMs = System.currentTimeMillis();
   private static final int PARALLEL_PIXEL_THRESHOLD = 700000;

   private GopDecoder(Path file, int workerCount, int[] keyFrames, int totalFrames) {
      this.file = file;
      this.workerCount = workerCount;
      this.keyFrames = keyFrames;
      this.totalFrames = totalFrames;
      int gopSize = keyFrames.length > 1 ? Math.max(1, keyFrames[1] - keyFrames[0]) : 12;
      this.window = Math.max(10, Math.min(20, gopSize * workerCount + 2));
   }

   static GopDecoder tryCreate(Path file) {
      int cores = Runtime.getRuntime().availableProcessors();
      if (cores < 4) {
         return null;
      } else {
         try {
            FrameGrab probe = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file.toFile()));
            DemuxerTrackMeta meta = probe.getVideoTrack().getMeta();
            int[] seekFrames = meta == null ? null : meta.getSeekFrames();
            int total = meta == null ? 0 : meta.getTotalFrames();
            if (seekFrames != null && seekFrames.length >= 3 && total > 0) {
               Picture first = probe.getNativeFrame();
               if (first == null) {
                  return null;
               } else {
                  int pixels = first.getCroppedWidth() * first.getCroppedHeight();
                  if (pixels > 2700000) {
                     FSCrates.LOGGER
                        .warn(
                           "[FSCrates] El video '{}' es de {}x{} ({} Mpx). Se reproducira, pero a saltos: decodificar en Java puro no llega a tanto. Recomendado 1280x720 o 1920x1080.",
                           new Object[]{
                              file.getFileName(), first.getCroppedWidth(), first.getCroppedHeight(), String.format("%.1f", (double)pixels / 1000000.0)
                           }
                        );
                  }

                  if (pixels < 700000) {
                     FSCrates.LOGGER.info("[FSCrates] Video '{}' ({} px): un solo hilo de decodificado (va sobrado).", file.getFileName(), pixels);
                     return null;
                  } else {
                     int workers = Math.max(2, Math.min(4, cores / 2 - 1));
                     GopDecoder decoder = new GopDecoder(file, workers, seekFrames, total);
                     FSCrates.LOGGER
                        .info(
                           "[FSCrates] Video '{}': {} fotogramas, {} GOPs, decodificando con {} hilos.",
                           new Object[]{file.getFileName(), total, seekFrames.length, workers}
                        );
                     return decoder;
                  }
               }
            } else {
               return null;
            }
         } catch (Throwable var10) {
            return null;
         }
      }
   }

   private int effectiveWindow() {
      long elapsed = System.currentTimeMillis() - this.startedAtMs;
      return elapsed >= 1200L ? this.window : Math.max(3, Math.min(this.window, (int)(3L + elapsed / 60L)));
   }

   void start() {
      this.startedAtMs = System.currentTimeMillis();
      this.workers = new Thread[this.workerCount];

      for (int i = 0; i < this.workerCount; i++) {
         Thread t = new Thread(this::runWorker, "FSCrates-VideoDecode-" + i);
         t.setDaemon(true);
         t.setPriority(1);
         this.workers[i] = t;
         t.start();
      }
   }

   private int gopStart(int gop) {
      return this.keyFrames[gop];
   }

   private int gopEnd(int gop) {
      return gop + 1 < this.keyFrames.length ? this.keyFrames[gop + 1] : this.totalFrames;
   }

   private void runWorker() {
      SeekableByteChannel channel = null;

      try {
         channel = NIOUtils.readableChannel(this.file.toFile());
         FrameGrab grab = FrameGrab.createFrameGrab(channel);

         while (!this.stopped) {
            int gop = this.nextGop.getAndIncrement();
            if (gop >= this.keyFrames.length) {
               return;
            }

            int from = this.gopStart(gop);
            int to = this.gopEnd(gop);
            synchronized (this.lock) {
               while (true) {
                  if (!this.stopped && from > this.nextToEmit + this.effectiveWindow()) {
                     this.lock.wait(50L);
                  } else {
                     if (this.stopped) {
                        return;
                     }
                     break;
                  }
               }
            }

            grab.seekToFrameSloppy(from);
            double baseTimestamp = -1.0;

            for (int decoded = 0; decoded < to - from && !this.stopped; decoded++) {
               PictureWithMetadata meta = grab.getNativeFrameWithMetadata();
               if (meta == null || meta.getPicture() == null) {
                  break;
               }

               if (baseTimestamp < 0.0) {
                  baseTimestamp = meta.getTimestamp();
               }

               double duration = meta.getDuration() > 1.0E-4 ? meta.getDuration() : 0.03333333333333333;
               int offset = (int)Math.round((meta.getTimestamp() - baseTimestamp) / duration);
               int index = Math.max(from, Math.min(to - 1, from + offset));
               this.publish(index, meta);
            }
         }
      } catch (Throwable var32) {
         if (!this.stopped) {
            this.failure = var32;
            synchronized (this.lock) {
               this.lock.notifyAll();
            }
         }
      } finally {
         synchronized (this.lock) {
            this.finishedWorkers++;
            this.lock.notifyAll();
         }

         NIOUtils.closeQuietly(channel);
      }
   }

   private void publish(int index, PictureWithMetadata meta) throws InterruptedException {
      Picture picture = meta.getPicture();
      int w = picture.getCroppedWidth();
      int h = picture.getCroppedHeight();
      int cw = (w + 1) / 2;
      int ch = (h + 1) / 2;
      GopDecoder.YuvFrame frame;
      synchronized (this.lock) {
         if (!this.windowAdjusted) {
            this.windowAdjusted = true;
            long frameBytes = (long)w * (long)h * 3L / 2L;
            int allowed = (int)Math.max(12L, Math.min((long)this.window, 75497472L / Math.max(1L, frameBytes)));
            if (allowed < this.window) {
               FSCrates.LOGGER.info("[FSCrates] Video {}x{}: ventana de decodificado reducida a {} fotogramas por memoria.", new Object[]{w, h, allowed});
               this.window = allowed;
            }
         }

         while (!this.stopped && index > this.nextToEmit + this.effectiveWindow()) {
            this.lock.wait(50L);
         }

         if (this.stopped) {
            return;
         }

         frame = this.pool.poll();
      }

      if (frame == null) {
         frame = new GopDecoder.YuvFrame();
      }

      if (frame.luma == null || frame.width != w || frame.height != h) {
         frame.luma = new byte[w * h];
         frame.cb = new byte[cw * ch];
         frame.cr = new byte[cw * ch];
         frame.width = w;
         frame.height = h;
         frame.chromaWidth = cw;
         frame.chromaHeight = ch;
      }

      frame.timestamp = meta.getTimestamp();
      frame.duration = meta.getDuration();
      frame.index = index;
      byte[] srcY = picture.getPlaneData(0);
      byte[] srcU = picture.getPlaneData(1);
      byte[] srcV = picture.getPlaneData(2);
      int lumaStride = picture.getPlaneWidth(0);
      int chromaStride = picture.getPlaneWidth(1);

      for (int y = 0; y < h; y++) {
         System.arraycopy(srcY, y * lumaStride, frame.luma, y * w, w);
      }

      for (int y = 0; y < ch; y++) {
         System.arraycopy(srcU, y * chromaStride, frame.cb, y * cw, cw);
         System.arraycopy(srcV, y * chromaStride, frame.cr, y * cw, cw);
      }

      synchronized (this.lock) {
         this.pending.put(index, frame);
         this.lock.notifyAll();
      }
   }

   GopDecoder.YuvFrame take() throws InterruptedException {
      synchronized (this.lock) {
         while (!this.stopped) {
            if (this.failure != null) {
               return null;
            }

            if (this.nextToEmit >= this.totalFrames) {
               return null;
            }

            GopDecoder.YuvFrame frame = this.pending.remove(this.nextToEmit);
            if (frame != null) {
               this.nextToEmit++;
               this.lock.notifyAll();
               return frame;
            }

            if (this.finishedWorkers >= this.workerCount && this.pending.isEmpty()) {
               return null;
            }

            this.lock.wait(100L);
         }

         return null;
      }
   }

   boolean reachedEnd() {
      synchronized (this.lock) {
         return this.nextToEmit >= this.totalFrames;
      }
   }

   void recycle(GopDecoder.YuvFrame frame) {
      if (frame != null) {
         synchronized (this.lock) {
            if (this.pool.size() <= this.window) {
               this.pool.offer(frame);
            }

            this.lock.notifyAll();
         }
      }
   }

   Throwable failure() {
      return this.failure;
   }

   private static void closeQuietly(FrameGrab grab) {
      if (grab != null) {
         try {
            if (grab.getVideoTrack() instanceof AutoCloseable closeable) {
               closeable.close();
            }
         } catch (Exception var3) {
         }
      }
   }

   @Override
   public void close() {
      this.stopped = true;
      synchronized (this.lock) {
         this.lock.notifyAll();
      }

      if (this.workers != null) {
         for (Thread t : this.workers) {
            if (t != null) {
               t.interrupt();
            }
         }

         this.workers = null;
      }

      synchronized (this.lock) {
         this.pending.clear();
         this.pool.clear();
      }
   }

   static final class YuvFrame {
      byte[] luma;
      byte[] cb;
      byte[] cr;
      int width;
      int height;
      int chromaWidth;
      int chromaHeight;
      double timestamp;
      double duration;
      int index;
   }
}
