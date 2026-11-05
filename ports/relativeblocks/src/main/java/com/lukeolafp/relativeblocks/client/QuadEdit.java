package com.lukeolafp.relativeblocks.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Lo que en Fabric hacía el QuadEmitter, sobre los datos de un BakedQuad de bloque (formato BLOCK: 8 enteros por
 * vértice: posición x, y, z; color ABGR; u, v; luz; normal).
 */
@OnlyIn(Dist.CLIENT)
public final class QuadEdit {
   static final int STRIDE = 8;
   private final int[] data;
   private final BakedQuad source;

   public QuadEdit(BakedQuad quad) {
      this.source = quad;
      this.data = quad.getVertices().clone();
   }

   public BakedQuad source() {
      return this.source;
   }

   public float x(int vertex) {
      return Float.intBitsToFloat(this.data[vertex * STRIDE]);
   }

   public float y(int vertex) {
      return Float.intBitsToFloat(this.data[vertex * STRIDE + 1]);
   }

   public float z(int vertex) {
      return Float.intBitsToFloat(this.data[vertex * STRIDE + 2]);
   }

   public void pos(int vertex, float x, float y, float z) {
      this.data[vertex * STRIDE] = Float.floatToRawIntBits(x);
      this.data[vertex * STRIDE + 1] = Float.floatToRawIntBits(y);
      this.data[vertex * STRIDE + 2] = Float.floatToRawIntBits(z);
   }

   public float u(int vertex) {
      return Float.intBitsToFloat(this.data[vertex * STRIDE + 4]);
   }

   public float v(int vertex) {
      return Float.intBitsToFloat(this.data[vertex * STRIDE + 5]);
   }

   public void uv(int vertex, float u, float v) {
      this.data[vertex * STRIDE + 4] = Float.floatToRawIntBits(u);
      this.data[vertex * STRIDE + 5] = Float.floatToRawIntBits(v);
   }

   /** El color del vértice multiplicado por un tinte 0xRRGGBB (el de BlockColors). */
   public void tint(int rgb) {
      int tr = rgb >> 16 & 0xFF;
      int tg = rgb >> 8 & 0xFF;
      int tb = rgb & 0xFF;
      for (int vertex = 0; vertex < 4; vertex++) {
         int abgr = this.data[vertex * STRIDE + 3];
         int r = (abgr & 0xFF) * tr / 255;
         int g = (abgr >> 8 & 0xFF) * tg / 255;
         int b = (abgr >> 16 & 0xFF) * tb / 255;
         this.data[vertex * STRIDE + 3] = abgr & 0xFF000000 | b << 16 | g << 8 | r;
      }
   }

   public void light(int packed) {
      for (int vertex = 0; vertex < 4; vertex++) {
         this.data[vertex * STRIDE + 6] = packed;
      }
   }

   public BakedQuad build(int tintIndex, boolean ao) {
      return new BakedQuad(this.data, tintIndex, this.source.getDirection(), this.source.getSprite(), this.source.isShade(), ao);
   }

   public Direction direction() {
      return this.source.getDirection();
   }

   // --- lo de Fabric (fitTexture / repaint): ajustar las coordenadas de textura a la cara recortada o estirada ---

   /** Los coeficientes de u (o v) = a + b·p + c·q sobre el plano de la cara, o null si no se pueden sacar. */
   public float[] fitTexture(boolean u) {
      Direction face = this.direction();
      int[][] tries = new int[][]{{0, 1, 2}, {0, 1, 3}, {0, 2, 3}, {1, 2, 3}};
      for (int[] corners : tries) {
         double[][] rows = new double[3][];
         double[] values = new double[3];
         for (int i = 0; i < 3; i++) {
            int vertex = corners[i];
            double[] plane = inPlane(this.x(vertex), this.y(vertex), this.z(vertex), face);
            rows[i] = new double[]{1.0, plane[0], plane[1]};
            values[i] = u ? this.u(vertex) : this.v(vertex);
         }
         double det = determinant(rows);
         if (Math.abs(det) >= 1.0E-9) {
            float[] fit = new float[3];
            for (int column = 0; column < 3; column++) {
               double[][] swapped = new double[3][];
               for (int i = 0; i < 3; i++) {
                  swapped[i] = rows[i].clone();
                  swapped[i][column] = values[i];
               }
               fit[column] = (float)(determinant(swapped) / det);
            }
            return fit;
         }
      }
      return null;
   }

   public void repaint(float[] uFit, float[] vFit, float[][] paintAt) {
      if (uFit == null || vFit == null) {
         return;
      }
      var sprite = this.source.getSprite();
      for (int vertex = 0; vertex < 4; vertex++) {
         float[] at = paintAt[vertex];
         double[] plane = inPlane(at[0], at[1], at[2], this.direction());
         float u = (float)(uFit[0] + uFit[1] * plane[0] + uFit[2] * plane[1]);
         float v = (float)(vFit[0] + vFit[1] * plane[0] + vFit[2] * plane[1]);
         u = Math.max(sprite.getU0(), Math.min(sprite.getU1(), u));
         v = Math.max(sprite.getV0(), Math.min(sprite.getV1(), v));
         this.uv(vertex, u, v);
      }
   }

   private static double[] inPlane(float x, float y, float z, Direction face) {
      return switch (face.getAxis()) {
         case X -> new double[]{y, z};
         case Y -> new double[]{x, z};
         case Z -> new double[]{x, y};
      };
   }

   private static double determinant(double[][] m) {
      return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
         - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
         + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
   }
}
