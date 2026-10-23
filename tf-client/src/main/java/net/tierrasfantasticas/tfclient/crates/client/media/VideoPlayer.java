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
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.model.Picture;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

public final class VideoPlayer implements AutoCloseable {
   private static final int READY_CAPACITY = 3;
   private static final int REORDER_WINDOW = 8;
   private static final double DEFAULT_FRAME_SECONDS = 0.041666666666666664;
   private static final double MAX_DRIFT_SECONDS = 0.4;
   private static final int PARALLEL_CONVERT_PIXELS = 2500000;
   private static final int PARALLEL_SCALE_PIXELS = 1000000;
   private static int textureSequence;
   private static ForkJoinPool convertPool;
   private volatile Path file;
   private final boolean staticImage;
   private final boolean unsupported;
   private final BlockingQueue<VideoPlayer.RgbaFrame> ready = new ArrayBlockingQueue<>(3);
   private final BlockingQueue<VideoPlayer.RgbaFrame> recycled = new ArrayBlockingQueue<>(5);
   private final Deque<VideoPlayer.YuvFrame> yuvPool = new ArrayDeque<>();
   private Thread decoderThread;
   private volatile boolean stopped;
   private volatile boolean failed;
   private int[] scratch;
   private volatile GopDecoder gopDecoder;
   private YuvToRgb converter;
   private volatile int panelWidth;
   private volatile int panelHeight;
   private byte[] sharpened;
   private int[] scaled;
   private short[] scaleMid;
   private FrameScaler.Taps tapsX;
   private FrameScaler.Taps tapsY;
   private VideoTexture texture;
   private ResourceLocation textureId;
   private VideoPlayer.RgbaFrame current;
   private long baseNanos;
   private double baseTimestamp;
   private boolean clockStarted;

   public VideoPlayer(Path file) {
      this.file = file;
      MediaCache.MediaType type = MediaCache.sniff(file);
      if (type == MediaCache.MediaType.UNKNOWN) {
         type = typeFromName(file);
      }

      this.staticImage = type.isImage();
      this.unsupported = type == MediaCache.MediaType.WEBM;
      if (this.unsupported) {
         this.failed = true;
         FSCrates.LOGGER
            .warn(
               "[FSCrates] '{}' es un WEBM y no se puede reproducir (VP8/VP9 no tiene decodificador en Java puro). Convertelo a MP4 (H.264) o usa una imagen PNG/JPG.",
               file == null ? "?" : file.getFileName()
            );
      }
   }

   private void reportFailure(Throwable t) {
      String name = this.file == null ? "?" : this.file.getFileName().toString();
      String message = t.getMessage() == null ? "" : t.getMessage();
      if (message.contains("High bit depth")) {
         FSCrates.LOGGER
            .error(
               "[FSCrates] '{}' esta en 10 bits y no se puede reproducir. Expórtalo en 8 bits (en ffmpeg: -pix_fmt yuv420p). Mira config/fscrates/_LEEME_VIDEOS.txt.",
               name
            );
      } else if (message.contains("Not a video track") || message.contains("hvc1") || message.contains("hev1")) {
         FSCrates.LOGGER
            .error(
               "[FSCrates] '{}' parece HEVC (H.265) y solo se soporta H.264. Reconviertelo (en ffmpeg: -c:v libx264). Mira config/fscrates/_LEEME_VIDEOS.txt.",
               name
            );
      } else if (message.contains("Unsupported h264 feature")) {
         FSCrates.LOGGER
            .error(
               "[FSCrates] '{}' usa una opcion de H.264 que no se soporta ({}). Reconviertelo con los ajustes de config/fscrates/_LEEME_VIDEOS.txt.",
               name,
               message
            );
      } else {
         FSCrates.LOGGER
            .error(
               "[FSCrates] No se pudo reproducir '{}': {}. Tiene que ser MP4 con video H.264 de 8 bits. Mira config/fscrates/_LEEME_VIDEOS.txt.",
               name,
               t.toString()
            );
      }
   }

   private static MediaCache.MediaType typeFromName(Path file) {
      String name = file == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
      if (name.endsWith(".png")) {
         return MediaCache.MediaType.PNG;
      } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
         return MediaCache.MediaType.JPEG;
      } else if (name.endsWith(".gif")) {
         return MediaCache.MediaType.GIF;
      } else {
         return !name.endsWith(".webm") && !name.endsWith(".mkv") ? MediaCache.MediaType.MP4 : MediaCache.MediaType.WEBM;
      }
   }

   public void start() {
      if (this.decoderThread == null && !this.failed && this.file != null) {
         Runnable task;
         if (this.staticImage) {
            task = this::runImage;
         } else {
            this.file = Mp4Sanitizer.prepare(this.file);
            GopDecoder parallel = GopDecoder.tryCreate(this.file);
            task = parallel != null ? () -> this.runParallelVideo(parallel) : this::runVideo;
         }

         this.decoderThread = new Thread(task, "FSCrates-VideoDecode");
         this.decoderThread.setDaemon(true);
         this.decoderThread.setPriority(1);
         this.decoderThread.start();
      }
   }

   public boolean hasFailed() {
      return this.failed;
   }

   public boolean hasPicture() {
      return this.current != null || !this.ready.isEmpty();
   }

   public void render(GuiGraphics g, int areaWidth, int areaHeight, float alpha) {
      if (!this.failed) {
         this.advanceFrame();
         if (this.current != null && this.texture != null && this.texture.hasFrame()) {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            if (guiScale <= 0.0) {
               guiScale = 1.0;
            }

            int targetWidth = (int)Math.ceil((double)areaWidth * guiScale);
            int targetHeight = (int)Math.ceil((double)areaHeight * guiScale);
            int fw = this.current.width();
            int fh = this.current.height();
            float scale = Math.max((float)targetWidth / (float)fw, (float)targetHeight / (float)fh);
            int dw = Math.max(1, Math.round((float)fw * scale));
            int dh = Math.max(1, Math.round((float)fh * scale));
            int x = (targetWidth - dw) / 2;
            int y = (targetHeight - dh) / 2;
            this.texture.setSharpFiltering(Math.abs(scale - 1.0F) < 0.01F);
            this.panelWidth = targetWidth;
            this.panelHeight = targetHeight;
            g.setColor(1.0F, 1.0F, 1.0F, Math.max(0.0F, Math.min(1.0F, alpha)));
            g.pose().pushPose();
            g.pose().scale((float)(1.0 / guiScale), (float)(1.0 / guiScale), 1.0F);
            g.blit(this.textureId, x, y, dw, dh, 0.0F, 0.0F, fw, fh, fw, fh);
            g.pose().popPose();
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }
   }

   private void advanceFrame() {
      if (this.staticImage) {
         if (this.current == null) {
            VideoPlayer.RgbaFrame first = this.ready.poll();
            if (first != null) {
               this.show(first);
            }
         }
      } else {
         long now = System.nanoTime();
         VideoPlayer.RgbaFrame chosen = null;

         while (true) {
            VideoPlayer.RgbaFrame next = this.ready.peek();
            if (next == null) {
               break;
            }

            if (!this.clockStarted) {
               this.clockStarted = true;
               this.baseNanos = now;
               this.baseTimestamp = next.timestamp();
            }

            if (next.timestamp() < this.baseTimestamp) {
               this.baseNanos = now;
               this.baseTimestamp = next.timestamp();
            }

            long dueAt = this.baseNanos + (long)((next.timestamp() - this.baseTimestamp) * 1.0E9);
            if (now < dueAt) {
               break;
            }

            if (now - dueAt > 400000000L) {
               this.baseNanos = now;
               this.baseTimestamp = next.timestamp();
            }

            this.ready.poll();
            if (chosen != null) {
               this.discard(chosen);
            }

            chosen = next;
         }

         if (chosen != null) {
            this.show(chosen);
         }
      }
   }

   private void discard(VideoPlayer.RgbaFrame frame) {
      if (frame != null && !this.recycled.offer(frame)) {
         MemoryUtil.memFree(frame.pixels());
      }
   }

   private void show(VideoPlayer.RgbaFrame frame) {
      if (this.texture == null) {
         this.texture = new VideoTexture();
         this.textureId = new ResourceLocation("fscrates", "video/" + textureSequence++);
         Minecraft.getInstance().getTextureManager().register(this.textureId, this.texture);
      }

      try {
         this.texture.upload(frame.pixels(), frame.width(), frame.height());
      } catch (Throwable var3) {
         FSCrates.LOGGER.error("[FSCrates] Error subiendo el fotograma de video a la GPU: {}", var3.toString());
         this.failed = true;
         return;
      }

      VideoPlayer.RgbaFrame previous = this.current;
      this.current = frame;
      if (previous != null && !this.recycled.offer(previous)) {
         MemoryUtil.memFree(previous.pixels());
      }
   }

   private void runParallelVideo(GopDecoder first) {
      GopDecoder decoder = first;
      this.gopDecoder = first;

      try {
         decoder.start();

         while (!this.stopped) {
            GopDecoder.YuvFrame frame = decoder.take();
            if (frame == null) {
               Throwable failure = decoder.failure();
               if (failure != null) {
                  throw failure;
               }

               boolean finished = decoder.reachedEnd();
               decoder.close();
               this.gopDecoder = null;
               if (this.stopped || !finished) {
                  return;
               }

               GopDecoder next = GopDecoder.tryCreate(this.file);
               if (next == null) {
                  this.runVideo();
                  return;
               }

               decoder = next;
               this.gopDecoder = next;
               next.start();
            } else {
               VideoPlayer.RgbaFrame out = this.convertShared(
                  frame.luma, frame.cb, frame.cr, frame.width, frame.height, frame.chromaWidth, frame.chromaHeight, frame.timestamp, frame.duration
               );
               decoder.recycle(frame);
               boolean queued = false;

               while (!this.stopped && !queued) {
                  queued = this.ready.offer(out, 100L, TimeUnit.MILLISECONDS);
               }

               if (!queued) {
                  MemoryUtil.memFree(out.pixels());
                  return;
               }
            }
         }
      } catch (InterruptedException var7) {
      } catch (Throwable var8) {
         FSCrates.LOGGER
            .error("[FSCrates] Fallo el decodificado en paralelo de '{}': {}. Se reintenta con un solo hilo.", this.file.getFileName(), var8.toString());
         decoder.close();
         this.gopDecoder = null;
         if (!this.stopped) {
            this.runVideo();
         }
      }
   }

   private void runVideo() {
      SeekableByteChannel channel = null;
      PriorityQueue<VideoPlayer.YuvFrame> window = new PriorityQueue<>(9, Comparator.comparingDouble(f -> f.timestamp));

      try {
         channel = NIOUtils.readableChannel(this.file.toFile());
         FrameGrab grab = FrameGrab.createFrameGrab(channel);
         long totalEmitted = 0L;
         int emptyPasses = 0;

         while (!this.stopped) {
            PictureWithMetadata meta = grab.getNativeFrameWithMetadata();
            if (meta != null && meta.getPicture() != null) {
               window.add(this.capture(meta));
               if (window.size() > 8) {
                  if (!this.emit(window.poll())) {
                     return;
                  }

                  totalEmitted++;
               }
            } else {
               int flushed;
               for (flushed = 0; !this.stopped && !window.isEmpty(); totalEmitted++) {
                  if (!this.emit(window.poll())) {
                     return;
                  }

                  flushed++;
               }

               if (totalEmitted == 0L) {
                  throw new IllegalStateException("no se pudo decodificar ningun fotograma");
               }

               emptyPasses = flushed == 0 ? emptyPasses + 1 : 0;
               if (emptyPasses >= 2) {
                  FSCrates.LOGGER.warn("[FSCrates] El video '{}' dejo de dar fotogramas; se detiene la reproduccion.", this.file.getFileName());
                  return;
               }

               grab.seekToFrameSloppy(0);
            }
         }
      } catch (Throwable var12) {
         this.reportFailure(var12);
         this.failed = true;
      } finally {
         window.forEach(this::release);
         window.clear();
         NIOUtils.closeQuietly(channel);
      }
   }

   private VideoPlayer.YuvFrame capture(PictureWithMetadata meta) {
      Picture picture = meta.getPicture();
      int w = picture.getCroppedWidth();
      int h = picture.getCroppedHeight();
      int cw = (w + 1) / 2;
      int ch = (h + 1) / 2;
      VideoPlayer.YuvFrame frame = this.borrowYuv();
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

      return frame;
   }

   private boolean emit(VideoPlayer.YuvFrame frame) {
      boolean var9;
      try {
         VideoPlayer.RgbaFrame out = this.convert(frame);

         do {
            if (this.stopped) {
               MemoryUtil.memFree(out.pixels());
               return false;
            }
         } while (!this.ready.offer(out, 100L, TimeUnit.MILLISECONDS));

         var9 = true;
      } catch (InterruptedException var7) {
         return false;
      } finally {
         this.release(frame);
      }

      return var9;
   }

   private VideoPlayer.RgbaFrame convert(VideoPlayer.YuvFrame frame) {
      return this.convertShared(
         frame.luma, frame.cb, frame.cr, frame.width, frame.height, frame.chromaWidth, frame.chromaHeight, frame.timestamp, frame.duration
      );
   }

   private VideoPlayer.RgbaFrame convertShared(byte[] luma, byte[] cb, byte[] cr, int w, int h, int cw, int ch, double timestamp, double duration) {
      int panelW = this.panelWidth;
      int panelH = this.panelHeight;
      float scale = panelW > 0 && panelH > 0 ? Math.max((float)panelW / (float)w, (float)panelH / (float)h) : 1.0F;
      int outW = w;
      int outH = h;
      if (scale > 1.0F) {
         int candidateW = Math.max(1, Math.round((float)w * scale));
         int candidateH = Math.max(1, Math.round((float)h * scale));
         if (FrameScaler.worthIt(w, h, candidateW, candidateH)) {
            outW = candidateW;
            outH = candidateH;
         }
      }

      boolean upscaling = outW != w || outH != h;
      int needed = outW * outH * 4;
      VideoPlayer.RgbaFrame reuse = this.recycled.poll();
      ByteBuffer buffer;
      if (reuse != null && reuse.pixels().capacity() == needed) {
         buffer = reuse.pixels();
      } else {
         if (reuse != null) {
            MemoryUtil.memFree(reuse.pixels());
         }

         buffer = MemoryUtil.memAlloc(needed);
      }

      if (this.scratch == null || this.scratch.length != w * h) {
         this.scratch = new int[w * h];
      }

      int[] scratch = this.scratch;
      YuvToRgb rgb = this.converter;
      if (rgb == null) {
         rgb = this.file == null ? YuvToRgb.byResolution(w, h) : YuvToRgb.forFile(this.file, w, h);
         this.converter = rgb;
         FSCrates.LOGGER.info("[FSCrates] '{}' {}x{}: color {}.", new Object[]{this.file == null ? "?" : this.file.getFileName(), w, h, rgb.describe()});
      }

      YuvToRgb use = rgb;
      float amount = upscaling ? 0.0F : LumaSharpen.amountFor(scale);
      int amount8 = Math.round(amount * 256.0F);
      byte[] target;
      if (amount > 0.0F) {
         if (this.sharpened == null || this.sharpened.length != w * h) {
            this.sharpened = new byte[w * h];
         }

         target = this.sharpened;
      } else {
         target = null;
      }

      IntConsumer rowJob = y -> {
         byte[] src = luma;
         if (target != null) {
            LumaSharpen.row(luma, target, w, h, y, amount8);
            src = target;
         }

         use.row(y, src, cb, cr, w, h, cw, ch, scratch);
      };
      ForkJoinPool pool = w * h >= 2500000 ? convertPool() : null;
      if (pool == null) {
         IntStream.range(0, h).forEach(rowJob);
      } else {
         pool.submit(() -> IntStream.range(0, h).parallel().forEach(rowJob)).join();
      }

      int[] result = scratch;
      if (upscaling) {
         ForkJoinPool scalePool = outW * outH >= 1000000 ? convertPool() : null;
         result = this.upscale(scratch, w, h, outW, outH, scalePool);
      }

      buffer.clear();
      buffer.asIntBuffer().put(result, 0, outW * outH);
      return new VideoPlayer.RgbaFrame(buffer, outW, outH, timestamp, duration);
   }

   private int[] upscale(int[] src, int w, int h, int outW, int outH, ForkJoinPool pool) {
      if (this.tapsX == null || !this.tapsX.matches(w, outW)) {
         this.tapsX = FrameScaler.taps(w, outW);
      }

      if (this.tapsY == null || !this.tapsY.matches(h, outH)) {
         this.tapsY = FrameScaler.taps(h, outH);
      }

      int midNeeded = FrameScaler.midSize(outW, h);
      if (this.scaleMid == null || this.scaleMid.length != midNeeded) {
         this.scaleMid = new short[midNeeded];
      }

      if (this.scaled == null || this.scaled.length != outW * outH) {
         this.scaled = new int[outW * outH];
      }

      short[] mid = this.scaleMid;
      int[] out = this.scaled;
      FrameScaler.Taps hx = this.tapsX;
      FrameScaler.Taps vy = this.tapsY;
      if (pool == null) {
         IntStream.range(0, h).forEach(r -> FrameScaler.rowHorizontal(src, w, mid, outW, r, hx));
         IntStream.range(0, outH).forEach(r -> FrameScaler.rowVertical(mid, outW, out, r, vy));
      } else {
         pool.submit(() -> {
            IntStream.range(0, h).parallel().forEach(r -> FrameScaler.rowHorizontal(src, w, mid, outW, r, hx));
            IntStream.range(0, outH).parallel().forEach(r -> FrameScaler.rowVertical(mid, outW, out, r, vy));
         }).join();
      }

      return out;
   }

   private void runImage() {
      try {
         BufferedImage img = ImageIO.read(new File(this.file.toString()));
         if (img == null) {
            throw new IllegalStateException("formato de imagen no reconocido");
         }

         int w = img.getWidth();
         int h = img.getHeight();
         int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
         int[] rgba = new int[w * h];

         for (int i = 0; i < rgba.length; i++) {
            int c = argb[i];
            rgba[i] = c & -16711936 | (c & 0xFF) << 16 | c >> 16 & 0xFF;
         }

         ByteBuffer buffer = MemoryUtil.memAlloc(w * h * 4);
         buffer.asIntBuffer().put(rgba);
         this.ready.offer(new VideoPlayer.RgbaFrame(buffer, w, h, 0.0, 0.0));
      } catch (Throwable var8) {
         FSCrates.LOGGER.error("[FSCrates] No se pudo cargar la imagen '{}': {}", this.file.getFileName(), var8.toString());
         this.failed = true;
      }
   }

   private VideoPlayer.YuvFrame borrowYuv() {
      VideoPlayer.YuvFrame frame = this.yuvPool.poll();
      return frame == null ? new VideoPlayer.YuvFrame() : frame;
   }

   private void release(VideoPlayer.YuvFrame frame) {
      if (frame != null && this.yuvPool.size() <= 10) {
         this.yuvPool.offer(frame);
      }
   }

   private static synchronized ForkJoinPool convertPool() {
      if (convertPool == null) {
         int parallelism = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
         if (parallelism <= 1) {
            return null;
         }

         AtomicInteger counter = new AtomicInteger();
         convertPool = new ForkJoinPool(parallelism, pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName("FSCrates-YUV-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
         }, null, false);
      }

      return convertPool;
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
      GopDecoder decoder = this.gopDecoder;
      if (decoder != null) {
         decoder.close();
         this.gopDecoder = null;
      }

      Thread thread = this.decoderThread;
      if (thread != null) {
         thread.interrupt();
         this.decoderThread = null;
      }

      if (this.textureId != null) {
         ResourceLocation id = this.textureId;
         this.textureId = null;
         Minecraft mc = Minecraft.getInstance();
         if (mc != null) {
            mc.execute(() -> {
               try {
                  mc.getTextureManager().release(id);
               } catch (Throwable var3x) {
               }
            });
         }
      }

      this.texture = null;
      VideoPlayer.RgbaFrame cur = this.current;
      this.current = null;
      if (cur != null) {
         MemoryUtil.memFree(cur.pixels());
      }

      VideoPlayer.RgbaFrame pending;
      while ((pending = this.ready.poll()) != null) {
         MemoryUtil.memFree(pending.pixels());
      }

      VideoPlayer.RgbaFrame spare;
      while ((spare = this.recycled.poll()) != null) {
         MemoryUtil.memFree(spare.pixels());
      }

      this.yuvPool.clear();
   }

   private static record RgbaFrame(ByteBuffer pixels, int width, int height, double timestamp, double duration) {
   }

   private static final class YuvFrame {
      byte[] luma;
      byte[] cb;
      byte[] cr;
      int width;
      int height;
      int chromaWidth;
      int chromaHeight;
      double timestamp;
      double duration;
   }
}
