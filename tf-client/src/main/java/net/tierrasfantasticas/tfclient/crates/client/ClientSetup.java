package net.tierrasfantasticas.tfclient.crates.client;

import net.tierrasfantasticas.tfclient.crates.client.media.DefaultMedia;
import net.tierrasfantasticas.tfclient.crates.config.MediaGuide;

public final class ClientSetup {
   private ClientSetup() {
   }

   public static void init() {
      DefaultMedia.warmUp();
      MediaGuide.write();
   }
}
