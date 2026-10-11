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

    public static long balance(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        return p == null ? 0 : Math.max(0, JsonStore.num(p, "verdes"));
    }

    /** Suma n (y se lo cuenta al pad del jugador para la barra de arriba). */
    public static void add(ServerPlayer player, long n) {
        if (n <= 0) return;
        JsonObject p = STORE_IMPL.player(player.getUUID());
        p.addProperty("verdes", balance(player.getUUID()) + n);
        p.addProperty("ganadas", JsonStore.num(p, "ganadas") + n);
        STORE_IMPL.changed();
        TFPadNet.sendState(player);
    }

    /** Quita n si las tiene; false si no le llegan. */
    public static boolean take(ServerPlayer player, long n) {
        long have = balance(player.getUUID());
        if (n <= 0 || have < n) return false;
        JsonObject p = STORE_IMPL.player(player.getUUID());
        p.addProperty("verdes", have - n);
        p.addProperty("gastadas", JsonStore.num(p, "gastadas") + n);
        STORE_IMPL.changed();
        TFPadNet.sendState(player);
        return true;
    }
}
