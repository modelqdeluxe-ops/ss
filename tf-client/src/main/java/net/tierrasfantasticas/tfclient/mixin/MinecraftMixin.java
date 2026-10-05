package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.client.TFLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** Toda pantalla de carga (la de Mojang o la de Forge) se muestra con el diseño de TF. */
    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), argsOnly = true)
    private Overlay tfclient$wrapLoadingOverlay(Overlay overlay) {
        if (overlay == null && ((Minecraft) (Object) this).getOverlay() instanceof TFLoadingOverlay current
                && current.holdAfterDelegate()) {
            return current; // la pantalla de TF se queda hasta terminar su fundido
        }
        if (overlay instanceof LoadingOverlay loading && !(overlay instanceof TFLoadingOverlay)) {
            try {
                return new TFLoadingOverlay(loading);
            } catch (Throwable t) {
                TFClient.LOGGER.error("TF Client: no se pudo crear la pantalla de carga, se usa la normal", t);
            }
        }
        return overlay;
    }

    /** Título de la ventana del juego. */
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true, require = 0)
    private void tfclient$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Tierras Fantásticas · TF Client");
    }
}
