/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.compat;

import net.minecraft.world.entity.player.Player;
import net.tierrasfantasticas.tfclient.items.TFWings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.167, el dueño: «recargar las alas me quita pollitos, te dije que no debía quitarme hambre») El mod Better Flight
 * (betterflight 2.2.0, del servidor y del modpack) da a todo lo que planea una barra de energía para aletear, y cada
 * punto que recarga lo cobra en hambre: el cliente le avisa al servidor y este llama a
 * FlightHandler.handleFlightStaminaExhaustion → Player.causeFoodExhaustion(ExhaustionPerChargePoint). Casi siempre
 * recarga en el suelo, sin planear, así que PlayerFoodWingsMixin (que solo quita el hambre mientras se planea) no lo
 * paraba. Con unas alas de los sets puestas, esa recarga no gasta hambre; con élitros normales sigue como en Better
 * Flight. @Pseudo y require = 0: sin Better Flight no pasa nada.
 */
@Pseudo
@Mixin(targets = "com.rejahtavi.betterflight.util.FlightHandler", remap = false)
public abstract class BetterFlightWingsMixin {
    @Inject(method = "handleFlightStaminaExhaustion", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void tfclient$noHungerForSetWings(Player player, CallbackInfo ci) {
        try {
            if (player != null && TFWings.wearsWings(player)) ci.cancel();
        } catch (Throwable t) {
            // nunca romper Better Flight por esto
        }
    }
}
