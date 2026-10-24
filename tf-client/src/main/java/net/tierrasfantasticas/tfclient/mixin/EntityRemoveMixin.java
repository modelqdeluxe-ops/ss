/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.entity.Entity;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFWebDrops;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (1.3.134) Las compras de la web tiradas en el suelo no las borra un clear lag (ver TFWebDrops.keep). */
@Mixin(Entity.class)
public abstract class EntityRemoveMixin {
    @Inject(method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$keepWebDrop(Entity.RemovalReason reason, CallbackInfo ci) {
        try {
            if (TFWebDrops.keep((Entity) (Object) this, reason)) ci.cancel();
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Web: fallo mirando si se quita un objeto del suelo", t);
        }
    }
}
