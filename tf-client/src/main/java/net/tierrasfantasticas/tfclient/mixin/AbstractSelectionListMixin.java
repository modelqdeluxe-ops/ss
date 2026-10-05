package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fuera de una partida, las listas (mundos, servidores, paquetes...) dejan ver el paisaje de TF en vez de la tierra. */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void tfclient$transparentInMenus(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) return;
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);
    }
}
