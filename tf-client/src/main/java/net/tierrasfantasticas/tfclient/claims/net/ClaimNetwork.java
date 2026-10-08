package net.tierrasfantasticas.tfclient.claims.net;

import net.tierrasfantasticas.tfclient.claims.net.ClaimBordersPacket;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ClaimNetwork {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    private ClaimNetwork() {
    }

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel((ResourceLocation)new ResourceLocation("tfclient", "claims"), () -> PROTOCOL, (Predicate)NetworkRegistry.acceptMissingOr((String)PROTOCOL), (Predicate)NetworkRegistry.acceptMissingOr((String)PROTOCOL));
        CHANNEL.registerMessage(0, ClaimBordersPacket.class, ClaimBordersPacket::encode, ClaimBordersPacket::decode, ClaimBordersPacket::handle);
    }

    public static void sendTo(ServerPlayer serverplayer, Object object) {
        if (CHANNEL != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverplayer), object);
        }
    }
}

