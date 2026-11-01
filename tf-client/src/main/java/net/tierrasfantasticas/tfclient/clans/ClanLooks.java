/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.server.PadClans;

/**
 * (1.3.159) Lo que cada TF Client sabe de cada clan para dibujarlo él: su etiqueta, su color, el efecto de la etiqueta
 * (ClanTagFx: degradado, arcoíris, onda) y su insignia, y de qué clan es cada jugador conectado. Con eso se animan la
 * etiqueta encima de la cabeza y en la lista de jugadores, los estandartes del clan (su insignia de ahora: si la cambian,
 * cambia en todos) y el emote del clan.
 * <p>
 * El servidor lo mira cada 2 s y, si algo cambió, lo manda a todos (y entero a quien entra).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class ClanLooks {
    /** Un clan visto desde el cliente. */
    public record Look(String id, String tag, String name, int rgb, String badge, ClanTagFx.Fx fx) {
        /** La imagen de la insignia (tfclient:textures/gui/pad/insignias/&lt;id&gt;.png). */
        public net.minecraft.resources.ResourceLocation badgeTexture() {
            return new net.minecraft.resources.ResourceLocation(TFClient.MOD_ID, "textures/gui/pad/insignias/" + badge + ".png");
        }
    }

    private static String last = "";
    private static boolean dirty;

    /** Lo de cada cliente (lo pone ClanNet al llegar). */
    private static volatile Map<String, Look> clans = Map.of();
    private static volatile Map<UUID, String> players = Map.of();

    private ClanLooks() {}

    /** Que se mande ya (sin esperar a los 2 s). */
    public static void dirty() {
        dirty = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || (!dirty && server.getTickCount() % 40 != 0)) return;
        dirty = false;
        try {
            String now = PadClans.looks(server).toString();
            if (now.equals(last)) return;
            last = now;
            ClanNet.sendLooks(null, now);
        } catch (RuntimeException e) {
            TFClient.LOGGER.warn("TF Clanes: no se pudo mandar cómo se ven los clanes", e);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        try {
            String now = PadClans.looks(p.getServer()).toString();
            last = now;
            ClanNet.sendLooks(null, now);
        } catch (RuntimeException e) {
            TFClient.LOGGER.warn("TF Clanes: no se pudo mandar cómo se ven los clanes", e);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Cliente
    // ---------------------------------------------------------------------------------------------------------------

    /** Llega del servidor (en el hilo del juego). */
    static void apply(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Map<String, Look> cl = new HashMap<>();
            for (var e : o.getAsJsonObject("c").entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                int rgb = c.get("r").getAsInt();
                cl.put(e.getKey(), new Look(e.getKey(), c.get("t").getAsString(), c.has("n") ? c.get("n").getAsString() : "", rgb, c.get("b").getAsString(),
                        ClanTagFx.decode(c.get("f").getAsString(), rgb)));
            }
            Map<UUID, String> pl = new HashMap<>();
            for (var e : o.getAsJsonObject("p").entrySet()) {
                try {
                    pl.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                } catch (IllegalArgumentException ignored) {
                    // se salta
                }
            }
            clans = cl;
            players = pl;
        } catch (RuntimeException e) {
            TFClient.LOGGER.warn("TF Clanes: no se entendió cómo se ven los clanes", e);
        }
    }

    /** (1.3.160) Los clanes con la etiqueta animada (arcoíris u onda): para el chat. */
    public static java.util.List<Look> animated() {
        java.util.List<Look> out = new java.util.ArrayList<>();
        for (Look l : clans.values()) if (l.fx().animated()) out.add(l);
        return out;
    }

    public static Look clan(String id) {
        return id == null ? null : clans.get(id);
    }

    public static Look ofPlayer(UUID uuid) {
        return clan(players.get(uuid));
    }

    /** Para la prueba (o si no hay servidor con TF): pone un clan a mano. */
    public static void putForTest(Look l, UUID member) {
        Map<String, Look> cl = new HashMap<>(clans);
        cl.put(l.id(), l);
        clans = cl;
        if (member != null) {
            Map<UUID, String> pl = new HashMap<>(players);
            pl.put(member, l.id());
            players = pl;
        }
    }
}
