/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ConnectionEvents {
    @SubscribeEvent
    public void onLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player2) {
            if (DownManager.isDown(player2)) {
                DownState st = DownManager.get(player2);
                if (st != null) {
                    st.downDimension = player2.serverLevel().dimension();
                    st.downPosition = player2.position();
                }

                DownManager.reattach(player2);
                player2.displayClientMessage(Component.literal("Sigues desangrándote. Otros jugadores pueden revivirte.").withStyle(ChatFormatting.RED), false);
            } else {
                DownManager.clearDownEffects(player2);
                DownManager.clearProne(player2);
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player2) {
            DownState st = DownManager.get(player2);
            if (st != null) {
                st.bossBar.removePlayer(player2);
            }
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player2) {
            DownState st = DownManager.get(player2);
            if (st != null) {
                st.downDimension = player2.serverLevel().dimension();
                st.downPosition = player2.position();
                st.bossBar.removePlayer(player2);
                st.bossBar.addPlayer(player2);
                DownManager.applyDownEffects(player2);
            }
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player2) {
            if (event.isEndConquered()) {
                if (DownManager.isDown(player2)) {
                    DownManager.reattach(player2);
                }
            } else {
                DownManager.removeWithoutRevival(player2.getUUID());
                DownManager.clearDownEffects(player2);
                DownManager.clearProne(player2);
            }
        }
    }

    @SubscribeEvent
    public void onClone(Clone event) {
        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            if (event.getOriginal() instanceof ServerPlayer oldPlayer) {
                if (DownManager.isDown(oldPlayer)) {
                    DownManager.applyDownEffects(newPlayer);
                }
            }
        }
    }
}
