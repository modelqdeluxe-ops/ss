/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class RestrictionEvents {
    private static boolean down(Player player) {
        ServerPlayer sp;
        return player instanceof ServerPlayer && DownManager.isDown(sp = (ServerPlayer)player);
    }

    @SubscribeEvent
    public void onBreak(BreakEvent e) {
        if (down(e.getPlayer())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(LeftClickBlock e) {
        if (down(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(RightClickBlock e) {
        if (down(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickItem(RightClickItem e) {
        if (down(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent e) {
        if (down(e.getEntity())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteract e) {
        if (down(e.getEntity())) {
            e.setCanceled(true);
        }
    }
}
