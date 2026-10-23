/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.animation;

public record CrateAnimation(String id, String displayName, CrateAnimation.Style style, CrateAnimation.Theme theme, int durationTicks, String description) {
   public boolean hasBeam() {
      return this.style != CrateAnimation.Style.INSTANT
         && (
            this.theme == CrateAnimation.Theme.CELESTIAL
               || this.theme == CrateAnimation.Theme.MAGIC
               || this.theme == CrateAnimation.Theme.NEON
               || this.theme == CrateAnimation.Theme.ANCIENT
               || this.theme == CrateAnimation.Theme.INFERNAL
         );
   }

   public static enum Style {
      ROULETTE,
      SLOT_MACHINE,
      INSTANT;
   }

   public static enum Theme {
      CLASSIC,
      CASINO,
      NEON,
      INFERNAL,
      CELESTIAL,
      MAGIC,
      NATURE,
      ANCIENT;
   }
}
