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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.156, el dueño: «el banco del clan: un cofre con slots mejorables mediante las clan coins; podrá contener cuanto
 * item quiera comprimido para evitar el lag; es un almacenamiento que solo tiene el nombre del item y la cantidad, como
 * digital nomás; el costo de la mejora es incremental, 5k de items inicial y así de 5k en 5k»)
 * <ul>
 *   <li>Digital: por clan, solo «id del objeto → cantidad» en &lt;mundo&gt;/tfclient/clan_banco.json (aparte de
 *   clanes.json). Los objetos con datos (encantados, con nombre, gastados, cajas llenas…) no entran: se perderían.</li>
 *   <li>Capacidad: 5.000 objetos y +5.000 por mejora, hasta 100.000 (19 mejoras). La mejora n cuesta n + 1 Fantastic
 *   Clan Coins (2, 3, 4 … 20; 209 en total), las paga el líder o un colíder.</li>
 *   <li>Meten todos los miembros; sacan el líder y los colíderes (y los veteranos si el líder lo pone en AJUSTES).</li>
 *   <li>Registro de los últimos 40 movimientos (quién, qué, cuánto).</li>
 *   <li>El cofre (tfclient:banco_clan) abre esta pestaña; lo da el botón COFRE al líder y a los colíderes.</li>
 * </ul>
 */
public final class PadClanBank {
    static final JsonStore STORE_IMPL = new JsonStore("clan_banco.json");
    public static final PadServer.Store STORE = STORE_IMPL;

    /** (1.3.158) De config/tfclient/clanes.json, «banco» ({@link PadClanConfig}); aquí, lo de fábrica. */
    public static long BASE = 5000, STEP = 5000;
    public static int MAX_UPGRADES = 19;
    public static final int LOG = 40;
    private static final Map<UUID, Long> CHEST_GIVEN = new java.util.HashMap<>();

    private PadClanBank() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Datos
    // ---------------------------------------------------------------------------------------------------------------

    static JsonObject bank(String clanId) {
        return STORE_IMPL.obj(STORE_IMPL.obj(STORE_IMPL.root, "bancos"), clanId);
    }

    /** Al disolver un clan: su banco se va con él (y su cofre y sus estandartes dejan de ser de nadie). */
    static void forget(String clanId) {
        STORE_IMPL.obj(STORE_IMPL.root, "bancos").remove(clanId);
        for (String k : new String[] {"cofres", "estandartes"}) STORE_IMPL.obj(STORE_IMPL.root, k).entrySet().removeIf(e -> e.getValue().getAsString().equals(clanId));
        STORE_IMPL.changed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // (1.3.159) El cofre (uno por clan) y los estandartes puestos en el mundo: de qué clan es cada uno
    // ---------------------------------------------------------------------------------------------------------------

    /** «dimensión|x|y|z». */
    public static String posKey(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.dimension().location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    /** El clan del cofre o del estandarte puesto ahí (kind: «cofres» o «estandartes»), o null. */
    public static String ownerAt(String kind, String key) {
        JsonObject m = STORE_IMPL.obj(STORE_IMPL.root, kind);
        return m.has(key) ? m.get(key).getAsString() : null;
    }

    /** Lo apunta al ponerlo (el cofre: también en el banco de su clan, para dar uno solo). */
    public static void placed(String kind, String key, String clanId) {
        STORE_IMPL.obj(STORE_IMPL.root, kind).addProperty(key, clanId);
        if (kind.equals("cofres")) bank(clanId).addProperty("cofre", key);
        STORE_IMPL.changed();
    }

    /** Lo quita al romperlo. */
    public static void removed(String kind, String key) {
        String clan = ownerAt(kind, key);
        STORE_IMPL.obj(STORE_IMPL.root, kind).remove(key);
        if (kind.equals("cofres") && clan != null && key.equals(TFJson.str(bank(clan), "cofre", ""))) bank(clan).remove("cofre");
        STORE_IMPL.changed();
    }

    /** Dónde tiene puesto su cofre ese clan («x, y, z»), o null. */
    public static String chestOf(String clanId) {
        String k = TFJson.str(bank(clanId), "cofre", "");
        if (k.isEmpty() || !clanId.equals(ownerAt("cofres", k))) return null;
        String[] p = k.split("\\|");
        return p.length == 4 ? p[1] + ", " + p[2] + ", " + p[3] : k;
    }

    /** ¿Quién del clan (conectado) lleva un cofre en el inventario? (su nombre, o null) */
    static String carrying(net.minecraft.server.MinecraftServer server, JsonObject c) {
        if (server == null) return null;
        net.minecraft.world.item.Item it = net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANK_ITEM.get();
        for (UUID u : PadClans.members(c)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) continue;
            if (p.getInventory().contains(new ItemStack(it)) || p.getEnderChestInventory().countItem(it) > 0) return p.getGameProfile().getName();
        }
        return null;
    }

    public static int upgrades(JsonObject b) {
        return (int) Math.max(0, Math.min(MAX_UPGRADES, TFJson.num(b, "mejoras", 0)));
    }

    public static long capacity(JsonObject b) {
        return BASE + STEP * upgrades(b);
    }

    /** Lo que cuesta la mejora número next (1 a 19): next + 1 Clan Coins (clanes.json: banco.primeraMejora y banco.subePorMejora). */
    public static long upgradeCost(int next) {
        return PadClanConfig.bankUpgradeCost(next);
    }

    static JsonObject items(JsonObject b) {
        return STORE_IMPL.obj(b, "objetos");
    }

    public static long used(JsonObject b) {
        long n = 0;
        for (Map.Entry<String, JsonElement> e : items(b).entrySet()) n += Math.max(0, e.getValue().getAsLong());
        return n;
    }

    public static long count(JsonObject b, String id) {
        return TFJson.num(items(b), id, 0);
    }

    /** El id con el que se guarda ese objeto, o null si no puede entrar (vacío o con datos). */
    static String key(ItemStack s) {
        if (s == null || s.isEmpty() || s.hasTag()) return null;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id == null ? null : id.toString();
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return it == null ? Items.AIR : it;
    }

    /** ¿Saca del banco? El líder y los colíderes; los veteranos, si el líder lo puso en AJUSTES. */
    static boolean canTake(JsonObject c, UUID uuid) {
        String r = PadClans.role(c, uuid);
        return r.equals("lider") || r.equals("colider") || r.equals("veterano") && TFJson.bool(c, "veteranosSacan", false);
    }

    private static void log(JsonObject b, ServerPlayer p, String verb, String id, long n) {
        JsonArray reg = b.has("registro") && b.get("registro").isJsonArray() ? b.getAsJsonArray("registro") : new JsonArray();
        JsonObject o = new JsonObject();
        o.addProperty("t", System.currentTimeMillis());
        o.addProperty("quien", p.getGameProfile().getName());
        o.addProperty("que", verb);
        o.addProperty("id", id);
        o.addProperty("n", n);
        JsonArray out = new JsonArray();
        out.add(o);
        for (int i = 0; i < reg.size() && out.size() < LOG; i++) out.add(reg.get(i));
        b.add("registro", out);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Meter y sacar
    // ---------------------------------------------------------------------------------------------------------------

    /** Mete todo lo de ese tipo que lleve en el inventario (sin pasarse de la capacidad). Devuelve cuántos. */
    static long deposit(ServerPlayer p, String clanId, String id) {
        JsonObject b = bank(clanId);
        long free = capacity(b) - used(b);
        if (free <= 0) return 0;
        long moved = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size() && moved < free; i++) {
            ItemStack s = inv.items.get(i);
            if (!id.equals(key(s))) continue;
            int n = (int) Math.min(s.getCount(), free - moved);
            s.shrink(n);
            moved += n;
        }
        if (moved > 0) {
            items(b).addProperty(id, count(b, id) + moved);
            log(b, p, "meter", id, moved);
            STORE_IMPL.changed();
            inv.setChanged();
            p.containerMenu.broadcastChanges();
        }
        return moved;
    }

    /** Cuántos de ese objeto le caben en el inventario. */
    static long room(ServerPlayer p, Item item) {
        long room = 0;
        int max = new ItemStack(item).getMaxStackSize();
        for (ItemStack s : p.getInventory().items) {
            if (s.isEmpty()) room += max;
            else if (s.getItem() == item && !s.hasTag()) room += Math.max(0, max - s.getCount());
        }
        return room;
    }

    /** Saca hasta n (lo que quepa en el inventario). Devuelve cuántos. */
    static long withdraw(ServerPlayer p, String clanId, String id, long n) {
        JsonObject b = bank(clanId);
        Item item = item(id);
        if (item == Items.AIR) return 0;
        long give = Math.min(Math.min(n, count(b, id)), room(p, item));
        if (give <= 0) return 0;
        long left = give;
        int max = new ItemStack(item).getMaxStackSize();
        while (left > 0) {
            int k = (int) Math.min(left, max);
            ItemStack s = new ItemStack(item, k);
            if (!p.getInventory().add(s) && !s.isEmpty()) {
                // no debería pasar (se miró el sitio): lo que sobre vuelve al banco
                left -= k - s.getCount();
                give -= s.getCount();
                break;
            }
            left -= k;
        }
        long now = count(b, id) - give;
        if (now <= 0) items(b).remove(id);
        else items(b).addProperty(id, now);
        log(b, p, "sacar", id, give);
        STORE_IMPL.changed();
        p.containerMenu.broadcastChanges();
        return give;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vista (pestaña BANCO de la app de clanes)
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * La pestaña BANCO (1.3.159, el dueño: «la pestaña del banco es demasiado grande, no me deja ver los items ni los que
     * puedo meter»): sin ficha grande arriba (lo del banco va en la cabecera) y en dos vistas que usan todo el sitio:
     * «banco», lo guardado (pulsar uno saca una pila), y «banco_meter» (METER ›), lo que llevas (pulsar uno lo guarda;
     * METER TODO, con doble clic, guarda todo lo que entra). «banco_reg» es el registro.
     */
    static PadView view(PadView.Builder b, ServerPlayer player, String clanId, JsonObject c, String mode) {
        UUID me = player.getUUID();
        JsonObject bank = bank(clanId);
        long used = used(bank), cap = capacity(bank);
        int up = upgrades(bank);
        boolean treasurer = PadClanProgress.treasurer(c, me), take = canTake(c, me);
        int kinds = items(bank).size();
        String status = PadClanViews.fmt(used) + " de " + PadClanViews.fmt(cap) + " objetos (" + Math.round(used * 100.0 / Math.max(1, cap)) + " %) · "
                + kinds + (kinds == 1 ? " tipo" : " tipos");
        b.compact();
        if (mode.equals("banco_reg")) {
            b.header("Los últimos movimientos del banco.");
            JsonArray reg = bank.has("registro") && bank.get("registro").isJsonArray() ? bank.getAsJsonArray("registro") : new JsonArray();
            for (JsonElement e : reg) {
                JsonObject o = e.getAsJsonObject();
                String id = TFJson.str(o, "id", "minecraft:air");
                boolean in = TFJson.str(o, "que", "").equals("meter");
                long n = TFJson.num(o, "n", 0);
                ItemStack icon = new ItemStack(item(id));
                b.row(new PadView.Row(icon, TFJson.str(o, "quien", "?") + (in ? " metió " : " sacó ") + PadClanViews.fmt(n) + " "
                        + net.tierrasfantasticas.tfclient.util.TFNames.es(icon), in ? 0x1E7C2C : 0xB0342C, List.of(ago(TFJson.num(o, "t", 0))), -1,
                        (in ? "+" : "−") + PadView.shortNumber(n), null, null));
            }
            b.empty("Aún no hay movimientos.");
            b.footer(PadView.Btn.of("ATRÁS", "banco.volver", PadView.BLUE));
            return b.build();
        }
        if (mode.equals("banco_meter")) {
            // lo que llevas y se puede guardar, en casillas (pulsar uno guarda todo lo de ese tipo)
            Map<String, Long> mine = new LinkedHashMap<>();
            int skipped = 0;
            for (ItemStack s : player.getInventory().items) {
                if (s.isEmpty()) continue;
                String k = key(s);
                if (k != null && !k.equals("tfclient:banco_clan") && !k.equals("tfclient:estandarte_clan")) mine.merge(k, (long) s.getCount(), Long::sum);
                else if (k == null) skipped++;
            }
            b.header(status + ". Toca lo que quieras guardar (todo lo de ese tipo)." + (skipped > 0 ? " Lo encantado o con nombre no entra." : ""));
            for (Map.Entry<String, Long> e : mine.entrySet()) {
                ItemStack icon = new ItemStack(item(e.getKey()));
                b.cell(icon, "×" + PadView.shortNumber(e.getValue()), 0x1E7C2C, "banco.meter:" + e.getKey(), false);
            }
            b.empty("No llevas nada que se pueda guardar (lo encantado, con nombre o gastado no entra).");
            b.footer(PadView.Btn.of("‹ BANCO", "banco.volver", PadView.BLUE));
            if (!mine.isEmpty()) {
                boolean sure = PadServer.confirming(player, "banco.metertodo");
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "METER TODO", "banco.metertodo", PadView.GREEN));
            }
            return b.build();
        }
        String who = TFJson.bool(c, "veteranosSacan", false) ? "el líder, los colíderes y los veteranos" : "el líder y los colíderes";
        b.header(status + (take ? ". Toca uno para sacar una pila." : ". Sacan " + who + "."));
        // lo guardado, de más a menos
        List<Map.Entry<String, JsonElement>> stored = new ArrayList<>(items(bank).entrySet());
        stored.sort((x, y) -> Long.compare(y.getValue().getAsLong(), x.getValue().getAsLong()));
        for (Map.Entry<String, JsonElement> e : stored) {
            Item it = item(e.getKey());
            if (it == Items.AIR) continue;
            long n = e.getValue().getAsLong();
            // (1.3.158, el dueño: «números repetidos») el objeto sin su número encima: la cantidad va una vez, debajo
            ItemStack icon = new ItemStack(it);
            b.cell(icon, PadView.shortNumber(n), 0x18265C, take ? "banco.sacar:" + e.getKey() : "", false);
        }
        b.empty("El banco está vacío. METER › para guardar lo que llevas.");
        b.footer(PadView.Btn.of("METER ›", "banco.vermeter", PadView.GREEN));
        if (treasurer && up < MAX_UPGRADES) {
            boolean sure = PadServer.confirming(player, "banco.mejorar");
            b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "MEJORAR " + upgradeCost(up + 1) + "¢", "banco.mejorar", PadView.GOLD));
        }
        if (treasurer) b.footer(PadView.Btn.of("COFRE", "banco.cofre", PadView.BLUE));
        b.footer(PadView.Btn.of("REGISTRO", "banco.registro", PadView.BLUE));
        return b.build();
    }

    private static String ago(long t) {
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        if (m < 1) return "Ahora mismo.";
        if (m < 60) return "Hace " + m + " min.";
        long h = m / 60;
        if (h < 24) return "Hace " + h + " h.";
        return "Hace " + (h / 24) + " días.";
    }

    /** Las acciones «banco.…» de la app de clanes. Devuelve la pestaña que hay que dibujar (null = la misma). */
    static String action(ServerPlayer player, String clanId, JsonObject c, String action) {
        UUID me = player.getUUID();
        String verb = action.contains(":") ? action.substring(0, action.indexOf(':')) : action;
        String arg = action.contains(":") ? action.substring(action.indexOf(':') + 1) : "";
        switch (verb) {
            case "banco.registro" -> {
                return "banco_reg";
            }
            case "banco.volver" -> {
                return "banco";
            }
            case "banco.vermeter" -> {
                return "banco_meter";
            }
            case "banco.meter" -> {
                long n = deposit(player, clanId, arg);
                JsonObject bank = bank(clanId);
                if (n <= 0) {
                    TFPadNet.notice(player, used(bank) >= capacity(bank) ? "El banco está lleno: mejóralo con Clan Coins." : "No llevas de eso (o tiene datos y no entra).");
                } else {
                    player.playNotifySound(SoundEvents.CHEST_CLOSE, SoundSource.MASTER, 0.5F, 1.2F);
                    TFPadNet.notice(player, "Metiste " + PadClanViews.fmt(n) + " en el banco del clan.");
                }
                return "banco_meter";
            }
            case "banco.metertodo" -> {
                if (!PadServer.confirm(player, "banco.metertodo")) return "banco_meter";
                java.util.Set<String> keys = new java.util.LinkedHashSet<>();
                for (ItemStack s : player.getInventory().items) {
                    String k = key(s);
                    if (k != null && !k.equals("tfclient:banco_clan") && !k.equals("tfclient:estandarte_clan")) keys.add(k);
                }
                long total = 0;
                for (String k : keys) total += Math.max(0, deposit(player, clanId, k));
                JsonObject bank = bank(clanId);
                if (total > 0) player.playNotifySound(SoundEvents.CHEST_CLOSE, SoundSource.MASTER, 0.5F, 1.2F);
                TFPadNet.notice(player, total > 0 ? "Metiste " + PadClanViews.fmt(total) + " en el banco del clan." + (used(bank) >= capacity(bank) ? " Está lleno." : "")
                        : used(bank) >= capacity(bank) ? "El banco está lleno: mejóralo con Clan Coins." : "No llevas nada que entre.");
                return "banco_meter";
            }
            case "banco.sacar" -> {
                if (!canTake(c, me)) {
                    TFPadNet.notice(player, "Solo sacan del banco el líder y los colíderes" + (TFJson.bool(c, "veteranosSacan", false) ? " y los veteranos." : "."));
                    return "banco";
                }
                Item it = item(arg);
                long n = withdraw(player, clanId, arg, new ItemStack(it).getMaxStackSize());
                if (n <= 0) TFPadNet.notice(player, "No te cabe en el inventario.");
                else {
                    player.playNotifySound(SoundEvents.CHEST_OPEN, SoundSource.MASTER, 0.5F, 1.2F);
                    TFPadNet.notice(player, "Sacaste " + PadClanViews.fmt(n) + " del banco del clan.");
                }
                return "banco";
            }
            case "banco.mejorar" -> {
                if (!PadClanProgress.treasurer(c, me)) return "banco";
                JsonObject bank = bank(clanId);
                int up = upgrades(bank);
                if (up >= MAX_UPGRADES) return "banco";
                long cost = upgradeCost(up + 1);
                if (PadClanProgress.coins(c) < cost) {
                    TFPadNet.notice(player, "La mejora cuesta " + cost + " Clan Coins y el clan tiene " + PadClanProgress.coins(c) + ".");
                    return "banco";
                }
                if (!PadServer.confirm(player, "banco.mejorar")) return "banco";
                if (!PadClanProgress.takeCoins(c, cost)) return "banco";
                bank.addProperty("mejoras", up + 1);
                STORE_IMPL.changed();
                PadClanProgress.tell(player.getServer(), c, PadStats.nameOf(me) + " mejoró el banco del clan: ahora caben " + PadClanViews.fmt(capacity(bank))
                        + " objetos.", net.minecraft.ChatFormatting.AQUA);
                return "banco";
            }
            case "banco.cofre" -> {
                // (1.3.159, el dueño: «que solo te dé 1 el pad nada más») un cofre por clan: si ya está puesto o alguien
                // del clan lo lleva, no se da otro (para moverlo: el líder o un colíder lo rompe y le vuelve al inventario)
                if (!PadClanProgress.treasurer(c, me)) return "banco";
                String at = chestOf(clanId);
                if (at != null) {
                    TFPadNet.notice(player, "Tu clan ya tiene su cofre en " + at + ". Para moverlo, rómpelo (vuelve a tu inventario).");
                    return "banco";
                }
                String who = carrying(player.getServer(), c);
                if (who != null) {
                    TFPadNet.notice(player, who.equals(player.getGameProfile().getName()) ? "Ya llevas el cofre del clan: ponlo donde quieras."
                            : who + " ya lleva el cofre del clan.");
                    return "banco";
                }
                Long given = CHEST_GIVEN.get(me);
                if (given != null && System.currentTimeMillis() - given < 30_000) {
                    TFPadNet.notice(player, "Espera un poco para otro cofre.");
                    return "banco";
                }
                CHEST_GIVEN.put(me, System.currentTimeMillis());
                ItemStack s = new ItemStack(net.tierrasfantasticas.tfclient.clans.ClanBlocks.BANK_ITEM.get());
                if (!player.getInventory().add(s)) player.drop(s, false);
                TFPadNet.notice(player, "Ahí tienes el cofre del clan (uno por clan): cualquiera del clan lo abre y ve el banco.");
                return "banco";
            }
            default -> {
                return null;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Prueba en el juego
    // ---------------------------------------------------------------------------------------------------------------

    /** Para la prueba: deja el banco del clan vacío y sin mejoras. */
    public static void testReset(String clanId) {
        STORE_IMPL.obj(STORE_IMPL.root, "bancos").remove(clanId);
        STORE_IMPL.changed();
    }

    public static String testAct(ServerPlayer player, String action) {
        String id = PadClans.clanIdOf(player.getUUID());
        return id == null ? null : action(player, id, PadClans.clan(id), action);
    }

    public static long testCount(String clanId, String itemId) {
        return count(bank(clanId), itemId);
    }

    public static long testCapacity(String clanId) {
        return capacity(bank(clanId));
    }
}
