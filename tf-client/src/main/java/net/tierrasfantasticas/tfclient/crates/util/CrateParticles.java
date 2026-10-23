/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.util;

import net.minecraft.world.level.Level;

/**
 * (1.3.109, el dueño: «dale rework a los efectos VFX de la crate, me parecen feos») las partículas propias de las crates:
 * luces suaves que brillan y destellos de cuatro puntas, del color que toque, sumando luz (como los efectos de la
 * escena). Las pinta el cliente (client/fx/CrateFxParticle, que se engancha aquí al arrancar); en el servidor no hay
 * nada que pintar y no hacen nada. Sustituyen al polvo de redstone, las letras de encantamiento, las varas del End, los
 * fuegos artificiales y las explosiones de antes.
 */
public final class CrateParticles {
   /** Luz suave (un orbe que flota y se apaga), destello (estrella de cuatro puntas que parpadea) y fogonazo. */
   public static final int GLOW = 0, SPARK = 1, FLASH = 2;

   public interface Sink {
      void add(Level level, int kind, double x, double y, double z, double vx, double vy, double vz, int rgb, float size);
   }

   public static volatile Sink sink;

   private CrateParticles() {
   }

   public static void add(Level level, int kind, double x, double y, double z, double vx, double vy, double vz, int rgb, float size) {
      Sink s = sink;
      if (s != null && level != null && level.isClientSide) s.add(level, kind, x, y, z, vx, vy, vz, rgb, size);
   }
}
