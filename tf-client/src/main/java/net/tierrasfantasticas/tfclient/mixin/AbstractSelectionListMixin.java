package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.tierrasfantasticas.tfclient.client.TFClientEvents;
import net.tierrasfantasticas.tfclient.client.TFDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fuera de una partida, las listas (mundos, servidores, paquetes...) se dibujan sobre el paisaje de TF: un panel azul
 * noche translúcido con filetes dorados arriba y abajo, en vez de la tierra de Minecraft.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
    @Shadow
    protected int x0;
    @Shadow
    protected int x1;
    @Shadow
    protected int y0;
    @Shadow
    protected int y1;

    @Inject(method = "render", at = @At("HEAD"))
    private void tfclient$panelInMenus(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().level != null) return;
        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        list.setRenderBackground(false);
        list.setRenderTopAndBottom(false);

        // Pantallas como Seleccionar mundo no dibujan fondo: sin esto se veía la pantalla anterior detrás.
        TFClientEvents.ensureMenuBackground(g);
        if (y1 - y0 < 8) return;
        g.fill(x0, y0, x1, y1, TFDraw.argb(0.62f, 0x070B18));
        g.fillGradient(x0, y0, x1, y0 + 6, TFDraw.argb(0.55f, 0x000000), 0);
        g.fillGradient(x0, y1 - 6, x1, y1, 0, TFDraw.argb(0.55f, 0x000000));
        g.fill(x0, y0 - 1, x1, y0, TFDraw.argb(0.85f, 0xC9A24A));
        g.fill(x0, y1, x1, y1 + 1, TFDraw.argb(0.85f, 0xC9A24A));
    }
}
