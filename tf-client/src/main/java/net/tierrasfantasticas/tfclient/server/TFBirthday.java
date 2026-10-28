/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.150, el dueño: «un comando tf cumpleaños felicitar (jugador)… un evento tipo el de la grieta en todo el mundo,
 * sobre todos los mundos overworld (usamos Multiverse): en el cielo felicitando al jugador, fuegos artificiales, mensaje
 * en el chat, breve música… un evento chingón, todo bonito») Minuto y medio de fiesta: el servidor lo anuncia en el chat y
 * avisa a cada jugador; lo demás lo pone cada cliente ({@code client.TFBirthdayClient}) en los mundos con cielo de
 * Overworld (todos los de Multiverse que lo sean; el Nether, el End y el Void no): el letrero en el cielo, cohetes,
 * confeti y «Cumpleaños feliz» con campanas. Son efectos del cliente: ni entidades, ni daño, ni carga para el servidor.
 * Quien entra o cambia de mundo durante la fiesta la ve desde donde va.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFBirthday {
    /** Lo que dura (ticks). */
    public static final int LENGTH = 20 * 90;

    private static String name;
    private static UUID honoree;
    private static int left;

    private TFBirthday() {}

    public static boolean active() {
        return name != null && left > 0;
    }

    /** ¿Ve el cielo de este mundo (Overworld y los de Multiverse como él)? */
    public static boolean skyHere(Level level) {
        return level.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.OVERWORLD_EFFECTS)
                && !net.tierrasfantasticas.tfclient.vacio.TFVoid.isVoid(level);
    }

    // ------------------------------------------------------------------------------------------------- comando

    /** /tf cumpleaños felicitar &lt;jugador&gt; y /tf cumpleaños parar (un solo comando, con eñe). */
    public static LiteralArgumentBuilder<CommandSourceStack> command(String literal) {
        return Commands.literal(literal).requires(s -> s.hasPermission(2))
                .then(Commands.literal("felicitar")
                        .then(Commands.argument("jugador", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b))
                                .executes(TFBirthday::celebrate)))
                .then(Commands.literal("parar").executes(ctx -> {
                    if (!active()) {
                        ctx.getSource().sendFailure(Component.literal("No hay ninguna fiesta en marcha."));
                        return 0;
                    }
                    stop(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Fiesta terminada."), true);
                    return 1;
                }));
    }

    private static int celebrate(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        String typed = StringArgumentType.getString(ctx, "jugador");
        ServerPlayer p = server.getPlayerList().getPlayerByName(typed);
        start(server, p != null ? p.getGameProfile().getName() : typed, p != null ? p.getUUID() : null);
        ctx.getSource().sendSuccess(() -> Component.literal("¡Fiesta de cumpleaños para " + name + " en marcha! (minuto y medio)"), true);
        return 1;
    }

    public static void start(MinecraftServer server, String who, UUID id) {
        name = who;
        honoree = id;
        left = LENGTH;
        TFClient.LOGGER.info("TF Cumpleaños: fiesta para {}", who);
        server.getPlayerList().broadcastSystemMessage(announcement(who), false);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p);
    }

    public static void stop(MinecraftServer server) {
        name = null;
        honoree = null;
        left = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Party("", 0, false, false));
    }

    /** Arcoíris, letra a letra. */
    static MutableComponent rainbow(String text, boolean bold) {
        MutableComponent out = Component.empty();
        int i = 0;
        for (int cp : text.codePoints().toArray()) {
            String ch = new String(Character.toChars(cp));
            int rgb = net.minecraft.util.Mth.hsvToRgb((i++ * 0.07F) % 1F, 0.55F, 1F) & 0xFFFFFF;
            out.append(Component.literal(ch).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold)));
        }
        return out;
    }

    private static Component announcement(String who) {
        return Component.literal("\n")
                .append(Component.literal("  ✦ ").withStyle(ChatFormatting.GOLD))
                .append(rainbow("¡FELIZ CUMPLEAÑOS, " + who.toUpperCase(java.util.Locale.ROOT) + "!", true))
                .append(Component.literal(" ✦\n").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  Todo Tierras Fantásticas te felicita. ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("¡Mira al cielo! ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("❤\n").withStyle(ChatFormatting.RED));
    }

    private static void send(ServerPlayer p) {
        if (!active()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Party(name, left, skyHere(p.level()), p.getUUID().equals(honoree)));
    }

    // ------------------------------------------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        if (--left <= 0) stop(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p);
    }

    @SubscribeEvent
    public static void onChangeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        name = null;
        left = 0;
    }

    // ------------------------------------------------------------------------------------------------- red

    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "cumple"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Party.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Party::write).decoder(Party::read).consumerMainThread(Party::handle).add();
    }

    /** La fiesta: de quién, cuántos ticks le quedan (0 = se acabó), si aquí se ve el cielo y si es la suya. */
    public record Party(String name, int left, boolean sky, boolean mine) {
        static void write(Party m, FriendlyByteBuf buf) {
            buf.writeUtf(m.name, 64);
            buf.writeVarInt(m.left);
            buf.writeBoolean(m.sky);
            buf.writeBoolean(m.mine);
        }

        static Party read(FriendlyByteBuf buf) {
            return new Party(buf.readUtf(64), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }

        static void handle(Party m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.client.TFBirthdayClient.party(m.name, m.left, m.sky, m.mine));
        }
    }
}
