/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.crate;

import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.RewardEntry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "tfclient"
)
public final class DelayedDelivery {
   private static final List<DelayedDelivery.Task> TASKS = new ArrayList<>();

   private DelayedDelivery() {
   }

   public static void schedule(ServerPlayer player, CrateConfig crate, List<RewardEntry> rewards, int delayTicks) {
      ServerLevel level = player.serverLevel();
      long due = level.getGameTime() + (long)Math.max(1, delayTicks);
      TASKS.add(new DelayedDelivery.Task(player.getUUID(), level, due, crate, rewards));
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent event) {
      if (event.phase == Phase.END && !TASKS.isEmpty()) {
         Iterator<DelayedDelivery.Task> it = TASKS.iterator();

         while (it.hasNext()) {
            DelayedDelivery.Task t = it.next();
            if (t.level.getGameTime() >= t.dueTick) {
               it.remove();
               ServerPlayer player = t.level.getServer() == null ? null : t.level.getServer().getPlayerList().getPlayer(t.player);
               if (player != null) {
                  LootEngine.deliver(player, t.crate, t.rewards);
               }
            }
         }
      }
   }

   static record Task(UUID player, ServerLevel level, long dueTick, CrateConfig crate, List<RewardEntry> rewards) {
   }
}
