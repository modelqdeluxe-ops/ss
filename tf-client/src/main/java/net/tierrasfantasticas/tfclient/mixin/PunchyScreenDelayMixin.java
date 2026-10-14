package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.tierrasfantasticas.tfclient.anim.PunchyBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Punchy apagado = Minecraft normal (1.3.71): el inventario y los cofres se abren al momento, sin esperar su animación. */
@Pseudo
@Mixin(targets = "punchy.client.state.InterfaceDelayState", remap = false)
public abstract class PunchyScreenDelayMixin {
    @Inject(method = "shouldDelay(Lnet/minecraft/client/gui/screens/Screen;)Z", at = @At("HEAD"), cancellable = true, remap = false,
            require = 0)
    private static void tfclient$sinEspera(Screen screen, CallbackInfoReturnable<Boolean> cir) {
        if (PunchyBridge.vanilla()) cir.setReturnValue(false);
    }
}
