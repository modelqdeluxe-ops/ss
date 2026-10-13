package net.tierrasfantasticas.tfclient.skins;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El canal de las skins del Armario (tfclient:skins). La skin que lleva cada uno la sabe el servidor y se la cuenta a
 * todos (Active: jugador, huella y brazos); quien no tiene esa imagen la pide (Want) y le llega (Png), y la guarda por su
 * huella. Para importar desde el ordenador, el servidor pide la lista de la carpeta del jugador (Scan → LocalList) y,
 * al elegir una, el PNG (UploadAsk → Upload). Exportar manda el PNG al jugador (Export), que lo guarda en su carpeta.
 * Todo lo que llega al servidor se comprueba allí (tamaño, medidas, huella). Los PNG viajan como mucho de 16 KB.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SkinNet {
    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "skins"), () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
    /** Lo más que trae una lista de la carpeta del jugador. */
    static final int MAX_LOCAL = 32;

    private static boolean registered;

    private SkinNet() {}

    @SubscribeEvent
    public static void onSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(SkinNet::register);
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Active.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Active::write).decoder(Active::read).consumerMainThread(Active::handle).add();
        CHANNEL.messageBuilder(Png.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Png::write).decoder(Png::read).consumerMainThread(Png::handle).add();
        CHANNEL.messageBuilder(Want.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Want::write).decoder(Want::read).consumerMainThread(Want::handle).add();
        CHANNEL.messageBuilder(Scan.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Scan::write).decoder(Scan::read).consumerMainThread(Scan::handle).add();
        CHANNEL.messageBuilder(LocalList.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LocalList::write).decoder(LocalList::read).consumerMainThread(LocalList::handle).add();
        CHANNEL.messageBuilder(UploadAsk.class, 5, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UploadAsk::write).decoder(UploadAsk::read).consumerMainThread(UploadAsk::handle).add();
        CHANNEL.messageBuilder(Upload.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Upload::write).decoder(Upload::read).consumerMainThread(Upload::handle).add();
        CHANNEL.messageBuilder(Export.class, 7, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Export::write).decoder(Export::read).consumerMainThread(Export::handle).add();
        CHANNEL.messageBuilder(OpenFolder.class, 8, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenFolder::write).decoder(OpenFolder::read).consumerMainThread(OpenFolder::handle).add();
    }

    /** ¿Tiene este jugador el canal? (los que no tienen el TF Client ven la skin de siempre). */
    static boolean has(ServerPlayer player) {
        return player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }

    static void send(ServerPlayer player, Object msg) {
        if (has(player)) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    private static byte[] readPng(FriendlyByteBuf buf) {
        return buf.readByteArray(SkinImages.MAX_PNG);
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** La skin que lleva un jugador: hash "" = la suya de siempre (la de su cuenta). slim: brazos finos (Alex). */
    public record Active(UUID player, String hash, boolean slim) {
        static void write(Active m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeUtf(m.hash, 40);
            buf.writeBoolean(m.slim);
        }

        static Active read(FriendlyByteBuf buf) {
            return new Active(buf.readUUID(), buf.readUtf(40), buf.readBoolean());
        }

        static void handle(Active m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.active(m.player, m.hash, m.slim));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Una imagen de skin (su huella y el PNG, de 16 KB como mucho). */
    public record Png(String hash, byte[] png) {
        static void write(Png m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 40);
            buf.writeByteArray(m.png);
        }

        static Png read(FriendlyByteBuf buf) {
            return new Png(buf.readUtf(40), readPng(buf));
        }

        static void handle(Png m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.png(m.hash, m.png));
            ctx.get().setPacketHandled(true);
        }
    }

    /** El cliente pide la imagen de una huella que no tiene. */
    public record Want(String hash) {
        static void write(Want m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 40);
        }

        static Want read(FriendlyByteBuf buf) {
            return new Want(buf.readUtf(40));
        }

        static void handle(Want m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) SkinServer.want(player, m.hash);
            ctx.get().setPacketHandled(true);
        }
    }

    /** El servidor pide la lista de skins de la carpeta del jugador (.minecraft/tfclient/skins). */
    public record Scan() {
        static void write(Scan m, FriendlyByteBuf buf) {}

        static Scan read(FriendlyByteBuf buf) {
            return new Scan();
        }

        static void handle(Scan m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> SkinClient::scan);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Una skin de la carpeta del jugador: el nombre del archivo (sin .png), su huella y si parece de brazos finos. */
    public record Local(String name, String hash, boolean slim) {}

    /** Lo que hay en la carpeta: las que valen (32 como mucho) y cuántos PNG no son skins. */
    public record LocalList(List<Local> skins, int bad) {
        static void write(LocalList m, FriendlyByteBuf buf) {
            int n = Math.min(MAX_LOCAL, m.skins.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Local l = m.skins.get(i);
                buf.writeUtf(l.name.length() > 48 ? l.name.substring(0, 48) : l.name, 192);
                buf.writeUtf(l.hash, 40);
                buf.writeBoolean(l.slim);
            }
            buf.writeVarInt(m.bad);
        }

        static LocalList read(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            if (n < 0 || n > MAX_LOCAL) throw new IllegalArgumentException("demasiadas skins");
            List<Local> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(new Local(buf.readUtf(192), buf.readUtf(40), buf.readBoolean()));
            return new LocalList(list, buf.readVarInt());
        }

        static void handle(LocalList m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) SkinServer.localList(player, m.skins, m.bad);
            ctx.get().setPacketHandled(true);
        }
    }

    /** El servidor pide el PNG de una skin de la carpeta (por su huella). */
    public record UploadAsk(String hash) {
        static void write(UploadAsk m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 40);
        }

        static UploadAsk read(FriendlyByteBuf buf) {
            return new UploadAsk(buf.readUtf(40));
        }

        static void handle(UploadAsk m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.upload(m.hash));
            ctx.get().setPacketHandled(true);
        }
    }

    /** El PNG de una skin de la carpeta (la huella que pidió el servidor y la imagen). */
    public record Upload(String hash, byte[] png) {
        static void write(Upload m, FriendlyByteBuf buf) {
            buf.writeUtf(m.hash, 40);
            buf.writeByteArray(m.png);
        }

        static Upload read(FriendlyByteBuf buf) {
            return new Upload(buf.readUtf(40), readPng(buf));
        }

        static void handle(Upload m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) SkinServer.uploaded(player, m.hash, m.png);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Guardar una skin en .minecraft/tfclient/skins/exportadas/&lt;file&gt;.png (y abrir la carpeta). */
    public record Export(String file, byte[] png) {
        static void write(Export m, FriendlyByteBuf buf) {
            buf.writeUtf(m.file, 64);
            buf.writeByteArray(m.png);
        }

        static Export read(FriendlyByteBuf buf) {
            return new Export(buf.readUtf(64), readPng(buf));
        }

        static void handle(Export m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.export(m.file, m.png));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Abrir la carpeta de skins del jugador (exports: la de las exportadas). */
    public record OpenFolder(boolean exports) {
        static void write(OpenFolder m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.exports);
        }

        static OpenFolder read(FriendlyByteBuf buf) {
            return new OpenFolder(buf.readBoolean());
        }

        static void handle(OpenFolder m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SkinClient.openFolder(m.exports));
            ctx.get().setPacketHandled(true);
        }
    }
}
