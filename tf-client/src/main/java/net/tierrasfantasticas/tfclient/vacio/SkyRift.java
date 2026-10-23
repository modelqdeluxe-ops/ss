/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La grieta del cielo (1.3.93; el dueño: «cuando yo coloque el NPC, una grieta grande se dibujará en el cielo, con
 * animación épica que asuste a todo el server; antes, un sonido de temblor, de que algo oscuro se avecina, y un leve
 * temblorcito; un mensaje del NPC: algo siniestro se avecina al Overworld; luego pum, la grieta en el cielo, como que
 * se rompe la dimensión; y luego el NPC dice dónde está, el centro de Tierras Fantásticas, el spawn, que lo busquen
 * porque tiene algo que decirles. Para cerrar esa grieta del cielo hay que cerrar la del Void: cuando derroten un
 * boss, se cierra en el Overworld momentáneamente; cuando se active otro, se vuelve a abrir con otro sonido; y así hasta
 * el final, que decidiremos nosotros»). El spawn del servidor es un mundo aparte (un plugin lo separa del Overworld de
 * los jugadores): la grieta sale en <b>todos los mundos de tipo Overworld</b> (por su cielo, no por su nombre), en el
 * mismo sitio del cielo para todos ({@link #YAW}, {@link #PITCH}).
 *
 * <p>El evento ({@link #start}, al poner al caballero con {@code /tf void npc} la primera vez): preludio (chat,
 * retumbar, el cielo que se apaga, relámpagos lejanos y temblor que crece, 12 s), la grieta se rompe y se abre
 * ({@link #FX_BURST}) y, 8 s después, el chat con dónde está. Su estado se guarda en el mundo ({@link Data}) y cada jugador lo recibe al entrar o cambiar de mundo. Lo
 * dibuja y lo hace sonar SkyRiftClient.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class SkyRift {
    /** Estados: nunca ha salido, abierta, sellada por ahora (cayó un jefe del Void), cerrada para siempre (el final). */
    public static final int OFF = 0, OPEN = 1, CLOSED = 2, ENDED = 3;
    /** Lo que ve el jugador al cambiar: nada (al entrar), el preludio, se rompe, se sella, se vuelve a abrir, el final. */
    public static final int FX_SYNC = 0, FX_PRELUDE = 1, FX_BURST = 2, FX_CLOSE = 3, FX_REOPEN = 4, FX_END = 5;
    /**
     * Dónde está en el cielo (1.3.93b): como el sol o la luna, en una dirección fija (hacia el norte, a 50° sobre el
     * horizonte) estés donde estés. Antes era un punto 190 bloques sobre el spawn: lejos de él (al reaparecer en tu cama,
     * por ejemplo) salía pequeña junto al horizonte (captura del dueño: «sale ahí y no en el cielo»).
     */
    public static final float YAW = 180F, PITCH = -50F;
    /** Ticks del evento: el preludio (12 s) dura hasta que se rompe; luego, el chat con dónde está. */
    static final int BURST_AT = 240, WHERE_AT = 400;
    private static int eventTick = -1;
    /** Cuándo se selló (ticks del servidor) y lo mínimo que se queda sellada: que se vea que se cerró. */
    private static int closedAt = -1;
    static final int MIN_CLOSED = 600;
    /** El mundo en el que estaba cada jugador cuando se le mandó la grieta. */
    private static final java.util.Map<java.util.UUID, String> SENT = new java.util.HashMap<>();
    /** Lo último que pasó (para la prueba en el juego). */
    private static String last = "";

    private SkyRift() {}

    /** Lo que se guarda en el mundo (Overworld del servidor: data/tfclient_cielo.dat). */
    static final class Data extends SavedData {
        int state = OFF;
        String dim = "";
        double x, y, z;

        static Data load(CompoundTag tag) {
            Data d = new Data();
            d.state = tag.getInt("Estado");
            d.dim = tag.getString("Mundo");
            d.x = tag.getDouble("X");
            d.y = tag.getDouble("Y");
            d.z = tag.getDouble("Z");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("Estado", state);
            tag.putString("Mundo", dim);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            return tag;
        }
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "tfclient_cielo");
    }

    private static MinecraftServer server() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
    }

    /** El estado de ahora (el diálogo de Caelthar lo usa). */
    public static int state() {
        MinecraftServer s = server();
        return s == null ? OFF : data(s).state;
    }

    /** ¿Un mundo con cielo de Overworld? (el del spawn, separado por un plugin, también) */
    static boolean overworldLike(Level l) {
        return l.dimensionType().effectsLocation().equals(BuiltinDimensionTypes.OVERWORLD_EFFECTS) && !TFVoid.isVoid(l);
    }

    private static void send(ServerPlayer p, Data d, int fx) {
        SENT.put(p.getUUID(), p.level().dimension().location().toString());
        VoidNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new VoidNet.Sky(d.state, fx, overworldLike(p.level())));
    }

    private static void sendAll(MinecraftServer server, Data d, int fx) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, d, fx);
    }

    /** Un mensaje de Caelthar en el chat de todos. */
    private static void say(MinecraftServer server, String text) {
        MutableComponent c = Component.literal("✦ " + KnightEntity.NAME + " » ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(text).withStyle(s -> s.withColor(0xD8B8FF).withBold(false).withItalic(true)));
        server.getPlayerList().broadcastSystemMessage(c, false);
        last = text;
    }

    /** Empieza el evento: la grieta saldrá sobre ese sitio (el de Caelthar) de ese mundo. */
    public static void start(ServerLevel level, Vec3 at) {
        MinecraftServer server = level.getServer();
        Data d = data(server);
        d.dim = level.dimension().location().toString();
        d.x = at.x;
        d.y = at.y;
        d.z = at.z;
        d.setDirty();
        eventTick = 0;
    }

    /** ¿Está en marcha el evento (antes del chat con dónde está)? */
    static boolean running() {
        return eventTick >= 0;
    }

    /** Cada tick del servidor (TFVoid): el evento. */
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0) resync(server);
        if (eventTick < 0) return;
        Data d = data(server);
        if (eventTick == 0) {
            say(server, "Algo siniestro se avecina al Overworld... La tierra tiembla y la luz se apaga. Prepárense.");
            sendAll(server, d, FX_PRELUDE);
        } else if (eventTick == BURST_AT) {
            d.state = OPEN;
            d.setDirty();
            sendAll(server, d, FX_BURST);
            last = "se rompe el cielo";
        } else if (eventTick == WHERE_AT) {
            say(server, "¡El Void ha desgarrado el cielo de Tierras Fantásticas! Búsquenme en el spawn, en el centro de Tierras "
                    + "Fantásticas: tengo algo muy importante que contarles.");
            eventTick = -1;
            return;
        }
        eventTick++;
    }

    /** Ha caído el jefe de la grieta del Void (Rifts.bossKilled): la del cielo se sella... por ahora. */
    public static void voidBossKilled() {
        MinecraftServer server = server();
        if (server == null) return;
        Data d = data(server);
        if (d.state != OPEN) return;
        d.state = CLOSED;
        d.setDirty();
        closedAt = server.getTickCount();
        sendAll(server, d, FX_CLOSE);
        say(server, "La grieta del cielo se ha sellado... por ahora. El Void duerme hasta que otra de sus criaturas despierte.");
    }

    /** La grieta del Void vuelve a estar lista, con otro jefe (Rifts): la del cielo se vuelve a abrir. */
    public static void voidReady() {
        voidReady(false);
    }

    /** Igual; «now»: ya, aunque se haya sellado hace menos de 30 s (lo ha encendido el staff, 1.3.98). */
    public static void voidReady(boolean now) {
        MinecraftServer server = server();
        if (server == null) return;
        Data d = data(server);
        if (d.state != CLOSED || !now && closedAt >= 0 && server.getTickCount() - closedAt < MIN_CLOSED) return;
        d.state = OPEN;
        d.setDirty();
        sendAll(server, d, FX_REOPEN);
        say(server, "¡La grieta del cielo se ha vuelto a abrir! Otra criatura ha despertado en el Void.");
    }

    /**
     * (1.3.96) Ha caído el último jefe de la secuencia de la grieta del Void y al acabar va el final (lo elige el staff en
     * /tf boss): la herida se cierra para siempre, como con {@code /tf void cielo final}.
     */
    public static void voidSequenceEnded() {
        MinecraftServer server = server();
        if (server == null) return;
        Data d = data(server);
        if (d.state != OPEN && d.state != CLOSED) {
            voidBossKilled();
            return;
        }
        end(server);
    }

    /** El final (lo decide el staff): la herida se cierra para siempre. */
    static void end(MinecraftServer server) {
        Data d = data(server);
        d.state = ENDED;
        d.setDirty();
        eventTick = -1;
        sendAll(server, d, FX_END);
        say(server, "Lo lograron: la herida del cielo se ha cerrado para siempre. Tierras Fantásticas vuelve a estar en paz... gracias a ustedes.");
    }

    private static void set(MinecraftServer server, int state, int fx) {
        Data d = data(server);
        d.state = state;
        d.setDirty();
        eventTick = -1;
        closedAt = state == CLOSED ? server.getTickCount() : -1;
        sendAll(server, d, fx);
    }

    /** Para la prueba en el juego: qué pasó lo último y en qué estado está. */
    public static String describe() {
        String[] names = {"no ha salido", "abierta", "sellada por ahora", "cerrada para siempre"};
        int st = state();
        return names[Math.max(0, Math.min(3, st))] + (last.isEmpty() ? "" : "; lo último: «" + last + "»");
    }

    /** La prueba en el juego: lo mismo que los comandos. */
    public static void testEnd() {
        MinecraftServer s = server();
        if (s != null) end(s);
    }

    public static void testReset() {
        MinecraftServer s = server();
        if (s != null) set(s, OFF, FX_SYNC);
        last = "";
    }

    // ------------------------------------------------------------------------------------------- comandos

    /** /tf void cielo evento|abrir|cerrar|final|quitar|estado (staff). */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("cielo")
                .then(Commands.literal("evento").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    var knights = p.serverLevel().getEntitiesOfClass(KnightEntity.class, p.getBoundingBox().inflate(64), k -> !k.home());
                    Vec3 at = knights.isEmpty() ? p.position() : knights.get(0).position();
                    start(p.serverLevel(), at);
                    ctx.getSource().sendSuccess(() -> Component.literal("Empieza el evento de la grieta del cielo (sobre "
                            + (knights.isEmpty() ? "ti" : KnightEntity.NAME) + ").").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    return 1;
                }))
                .then(Commands.literal("abrir").executes(ctx -> {
                    set(ctx.getSource().getServer(), OPEN, FX_REOPEN);
                    ctx.getSource().sendSuccess(() -> Component.literal("Grieta del cielo: abierta."), true);
                    return 1;
                }))
                .then(Commands.literal("cerrar").executes(ctx -> {
                    set(ctx.getSource().getServer(), CLOSED, FX_CLOSE);
                    ctx.getSource().sendSuccess(() -> Component.literal("Grieta del cielo: sellada (se vuelve a abrir cuando la del Void esté lista)."), true);
                    return 1;
                }))
                .then(Commands.literal("final").executes(ctx -> {
                    end(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Grieta del cielo: cerrada para siempre (el final)."), true);
                    return 1;
                }))
                .then(Commands.literal("quitar").executes(ctx -> {
                    set(ctx.getSource().getServer(), OFF, FX_SYNC);
                    ctx.getSource().sendSuccess(() -> Component.literal("Grieta del cielo quitada (sin evento). La próxima vez que pongas a "
                            + KnightEntity.NAME + " empezará otra vez."), true);
                    return 1;
                }))
                .then(Commands.literal("estado").executes(ctx -> {
                    Data d = data(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Grieta del cielo: " + describe() + (d.dim.isEmpty() ? ""
                            : String.format(" · sobre %s %.0f %.0f %.0f", d.dim, d.x, d.y, d.z))), false);
                    return 1;
                }));
    }

    // ------------------------------------------------------------------------------------------- jugadores

    /**
     * Cada segundo: a quien ha cambiado de mundo sin que Forge lo cuente (los mundos de los plugins en Mohist, como el
     * del spawn), se le manda otra vez dónde está la grieta en el suyo.
     */
    private static void resync(MinecraftServer server) {
        Data d = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String dim = p.level().dimension().location().toString();
            if (dim.equals(SENT.get(p.getUUID()))) continue;
            if (d == null) d = data(server);
            send(p, d, FX_SYNC);
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p, data(p.server), FX_SYNC);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p, data(p.server), FX_SYNC);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p, data(p.server), FX_SYNC);
    }
}
