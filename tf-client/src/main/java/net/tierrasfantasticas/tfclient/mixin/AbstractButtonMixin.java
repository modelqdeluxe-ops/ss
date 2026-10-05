package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.tierrasfantasticas.tfclient.client.TFButtonTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Botones normales y de alternar opciones con el tema de TF fuera de una partida. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void tfclient$themeButton(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (TFButtonTheme.renderButton((AbstractButton) (Object) this, g)) ci.cancel();
    }
}
