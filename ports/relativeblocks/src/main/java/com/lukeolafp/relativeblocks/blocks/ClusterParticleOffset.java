package com.lukeolafp.relativeblocks.blocks;

public final class ClusterParticleOffset {
   private static double offsetX;
   private static double offsetZ;
   private static boolean active;

   private ClusterParticleOffset() {
   }

   public static void begin(double x, double z) {
      offsetX = x;
      offsetZ = z;
      active = true;
   }

   public static void end() {
      active = false;
      offsetX = 0.0;
      offsetZ = 0.0;
   }

   public static boolean isActive() {
      return active;
   }

   public static double x() {
      return offsetX;
   }

   public static double z() {
      return offsetZ;
   }
}
