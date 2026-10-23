/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.tierrasfantasticas.tfclient.client.TFAutoModpack;
import net.tierrasfantasticas.tfclient.client.TFLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.117) Toda conexión a un servidor pasa por aquí (el menú, la lista de servidores, la conexión directa, LAN, juego
 * rápido): si no es el oficial, va al oficial ({@link TFLock}). (1.3.121) Y sin AutoModpack no se entra: se pide
 * primero ({@link TFAutoModpack}). Tiene que aplicarse sí o sí (require = 1).
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true, require = 1)
    private static void tfclient$onlyOfficial(Screen parent, Minecraft mc, ServerAddress address, ServerData data, boolean quickPlay,
                                              CallbackInfo ci) {
        if (!TFAutoModpack.present()) {
            ci.cancel();
            mc.setScreen(new TFAutoModpack.Missing(parent));
            return;
        }
        if (TFLock.official(address)) return;
        ci.cancel();
        TFLock.redirect(parent, mc, address, quickPlay);
    }
}
