/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client;

public final class CinematicDiag {
   public static volatile long lastCullNanos = 0L;
   public static volatile long cullFrames = 0L;

   private CinematicDiag() {
   }

   public static void markCull() {
      lastCullNanos = System.nanoTime();
      cullFrames++;
   }
}
