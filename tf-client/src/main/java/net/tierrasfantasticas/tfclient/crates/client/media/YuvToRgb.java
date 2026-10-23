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
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.H264Utils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.SeqParameterSet;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.codecs.h264.io.model.VUIParameters;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.NIOUtils;
import net.tierrasfantasticas.tfclient.crates.libs.org.jcodec.common.io.SeekableByteChannel;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public final class YuvToRgb {
   private static final double[] BT601_LIMITED = new double[]{1.164383, 1.596027, -0.391762, -0.812968, 2.017232};
   private static final double[] BT601_FULL = new double[]{1.0, 1.402, -0.344136, -0.714136, 1.772};
   private static final double[] BT709_LIMITED = new double[]{1.164383, 1.792741, -0.213249, -0.532909, 2.112402};
   private static final double[] BT709_FULL = new double[]{1.0, 1.5748, -0.187324, -0.468124, 1.8556};
   private static final double[] BT2020_LIMITED = new double[]{1.164384, 1.678674, -0.187326, -0.650424, 2.141772};
   private static final double[] BT2020_FULL = new double[]{1.0, 1.4746, -0.164553, -0.571353, 1.8814};
   private final int[] yTab = new int[256];
   private final int[] rFromV = new int[256];
   private final int[] gFromU = new int[256];
   private final int[] gFromV = new int[256];
   private final int[] bFromU = new int[256];
   private final YuvToRgb.Matrix matrix;
   private final boolean fullRange;
   private final boolean declared;

   private YuvToRgb(YuvToRgb.Matrix matrix, boolean fullRange, boolean declared) {
      this.matrix = matrix;
      this.fullRange = fullRange;
      this.declared = declared;

      double[] k = switch (matrix) {
         case BT601 -> fullRange ? BT601_FULL : BT601_LIMITED;
         case BT2020 -> fullRange ? BT2020_FULL : BT2020_LIMITED;
         default -> fullRange ? BT709_FULL : BT709_LIMITED;
      };
      int black = fullRange ? 0 : 16;

      for (int i = 0; i < 256; i++) {
         this.yTab[i] = (int)Math.round(k[0] * (double)(i - black) * 65536.0);
         int c = i - 128;
         this.rFromV[i] = (int)Math.round(k[1] * (double)c * 65536.0);
         this.gFromU[i] = (int)Math.round(k[2] * (double)c * 65536.0);
         this.gFromV[i] = (int)Math.round(k[3] * (double)c * 65536.0);
         this.bFromU[i] = (int)Math.round(k[4] * (double)c * 65536.0);
      }
   }

   public YuvToRgb.Matrix matrix() {
      return this.matrix;
   }

   public boolean fullRange() {
      return this.fullRange;
   }

   public String describe() {
      return this.matrix
         + (this.fullRange ? " rango completo" : " rango limitado")
         + (this.declared ? " (declarado en el archivo)" : " (deducido por la resolucion)");
   }

   public static YuvToRgb forFile(Path file, int width, int height) {
      try {
         VUIParameters vui = readVui(file);
         if (vui != null) {
            boolean full = vui.videoSignalTypePresentFlag && vui.videoFullRangeFlag;
            if (vui.colourDescriptionPresentFlag) {
               YuvToRgb.Matrix m = fromCoefficients(vui.matrixCoefficients);
               if (m != null) {
                  return new YuvToRgb(m, full, true);
               }
            }

            if (vui.videoSignalTypePresentFlag) {
               return new YuvToRgb(byHeight(height), full, false);
            }
         }
      } catch (Throwable var6) {
         FSCrates.LOGGER.debug("[FSCrates] No se pudieron leer los metadatos de color: {}", var6.toString());
      }

      return new YuvToRgb(byHeight(height), false, false);
   }

   public static YuvToRgb byResolution(int width, int height) {
      return new YuvToRgb(byHeight(height), false, false);
   }

   private static YuvToRgb.Matrix byHeight(int height) {
      return height >= 720 ? YuvToRgb.Matrix.BT709 : YuvToRgb.Matrix.BT601;
   }

   private static YuvToRgb.Matrix fromCoefficients(int code) {
      switch (code) {
         case 1:
            return YuvToRgb.Matrix.BT709;
         case 2:
         case 3:
         case 7:
         case 8:
         default:
            return null;
         case 4:
         case 5:
         case 6:
            return YuvToRgb.Matrix.BT601;
         case 9:
         case 10:
            return YuvToRgb.Matrix.BT2020;
      }
   }

   private static VUIParameters readVui(Path file) throws Exception {
      SeekableByteChannel channel = null;

      Object parsed;
      try {
         channel = NIOUtils.readableChannel(new File(file.toString()));
         ByteBuffer priv = FrameGrab.createFrameGrab(channel).getVideoTrack().getMeta().getCodecPrivate();
         if (priv == null) {
            return null;
         }

         ByteBuffer sps = findNal(priv, 7);
         if (sps != null) {
            SeqParameterSet parsedx = H264Utils.readSPS(sps);
            return parsedx == null ? null : parsedx.vuiParams;
         }

         parsed = null;
      } finally {
         if (channel != null) {
            try {
               channel.close();
            } catch (Throwable var14) {
            }
         }
      }

      return (VUIParameters)parsed;
   }

   private static ByteBuffer findNal(ByteBuffer buffer, int nalType) {
      byte[] b = new byte[buffer.remaining()];
      buffer.duplicate().get(b);

      for (int i = 0; i + 4 < b.length; i++) {
         boolean start3 = b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1;
         boolean start4 = b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 0 && b[i + 3] == 1;
         if (start3 || start4) {
            int start = i + (start4 ? 4 : 3);
            if (start < b.length && (b[start] & 31) == nalType) {
               int end = b.length;

               for (int j = start + 1; j + 2 < b.length; j++) {
                  if (b[j] == 0 && b[j + 1] == 0 && (b[j + 2] == 1 || b[j + 2] == 0 && j + 3 < b.length && b[j + 3] == 1)) {
                     end = j;
                     break;
                  }
               }

               return ByteBuffer.wrap(b, start + 1, end - start - 1);
            }
         }
      }

      return null;
   }

   public void row(int y, byte[] luma, byte[] cb, byte[] cr, int w, int h, int cw, int ch, int[] out) {
      int lumaRow = y * w;
      int near = y >> 1;
      int far = (y & 1) == 0 ? near - 1 : near + 1;
      if (far < 0) {
         far = 0;
      } else if (far > ch - 1) {
         far = ch - 1;
      }

      int nearRow = near * cw;
      int farRow = far * cw;
      int lastCol = cw - 1;
      int x = 0;

      for (int cx = 0; cx < cw && x < w; x += 2) {
         int cx2 = cx < lastCol ? cx + 1 : lastCol;
         int uNear = cb[nearRow + cx] + 128;
         int uFar = cb[farRow + cx] + 128;
         int vNear = cr[nearRow + cx] + 128;
         int vFar = cr[farRow + cx] + 128;
         this.write(out, lumaRow + x, luma[lumaRow + x] + 128, 3 * uNear + uFar + 2 >> 2, 3 * vNear + vFar + 2 >> 2);
         if (x + 1 >= w) {
            break;
         }

         int uNext = cb[nearRow + cx2] + 128;
         int uFarNext = cb[farRow + cx2] + 128;
         int vNext = cr[nearRow + cx2] + 128;
         int vFarNext = cr[farRow + cx2] + 128;
         this.write(
            out,
            lumaRow + x + 1,
            luma[lumaRow + x + 1] + 128,
            3 * (uNear + uNext + 1 >> 1) + (uFar + uFarNext + 1 >> 1) + 2 >> 2,
            3 * (vNear + vNext + 1 >> 1) + (vFar + vFarNext + 1 >> 1) + 2 >> 2
         );
         cx++;
      }
   }

   private void write(int[] out, int index, int lumaValue, int u, int v) {
      int yy = this.yTab[lumaValue];
      int r = yy + this.rFromV[v] >> 16;
      int g = yy + this.gFromU[u] + this.gFromV[v] >> 16;
      int b = yy + this.bFromU[u] >> 16;
      out[index] = 0xFF000000 | clamp(b) << 16 | clamp(g) << 8 | clamp(r);
   }

   private static int clamp(int v) {
      return v < 0 ? 0 : (v > 255 ? 255 : v);
   }

   public static enum Matrix {
      BT601("BT.601"),
      BT709("BT.709"),
      BT2020("BT.2020");

      private final String label;

      private Matrix(String label) {
         this.label = label;
      }

      @Override
      public String toString() {
         return this.label;
      }
   }
}
