/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.mixin.items;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFCreativeGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (1.3.168) El paquete del modo creativo «pon este objeto en este hueco» ({@link TFCreativeGuard}): lo nuestro no entra
 * ni se mueve por ahí. Se mira en el hilo del servidor: el paquete llega primero por el hilo de red, Minecraft lo vuelve a
 * mandar al del servidor y es entonces (con el inventario de verdad) cuando se decide. Al principio del método, antes que
 * el código de Mohist/CraftBukkit (InventoryCreativeEvent), para que ningún plugin lo deje pasar.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSlotGuardMixin {
    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void tfclient$creativeSlot(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        try {
            ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).getPlayer();
            if (player == null || player.getServer() == null || !player.getServer().isSameThread()) return;
            if (TFCreativeGuard.blocksCreativeSlot(player, packet.getSlotNum(), packet.getItem())) ci.cancel();
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Creativo: fallo mirando un paquete de creativo", t);
        }
    }
}
