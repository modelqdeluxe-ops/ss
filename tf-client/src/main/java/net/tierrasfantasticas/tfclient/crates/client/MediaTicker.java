/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.crates.client;

import net.tierrasfantasticas.tfclient.crates.client.media.CrateMedia;
import net.tierrasfantasticas.tfclient.crates.client.screen.CratePoolScreen;
import net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "tfclient",
   value = {Dist.CLIENT},
   bus = Bus.FORGE
)
public final class MediaTicker {
   private MediaTicker() {
   }

   private static int inWorld;
   private static boolean warmed;

   /**
    * (1.3.100) El santuario de las crates se construye a los 5 s de entrar al mundo (y no al tocar la primera caja: la
    * primera vez tardaba ~2,5 s y el juego se quedaba congelado al abrir la pantalla de la caja).
    */
   private static void prewarm() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         inWorld = 0;
         return;
      }

      if (warmed || !net.tierrasfantasticas.tfclient.crates.FSCrates.enabled() || ++inWorld < 100) {
         return;
      }

      warmed = true;

      try {
         net.tierrasfantasticas.tfclient.crates.client.scene.SanctuaryScene sc = net.tierrasfantasticas.tfclient.crates.client.scene.SanctuaryScene.get();
         if (sc != null) {
            sc.ensureBuilt();
         }
      } catch (Throwable t) {
         net.tierrasfantasticas.tfclient.crates.FSCrates.LOGGER.warn("[TF Crates] No se pudo preparar el santuario al entrar", t);
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         prewarm();
         Screen screen = Minecraft.getInstance().screen;
         if (!(screen instanceof CratePreOpenScreen) && !(screen instanceof CratePoolScreen)) {
            if (CrateMedia.isActive()) {
               CrateMedia.stop();
            }

            // (el santuario de detrás y su ambiente, si se salió por otro camino)
            CratePreOpenScreen.stopIdleSanctuary();
         }
      }
   }
}
