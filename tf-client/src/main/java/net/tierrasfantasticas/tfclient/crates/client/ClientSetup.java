/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client;

import net.tierrasfantasticas.tfclient.crates.client.media.DefaultMedia;
import net.tierrasfantasticas.tfclient.crates.config.MediaGuide;

public final class ClientSetup {
   private ClientSetup() {
   }

   public static void init() {
      // (1.3.109) las partículas propias de las crates (luces y destellos: CrateParticles)
      net.tierrasfantasticas.tfclient.crates.client.fx.CrateFxParticle.install();
      DefaultMedia.warmUp();
      MediaGuide.write();
   }
}
