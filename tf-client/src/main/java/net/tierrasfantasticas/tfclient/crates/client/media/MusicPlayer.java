package net.tierrasfantasticas.tfclient.crates.client.media;

import net.tierrasfantasticas.tfclient.crates.FSCrates;
import net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.decoder.Bitstream;
import net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.decoder.Decoder;
import net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.decoder.Header;
import net.tierrasfantasticas.tfclient.crates.libs.javazoom.jl.decoder.SampleBuffer;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.FloatControl.Type;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class MusicPlayer implements AutoCloseable {
   private static final int OGG_CHUNK_SAMPLES = 4096;
   private final Path file;
   private Thread thread;
   private volatile boolean stopped;
   private volatile SourceDataLine activeLine;
   private volatile float volume = 1.0F;
   private volatile int volumeGeneration;
   private int appliedGeneration = -1;

   public MusicPlayer(Path file) {
      this.file = file;
   }

   public void setVolume(float value) {
      this.volume = Math.max(0.0F, value);
      this.volumeGeneration++;
   }

   public void start() {
      if (this.thread == null && this.file != null) {
         this.thread = new Thread(this::runLoop, "FSCrates-Music");
         this.thread.setDaemon(true);
         this.thread.start();
      }
   }

   private void runLoop() {
      String name = this.file.getFileName().toString().toLowerCase(Locale.ROOT);
      MediaCache.MediaType type = MediaCache.sniff(this.file);

      while (!this.stopped) {
         long startedAt = System.currentTimeMillis();

         try {
            if (type == MediaCache.MediaType.MP3 || name.endsWith(".mp3") || name.endsWith(".mpeg") || name.endsWith(".mpga")) {
               this.streamMp3();
            } else if (type != MediaCache.MediaType.OGG && !name.endsWith(".ogg") && !name.endsWith(".oga")) {
               this.streamSampled();
            } else {
               this.streamOgg();
            }
         } catch (Throwable var6) {
            FSCrates.LOGGER.error("[FSCrates] No se pudo reproducir la musica '{}': {}", name, var6.toString());
            return;
         }

         if (!this.stopped && System.currentTimeMillis() - startedAt < 250L) {
            FSCrates.LOGGER.warn("[FSCrates] La pista '{}' no produjo audio; se detiene el bucle.", name);
            return;
         }
      }
   }

   private void streamMp3() throws Exception {
      SourceDataLine line = null;

      try (InputStream in = new BufferedInputStream(Files.newInputStream(this.file), 16384)) {
         Bitstream bitstream = new Bitstream(in);
         Decoder decoder = new Decoder();

         Header header;
         while (!this.stopped && (header = bitstream.readFrame()) != null) {
            SampleBuffer output = (SampleBuffer)decoder.decodeFrame(header, bitstream);
            if (line == null) {
               AudioFormat format = new AudioFormat((float)output.getSampleFrequency(), 16, output.getChannelCount(), true, false);
               line = this.openLine(format);
            }

            this.applyGainIfNeeded(line);
            byte[] pcm = toLittleEndian(output.getBuffer(), output.getBufferLength());
            line.write(pcm, 0, pcm.length);
            bitstream.closeFrame();
         }

         bitstream.close();
      } finally {
         this.closeLine(line);
      }
   }

   private void streamOgg() throws Exception {
      byte[] bytes = Files.readAllBytes(this.file);
      ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
      SourceDataLine line = null;
      long handle = 0L;

      try {
         data.put(bytes);
         data.flip();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
            if (handle == 0L) {
               throw new IllegalStateException("STBVorbis no pudo abrir el OGG (codigo " + error.get(0) + ")");
            }
         } catch (Throwable var23) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var21) {
                  var23.addSuppressed(var21);
               }
            }

            throw var23;
         }

         if (stack != null) {
            stack.close();
         }

         STBVorbisInfo info = STBVorbisInfo.malloc();

         int sampleRate;
         int channels;
         try {
            STBVorbis.stb_vorbis_get_info(handle, info);
            channels = info.channels();
            sampleRate = info.sample_rate();
         } finally {
            info.free();
         }

         line = this.openLine(new AudioFormat((float)sampleRate, 16, channels, true, false));
         short[] pcm = new short[4096 * channels];

         while (!this.stopped) {
            int samplesPerChannel = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm);
            if (samplesPerChannel <= 0) {
               break;
            }

            this.applyGainIfNeeded(line);
            byte[] out = toLittleEndian(pcm, samplesPerChannel * channels);
            line.write(out, 0, out.length);
         }
      } finally {
         if (handle != 0L) {
            STBVorbis.stb_vorbis_close(handle);
         }

         MemoryUtil.memFree(data);
         this.closeLine(line);
      }
   }

   private void streamSampled() throws Exception {
      SourceDataLine line = null;
      AudioInputStream raw = null;
      AudioInputStream pcmStream = null;

      try {
         raw = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(this.file), 16384));
         AudioFormat source = raw.getFormat();
         AudioFormat target = new AudioFormat(
            Encoding.PCM_SIGNED, source.getSampleRate(), 16, source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false
         );
         pcmStream = AudioSystem.getAudioInputStream(target, raw);
         line = this.openLine(target);
         byte[] buffer = new byte[8192];

         int read;
         while (!this.stopped && (read = pcmStream.read(buffer)) > 0) {
            this.applyGainIfNeeded(line);
            line.write(buffer, 0, read);
         }
      } finally {
         closeQuietly(pcmStream);
         closeQuietly(raw);
         this.closeLine(line);
      }
   }

   private SourceDataLine openLine(AudioFormat format) throws Exception {
      Info info = new Info(SourceDataLine.class, format);
      SourceDataLine line = (SourceDataLine)AudioSystem.getLine(info);
      line.open(format);
      line.start();
      this.activeLine = line;
      this.appliedGeneration = -1;
      this.applyGain(line);
      return line;
   }

   private void applyGainIfNeeded(SourceDataLine line) {
      if (this.appliedGeneration != this.volumeGeneration) {
         this.applyGain(line);
         this.appliedGeneration = this.volumeGeneration;
      }
   }

   private void applyGain(SourceDataLine line) {
      if (line != null) {
         try {
            if (line.isControlSupported(Type.MASTER_GAIN)) {
               FloatControl control = (FloatControl)line.getControl(Type.MASTER_GAIN);
               float linear = this.volume;
               float decibels = linear <= 1.0E-4F ? control.getMinimum() : (float)(20.0 * Math.log10((double)linear));
               control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), decibels)));
            }
         } catch (Exception var5) {
         }
      }
   }

   private static byte[] toLittleEndian(short[] samples, int length) {
      byte[] out = new byte[length * 2];

      for (int i = 0; i < length; i++) {
         short sample = samples[i];
         out[i * 2] = (byte)(sample & 255);
         out[i * 2 + 1] = (byte)(sample >> 8 & 0xFF);
      }

      return out;
   }

   private void closeLine(SourceDataLine line) {
      if (line != null) {
         try {
            line.stop();
            line.flush();
            line.close();
         } catch (Exception var3) {
         }

         if (this.activeLine == line) {
            this.activeLine = null;
         }
      }
   }

   private static void closeQuietly(AutoCloseable closeable) {
      if (closeable != null) {
         try {
            closeable.close();
         } catch (Exception var2) {
         }
      }
   }

   @Override
   public void close() {
      this.stopped = true;
      SourceDataLine line = this.activeLine;
      if (line != null) {
         try {
            line.stop();
            line.flush();
            line.close();
         } catch (Exception var3) {
         }

         this.activeLine = null;
      }

      Thread t = this.thread;
      if (t != null) {
         t.interrupt();
         this.thread = null;
      }
   }
}
