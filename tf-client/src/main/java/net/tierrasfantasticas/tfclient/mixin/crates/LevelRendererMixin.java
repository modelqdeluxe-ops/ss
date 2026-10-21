package net.tierrasfantasticas.tfclient.mixin.crates;

import net.tierrasfantasticas.tfclient.crates.client.CinematicDiag;
import net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicClassic;
import net.tierrasfantasticas.tfclient.crates.client.screen.CrateCinematicScreen;
import net.tierrasfantasticas.tfclient.crates.client.screen.CratePreOpenScreen;
import net.tierrasfantasticas.tfclient.vacio.ShaderPacks;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public class LevelRendererMixin {
   private static final Logger FSCRATES_LOGGER = LogUtils.getLogger();
   private static boolean fscrates$loggedCull = false;
   private static boolean fscrates$loggedShaders = false;

   @Inject(
      method = {"renderLevel"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void fscrates$skipWorldDuringCinematic(CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof CrateCinematicScreen || mc.screen instanceof CrateCinematicClassic
         || CratePreOpenScreen.sanctuaryShown(mc.screen)) {
         // (1.3.106) con paquete de shaders, el mundo se dibuja (detrás de la cinemática no se ve): Oculus empieza su
         // pasada al principio de renderLevel y la acaba al final; si se corta aquí, se queda «dibujando el mundo» y
         // cambia los shaders de Minecraft por los suyos también en la escena de la crate. Así le pasaba al dueño
         // (Photon): ningún efecto (halos, hadas, luciérnagas, pétalos, anillos) ni partícula; lo reprodujo la prueba
         // con Photon (1.3.103). Sin paquete, como siempre: no se dibuja.
         if (ShaderPacks.inUse()) {
            if (!fscrates$loggedShaders) {
               fscrates$loggedShaders = true;
               FSCRATES_LOGGER.info("[TF Crates] Cinemática con paquete de shaders: el mundo se sigue dibujando detrás (para Oculus)");
            }
            return;
         }
         CinematicDiag.markCull();
         ci.cancel();
      }
   }
}
