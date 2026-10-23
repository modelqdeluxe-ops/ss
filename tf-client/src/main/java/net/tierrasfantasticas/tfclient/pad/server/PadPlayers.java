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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFBots;
import net.tierrasfantasticas.tfclient.server.TFRanks;
import net.tierrasfantasticas.tfclient.util.TFChat;

/**
 * Jugadores: los conectados en tarjetas (cabeza, nombre y rango), también los bots. Al pulsar uno (1.3.117, sin su
 * ficha): Invitar al clan (si eres líder u oficial y él no tiene clan), Invitar a la protección (eliges la zona) y
 * Enviar regalo: un objeto de tu inventario o monedas. Si está desconectado, el regalo le llega al entrar. Lo que va
 * ligado a su dueño o se compró en la web no se puede regalar (como en el GTS). Límite diario en pad.json.
 * Pestañas: "" la lista; "f:&lt;uuid&gt;" lo que se le puede hacer; "p:&lt;uuid&gt;" a qué zona; "r:&lt;uuid&gt;" regalar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadPlayers {
    public static final PadServer.App APP = new App();
    static final JsonStore GIFTS = new JsonStore("regalos.json");
    public static final PadServer.Store STORE = GIFTS;

    private static final int TEXT = 0x18265C, MUTED = 0x4A6694;

    private PadPlayers() {}

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            if (tab.length() > 2 && (tab.startsWith("f:") || tab.startsWith("r:") || tab.startsWith("p:"))) {
                UUID who = uuid(tab.substring(2));
                if (who != null && !who.equals(player.getUUID())) {
                    return switch (tab.charAt(0)) {
                        case 'r' -> gift(player, who);
                        case 'p' -> zones(player, who);
                        default -> menu(player, who);
                    };
                }
            }
            // los de verdad y los bots de /tf bots (1.3.113: salen igual, con su cara)
            record Who(UUID id, String name) {}
            List<Who> online = new ArrayList<>();
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                if (!TFBots.isBotPlayer(p)) online.add(new Who(p.getUUID(), p.getGameProfile().getName())); // (los bots, abajo)
            }
            for (TFBots.Bot bot : TFBots.online()) online.add(new Who(bot.id(), bot.name()));
            online.sort(Comparator.comparing((Who p) -> !p.id().equals(player.getUUID()))
                    .thenComparing(Who::name, String.CASE_INSENSITIVE_ORDER));
            PadView.Builder b = PadView.of("jugadores").header(online.size() + (online.size() == 1 ? " conectado" : " conectados")
                    + " · pulsa uno para invitarlo o hacerle un regalo.");
            for (Who p : online) {
                TFRanks.Rank rank = TFRanks.of(p.id());
                boolean me = p.id().equals(player.getUUID());
                int color = rank != null && rank.hex() >= 0 ? rank.hex() : 0x3496FA;
                b.card(PadClans.head(p.id(), p.name()), me ? p.name() + " (tú)" : p.name(), color, rank == null ? "" : rank.name(),
                        me ? "" : "tab:f:" + p.id(), me);
            }
            return b.build();
        }

        private static String name(ServerPlayer viewer, UUID uuid) {
            return nameOf(viewer.getServer(), uuid);
        }

        /**
         * (1.3.117, el dueño: «elimina esa opción de ver la tarjeta del jugador; al tocar el nombre: Invitar al clan (si
         * aplica), Invitar a la protección (elegir la prote, paso siguiente) y Enviar regalo (elegir un objeto de mi
         * inventario)») lo que se le puede hacer a un jugador o a un bot, sin su ficha.
         */
        private PadView menu(ServerPlayer viewer, UUID uuid) {
            String name = name(viewer, uuid);
            boolean online = viewer.getServer().getPlayerList().getPlayer(uuid) != null || TFBots.byId(uuid) != null;
            TFRanks.Rank rank = TFRanks.of(uuid);
            // (1.3.120) la vista dice en qué pantalla está (sin esto, al tocar algo el pad volvía a la lista)
            PadView.Builder b = PadView.of("jugadores").selected("f:" + uuid);
            b.hero(new PadView.Row(PadClans.head(uuid, name), name, TEXT, List.of((rank == null ? "Sin rango" : rank.name())
                    + " · " + (online ? "conectado" : "desconectado")), -1, "", null, null));
            if (PadClans.canInvite(viewer, uuid)) {
                b.row(new PadView.Row(new ItemStack(Items.WHITE_BANNER), "Invitar al clan", TEXT,
                        List.of("A " + PadClans.clanNameOf(viewer.getUUID()) + "."), -1, "", null, null).clickable("clan:" + uuid));
            }
            boolean zones = !net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance().getClaimsOf(viewer.getUUID()).isEmpty();
            b.row(new PadView.Row(new ItemStack(Items.SHIELD), "Invitar a la protección", zones ? TEXT : MUTED,
                    List.of(zones ? "Elige a cuál de tus zonas." : "Aún no tienes ninguna zona."), -1, "", null, null)
                    .clickable(zones ? "tab:p:" + uuid : ""));
            if (PadConfig.on("regalos.activado")) {
                int left = (int) Math.max(0, PadConfig.get("regalos.porDia") - sentToday(viewer.getUUID()));
                b.row(new PadView.Row(new ItemStack(Items.CHEST), "Enviar regalo", TEXT,
                        List.of("Un objeto de tu inventario o monedas · te quedan " + left + " hoy."), -1, "", null, null).clickable("tab:r:" + uuid));
            }
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            return b.build();
        }

        /** Invitar a la protección: a cuál de sus zonas (al pulsarla, ya es miembro). */
        private PadView zones(ServerPlayer viewer, UUID uuid) {
            PadView.Builder b = PadView.of("jugadores").selected("p:" + uuid).header("¿A qué zona invitas a " + name(viewer, uuid) + "?");
            for (PadView.Row r : PadClaims.zonesFor(viewer, uuid, "zona:" + uuid + ":")) b.row(r);
            b.empty("No tienes zonas. Coloca una piedra de protección.");
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            return b.build();
        }

        /**
         * Regalar (1.3.120, el dueño: «el sistema de regalos no me gusta: toco el objeto y me saca; debo tocar el objeto,
         * poner la cantidad que quiero darle de lo que tengo, y al regalar se va de mi inventario»): toca un objeto de tu
         * inventario y escribe cuántos (de todos los que tienes de ese, en cualquier hueco); o, sin elegir nada, monedas.
         */
        private PadView gift(ServerPlayer viewer, UUID uuid) {
            String name = name(viewer, uuid);
            PadView.Builder b = PadView.of("jugadores").selected("r:" + uuid);
            int left = (int) Math.max(0, PadConfig.get("regalos.porDia") - sentToday(viewer.getUUID()));
            ItemStack snap = chosen(viewer);
            if (snap != null) {
                int have = count(viewer, snap);
                b.header("Regalo para " + name + " · te quedan " + left + " hoy.");
                b.hero(new PadView.Row(snap.copyWithCount(1), snap.getHoverName().getString(), TEXT,
                        List.of("Tienes " + have + ". Escribe cuántos le das y pulsa REGALAR."), -1, "", null, null));
                b.input("dar:" + uuid, "CANTIDAD (1-" + have + ")", 6, "REGALAR");
                b.footer(PadView.Btn.of("OTRO OBJETO", "soltar", PadView.BLUE));
                if (have > 1) b.footer(PadView.Btn.of("TODOS (" + have + ")", "dar:" + uuid + ":" + have, PadView.GOLD));
                return b.build();
            }
            b.header("Regalo para " + name + " · toca un objeto o regala monedas · te quedan " + left + " hoy.");
            var items = viewer.getInventory().items;
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty()) continue;
                boolean ok = TFMarket.whyNot(s) == null;
                b.cell(s, ok ? "" : "NO", MUTED, "elegir:" + i, false);
            }
            b.empty("Tu inventario está vacío.");
            b.input("monedas:" + uuid, "MONEDAS", 12, "REGALAR");
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            return b.build();
        }

        /** El objeto elegido para regalar (si aún tiene alguno), o null. */
        private static ItemStack chosen(ServerPlayer player) {
            ItemStack snap = PadServer.get(player, "jugadores.snap", null);
            if (snap == null || snap.isEmpty()) return null;
            if (count(player, snap) <= 0) {
                PadServer.put(player, "jugadores.snap", null);
                return null;
            }
            return snap;
        }

        /** Cuántos de ese objeto (el mismo, con sus datos) tiene en el inventario. */
        private static int count(ServerPlayer player, ItemStack like) {
            int n = 0;
            for (ItemStack s : player.getInventory().items) if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, like)) n += s.getCount();
            return n;
        }

        /** Saca n de ese objeto del inventario y los devuelve en montones de su tamaño máximo. */
        private static List<ItemStack> take(ServerPlayer player, ItemStack like, int n) {
            List<ItemStack> out = new ArrayList<>();
            int left = n;
            var items = player.getInventory().items;
            for (int i = 0; i < items.size() && left > 0; i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, like)) continue;
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
            }
            int max = Math.max(1, like.getMaxStackSize()), given = n - left;
            while (given > 0) {
                int k = Math.min(max, given);
                out.add(like.copyWithCount(k));
                given -= k;
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return out;
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            switch (a[0]) {
                case "volver" -> {
                    PadServer.put(player, "jugadores.snap", null);
                    if (tab.startsWith("r:") || tab.startsWith("p:")) return "f:" + tab.substring(2);
                    return tab.isEmpty() ? null : "";
                }
                case "clan" -> {
                    UUID to = uuid(a.length > 1 ? a[1] : "");
                    if (to != null && PadClans.canInvite(player, to)) PadClans.invite(player, to, nameOf(player.getServer(), to));
                    return null;
                }
                case "zona" -> { // zona:<jugador>:<zona>
                    String[] z = a.length > 1 ? a[1].split(":", 2) : new String[0];
                    UUID to = z.length == 2 ? uuid(z[0]) : null;
                    if (to == null || to.equals(player.getUUID())) return null;
                    String who = nameOf(player.getServer(), to);
                    if (TFBots.byId(to) != null || TFBots.ofId(to) != null) {
                        // un bot no es un jugador de verdad: no entra en los datos de la zona (como quien no contesta)
                        TFPadNet.notice(player, "Invitación a tu zona enviada a " + who + ".");
                    } else {
                        PadClaims.addMember(player, z[1], to, who);
                    }
                    return "f:" + to;
                }
                case "elegir" -> {
                    int slot;
                    try {
                        slot = Integer.parseInt(a[1]);
                    } catch (Exception e) {
                        return null;
                    }
                    if (slot < 0 || slot >= player.getInventory().items.size()) return null;
                    ItemStack s = player.getInventory().items.get(slot);
                    if (s.isEmpty()) return null;
                    String why = TFMarket.whyNot(s);
                    if (why != null) {
                        TFPadNet.notice(player, why.replace("vender", "regalar").replace("GTS", "pad"));
                        return null;
                    }
                    PadServer.put(player, "jugadores.snap", s.copyWithCount(1));
                    return null;
                }
                case "soltar" -> {
                    PadServer.put(player, "jugadores.snap", null);
                    return null;
                }
                case "dar" -> { // dar:<jugador> (la cantidad escrita) o dar:<jugador>:<cantidad>
                    String[] d = a.length > 1 ? a[1].split(":", 2) : new String[0];
                    UUID to = d.length > 0 ? uuid(d[0]) : null;
                    if (to == null || to.equals(player.getUUID()) || !canGift(player)) return null;
                    ItemStack snap = chosen(player);
                    if (snap == null) {
                        TFPadNet.notice(player, "Ese objeto ya no está. Elige otro.");
                        return null;
                    }
                    if (TFMarket.whyNot(snap) != null) return null;
                    int have = count(player, snap), n;
                    try {
                        n = Integer.parseInt((d.length > 1 ? d[1] : text).trim());
                    } catch (Exception e) {
                        TFPadNet.notice(player, "Escribe cuántos (de 1 a " + have + ").");
                        return null;
                    }
                    if (n < 1 || n > have) {
                        TFPadNet.notice(player, "Tienes " + have + ": escribe de 1 a " + have + ".");
                        return null;
                    }
                    List<ItemStack> gift = take(player, snap, n);
                    PadServer.put(player, "jugadores.snap", null);
                    deliver(player, to, gift, 0);
                    return null;
                }
                case "monedas" -> {
                    UUID to = uuid(a.length > 1 ? a[1] : "");
                    if (to == null || to.equals(player.getUUID()) || !canGift(player)) return null;
                    long amount = TFMarket.parsePrice(text);
                    if (amount <= 0) {
                        TFPadNet.notice(player, "Cantidad no válida (500, 5k).");
                        return null;
                    }
                    if (amount > PadConfig.get("monedero.maximo")) {
                        TFPadNet.notice(player, "Como mucho " + TFEconomy.format(PadConfig.get("monedero.maximo")) + " por regalo.");
                        return null;
                    }
                    if (!TFEconomy.take(player, amount)) {
                        TFPadNet.notice(player, "No tienes " + TFEconomy.format(amount) + ".");
                        return null;
                    }
                    deliver(player, to, List.of(), amount);
                    TFPadNet.sendState(player);
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** El nombre de un jugador o bot (conectado, del pad o de la caché de perfiles del servidor). */
    static String nameOf(net.minecraft.server.MinecraftServer server, UUID uuid) {
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        TFBots.Bot bot = TFBots.ofId(uuid);
        if (bot != null) return bot.name();
        String n = PadStats.nameOf(uuid);
        if (!"?".equals(n) || server.getProfileCache() == null) return n;
        return server.getProfileCache().get(uuid).map(com.mojang.authlib.GameProfile::getName).orElse(n);
    }

    private static String today() {
        return LocalDate.now().toString();
    }

    private static long sentToday(UUID uuid) {
        JsonObject p = GIFTS.playerIfAny(uuid);
        if (p == null || !today().equals(JsonStore.str(p, "dia"))) return 0;
        return JsonStore.num(p, "enviados");
    }

    private static boolean canGift(ServerPlayer player) {
        if (!PadConfig.on("regalos.activado")) {
            TFPadNet.notice(player, "Los regalos están desactivados.");
            return false;
        }
        if (sentToday(player.getUUID()) >= PadConfig.get("regalos.porDia")) {
            TFPadNet.notice(player, "Ya hiciste " + PadConfig.get("regalos.porDia") + " regalos hoy. Mañana más.");
            return false;
        }
        return true;
    }

    /**
     * Manda el regalo (objeto o monedas) al buzón de quien lo recibe: lo recoge en el pad, Recompensas › REGALOS (esté
     * conectado o no). Si está conectado, se le avisa por el chat.
     */
    /** Manda el regalo (objetos, en montones, o monedas) al buzón de quien lo recibe; cuenta como un regalo del día. */
    private static void deliver(ServerPlayer from, UUID to, List<ItemStack> items, long coins) {
        JsonObject me = GIFTS.player(from.getUUID());
        if (!today().equals(JsonStore.str(me, "dia"))) {
            me.addProperty("dia", today());
            me.addProperty("enviados", 0);
        }
        me.addProperty("enviados", JsonStore.num(me, "enviados") + 1);
        String fromName = from.getGameProfile().getName();
        int total = 0;
        for (ItemStack st : items) total += st.getCount();
        String what = coins > 0 ? TFEconomy.format(coins)
                : items.isEmpty() ? "nada" : (total > 1 ? total + "× " : "") + items.get(0).getHoverName().getString();
        if (coins > 0) {
            JsonObject g = note(fromName, what);
            g.addProperty("monedas", coins);
            g.addProperty("fecha", System.currentTimeMillis());
            mailbox(to).add(g);
        } else {
            for (ItemStack st : items) {
                JsonObject g = note(fromName, what);
                g.addProperty("objeto", st.save(new CompoundTag()).toString());
                g.addProperty("fecha", System.currentTimeMillis());
                mailbox(to).add(g);
            }
        }
        GIFTS.changed();
        ServerPlayer target = from.getServer().getPlayerList().getPlayer(to);
        if (target != null) receive(target, fromName, what);
        from.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.4F, 1.8F);
        TFPadNet.notice(from, "Regalaste " + what + " a " + nameOf(from.getServer(), to) + ".");
    }

    /** Un regalo en el buzón: quién, qué (texto), el objeto (o vacío) y las monedas (0 si es un objeto). */
    public record Gift(String from, String what, ItemStack item, long coins, long at) {}

    private static JsonArray mailbox(UUID uuid) {
        JsonObject p = GIFTS.player(uuid);
        if (!p.has("buzon") || !p.get("buzon").isJsonArray()) p.add("buzon", new JsonArray());
        return p.getAsJsonArray("buzon");
    }

    /** Los regalos que esperan en el buzón del jugador, del más nuevo al más viejo. */
    public static List<Gift> gifts(UUID uuid) {
        JsonObject p = GIFTS.playerIfAny(uuid);
        List<Gift> out = new ArrayList<>();
        if (p == null || !p.has("buzon") || !p.get("buzon").isJsonArray()) return out;
        for (JsonElement e : p.getAsJsonArray("buzon")) {
            JsonObject g = e.getAsJsonObject();
            ItemStack stack = ItemStack.EMPTY;
            if (g.has("objeto")) {
                try {
                    stack = ItemStack.of(TagParser.parseTag(g.get("objeto").getAsString()));
                } catch (Exception ex) {
                    stack = ItemStack.EMPTY;
                }
            }
            out.add(0, new Gift(JsonStore.str(g, "de"), JsonStore.str(g, "que"), stack, JsonStore.num(g, "monedas"), JsonStore.num(g, "fecha")));
        }
        return out;
    }

    /** Recoge el regalo i (en el orden de {@link #gifts}, del más nuevo) o todos (i &lt; 0). Devuelve cuántos. */
    public static int claimGifts(ServerPlayer player, int i) {
        JsonArray box = mailbox(player.getUUID());
        int n = box.size(), taken = 0;
        JsonArray keep = new JsonArray();
        for (int k = 0; k < n; k++) {
            int shown = n - 1 - k; // gifts() les da la vuelta
            JsonObject g = box.get(k).getAsJsonObject();
            if (i >= 0 && shown != i) {
                keep.add(g);
                continue;
            }
            long coins = JsonStore.num(g, "monedas");
            if (coins > 0) {
                if (!TFEconomy.give(player.getServer(), player.getUUID(), player.getGameProfile().getName(), coins)) {
                    keep.add(g);
                    continue;
                }
            } else if (g.has("objeto")) {
                try {
                    ItemStack stack = ItemStack.of(TagParser.parseTag(g.get("objeto").getAsString()));
                    if (!stack.isEmpty()) PadKits.give(player, stack);
                } catch (Exception ex) {
                    TFClient.LOGGER.error("TF Pad: un regalo para {} no se pudo leer: {}", player.getGameProfile().getName(), ex.getMessage());
                }
            }
            taken++;
        }
        GIFTS.player(player.getUUID()).add("buzon", keep);
        GIFTS.changed();
        if (taken > 0) {
            player.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.MASTER, 0.8F, 1.1F);
            player.containerMenu.broadcastChanges();
            TFPadNet.sendState(player);
        }
        return taken;
    }

    private static JsonObject note(String from, String what) {
        JsonObject g = new JsonObject();
        g.addProperty("de", from);
        g.addProperty("que", what);
        return g;
    }

    private static JsonArray pending(UUID uuid) {
        JsonObject p = GIFTS.player(uuid);
        if (!p.has("pendientes") || !p.get("pendientes").isJsonArray()) p.add("pendientes", new JsonArray());
        return p.getAsJsonArray("pendientes");
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            if (!stack.isEmpty()) player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
    }

    private static void receive(ServerPlayer player, String from, String what) {
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.6F);
        // solo el chat (el aviso del pad salía además encima de la barra con el pad cerrado)
        TFChat.send(player, "Regalo", ChatFormatting.YELLOW, TFChat.hl(from), " te regaló ", TFChat.money(what),
                ". Recógelo en el pad: Recompensas › Regalos.");
        TFPadNet.sendState(player);
    }

    /** Al entrar: lo que quedó pendiente con el sistema de antes pasa al buzón, y se avisa si hay regalos. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || PadServer.server() == null) return;
        JsonObject p = GIFTS.playerIfAny(player.getUUID());
        if (p == null) return;
        if (p.has("pendientes") && p.get("pendientes").isJsonArray()) {
            JsonArray box = mailbox(player.getUUID());
            for (JsonElement e : p.getAsJsonArray("pendientes")) {
                JsonObject g = e.getAsJsonObject();
                if (g.has("objeto")) box.add(g); // las monedas de antes ya se pagaron al mandarlas
            }
            p.remove("pendientes");
            GIFTS.changed();
        }
        int n = gifts(player.getUUID()).size();
        if (n > 0) TFChat.send(player, "Regalo", ChatFormatting.YELLOW, "Tienes " + n + (n == 1 ? " regalo" : " regalos")
                + " esperando en el pad: Recompensas › Regalos.");
    }
}
