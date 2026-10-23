/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.config;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;

public class ParticleLayer {
   public String particleId = "minecraft:enchant";
   public ParticleLayer.Phase phase = ParticleLayer.Phase.IDLE;
   public ParticleLayer.Shape shape = ParticleLayer.Shape.HALO;
   public int count = 4;
   public double speed = 0.04;
   public double spread = 0.4;
   public double radius = 0.85;
   public double yOffset = 1.1;
   public boolean useRarityColor = true;
   public String colorHex = "#FFFFFF";
   public int interval = 4;

   public ParticleLayer() {
      this.applyShapeDefaults();
   }

   public ParticleLayer(String id, ParticleLayer.Phase phase, ParticleLayer.Shape shape) {
      this.particleId = id;
      this.phase = phase;
      this.shape = shape;
      this.applyShapeDefaults();
   }

   public void applyShapeDefaults() {
      switch (this.shape) {
         case HALO:
            this.radius = 0.85;
            this.yOffset = 1.1;
            this.spread = 0.2;
            this.count = 6;
            this.speed = 0.02;
            break;
         case RING:
            this.radius = 0.95;
            this.yOffset = 0.45;
            this.spread = 0.05;
            this.count = 24;
            this.speed = 0.02;
            break;
         case BURST:
            this.radius = 0.1;
            this.yOffset = 0.85;
            this.spread = 0.55;
            this.count = 14;
            this.speed = 0.3;
            break;
         case COLUMN:
            this.radius = 0.1;
            this.yOffset = 0.85;
            this.spread = 0.2;
            this.count = 6;
            this.speed = 0.1;
            break;
         case SPIRAL:
            this.radius = 0.85;
            this.yOffset = 0.2;
            this.spread = 0.1;
            this.count = 16;
            this.speed = 0.05;
            break;
         case FOUNTAIN:
            this.radius = 0.05;
            this.yOffset = 0.85;
            this.spread = 0.45;
            this.count = 10;
            this.speed = 0.3;
            break;
         case VORTEX:
            this.radius = 0.85;
            this.yOffset = 0.95;
            this.spread = 0.05;
            this.count = 16;
            this.speed = 0.05;
            break;
         case RAIN:
            this.radius = 0.0;
            this.yOffset = 1.8;
            this.spread = 1.1;
            this.count = 6;
            this.speed = 0.3;
            break;
         case POINT:
            this.radius = 0.0;
            this.yOffset = 0.9;
            this.spread = 0.05;
            this.count = 1;
            this.speed = 0.04;
      }
   }

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("p", this.particleId);
      t.putString("phase", this.phase.name());
      t.putString("shape", this.shape.name());
      t.putInt("count", this.count);
      t.putDouble("speed", this.speed);
      t.putDouble("spread", this.spread);
      t.putDouble("radius", this.radius);
      t.putDouble("y", this.yOffset);
      t.putBoolean("tier", this.useRarityColor);
      t.putString("hex", this.colorHex);
      t.putInt("interval", this.interval);
      return t;
   }

   public static ParticleLayer load(CompoundTag t) {
      ParticleLayer l = new ParticleLayer();
      l.particleId = t.contains("p") ? t.getString("p") : "minecraft:enchant";
      l.phase = enumOr(ParticleLayer.Phase.class, t.getString("phase"), ParticleLayer.Phase.IDLE);
      l.shape = enumOr(ParticleLayer.Shape.class, t.getString("shape"), ParticleLayer.Shape.HALO);
      l.count = t.contains("count") ? t.getInt("count") : 4;
      l.speed = t.contains("speed") ? t.getDouble("speed") : 0.04;
      l.spread = t.contains("spread") ? t.getDouble("spread") : 0.4;
      l.radius = t.contains("radius") ? t.getDouble("radius") : 0.85;
      l.yOffset = t.contains("y") ? t.getDouble("y") : 1.1;
      l.useRarityColor = !t.contains("tier") || t.getBoolean("tier");
      l.colorHex = t.contains("hex") ? t.getString("hex") : "#FFFFFF";
      l.interval = t.contains("interval") ? Math.max(1, t.getInt("interval")) : 4;
      return l;
   }

   public ParticleLayer copy() {
      return load(this.save());
   }

   public String shortLabel() {
      String pid = this.particleId.contains(":") ? this.particleId.substring(this.particleId.indexOf(58) + 1) : this.particleId;
      return ParticleNames.spanish(pid) + " §8" + this.phase.label.charAt(0) + "/" + this.shape.label.charAt(0);
   }

   private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E def) {
      try {
         return Enum.valueOf(type, name);
      } catch (Exception var4) {
         return def;
      }
   }

   /**
    * Las capas de serie. (1.3.109, el dueño: «dale rework a los efectos VFX de la crate, me parecen feos») con las
    * partículas propias de TF (CrateParticles: luces suaves y destellos que suman luz, del color de la rareza) en vez del
    * polvo de redstone, las letras de encantamiento, las varas del End y los fuegos artificiales de antes.
    */
   public static List<ParticleLayer> defaults() {
      ArrayList<ParticleLayer> list = new ArrayList<>();
      // en reposo: unas luces que flotan alrededor y algún destello más bajo
      ParticleLayer halo = new ParticleLayer("tfclient:crate_glow", ParticleLayer.Phase.IDLE, ParticleLayer.Shape.HALO);
      halo.count = 2;
      halo.interval = 5;
      halo.speed = 0.01;
      list.add(halo);
      ParticleLayer ring = new ParticleLayer("tfclient:crate_spark", ParticleLayer.Phase.IDLE, ParticleLayer.Shape.RING);
      ring.count = 4;
      ring.interval = 9;
      ring.speed = 0.008;
      ring.radius = 0.9;
      ring.yOffset = 0.55;
      list.add(ring);
      // tensión: una espiral de luces con destellos
      ParticleLayer spiral = new ParticleLayer("tfclient:crate_glow", ParticleLayer.Phase.ANTICIPATION, ParticleLayer.Shape.SPIRAL);
      spiral.count = 10;
      spiral.radius = 0.85;
      spiral.yOffset = 0.2;
      spiral.speed = 0.04;
      list.add(spiral);
      ParticleLayer spiralSpark = new ParticleLayer("tfclient:crate_spark", ParticleLayer.Phase.ANTICIPATION, ParticleLayer.Shape.SPIRAL);
      spiralSpark.count = 6;
      spiralSpark.radius = 0.7;
      spiralSpark.yOffset = 0.2;
      spiralSpark.speed = 0.05;
      list.add(spiralSpark);
      // apertura: una fuente de destellos y un golpe de luz
      ParticleLayer open = new ParticleLayer("tfclient:crate_spark", ParticleLayer.Phase.OPEN, ParticleLayer.Shape.FOUNTAIN);
      open.count = 14;
      open.speed = 0.28;
      open.spread = 0.3;
      list.add(open);
      ParticleLayer openGlow = new ParticleLayer("tfclient:crate_glow", ParticleLayer.Phase.OPEN, ParticleLayer.Shape.BURST);
      openGlow.count = 8;
      openGlow.speed = 0.1;
      openGlow.spread = 0.2;
      list.add(openGlow);
      // el premio: un remolino de luces
      ParticleLayer reveal = new ParticleLayer("tfclient:crate_glow", ParticleLayer.Phase.REVEAL, ParticleLayer.Shape.VORTEX);
      reveal.count = 12;
      reveal.radius = 0.9;
      reveal.yOffset = 1.15;
      list.add(reveal);
      // el final: un estallido de destellos y luces
      ParticleLayer finale = new ParticleLayer("tfclient:crate_spark", ParticleLayer.Phase.FINALE, ParticleLayer.Shape.BURST);
      finale.count = 22;
      finale.speed = 0.35;
      finale.spread = 0.5;
      finale.yOffset = 1.0;
      list.add(finale);
      ParticleLayer finaleGlow = new ParticleLayer("tfclient:crate_glow", ParticleLayer.Phase.FINALE, ParticleLayer.Shape.BURST);
      finaleGlow.count = 10;
      finaleGlow.speed = 0.18;
      finaleGlow.spread = 0.3;
      finaleGlow.yOffset = 1.0;
      list.add(finaleGlow);
      return list;
   }

   /** Las capas de serie de antes de la 1.3.109 (polvo, encantamiento, varas del End, fuegos): partícula, fase y forma. */
   private static final String[][] LEGACY = {
      {"minecraft:enchant", "IDLE", "HALO"}, {"minecraft:dust", "IDLE", "RING"}, {"minecraft:dust", "ANTICIPATION", "SPIRAL"},
      {"minecraft:end_rod", "ANTICIPATION", "SPIRAL"}, {"minecraft:end_rod", "OPEN", "FOUNTAIN"}, {"minecraft:dust", "REVEAL", "VORTEX"},
      {"minecraft:firework", "FINALE", "BURST"}};

   /**
    * ¿Son las capas de serie de antes, sin tocar (las de las crates ya puestas)? Esas se cambian solas por las nuevas al
    * cargar (CrateConfig.load); las que el dueño cambió en el editor se quedan como estén.
    */
   public static boolean legacyDefaults(List<ParticleLayer> layers) {
      if (layers.size() != LEGACY.length) return false;
      for (int i = 0; i < LEGACY.length; i++) {
         ParticleLayer l = layers.get(i);
         if (!LEGACY[i][0].equals(l.particleId) || !LEGACY[i][1].equals(l.phase.name()) || !LEGACY[i][2].equals(l.shape.name())) return false;
      }
      return true;
   }

   public static enum Phase {
      IDLE("Reposo"),
      ANTICIPATION("Tensión"),
      OPEN("Apertura"),
      REVEAL("Revelación"),
      FINALE("Final");

      public final String label;

      private Phase(String l) {
         this.label = l;
      }

      public ParticleLayer.Phase next() {
         ParticleLayer.Phase[] v = values();
         return v[(this.ordinal() + 1) % v.length];
      }
   }

   public static enum Shape {
      HALO("Halo"),
      RING("Anillo"),
      BURST("Estallido"),
      COLUMN("Columna"),
      SPIRAL("Espiral"),
      FOUNTAIN("Fuente"),
      VORTEX("Vórtice"),
      RAIN("Lluvia"),
      POINT("Punto");

      public final String label;

      private Shape(String l) {
         this.label = l;
      }

      public ParticleLayer.Shape next() {
         ParticleLayer.Shape[] v = values();
         return v[(this.ordinal() + 1) % v.length];
      }
   }
}
