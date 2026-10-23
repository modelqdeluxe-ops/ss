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
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;

/**
 * Las monedas verdes (1.3.38): las que abren el Gachapón. Son un saldo propio de cada jugador en el servidor
 * (&lt;mundo&gt;/tfclient/monedas-verdes.json: jugadores.&lt;uuid&gt;.{verdes, ganadas, gastadas}), aparte de la economía
 * de monedas normales. Solo se ganan jugando, con el TF Pass (gratis): no hay comando para darlas, la web no las vende
 * ni las conoce y no se pueden mandar a otro jugador (las normas de Mojang no dejan vender con dinero premios al azar).
 * El objeto tfclient:fantastic_coin_green es solo su icono, como la Fantastic Coin de oro.
 */
public final class PadGreen {
    static final JsonStore STORE_IMPL = new JsonStore("monedas-verdes.json");
    public static final PadServer.Store STORE = STORE_IMPL;

    private PadGreen() {}

    /** (1.3.125) Menos lo que deba por un reembolso (TFDebt): puede ser negativo. */
    public static long balance(UUID uuid) {
        return raw(uuid) - net.tierrasfantasticas.tfclient.economy.TFDebt.get(PadServer.server(), uuid,
                net.tierrasfantasticas.tfclient.economy.TFDebt.GREEN);
    }

    /** Lo que tiene guardado (sin la deuda). */
    private static long raw(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        return p == null ? 0 : Math.max(0, JsonStore.num(p, "verdes"));
    }

    /** Suma n (y se lo cuenta al pad del jugador para la barra de arriba). Si debe, primero paga la deuda. */
    public static void add(ServerPlayer player, long n) {
        if (n <= 0) return;
        n = net.tierrasfantasticas.tfclient.economy.TFDebt.payFrom(player.getServer(), player.getUUID(),
                net.tierrasfantasticas.tfclient.economy.TFDebt.GREEN, n);
        if (n > 0) {
            JsonObject p = STORE_IMPL.player(player.getUUID());
            p.addProperty("verdes", raw(player.getUUID()) + n);
            p.addProperty("ganadas", JsonStore.num(p, "ganadas") + n);
            STORE_IMPL.changed();
        }
        TFPadNet.sendState(player);
    }

    /**
     * (1.3.125) Reembolso: quita n (también sin estar conectado); lo que no tenga queda como deuda. Devuelve lo que se
     * le quitó ya.
     */
    public static long revoke(net.minecraft.server.MinecraftServer server, UUID uuid, long n) {
        if (n <= 0) return 0;
        long now = Math.min(n, raw(uuid));
        if (now > 0) {
            JsonObject p = STORE_IMPL.player(uuid);
            p.addProperty("verdes", raw(uuid) - now);
            STORE_IMPL.changed();
        }
        net.tierrasfantasticas.tfclient.economy.TFDebt.add(server, uuid, net.tierrasfantasticas.tfclient.economy.TFDebt.GREEN, n - now);
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) TFPadNet.sendState(online);
        return now;
    }

    /** (1.3.125) Debe y tiene verdes guardadas: se cobra lo que se pueda. */
    public static void collectDebt(ServerPlayer player) {
        long debt = net.tierrasfantasticas.tfclient.economy.TFDebt.get(player.getServer(), player.getUUID(),
                net.tierrasfantasticas.tfclient.economy.TFDebt.GREEN);
        long have = raw(player.getUUID());
        if (debt <= 0 || have <= 0) return;
        long pay = Math.min(debt, have);
        STORE_IMPL.player(player.getUUID()).addProperty("verdes", have - pay);
        STORE_IMPL.changed();
        net.tierrasfantasticas.tfclient.economy.TFDebt.payFrom(player.getServer(), player.getUUID(),
                net.tierrasfantasticas.tfclient.economy.TFDebt.GREEN, pay);
        TFPadNet.sendState(player);
    }

    /** Quita n si las tiene; false si no le llegan. */
    public static boolean take(ServerPlayer player, long n) {
        long have = balance(player.getUUID());
        if (n <= 0 || have < n) return false;
        JsonObject p = STORE_IMPL.player(player.getUUID());
        p.addProperty("verdes", raw(player.getUUID()) - n);
        p.addProperty("gastadas", JsonStore.num(p, "gastadas") + n);
        STORE_IMPL.changed();
        TFPadNet.sendState(player);
        return true;
    }
}
