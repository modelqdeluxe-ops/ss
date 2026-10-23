/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.tierrasfantasticas.tfclient.bosses.BossEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * La vida máxima de los jefes sin el tope de 1024 del atributo de Minecraft (en Demencial pasan de 6.000):
 * getMaxHealth es final en LivingEntity, así que se responde aquí con la de {@link BossEntity} (sincronizada). Así
 * setHealth, heal, la barra y los porcentajes de vida de sus skills van con la vida de verdad.
 */
@Mixin(LivingEntity.class)
public abstract class BossHealthMixin {
    @Inject(method = "getMaxHealth()F", at = @At("HEAD"), cancellable = true)
    private void tfclient$bossMaxHealth(CallbackInfoReturnable<Float> cir) {
        float max = BossEntity.bossMaxHealth((LivingEntity) (Object) this);
        if (max > 0F) cir.setReturnValue(max);
    }
}
