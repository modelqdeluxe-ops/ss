/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin;

import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.server.BotCommands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.112) /tpa y /msg a un bot (server/BotCommands): se contestan antes de que el comando llegue a EssentialsX o a
 * Minecraft (que dirían «jugador no encontrado» o «no se ha encontrado ninguna entidad»). En performChatCommand: ya en el
 * hilo del servidor y con la firma del chat validada (cortarlo antes rompería la cadena de mensajes firmados del jugador).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BotCommandsMixin {
    @Inject(method = "performChatCommand", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$botCommands(ServerboundChatCommandPacket packet, LastSeenMessages lastSeen, CallbackInfo ci) {
        try {
            if (BotCommands.handle(((ServerGamePacketListenerImpl) (Object) this).getPlayer(), packet.command())) ci.cancel();
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Bots: fallo con el comando «{}»", packet.command(), t);
        }
    }
}
