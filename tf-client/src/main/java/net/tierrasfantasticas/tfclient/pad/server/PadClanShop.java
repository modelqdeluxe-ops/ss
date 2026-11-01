/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156) La tienda del clan (pestaña TIENDA): se paga con Fantastic Clan Coins y compran el líder y los colíderes.
 * El dueño (31 oct 2026): «protecciones del clan de 50x50 y 120x120, 2 por clan de cada una; ponlas caras, no
 * regaladas; y no pongas esa moneda inflada: como las del gacha, 1 o 2 en las recompensas». Con ~6 Clan Coins a la
 * semana de las misiones (y 1–2 por nivel o guerra), los precios son de semanas o meses de clan. (1.3.158) Todo sale de
 * config/tfclient/clanes.json («tienda»; el pad de administrador › CLANES › TIENDA), con los precios al doble.
 */
final class PadClanShop {
    /**
     * Una cosa de la tienda (1.3.158: de config/tfclient/clanes.json, «tienda»): su clave, nombre, descripción, icono,
     * precio en Clan Coins, cuántas por clan (0 = sin tope), su tipo (proteccion, plazas, potenciador, objeto, verdes o
     * comando) y el resto de su JSON (piedra, horas, cantidad, objeto, comandos).
     */
    record Offer(String key, String name, String desc, Supplier<ItemStack> icon, long price, int max, String type, JsonObject data) {}

    static List<Offer> OFFERS = List.of();

    private PadClanShop() {}

    /** Lee la tienda de clanes.json (solo las que están a la venta). */
    static void load(JsonArray list) {
        List<Offer> out = new ArrayList<>();
        for (JsonElement e : list) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String key = TFJson.str(o, "id", "").trim();
            if (key.isEmpty() || !TFJson.bool(o, "activa", true)) continue;
            String type = TFJson.str(o, "tipo", "objeto").toLowerCase(Locale.ROOT);
            out.add(new Offer(key, fill(TFJson.str(o, "nombre", key), type, o), fill(TFJson.str(o, "descripcion", ""), type, o), () -> icon(type, o),
                    Math.max(0, TFJson.num(o, "precio", 1)), (int) Math.max(0, TFJson.num(o, "maximo", 0)), type, o.deepCopy()));
        }
        OFFERS = List.copyOf(out);
    }

    /** {horas}, {x} (lo que multiplica el potenciador) y {cantidad} (las plazas de cada compra o las verdes). */
    static String fill(String text, String type, JsonObject o) {
        long amount = type.equals("plazas") ? PadClanConfig.num("miembros", "porCompra") : TFJson.num(o, "cantidad", 1);
        return text.replace("{horas}", String.valueOf(hours(o))).replace("{x}", PadClanConfig.boostText()).replace("{cantidad}", String.valueOf(amount));
    }

    static long hours(JsonObject o) {
        return Math.max(1, TFJson.num(o, "horas", 24));
    }

    /** Lo que se ve (y lo que se da, en las protecciones y los objetos). */
    static ItemStack icon(String type, JsonObject o) {
        if (type.equals("proteccion")) {
            ClaimTier tier = ClaimTier.byId(TFJson.str(o, "piedra", ""));
            if (tier != null) return ClaimBlocks.createTierItem(tier, 1);
        }
        if (type.equals("objeto")) {
            ItemStack s = stored(o);
            if (!s.isEmpty()) return s;
        }
        return new ItemStack(PadClanConfig.item(TFJson.str(o, "icono", "minecraft:chest")));
    }

    /**
     * El objeto de una oferta «objeto»: «objeto» como lo guarda el pad de administrador ({"nbt": …}, con encantamientos y
     * nombre) o "minecraft:diamond 3"; si no hay, el del icono con «cantidad».
     */
    static ItemStack stored(JsonObject o) {
        JsonElement e = o.get("objeto");
        if (e != null && !e.isJsonNull()) {
            ItemStack s = PadItemPicker.stack(e);
            if (!s.isEmpty()) return s;
            TFClient.LOGGER.warn("TF Clanes: el objeto de «{}» en la tienda del clan no se entiende", TFJson.str(o, "id", "?"));
        }
        Item it = PadClanConfig.item(TFJson.str(o, "icono", "minecraft:chest"));
        return new ItemStack(it, (int) Math.max(1, Math.min(64, TFJson.num(o, "cantidad", 1))));
    }

    static Offer offer(String key) {
        for (Offer o : OFFERS) if (o.key().equals(key)) return o;
        return null;
    }

    static int bought(JsonObject c, String key) {
        return (int) TFJson.num(TFJson.obj(c, "compras"), key, 0);
    }

    private static void markBought(JsonObject c, String key) {
        JsonObject b = TFJson.obj(c, "compras");
        b.addProperty(key, bought(c, key) + 1);
        c.add("compras", b);
        PadClans.STORE_IMPL.changed();
    }

    static PadView view(PadView.Builder b, ServerPlayer player, String id, JsonObject c) {
        UUID me = player.getUUID();
        boolean treasurer = PadClanProgress.treasurer(c, me);
        long coins = PadClanProgress.coins(c);
        b.header(treasurer ? "Se paga con las Clan Coins del clan: tienen " + coins + "." : "La tienda la usan el líder y los colíderes.");
        for (Offer o : OFFERS) {
            int n = bought(c, o.key());
            List<String> lines = new ArrayList<>();
            lines.add(o.desc());
            if (o.type().equals("potenciador") && PadClanProgress.boosted(c)) {
                lines.add("Activo: quedan " + PadClanViews.until(TFJson.num(c, "potenciadorHasta", 0) - System.currentTimeMillis()) + ".");
            } else if (o.max() > 0) {
                lines.add("Compradas: " + n + " de " + o.max() + ".");
            }
            boolean full = o.max() > 0 && n >= o.max();
            boolean sure = PadServer.confirming(player, "tienda.comprar:" + o.key());
            PadView.Btn buy = !treasurer ? null : full ? PadView.Btn.off("AGOTADO")
                    : coins < o.price() ? PadView.Btn.off("FALTAN " + (o.price() - coins)) : PadView.Btn.of(sure ? "¿SEGURO?" : "COMPRAR", "tienda.comprar:" + o.key(), PadView.GOLD);
            b.row(new PadView.Row(o.icon().get(), o.name(), full ? 0x7E8CA8 : 0x18265C, lines, -1, PadView.clanCoins(o.price()), buy, null));
        }
        return b.build();
    }

    /** Las acciones «tienda.…». */
    static String action(ServerPlayer player, String id, JsonObject c, String action) {
        if (!action.startsWith("tienda.comprar:") || !PadClanProgress.treasurer(c, player.getUUID())) return null;
        Offer o = offer(action.substring("tienda.comprar:".length()));
        if (o == null) return "tienda";
        if (o.max() > 0 && bought(c, o.key()) >= o.max()) {
            TFPadNet.notice(player, "Ya tienen las " + o.max() + " que se pueden comprar.");
            return "tienda";
        }
        if (PadClanProgress.coins(c) < o.price()) {
            TFPadNet.notice(player, "Cuesta " + o.price() + " Clan Coins y el clan tiene " + PadClanProgress.coins(c) + ".");
            return "tienda";
        }
        if (!PadServer.confirm(player, action)) return "tienda";
        if (!PadClanProgress.takeCoins(c, o.price())) return "tienda";
        markBought(c, o.key());
        give(player, id, c, o);
        PadClans.STORE_IMPL.changed();
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.3F);
        PadClanProgress.tell(player.getServer(), c, PadStats.nameOf(player.getUUID()) + " compró en la tienda del clan: " + o.name() + " (" + o.price()
                + " Clan Coins).", ChatFormatting.AQUA);
        return "tienda";
    }

    /** Lo que da cada tipo de oferta. */
    private static void give(ServerPlayer player, String id, JsonObject c, Offer o) {
        MinecraftServer server = player.getServer();
        switch (o.type()) {
            case "plazas" -> c.addProperty("plazasCompradas", TFJson.num(c, "plazasCompradas", 0) + 1);
            case "potenciador" -> c.addProperty("potenciadorHasta",
                    Math.max(System.currentTimeMillis(), TFJson.num(c, "potenciadorHasta", 0)) + hours(o.data()) * 60L * 60 * 1000);
            case "verdes" -> {
                long n = Math.max(1, TFJson.num(o.data(), "cantidad", 1));
                for (UUID u : PadClans.members(c)) PadGreen.add(server, u, n);
            }
            case "comando" -> {
                JsonArray cmds = o.data().has("comandos") && o.data().get("comandos").isJsonArray() ? o.data().getAsJsonArray("comandos") : new JsonArray();
                if (o.data().has("comando")) cmds.add(TFJson.str(o.data(), "comando", ""));
                for (JsonElement e : cmds) {
                    String cmd = e.isJsonPrimitive() ? e.getAsString().trim() : "";
                    if (cmd.isEmpty()) continue;
                    cmd = cmd.replace("{jugador}", player.getGameProfile().getName()).replace("{clan}", id)
                            .replace("{etiqueta}", TFJson.str(c, "etiqueta", ""));
                    try {
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
                    } catch (RuntimeException ex) {
                        TFClient.LOGGER.warn("TF Clanes: el comando «{}» de la tienda del clan falló", cmd, ex);
                    }
                }
            }
            default -> {
                ItemStack s = o.icon().get();
                if (!player.getInventory().add(s)) player.drop(s, false);
            }
        }
    }

    static String testAct(ServerPlayer player, String action) {
        String id = PadClans.clanIdOf(player.getUUID());
        return id == null ? null : action(player, id, PadClans.clan(id), action);
    }
}
