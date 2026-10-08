package net.tierrasfantasticas.tfclient.claims.event;

import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimGroup;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.event.PassiveEffectsManager;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class PlayerTracker {
    private static final Map<UUID, UUID> lastClaim = new ConcurrentHashMap<UUID, UUID>();
    private static final Map<UUID, Long> lastAlert = new ConcurrentHashMap<UUID, Long>();
    private static final long ALERT_COOLDOWN_TICKS = 600L;
    private static int bypassReminderCounter = 0;
    private static final Map<UUID, Long> lastBanHit = new ConcurrentHashMap<UUID, Long>();

    public static void onDisconnect(UUID uuid) {
        lastClaim.remove(uuid);
        lastAlert.remove(uuid);
        ClaimManager.getInstance().onPlayerDisconnect(uuid);
        PassiveEffectsManager.onPlayerDisconnect(uuid);
    }

    public static void tick(MinecraftServer minecraftserver) {
        boolean flag = ++bypassReminderCounter % 60 == 0;
        for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
            for (ServerPlayer serverplayer : serverlevel.players()) {
                PlayerTracker.handle(serverlevel, serverplayer);
                if (!flag || !serverplayer.hasPermissions(2) || !ClaimManager.getInstance().isBypassing(serverplayer.getUUID())) continue;
                serverplayer.displayClientMessage((Component)Component.literal((String)"[!] BYPASS ACTIVO").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}), true);
            }
        }
    }

    private static void handle(ServerLevel serverlevel, ServerPlayer serverplayer) {
        UUID uuid;
        Claim claim = ClaimManager.getInstance().getClaimAt((Level)serverlevel, serverplayer.blockPosition());
        UUID uuid1 = lastClaim.get(serverplayer.getUUID());
        UUID uUID = uuid = claim == null ? null : PlayerTracker.zoneId(claim);
        if (Objects.equals(uuid1, uuid)) {
            if (claim != null && claim.isBanned(serverplayer.getUUID()) && !serverplayer.hasPermissions(2)) {
                PlayerTracker.repelBanned(serverlevel, serverplayer, claim);
            }
        } else {
            Claim claim1 = PlayerTracker.resolveZone(uuid1);
            if (claim1 != null) {
                MutableComponent mutablecomponent = claim1.getFlags().showLeave && claim1.getFlags().leaveMessage != null && !claim1.getFlags().leaveMessage.isBlank() ? Component.literal((String)"[Protecci\u00f3n] ").withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)PlayerTracker.truncate(claim1.getFlags().leaveMessage, 50)).withStyle(ChatFormatting.GOLD)) : Component.literal((String)"[Protecci\u00f3n] ").withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)"Saliendo de la zona ").withStyle(ChatFormatting.RED)).append((Component)Component.literal((String)PlayerTracker.truncate(PlayerTracker.zoneLabel(claim1), 24)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
                serverplayer.displayClientMessage((Component)mutablecomponent, true);
                serverplayer.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 0.9f);
            }
            if (claim != null) {
                if (claim.isBanned(serverplayer.getUUID()) && !serverplayer.hasPermissions(2)) {
                    PlayerTracker.repelBanned(serverlevel, serverplayer, claim);
                    lastClaim.remove(serverplayer.getUUID());
                    return;
                }
                MutableComponent mutablecomponent2 = claim.getFlags().showWelcome && claim.getFlags().welcomeMessage != null && !claim.getFlags().welcomeMessage.isBlank() ? Component.literal((String)"[Protecci\u00f3n] ").withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)PlayerTracker.truncate(claim.getFlags().welcomeMessage, 50)).withStyle(ChatFormatting.GREEN)) : Component.literal((String)"[Protecci\u00f3n] ").withStyle(ChatFormatting.GRAY).append((Component)Component.literal((String)"Entrando a la zona ").withStyle(ChatFormatting.GREEN)).append((Component)Component.literal((String)PlayerTracker.truncate(PlayerTracker.zoneLabel(claim), 24)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD}));
                serverplayer.displayClientMessage((Component)mutablecomponent2, true);
                serverplayer.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 2.0f, 1.4f);
                if (claim.getFlags().trespasserAlerts && !claim.canModify((Player)serverplayer)) {
                    long i = serverlevel.getGameTime();
                    Long olong = lastAlert.get(serverplayer.getUUID());
                    if (olong == null || i - olong > (long)ClaimConfig.get().trespasserAlertTicks()) {
                        ServerPlayer serverplayer1;
                        lastAlert.put(serverplayer.getUUID(), i);
                        UUID uuid2 = PlayerTracker.zoneOwner(claim);
                        ServerPlayer serverPlayer = serverplayer1 = uuid2 == null ? null : serverlevel.getServer().getPlayerList().getPlayer(uuid2);
                        if (serverplayer1 != null) {
                            MutableComponent mutablecomponent1 = Component.literal((String)"[!] ").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}).append((Component)Component.literal((String)PlayerTracker.truncate(serverplayer.getName().getString(), 16)).withStyle(new ChatFormatting[]{ChatFormatting.WHITE, ChatFormatting.BOLD})).append((Component)Component.literal((String)(" entr\u00f3 a tu zona en X=" + claim.getX() + " Z=" + claim.getZ())).withStyle(ChatFormatting.YELLOW));
                            serverplayer1.displayClientMessage((Component)mutablecomponent1, false);
                        }
                    }
                }
            }
            if (uuid == null) {
                lastClaim.remove(serverplayer.getUUID());
            } else {
                lastClaim.put(serverplayer.getUUID(), uuid);
            }
        }
    }

    private static UUID zoneId(Claim claim) {
        return claim.getGroupId() != null ? claim.getGroupId() : claim.getClaimId();
    }

    private static Claim resolveZone(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ClaimGroup claimgroup = ClaimManager.getInstance().getGroup(uuid);
        return claimgroup != null ? ClaimManager.getInstance().getMotherClaim(uuid) : PlayerTracker.findClaimById(uuid);
    }

    private static String zoneLabel(Claim claim) {
        ClaimGroup claimgroup;
        if (claim == null) {
            return "";
        }
        return claim.getGroupId() != null && (claimgroup = ClaimManager.getInstance().getGroup(claim.getGroupId())) != null ? claimgroup.getName() : claim.getOwnerName() + " (" + claim.sizeLabel() + ")";
    }

    private static UUID zoneOwner(Claim claim) {
        ClaimGroup claimgroup;
        return claim.getGroupId() != null && (claimgroup = ClaimManager.getInstance().getGroup(claim.getGroupId())) != null && claimgroup.getMotherOwnerId() != null ? claimgroup.getMotherOwnerId() : claim.getOwnerUUID();
    }

    private static Claim findClaimById(UUID uuid) {
        for (Claim claim : ClaimManager.getInstance().getAllClaims()) {
            if (!claim.getClaimId().equals(uuid)) continue;
            return claim;
        }
        return null;
    }

    private static void repelBanned(ServerLevel serverlevel, ServerPlayer serverplayer, Claim claim) {
        double d0 = (double)claim.getX() + 0.5;
        double d1 = (double)claim.getZ() + 0.5;
        double d2 = (double)claim.getRadius() + 1.5;
        double d3 = serverplayer.getX();
        double d4 = serverplayer.getZ();
        double d5 = d3 - (d0 - d2);
        double d6 = d0 + d2 - d3;
        double d7 = d4 - (d1 - d2);
        double d8 = d1 + d2 - d4;
        double d9 = Math.min(Math.min(d5, d6), Math.min(d7, d8));
        double d10 = d3;
        double d11 = d4;
        if (d9 == d5) {
            d10 = d0 - d2;
        } else if (d9 == d6) {
            d10 = d0 + d2;
        } else {
            d11 = d9 == d7 ? d1 - d2 : d1 + d2;
        }
        ClaimConfig claimconfig = ClaimConfig.get();
        if (claimconfig.banTeleportOut) {
            double d12 = PlayerTracker.safeY(serverlevel, d10, serverplayer.getY(), d11);
            serverplayer.teleportTo(serverlevel, d10, d12, d11, serverplayer.getYRot(), serverplayer.getXRot());
            serverplayer.setDeltaMovement(0.0, 0.0, 0.0);
        } else {
            double d15 = serverplayer.getX() - d0;
            double d13 = serverplayer.getZ() - d1;
            double d14 = Math.max(1.0E-4, Math.sqrt(d15 * d15 + d13 * d13));
            serverplayer.setDeltaMovement(d15 / d14 * 1.2, 0.42, d13 / d14 * 1.2);
        }
        serverplayer.hurtMarked = true;
        long i = serverlevel.getGameTime();
        Long olong = lastBanHit.get(serverplayer.getUUID());
        if (olong == null || i - olong >= ClaimConfig.get().banNoticeTicks()) {
            lastBanHit.put(serverplayer.getUUID(), i);
            if (claimconfig.banDamage > 0.0f) {
                serverplayer.invulnerableTime = 0;
                serverplayer.hurt(serverplayer.damageSources().magic(), claimconfig.banDamage);
            }
            serverplayer.displayClientMessage((Component)Component.literal((String)"[!] Est\u00e1s baneado de esta zona.").withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.BOLD}), false);
            serverplayer.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
    }

    private static double safeY(ServerLevel serverlevel, double d0, double d1, double d2) {
        int i = (int)Math.floor(d0);
        int j = (int)Math.floor(d2);
        int k = (int)Math.floor(d1);
        for (int l = 0; l <= 6; ++l) {
            for (int b0 = 1; b0 >= -1; b0 = (int)((byte)(b0 - 2))) {
                int i1 = k + l * b0;
                if (i1 < serverlevel.getMinBuildHeight() || i1 > serverlevel.getMaxBuildHeight() - 2 || !serverlevel.getBlockState(new BlockPos(i, i1, j)).isAir() || !serverlevel.getBlockState(new BlockPos(i, i1 + 1, j)).isAir()) continue;
                return i1;
            }
        }
        return d1;
    }

    private static String truncate(String s, int i) {
        if (s == null) {
            return "";
        }
        return s.length() <= i ? s : s.substring(0, Math.max(0, i - 3)) + "...";
    }
}

