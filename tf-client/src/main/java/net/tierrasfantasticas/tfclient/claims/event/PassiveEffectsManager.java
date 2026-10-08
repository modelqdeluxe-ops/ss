package net.tierrasfantasticas.tfclient.claims.event;

import net.tierrasfantasticas.tfclient.claims.data.Claim;
import net.tierrasfantasticas.tfclient.claims.data.ClaimConfig;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

public final class PassiveEffectsManager {
    private static int counter = 0;
    private static final Set<UUID> grantedFlight = ConcurrentHashMap.newKeySet();

    private PassiveEffectsManager() {
    }

    public static void tick(MinecraftServer minecraftserver) {
        if (++counter % 20 == 0) {
            boolean flag = counter % Math.max(20, ClaimConfig.get().passiveEffectIntervalTicks) < 20;
            for (ServerLevel serverlevel : minecraftserver.getAllLevels()) {
                for (ServerPlayer serverplayer : serverlevel.players()) {
                    Claim claim = ClaimManager.getInstance().getClaimAt((Level)serverlevel, serverplayer.blockPosition());
                    PassiveEffectsManager.handleFlight(serverplayer, claim);
                    if (!flag) continue;
                    PassiveEffectsManager.applyEffects(serverplayer, claim);
                }
            }
        }
    }

    private static int paidLevel(ClaimTier claimtier) {
        String s1;
        if (claimtier == null) {
            return 0;
        }
        String s = claimtier.id;
        return switch (s1 = claimtier.id) {
            case "claimstone_250x250" -> 1;
            case "claimstone_300x300" -> 2;
            case "claimstone_500x500" -> 3;
            default -> 0;
        };
    }

    private static void handleFlight(ServerPlayer serverplayer, Claim claim) {
        UUID uuid = serverplayer.getUUID();
        GameType gametype = serverplayer.gameMode.getGameModeForPlayer();
        if (gametype != GameType.CREATIVE && gametype != GameType.SPECTATOR) {
            boolean flag = false;
            if (claim != null && PassiveEffectsManager.paidLevel(claim.getTier()) >= 3 && claim.canModify((Player)serverplayer) && claim.getFlags().allowFlight) {
                flag = true;
            }
            boolean flag1 = grantedFlight.contains(uuid);
            boolean flag2 = serverplayer.getAbilities().mayfly;
            if (flag) {
                if (!flag1 && !flag2) {
                    serverplayer.getAbilities().mayfly = true;
                    serverplayer.onUpdateAbilities();
                    grantedFlight.add(uuid);
                    serverplayer.displayClientMessage((Component)Component.literal((String)"\u2714 Vuelo activado (zona 500x500).").withStyle(ChatFormatting.GREEN), true);
                }
            } else if (flag1) {
                grantedFlight.remove(uuid);
                serverplayer.getAbilities().mayfly = false;
                serverplayer.getAbilities().flying = false;
                serverplayer.onUpdateAbilities();
                serverplayer.displayClientMessage((Component)Component.literal((String)"[i] Saliste de la zona de vuelo.").withStyle(ChatFormatting.AQUA), true);
            }
        } else {
            grantedFlight.remove(uuid);
        }
    }

    private static void applyEffects(ServerPlayer serverplayer, Claim claim) {
        int i;
        if (claim != null && (i = PassiveEffectsManager.paidLevel(claim.getTier())) != 0 && claim.canModify((Player)serverplayer)) {
            if (i >= 1 && claim.getFlags().effectRegeneration) {
                serverplayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
            if (i >= 2 && claim.getFlags().effectResistance) {
                serverplayer.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
            if (i >= 2 && claim.getFlags().effectSpeed) {
                serverplayer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ClaimConfig.get().effectDurationTicks, 0, true, false, true));
            }
        }
    }

    public static void onPlayerDisconnect(UUID uuid) {
        grantedFlight.remove(uuid);
    }
}

