/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.revive;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3f;

public final class DownTicker {
    private static final float[] SEQ = new float[]{0.66F, 0.75F, 0.84F, 1.0F, 1.12F, 1.26F, 1.5F, 1.68F};

    private static DustParticleOptions blood(ReviveConfig cfg) {
        return new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), cfg.bloodParticleScale);
    }

    private static DustParticleOptions white(ReviveConfig cfg) {
        return new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), cfg.whiteParticleScale);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent event) {
        if (event.phase == Phase.END) {
            tick(event.getServer());
        }
    }

    private static void tick(MinecraftServer server) {
        DownManager.clearActiveRevivers();
        if (!DownManager.all().isEmpty()) {
            ReviveConfig cfg = TFRevive.getConfig();
            ArrayList<UUID> toRemove = new ArrayList<>();
            DownState[] snapshot = DownManager.all().toArray(new DownState[0]);

            for (DownState state : snapshot) {
                ServerPlayer downed;
                if (DownManager.isDown(state.playerUuid) && (downed = server.getPlayerList().getPlayer(state.playerUuid)) != null) {
                    if (downed.isAlive() && !downed.isRemoved()) {
                        ServerLevel world = downed.serverLevel();
                        DownManager.enforcePosition(downed);
                        DownManager.enforceLockedSlot(downed);
                        downed.setPose(Pose.SWIMMING);
                        if (state.remainingTicks % 20 == 0) {
                            DownManager.applyDownEffects(downed);
                        }

                        if (DownManager.consumeSurrenderToggle(state.playerUuid)) {
                            DownManager.forceDeath(downed, downed.damageSources().genericKill());
                        } else if (!DownManager.consumeSelfToggle(state.playerUuid) || !DownManager.selfRevive(downed)) {
                            if (state.remainingTicks % Math.max(1, cfg.bloodParticleInterval) == 0) {
                                world.sendParticles(
                                    blood(cfg), downed.getX(), downed.getY() + 0.2, downed.getZ(), Math.max(0, cfg.bloodParticleCount), 0.3, 0.05, 0.3, 0.0
                                );
                                world.sendParticles(
                                    white(cfg), downed.getX(), downed.getY() + 0.2, downed.getZ(), Math.max(0, cfg.whiteParticleCount), 0.3, 0.05, 0.3, 0.0
                                );
                            }

                            List<ServerPlayer> revivers;
                            int n;
                            if ((n = (revivers = collectRevivers(downed, state, cfg, server)).size()) > 0) {
                                state.reviveProgressTicks += n;
                                boolean wasActive = state.channelActive;
                                state.channelActive = true;

                                for (ServerPlayer r : revivers) {
                                    DownManager.markReviving(r.getUUID());
                                }

                                if (!wasActive) {
                                    world.playSound(null, downed.getX(), downed.getY(), downed.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.2F);
                                }

                                if (state.reviveProgressTicks % 4 == 0) {
                                    world.sendParticles(ParticleTypes.HEART, downed.getX(), downed.getY() + 1.0, downed.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
                                }

                                if (state.reviveProgressTicks / 10 != (state.reviveProgressTicks - n) / 10) {
                                    int step = state.reviveProgressTicks / 10 % SEQ.length;
                                    world.playSound(
                                        null,
                                        downed.getX(),
                                        downed.getY(),
                                        downed.getZ(),
                                        SoundEvents.AMETHYST_BLOCK_CHIME,
                                        SoundSource.PLAYERS,
                                        cfg.reviveTickVolume,
                                        SEQ[step]
                                    );
                                }

                                int pct = Math.min(100, (int)(100.0 * (double)state.reviveProgressTicks / (double)Math.max(1, cfg.reviveTimeTicks)));
                                Object speed = n > 1 ? " x" + n : "";
                                String bar = bar(pct);
                                ClientboundSetActionBarTextPacket reviverMsg = new ClientboundSetActionBarTextPacket(
                                    Component.literal("Reviviendo ")
                                        .withStyle(ChatFormatting.WHITE)
                                        .append(Component.literal("[" + bar + "] " + pct + "%" + (String)speed).withStyle(ChatFormatting.YELLOW))
                                );

                                for (ServerPlayer r : revivers) {
                                    r.connection.send(reviverMsg);
                                }

                                downed.connection
                                    .send(
                                        new ClientboundSetActionBarTextPacket(
                                            Component.literal("Te están reviviendo ")
                                                .withStyle(ChatFormatting.WHITE)
                                                .append(Component.literal("[" + bar + "] " + pct + "%").withStyle(ChatFormatting.GREEN))
                                        )
                                    );
                                if (state.reviveProgressTicks >= cfg.reviveTimeTicks) {
                                    DownManager.revive(downed);
                                    continue;
                                }
                            } else {
                                if (state.channelActive) {
                                    world.playSound(null, downed.getX(), downed.getY(), downed.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 0.6F);
                                }

                                state.channelActive = false;
                                state.reviveProgressTicks = 0;
                            }

                            state.remainingTicks--;
                            state.bossBar.setProgress(Math.max(0.0F, (float)state.remainingTicks / (float)state.totalTicks));
                            if (state.remainingTicks % 20 == 0) {
                                int secondsLeft = (state.remainingTicks + 19) / 20;
                                state.bossBar
                                    .setName(
                                        Component.literal("Desangrándose - ")
                                            .withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD})
                                            .append(Component.literal(secondsLeft + "s").withStyle(ChatFormatting.WHITE))
                                    );
                                if (!state.channelActive && state.remainingTicks > 0) {
                                    int idx = state.remainingTicks / 20 % SEQ.length;
                                    float pitch = SEQ[SEQ.length - 1 - idx];
                                    world.playSound(
                                        null,
                                        downed.getX(),
                                        downed.getY(),
                                        downed.getZ(),
                                        SoundEvents.AMETHYST_BLOCK_CHIME,
                                        SoundSource.PLAYERS,
                                        cfg.countdownTickVolume,
                                        pitch
                                    );
                                }
                            }

                            if (state.remainingTicks <= 0) {
                                DownManager.forceDeath(downed, downed.damageSources().genericKill());
                            }
                        }
                    } else {
                        toRemove.add(state.playerUuid);
                    }
                }
            }

            for (UUID id : toRemove) {
                DownManager.removeWithoutRevival(id);
            }
        }
    }

    private static String bar(int pct) {
        int filled = pct / 5;
        StringBuilder b = new StringBuilder(20);

        for (int i = 0; i < 20; i++) {
            b.append((char)(i < filled ? '|' : '.'));
        }

        return b.toString();
    }

    private static List<ServerPlayer> collectRevivers(ServerPlayer downed, DownState state, ReviveConfig cfg, MinecraftServer server) {
        ArrayList<ServerPlayer> result = new ArrayList<>();
        double maxSq = cfg.reviveDistance * cfg.reviveDistance;
        Iterator<Entry<UUID, Integer>> it = state.reviverWindow.entrySet().iterator();

        while (it.hasNext()) {
            Entry<UUID, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left <= 0) {
                it.remove();
            } else {
                e.setValue(left);
                ServerPlayer r = server.getPlayerList().getPlayer(e.getKey());
                if (r != null && r.isAlive() && !r.isSpectator() && !DownManager.isDown(r) && r.serverLevel() == downed.serverLevel() && !(r.distanceToSqr(downed) > maxSq)) {
                    result.add(r);
                }
            }
        }

        return result;
    }
}
