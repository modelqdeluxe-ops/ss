/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156, el dueño: «duelos: puedes retar a cualquier jugador que esté dentro de un clan, con el comando del chat del
 * clan más el subcomando duelo (player); solo reconoce personas dentro de un clan y no acepta duelos entre los mismos
 * integrantes; y el clan con esos duelos sube prestigio»)
 * <ul>
 *   <li>/clan duelo &lt;jugador&gt;: los dos en un clan y de clanes distintos, conectados y a menos de 48 bloques. El otro
 *   tiene 60 s: /clan duelo aceptar (o clic en [ACEPTAR] del chat) o /clan duelo rechazar.</li>
 *   <li>Cuenta atrás de 3 s y a pelear (aunque el PvP esté apagado en la zona o en el servidor: el duelo manda). Gana el
 *   que deja al otro a 1 corazón: nadie muere ni pierde nada (si uno fuera a morir por otra cosa, se queda a 1 corazón y
 *   pierde). Si uno se aleja más de 64 bloques, se desconecta o pasan 3 minutos, se acaba (el que huye pierde; el tiempo,
 *   empate).</li>
 *   <li>Premio: +{@link #PRESTIGE} de prestigio al clan del ganador (el que pierde no baja). Contra la misma pareja solo
 *   cuenta una vez cada 24 h (para que no se haga farmeo).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadClanDuels {
    /** (1.3.158) De config/tfclient/clanes.json, «duelos» ({@link PadClanConfig}); aquí, lo de fábrica. */
    static long PRESTIGE = 25;
    static long CHALLENGE_MS = 60_000, MAX_MS = 180_000, PAIR_MS = 24L * 60 * 60 * 1000;
    static double START_DIST = 48, FLEE_DIST = 64;

    /** Retos pendientes: retado → (retador, cuándo). */
    private static final Map<UUID, Map.Entry<UUID, Long>> CHALLENGES = new HashMap<>();
    /** Duelos en marcha: cada jugador → su duelo. */
    private static final Map<UUID, Duel> DUELS = new HashMap<>();
    /** Última vez que contó el duelo de una pareja (para el premio). */
    private static final Map<String, Long> PAIRS = new HashMap<>();

    static final class Duel {
        final UUID a, b;
        final long start;
        boolean fighting;
        String result = "";
        /** Dónde estaba cada uno al empezar a pelear (el que más se aleja de su sitio es el que huye). */
        net.minecraft.world.phys.Vec3 fromA, fromB;
        /** El último segundo de la cuenta atrás que se enseñó. */
        int shown = -1;

        Duel(UUID a, UUID b, long start) {
            this.a = a;
            this.b = b;
            this.start = start;
        }

        UUID other(UUID u) {
            return u.equals(a) ? b : a;
        }
    }

    private PadClanDuels() {}

    /** ¿Están estos dos peleando un duelo ahora mismo? (claims y fuego amigo dejan pasar el daño). */
    public static boolean fighting(UUID x, UUID y) {
        Duel d = DUELS.get(x);
        return d != null && d.fighting && d.other(x).equals(y);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Comando: /clan duelo <jugador> | aceptar | rechazar
    // ---------------------------------------------------------------------------------------------------------------

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("duelo")
                .executes(ctx -> usage(ctx.getSource()))
                .then(Commands.literal("aceptar").executes(ctx -> accept(ctx.getSource())))
                .then(Commands.literal("rechazar").executes(ctx -> reject(ctx.getSource())))
                .then(Commands.argument("jugador", EntityArgument.player())
                        .executes(ctx -> challenge(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"))));
    }

    private static MutableComponent tag(String text, ChatFormatting color) {
        return Component.literal("[Duelo] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(color));
    }

    private static int usage(CommandSourceStack src) {
        src.sendSuccess(() -> tag("/clan duelo <jugador>: retas a alguien de otro clan. Gana el que deja al otro a 1 corazón (nadie muere) "
                + "y su clan sube " + PRESTIGE + " de prestigio.", ChatFormatting.YELLOW), false);
        return 1;
    }

    /** Por qué no pueden batirse (null si pueden). */
    static String whyNot(ServerPlayer from, ServerPlayer to) {
        String a = PadClans.clanIdOf(from.getUUID()), b = PadClans.clanIdOf(to.getUUID());
        if (a == null) return "Solo los de un clan pueden retar a duelo.";
        if (b == null) return to.getGameProfile().getName() + " no está en ningún clan: solo se reta a gente de un clan.";
        if (a.equals(b)) return "No hay duelos entre los del mismo clan.";
        if (from.getUUID().equals(to.getUUID())) return "No puedes retarte a ti mismo.";
        if (DUELS.containsKey(from.getUUID())) return "Ya estás en un duelo.";
        if (DUELS.containsKey(to.getUUID())) return to.getGameProfile().getName() + " ya está en un duelo.";
        if (from.level() != to.level() || from.distanceTo(to) > START_DIST) return "Tienen que estar cerca (a menos de " + (int) START_DIST + " bloques).";
        return null;
    }

    static int challenge(CommandSourceStack src, ServerPlayer to) {
        ServerPlayer from = src.getPlayer();
        if (from == null) return 0;
        String why = whyNot(from, to);
        if (why != null) {
            src.sendFailure(tag(why, ChatFormatting.RED));
            return 0;
        }
        CHALLENGES.put(to.getUUID(), Map.entry(from.getUUID(), System.currentTimeMillis()));
        to.sendSystemMessage(challengeMessage(from));
        to.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.7F, 0.8F);
        src.sendSuccess(() -> tag("Retaste a " + to.getGameProfile().getName() + ". Tiene 60 s para aceptar.", ChatFormatting.YELLOW), false);
        return 1;
    }

    /**
     * (1.3.159, el dueño: «la petición de un duelo debe salirle en el chat con aceptar o rechazar como botones
     * presionables con el mouse») El reto: quién reta (y su clan) y dos botones que se pulsan con el ratón (ejecutan
     * /clan duelo aceptar o rechazar), con su explicación al pasar el ratón y el tiempo que hay.
     */
    static MutableComponent challengeMessage(ServerPlayer from) {
        long secs = CHALLENGE_MS / 1000;
        MutableComponent yes = Component.literal(" ✔ ACEPTAR ").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan duelo aceptar"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clic para aceptar el duelo contra "
                        + from.getGameProfile().getName() + ".\nGana el que deja al otro a 1 corazón (nadie muere).").withStyle(ChatFormatting.GREEN))));
        MutableComponent no = Component.literal(" ✖ RECHAZAR ").withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan duelo rechazar"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clic para rechazar el duelo.").withStyle(ChatFormatting.RED))));
        return tag("⚔ " + from.getGameProfile().getName() + " (" + PadClans.clanNameOf(from.getUUID()) + ") te reta a un duelo.", ChatFormatting.YELLOW)
                .append(Component.literal("\n        "))
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GREEN)).append(yes).append(Component.literal("]").withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal("   "))
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_RED)).append(no).append(Component.literal("]").withStyle(ChatFormatting.DARK_RED))
                .append(Component.literal("   " + secs + " s").withStyle(ChatFormatting.GRAY));
    }

    /** (1.3.159) Para la prueba en el juego: from reta a to (como /clan duelo, sin mirar la distancia). */
    public static void testChallenge(ServerPlayer from, ServerPlayer to) {
        CHALLENGES.put(to.getUUID(), Map.entry(from.getUUID(), System.currentTimeMillis()));
        to.sendSystemMessage(challengeMessage(from));
    }

    /** (1.3.159) Para la prueba: ¿hay un duelo en marcha (o en su cuenta atrás) de ese jugador? */
    public static boolean testInDuel(java.util.UUID u) {
        return DUELS.containsKey(u);
    }

    static int accept(CommandSourceStack src) {
        ServerPlayer me = src.getPlayer();
        if (me == null) return 0;
        Map.Entry<UUID, Long> c = CHALLENGES.remove(me.getUUID());
        if (c == null || System.currentTimeMillis() - c.getValue() > CHALLENGE_MS) {
            src.sendFailure(tag("No tienes ningún reto pendiente.", ChatFormatting.RED));
            return 0;
        }
        ServerPlayer from = me.getServer().getPlayerList().getPlayer(c.getKey());
        if (from == null) {
            src.sendFailure(tag("Quien te retó ya no está.", ChatFormatting.RED));
            return 0;
        }
        String why = whyNot(from, me);
        if (why != null) {
            src.sendFailure(tag(why, ChatFormatting.RED));
            return 0;
        }
        start(from, me);
        return 1;
    }

    static int reject(CommandSourceStack src) {
        ServerPlayer me = src.getPlayer();
        if (me == null) return 0;
        Map.Entry<UUID, Long> c = CHALLENGES.remove(me.getUUID());
        if (c == null) {
            src.sendFailure(tag("No tienes ningún reto pendiente.", ChatFormatting.RED));
            return 0;
        }
        ServerPlayer from = me.getServer().getPlayerList().getPlayer(c.getKey());
        if (from != null) from.sendSystemMessage(tag(me.getGameProfile().getName() + " no aceptó el duelo.", ChatFormatting.GRAY));
        src.sendSuccess(() -> tag("Rechazaste el duelo.", ChatFormatting.GRAY), false);
        return 1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // El duelo
    // ---------------------------------------------------------------------------------------------------------------

    static Duel start(ServerPlayer a, ServerPlayer b) {
        Duel d = new Duel(a.getUUID(), b.getUUID(), System.currentTimeMillis());
        DUELS.put(a.getUUID(), d);
        DUELS.put(b.getUUID(), d);
        for (ServerPlayer p : new ServerPlayer[] {a, b}) {
            p.sendSystemMessage(tag("¡Duelo contra " + (p == a ? b : a).getGameProfile().getName() + "! Gana quien deje al otro a 1 corazón.", ChatFormatting.GOLD));
        }
        return d;
    }

    private static void title(ServerPlayer p, String big, String small, ChatFormatting color) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(2, 16, 4));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(big).withStyle(color, ChatFormatting.BOLD)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(small).withStyle(ChatFormatting.GRAY)));
    }

    /** Acaba un duelo: winner null = empate. */
    static void end(MinecraftServer server, Duel d, UUID winner, String why) {
        DUELS.remove(d.a);
        DUELS.remove(d.b);
        d.fighting = false;
        ServerPlayer pa = server.getPlayerList().getPlayer(d.a), pb = server.getPlayerList().getPlayer(d.b);
        if (winner == null) {
            d.result = "empate";
            for (ServerPlayer p : new ServerPlayer[] {pa, pb}) if (p != null) title(p, "EMPATE", why, ChatFormatting.YELLOW);
            return;
        }
        UUID loser = d.other(winner);
        ServerPlayer w = server.getPlayerList().getPlayer(winner), l = server.getPlayerList().getPlayer(loser);
        String wn = PadStats.nameOf(winner), ln = PadStats.nameOf(loser);
        String pair = d.a.compareTo(d.b) < 0 ? d.a + ":" + d.b : d.b + ":" + d.a;
        long now = System.currentTimeMillis();
        Long last = PAIRS.get(pair);
        boolean counts = last == null || now - last > PAIR_MS;
        String clanId = PadClans.clanIdOf(winner);
        JsonObject c = clanId == null ? null : PadClans.clan(clanId);
        if (counts && c != null) {
            PAIRS.put(pair, now);
            PadClanProgress.addPrestige(c, PRESTIGE);
            c.addProperty("duelosGanados", TFJson.num(c, "duelosGanados", 0) + 1);
            PadClans.STORE_IMPL.changed();
        }
        d.result = wn;
        if (w != null) {
            title(w, "¡VICTORIA!", "Contra " + ln + (counts ? " · +" + PRESTIGE + " de prestigio para tu clan" : " · (esta pareja ya contó hoy)"), ChatFormatting.GOLD);
            w.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.6F, 1.0F);
        }
        if (l != null) title(l, "DERROTA", why.isEmpty() ? "Contra " + wn : why, ChatFormatting.RED);
        if (counts && c != null) {
            PadClanProgress.tell(server, c, wn + " ganó un duelo contra " + ln + " (" + PadClans.clanNameOf(loser) + "): +" + PRESTIGE + " de prestigio.",
                    ChatFormatting.GOLD);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || DUELS.isEmpty() && CHALLENGES.isEmpty()) return;
        MinecraftServer server = PadServer.server();
        if (server == null) return;
        long now = System.currentTimeMillis();
        CHALLENGES.values().removeIf(c -> now - c.getValue() > CHALLENGE_MS);
        for (Duel d : new java.util.HashSet<>(DUELS.values())) {
            ServerPlayer a = server.getPlayerList().getPlayer(d.a), b = server.getPlayerList().getPlayer(d.b);
            if (a == null || b == null) {
                end(server, d, a == null ? d.b : d.a, "Se desconectó");
                continue;
            }
            long t = now - d.start;
            if (!d.fighting) {
                // la cuenta atrás: 3, 2, 1, ¡YA!
                int sec = (int) (t / 1000);
                if (sec != d.shown && sec < 3) {
                    d.shown = sec;
                    for (ServerPlayer p : new ServerPlayer[] {a, b}) {
                        {
                            title(p, String.valueOf(3 - sec), "Duelo contra " + (p == a ? b : a).getGameProfile().getName(), ChatFormatting.YELLOW);
                            p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.8F, 1.0F);
                        }
                    }
                }
                if (t >= 3000) {
                    d.fighting = true;
                    d.fromA = a.position();
                    d.fromB = b.position();
                    for (ServerPlayer p : new ServerPlayer[] {a, b}) {
                        title(p, "¡YA!", "Gana quien deje al otro a 1 corazón", ChatFormatting.RED);
                        p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 0.4F, 1.4F);
                    }
                }
                continue;
            }
            if (t > MAX_MS) {
                end(server, d, null, "Se acabó el tiempo (3 min)");
            } else if (a.level() != b.level() || a.distanceTo(b) > FLEE_DIST) {
                // huye el que más se alejó de donde empezó a pelear (o el que cambió de mundo): gana el otro
                double ma = d.fromA == null ? 0 : a.position().distanceTo(d.fromA), mb = d.fromB == null ? 0 : b.position().distanceTo(d.fromB);
                boolean aFled = ma >= mb;
                end(server, d, aFled ? d.b : d.a, "Huiste del duelo");
            }
        }
    }

    /** El daño entre los del duelo: siempre pasa (aunque el PvP esté apagado); al llegar a 1 corazón, se acaba. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (DUELS.isEmpty() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        Duel d = DUELS.get(victim.getUUID());
        if (d == null || !d.fighting) return;
        float after = victim.getHealth() - event.getAmount();
        if (after > 2.0F) return;
        // a 1 corazón: el duelo acaba y nadie muere
        event.setAmount(Math.max(0F, victim.getHealth() - 2.0F));
        UUID by = event.getSource().getEntity() instanceof Player p ? p.getUUID() : null;
        end(victim.getServer(), d, d.other(victim.getUUID()), by != null && by.equals(d.other(victim.getUUID())) ? "" : "Te quedaste a 1 corazón");
    }

    /** Si uno de los del duelo fuera a morir (por lo que sea), se queda a 1 corazón y pierde. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (DUELS.isEmpty() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        Duel d = DUELS.get(victim.getUUID());
        if (d == null) return;
        event.setCanceled(true);
        victim.setHealth(2.0F);
        end(victim.getServer(), d, d.other(victim.getUUID()), "Te quedaste a 1 corazón");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Duel d = DUELS.get(event.getEntity().getUUID());
        if (d != null && event.getEntity().getServer() != null) end(event.getEntity().getServer(), d, d.other(event.getEntity().getUUID()), "Se desconectó");
        CHALLENGES.remove(event.getEntity().getUUID());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Prueba en el juego
    // ---------------------------------------------------------------------------------------------------------------

    /** Para la prueba: por qué no (o null), y el duelo empezado ya peleando. */
    public static String testWhyNot(ServerPlayer a, ServerPlayer b) {
        return whyNot(a, b);
    }

    public static Object testStart(ServerPlayer a, ServerPlayer b, boolean fighting) {
        Duel d = start(a, b);
        d.fighting = fighting;
        return d;
    }

    public static String testResult(Object duel) {
        return duel instanceof Duel d ? d.result : "";
    }

    public static boolean testActive(UUID u) {
        return DUELS.containsKey(u);
    }

    public static void testClear() {
        DUELS.clear();
        CHALLENGES.clear();
        PAIRS.clear();
    }

    static Iterator<Duel> all() {
        return DUELS.values().iterator();
    }
}
