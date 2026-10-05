package net.tierrasfantasticas.tfclient.mixin;

import net.minecraftforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sin las líneas de versión de Minecraft/Forge ("Minecraft 1.20.1", "Forge 47.x", "N mods loaded") en el menú. */
@Mixin(value = BrandingControl.class, remap = false)
public abstract class BrandingControlMixin {
    @Inject(method = "forEachLine", at = @At("HEAD"), cancellable = true)
    private static void tfclient$hideBranding(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "forEachAboveCopyrightLine", at = @At("HEAD"), cancellable = true)
    private static void tfclient$hideAboveCopyright(CallbackInfo ci) {
        ci.cancel();
    }
}
