/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Deudas de monedas (1.3.125, el dueño: «al reembolsar debe retirarme las monedas del rango o de la compra; si ya las
 * gasté, será saldo negativo y deberá pagarlo con las monedas que dan las misiones y eso»).
 * <ul>
 *   <li>Al reembolsar (la web manda {@code /tf web monedas|verdes retirar <uuid> <n>}) se quita lo que tenga y lo que
 *   falte queda aquí como deuda: su saldo se ve negativo (TFEconomy.balance, PadGreen.balance) y no puede gastar.</li>
 *   <li>Lo siguiente que gane (misiones, ventas, premios…) paga primero la deuda.</li>
 *   <li>Si le entran monedas por otro camino (otro plugin, /eco give, /pay), cada minuto y al entrar se cobra lo que
 *   tenga hasta saldar la deuda.</li>
 * </ul>
 * Se guarda en &lt;mundo&gt;/tfclient/deudas.json: {uuid: {monedas: n, verdes: n}}. Funciona con la economía que sea (la
 * del TF Client, Vault/EssentialsX o comandos), porque la deuda la lleva el TF Client.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFDebt {
    public static final String COINS = "monedas", GREEN = "verdes";

    private static JsonObject data;
    private static MinecraftServer loaded;
    private static boolean dirty;
    private static int tick;

    private TFDebt() {}

    private static JsonObject data(MinecraftServer server) {
        if (data == null || loaded != server) {
            JsonObject o = server == null ? null : TFJson.read(TFJson.worldFile(server, "deudas.json"));
            data = o == null ? new JsonObject() : o;
            loaded = server;
        }
        return data;
    }

    /** Lo que debe en esa moneda (0 si nada). */
    public static long get(MinecraftServer server, UUID uuid, String currency) {
        JsonObject p = TFJson.obj(data(server), uuid.toString());
        return p == null ? 0 : Math.max(0, TFJson.num(p, currency, 0));
    }

    private static void put(MinecraftServer server, UUID uuid, String currency, long amount) {
        JsonObject all = data(server);
        JsonObject p;
        if (all.has(uuid.toString()) && all.get(uuid.toString()).isJsonObject()) {
            p = all.getAsJsonObject(uuid.toString());
        } else {
            p = new JsonObject();
            all.add(uuid.toString(), p);
        }
        if (amount <= 0) p.remove(currency);
        else p.addProperty(currency, amount);
        if (p.size() == 0) all.remove(uuid.toString());
        dirty = true;
        save(server);
    }

    /** Suma a la deuda. */
    public static void add(MinecraftServer server, UUID uuid, String currency, long amount) {
        if (amount > 0) put(server, uuid, currency, get(server, uuid, currency) + amount);
    }

    /**
     * Lo que se va a ganar paga antes la deuda: devuelve lo que queda para el jugador (y avisa si está conectado).
     */
    public static long payFrom(MinecraftServer server, UUID uuid, String currency, long amount) {
        long debt = get(server, uuid, currency);
        if (debt <= 0 || amount <= 0) return amount;
        long pay = Math.min(debt, amount);
        put(server, uuid, currency, debt - pay);
        tell(server, uuid, pay, debt - pay, currency);
        return amount - pay;
    }

    private static void tell(MinecraftServer server, UUID uuid, long paid, long left, String currency) {
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (p == null) return;
        String what = currency.equals(GREEN) ? " Fantastic Gacha Coins" : " monedas";
        p.sendSystemMessage(Component.literal(left > 0 ? "Pagadas " + paid + what + " de tu deuda (te quedan " + left + " por pagar)."
                : "Deuda saldada: pagadas " + paid + what + ".").withStyle(ChatFormatting.GOLD));
    }

    private static void save(MinecraftServer server) {
        if (!dirty || server == null) return;
        dirty = false;
        TFJson.write(TFJson.worldFile(server, "deudas.json"), data(server));
    }

    /** Quien tenga deuda y saldo (le entraron monedas por otro camino): se cobra lo que se pueda. */
    private static void collect(MinecraftServer server, ServerPlayer player) {
        JsonObject p = TFJson.obj(data(server), player.getUUID().toString());
        if (p == null) return;
        if (TFJson.num(p, COINS, 0) > 0) TFEconomy.collectDebt(server, player);
        if (TFJson.num(p, GREEN, 0) > 0) net.tierrasfantasticas.tfclient.pad.server.PadGreen.collectDebt(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) collect(player.getServer(), player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 1200 != 0) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || data(server).size() == 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) collect(server, player);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        data = null;
        loaded = null;
    }

    /** Para el pad de administrador y /tf web monedas ver: lo que debe cada uno (uuid → texto). */
    public static String describe(MinecraftServer server, UUID uuid) {
        long c = get(server, uuid, COINS), g = get(server, uuid, GREEN);
        if (c <= 0 && g <= 0) return "";
        StringBuilder b = new StringBuilder("debe");
        if (c > 0) b.append(' ').append(c).append(" monedas");
        if (g > 0) b.append(c > 0 ? " y " : " ").append(g).append(" Gacha Coins");
        return b.toString();
    }

    /** Todos los que deben (para limpiar al reiniciar los datos). */
    static Iterable<java.util.Map.Entry<String, JsonElement>> all(MinecraftServer server) {
        return data(server).entrySet();
    }
}
