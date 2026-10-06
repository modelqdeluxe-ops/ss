package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Para quitar el rótulo «Inventario» de los menús con fondo propio (lo taparía el marco). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("inventoryLabelY")
    void tfclient$setInventoryLabelY(int y);
}
