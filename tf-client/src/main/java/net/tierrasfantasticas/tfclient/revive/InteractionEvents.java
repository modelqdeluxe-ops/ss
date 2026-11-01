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
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class InteractionEvents {
    private static final int CLICK_WINDOW = 12;

    @SubscribeEvent
    public void onEntityInteract(EntityInteract event) {
        if (!event.getLevel().isClientSide && event.getHand() == InteractionHand.MAIN_HAND) {
            if (event.getEntity() instanceof ServerPlayer reviver) {
                Entity entity = event.getTarget();
                ServerPlayer downed;
                if (entity instanceof ServerPlayer && DownManager.isDown(downed = (ServerPlayer)entity) && canRevive(reviver)) {
                    tryGrant(reviver, downed);
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(RightClickBlock event) {
        if (!event.getLevel().isClientSide && event.getHand() == InteractionHand.MAIN_HAND) {
            Player player = event.getEntity();
            ServerPlayer reviver;
            if (player instanceof ServerPlayer && canRevive(reviver = (ServerPlayer)player) && grantNearestTarget(reviver)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(RightClickItem event) {
        if (!event.getLevel().isClientSide && event.getHand() == InteractionHand.MAIN_HAND) {
            Player player = event.getEntity();
            ServerPlayer reviver;
            if (player instanceof ServerPlayer && canRevive(reviver = (ServerPlayer)player) && grantNearestTarget(reviver)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    private static boolean canRevive(ServerPlayer reviver) {
        return !reviver.isSpectator() && !DownManager.isDown(reviver);
    }

    private static boolean tryGrant(ServerPlayer reviver, ServerPlayer downed) {
        ReviveConfig cfg = TFRevive.getConfig();
        double maxSq = cfg.reviveDistance * cfg.reviveDistance;
        if (reviver.distanceToSqr(downed) > maxSq) {
            reviver.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("Demasiado lejos").withStyle(ChatFormatting.RED)));
            return false;
        } else {
            DownState st = DownManager.get(downed);
            if (st == null) {
                return false;
            } else {
                st.reviverWindow.put(reviver.getUUID(), 12);
                return true;
            }
        }
    }

    private static boolean grantNearestTarget(ServerPlayer reviver) {
        ReviveConfig cfg = TFRevive.getConfig();
        double maxSq = cfg.reviveDistance * cfg.reviveDistance;
        ServerPlayer best = null;
        double bestDot = 0.5;
        Vec3 eye = reviver.getEyePosition();
        Vec3 look = reviver.getViewVector(1.0F);

        for (ServerPlayer p : reviver.serverLevel().players()) {
            if (DownManager.isDown(p) && !p.getUUID().equals(reviver.getUUID()) && !(reviver.distanceToSqr(p) > maxSq)) {
                Vec3 aim = p.position().add(0.0, (double)p.getBbHeight() * 0.5, 0.0).subtract(eye);
                double dist = aim.length();
                if (dist < 0.001) {
                    best = p;
                    break;
                }

                double dot = look.dot(aim.scale(1.0 / dist));
                if (dot > bestDot) {
                    bestDot = dot;
                    best = p;
                }
            }
        }

        return best == null ? false : tryGrant(reviver, best);
    }
}
