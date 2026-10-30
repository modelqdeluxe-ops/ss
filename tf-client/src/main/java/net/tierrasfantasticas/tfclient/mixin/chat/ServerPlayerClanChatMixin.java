/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.chat;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.pad.server.PadClanChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.154) Quien tiene puesto el chat del clan (/clan chat enable) no recibe el chat público de los demás: cada mensaje
 * de chat de un jugador que Minecraft le iba a mandar (PlayerList.broadcastChatMessage) se para aquí. Los avisos del
 * sistema y los /msg siguen llegando (PadClanChat.blockPublic). En Mohist con un plugin de chat, lo mismo lo hace el
 * oyente de Bukkit (TFBukkit.registerChatFilter).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerClanChatMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$hidePublicChat(OutgoingChatMessage message, boolean filtered, ChatType.Bound bound, CallbackInfo ci) {
        try {
            if (PadClanChat.blockPublic((ServerPlayer) (Object) this, message, bound)) ci.cancel();
        } catch (Throwable t) {
            // nunca romper el chat por esto
        }
    }
}
