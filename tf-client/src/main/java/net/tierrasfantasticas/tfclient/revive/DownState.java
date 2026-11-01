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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;

/** Un jugador noqueado: lo que le queda, quién le está reviviendo y lo que tenía al caer (como en el revivemod). */
public class DownState {
    public final UUID playerUuid;
    public final int totalTicks;
    public int remainingTicks;
    public ResourceKey<Level> downDimension;
    public Vec3 downPosition;
    public final ServerBossEvent bossBar;
    public final Map<UUID, Integer> reviverWindow = new HashMap<>();
    public int reviveProgressTicks;
    public boolean channelActive;
    public int lockedSlot;
    public boolean surrendering;
    public int surrenderTicks;
    public boolean selfReviving;
    public int selfTicks;
    public int snapshotFood;
    public float snapshotSaturation;
    public final List<MobEffectInstance> snapshotEffects = new ArrayList<>();

    public DownState(UUID playerUuid, int totalTicks, ResourceKey<Level> dim, Vec3 pos) {
        this.playerUuid = playerUuid;
        this.totalTicks = totalTicks;
        this.remainingTicks = totalTicks;
        this.downDimension = dim;
        this.downPosition = pos;
        this.bossBar = new ServerBossEvent(Component.literal("Noqueado"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        this.bossBar.setDarkenScreen(false);
        this.bossBar.setCreateWorldFog(false);
        this.bossBar.setProgress(1.0F);
    }

    public ResourceLocation bossBarId() {
        return new ResourceLocation(TFClient.MOD_ID, "down_" + this.playerUuid);
    }
}
