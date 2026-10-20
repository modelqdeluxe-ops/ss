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

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END && CrateMedia.isActive()) {
         Screen screen = Minecraft.getInstance().screen;
         if (!(screen instanceof CratePreOpenScreen) && !(screen instanceof CratePoolScreen)) {
            CrateMedia.stop();
         }
      }
   }
}
