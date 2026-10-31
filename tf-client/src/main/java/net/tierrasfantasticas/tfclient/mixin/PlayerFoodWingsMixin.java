/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.world.entity.player.Player;
import net.tierrasfantasticas.tfclient.items.TFWings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.155, el dueño: «el vuelo de los sets, quita que me quite hambre volar») Mientras se planea con unas alas de los
 * sets no se gasta hambre: Minecraft no la gasta al planear, pero otros mods del servidor sí (todo pasa por
 * Player.causeFoodExhaustion). Con élitros normales, o andando, todo sigue igual.
 */
@Mixin(Player.class)
public abstract class PlayerFoodWingsMixin {
    @Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$noHungerWhileGliding(float exhaustion, CallbackInfo ci) {
        try {
            if (exhaustion > 0 && TFWings.glidingWithWings((Player) (Object) this)) ci.cancel();
        } catch (Throwable t) {
            // nunca romper el hambre por esto
        }
    }
}
