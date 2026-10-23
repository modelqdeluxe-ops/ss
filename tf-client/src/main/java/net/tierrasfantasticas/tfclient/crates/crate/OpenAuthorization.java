/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.crate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class OpenAuthorization {
   private static final long TTL_MS = 300000L;
   private static final Map<UUID, OpenAuthorization.Entry> GRANTED = new ConcurrentHashMap<>();

   private OpenAuthorization() {
   }

   public static void grant(ServerPlayer player, BlockPos pos) {
      if (player != null && pos != null) {
         GRANTED.put(player.getUUID(), new OpenAuthorization.Entry(pos.immutable(), System.currentTimeMillis() + 300000L));
      }
   }

   public static boolean consume(ServerPlayer player, BlockPos pos) {
      if (player != null && pos != null) {
         OpenAuthorization.Entry entry = GRANTED.remove(player.getUUID());
         if (entry == null) {
            return false;
         } else {
            return System.currentTimeMillis() > entry.expiresAt() ? false : entry.pos().equals(pos);
         }
      } else {
         return false;
      }
   }

   public static void forget(ServerPlayer player) {
      if (player != null) {
         GRANTED.remove(player.getUUID());
      }
   }

   private static record Entry(BlockPos pos, long expiresAt) {
   }
}
