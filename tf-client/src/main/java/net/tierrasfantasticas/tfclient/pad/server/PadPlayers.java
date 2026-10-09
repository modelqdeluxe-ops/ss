package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
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
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData;
import net.tierrasfantasticas.tfclient.market.TFMarket;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.server.TFRanks;

/**
 * Jugadores: los conectados en tarjetas (cabeza, nombre y rango). Al pulsar uno, su ficha (rango, clan, oficios,
 * misiones, fotos…) y REGALAR: un objeto de tu inventario o monedas. Si está desconectado, el regalo le llega al entrar.
 * Lo que va ligado a su dueño o se compró en la web no se puede regalar (como en el GTS). Límite diario en pad.json.
 * Pestañas: "" la lista; "f:&lt;uuid&gt;" la ficha; "r:&lt;uuid&gt;" regalar.
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
            if (tab.length() > 2 && (tab.startsWith("f:") || tab.startsWith("r:"))) {
                UUID who = uuid(tab.substring(2));
                if (who != null) return tab.startsWith("r:") && !who.equals(player.getUUID()) ? gift(player, who) : profile(player, who);
            }
            List<ServerPlayer> online = new ArrayList<>(player.getServer().getPlayerList().getPlayers());
            online.sort(Comparator.comparing((ServerPlayer p) -> !p.getUUID().equals(player.getUUID()))
                    .thenComparing(p -> p.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
            PadView.Builder b = PadView.of("jugadores").header(online.size() + (online.size() == 1 ? " conectado" : " conectados")
                    + (PadConfig.on("regalos.activado") ? " · pulsa uno para ver su ficha o hacerle un regalo." : " · pulsa uno para ver su ficha."));
            for (ServerPlayer p : online) {
                String name = p.getGameProfile().getName();
                TFRanks.Rank rank = TFRanks.of(p.getUUID());
                boolean me = p.getUUID().equals(player.getUUID());
                int color = rank != null && rank.hex() >= 0 ? rank.hex() : 0x3496FA;
                b.card(PadClans.head(p.getUUID(), name), me ? name + " (tú)" : name, color, rank == null ? "" : rank.name(),
                        "tab:f:" + p.getUUID(), me);
            }
            return b.build();
        }

        private static PadView.Builder tabs(ServerPlayer viewer, UUID uuid, String sel) {
            PadView.Builder b = PadView.of("jugadores").tab("f:" + uuid, "FICHA");
            if (!uuid.equals(viewer.getUUID()) && PadConfig.on("regalos.activado")) b.tab("r:" + uuid, "REGALAR");
            return b.selected(sel + uuid);
        }

        private static String name(ServerPlayer viewer, UUID uuid) {
            ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(uuid);
            return online != null ? online.getGameProfile().getName() : PadStats.nameOf(uuid);
        }

        private PadView profile(ServerPlayer viewer, UUID uuid) {
            String name = name(viewer, uuid);
            boolean online = viewer.getServer().getPlayerList().getPlayer(uuid) != null;
            TFRanks.Rank rank = TFRanks.of(uuid);
            PadView.Builder b = tabs(viewer, uuid, "f:");
            b.row(new PadView.Row(PadClans.head(uuid, name), name, TEXT, List.of((rank == null ? "Sin rango" : rank.name())
                    + " · " + (online ? "conectado" : "desconectado")), -1, "", null, null));
            String clan = PadClans.clanNameOf(uuid);
            b.row(info(new ItemStack(Items.WHITE_BANNER), "Clan", clan.isEmpty() ? "Sin clan" : clan));
            // los tres oficios con más nivel
            var jobs = TFJobsData.get(viewer.getServer()).player(uuid).jobs;
            List<Map.Entry<String, TFJobsData.JobProgress>> top = new ArrayList<>(jobs.entrySet());
            top.sort(Comparator.comparingInt((Map.Entry<String, TFJobsData.JobProgress> e) -> e.getValue().level).reversed());
            StringBuilder js = new StringBuilder();
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                TFJobsConfig.Job job = TFJobsConfig.job(top.get(i).getKey());
                if (js.length() > 0) js.append(" · ");
                js.append(job == null ? top.get(i).getKey() : job.name()).append(" ").append(top.get(i).getValue().level);
            }
            b.row(info(new ItemStack(Items.IRON_PICKAXE), "Oficios", js.length() == 0 ? "Sin oficio" : js.toString()));
            b.row(info(new ItemStack(Items.WRITABLE_BOOK), "Misiones y cazas",
                    PadStats.get(uuid, PadStats.MISSIONS) + " misiones · " + PadStats.get(uuid, PadStats.HUNTS) + " cazas"));
            b.row(info(new ItemStack(Items.PAINTING), "Comunidad",
                    PadStats.get(uuid, PadStats.PHOTOS) + " fotos · " + PadStats.get(uuid, PadStats.LIKES) + " likes recibidos"));
            b.row(info(new ItemStack(Items.SPYGLASS), "Aventura",
                    PadStats.get(uuid, PadStats.EXPLORES) + " exploraciones · " + PadStats.get(uuid, PadStats.TRAVELS) + " viajes"));
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            return b.build();
        }

        private PadView gift(ServerPlayer viewer, UUID uuid) {
            String name = name(viewer, uuid);
            PadView.Builder b = tabs(viewer, uuid, "r:");
            int left = (int) Math.max(0, PadConfig.get("regalos.porDia") - sentToday(viewer.getUUID()));
            Integer slot = PadServer.get(viewer, "jugadores.slot", null);
            ItemStack snap = PadServer.get(viewer, "jugadores.snap", null);
            var items = viewer.getInventory().items;
            if (slot != null && (slot >= items.size() || snap == null || !ItemStack.matches(items.get(slot), snap))) {
                slot = null;
                PadServer.put(viewer, "jugadores.slot", null);
            }
            b.header("Regalo para " + name + " · te quedan " + left + " hoy.");
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty()) continue;
                boolean ok = TFMarket.whyNot(s) == null;
                b.cell(s, ok ? "" : "NO", MUTED, "elegir:" + i, slot != null && slot == i);
            }
            b.empty("Tu inventario está vacío.");
            b.footer(PadView.Btn.of("ATRÁS", "volver", PadView.BLUE));
            if (slot != null) {
                boolean sure = PadServer.confirming(viewer, "regalo:" + uuid);
                b.footer(PadView.Btn.of(sure ? "¿SEGURO?" : "REGALAR " + items.get(slot).getCount(), "regalar:" + uuid, PadView.GOLD));
            } else {
                b.input("monedas:" + uuid, "MONEDAS", 12, "REGALAR");
            }
            return b.build();
        }

        private static PadView.Row info(ItemStack icon, String title, String value) {
            return new PadView.Row(icon, title, MUTED, List.of(value), -1, "", null, null);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            String[] a = action.split(":", 2);
            switch (a[0]) {
                case "volver" -> {
                    PadServer.put(player, "jugadores.slot", null);
                    return tab.isEmpty() ? null : "";
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
                    String why = TFMarket.whyNot(s);
                    if (why != null) {
                        TFPadNet.notice(player, why.replace("vender", "regalar").replace("GTS", "pad"));
                        return null;
                    }
                    Integer cur = PadServer.get(player, "jugadores.slot", null);
                    if (cur != null && cur == slot) {
                        PadServer.put(player, "jugadores.slot", null);
                    } else {
                        PadServer.put(player, "jugadores.slot", slot);
                        PadServer.put(player, "jugadores.snap", s.copy());
                    }
                    return null;
                }
                case "regalar" -> {
                    UUID to = uuid(a.length > 1 ? a[1] : "");
                    if (to == null || to.equals(player.getUUID()) || !canGift(player)) return null;
                    Integer slot = PadServer.get(player, "jugadores.slot", null);
                    ItemStack snap = PadServer.get(player, "jugadores.snap", null);
                    if (slot == null || slot >= player.getInventory().items.size()) return null;
                    ItemStack s = player.getInventory().items.get(slot);
                    if (snap == null || !ItemStack.matches(s, snap) || TFMarket.whyNot(s) != null) {
                        TFPadNet.notice(player, "Ese objeto ya no está. Elige otro.");
                        PadServer.put(player, "jugadores.slot", null);
                        return null;
                    }
                    if (!PadServer.confirm(player, "regalo:" + to)) return null;
                    ItemStack gift = s.copy();
                    player.getInventory().items.set(slot, ItemStack.EMPTY);
                    player.getInventory().setChanged();
                    player.containerMenu.broadcastChanges();
                    PadServer.put(player, "jugadores.slot", null);
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
                    deliver(player, to, ItemStack.EMPTY, amount);
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

    /** Entrega el regalo (objeto o monedas). Si no está conectado, se guarda y le llega al entrar. */
    private static void deliver(ServerPlayer from, UUID to, ItemStack item, long coins) {
        JsonObject me = GIFTS.player(from.getUUID());
        if (!today().equals(JsonStore.str(me, "dia"))) {
            me.addProperty("dia", today());
            me.addProperty("enviados", 0);
        }
        me.addProperty("enviados", JsonStore.num(me, "enviados") + 1);
        String fromName = from.getGameProfile().getName();
        String what = coins > 0 ? TFEconomy.format(coins) : TFMarket.describe(item);
        ServerPlayer target = from.getServer().getPlayerList().getPlayer(to);
        if (coins > 0 && target == null) {
            // las monedas se pueden pagar aunque no esté (la economía lo hace con su nombre)
            TFEconomy.give(from.getServer(), to, PadStats.nameOf(to), coins);
            pending(to).add(note(fromName, what));
        } else if (coins > 0) {
            TFEconomy.give(from.getServer(), to, target.getGameProfile().getName(), coins);
            receive(target, fromName, what);
        } else if (target != null) {
            giveItem(target, item);
            receive(target, fromName, what);
        } else {
            JsonObject g = note(fromName, what);
            g.addProperty("objeto", item.save(new CompoundTag()).toString());
            pending(to).add(g);
        }
        GIFTS.changed();
        from.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.4F, 1.8F);
        TFPadNet.notice(from, "Regalaste " + what + " a " + (target != null ? target.getGameProfile().getName() : PadStats.nameOf(to))
                + (target == null ? ". Le llegará al entrar." : "."));
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
        TFPadNet.notice(player, from + " te regaló " + what + ".");
        player.sendSystemMessage(Component.literal("🎁 " + from + " te regaló " + what + ".").withStyle(ChatFormatting.GOLD));
        TFPadNet.sendState(player);
    }

    /** Al entrar: los regalos que llegaron mientras no estaba. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || PadServer.server() == null) return;
        JsonObject p = GIFTS.playerIfAny(player.getUUID());
        if (p == null || !p.has("pendientes")) return;
        JsonArray list = p.getAsJsonArray("pendientes");
        p.remove("pendientes");
        GIFTS.changed();
        for (JsonElement e : list) {
            JsonObject g = e.getAsJsonObject();
            String from = JsonStore.str(g, "de"), what = JsonStore.str(g, "que");
            if (g.has("objeto")) {
                try {
                    ItemStack stack = ItemStack.of(TagParser.parseTag(g.get("objeto").getAsString()));
                    if (!stack.isEmpty()) giveItem(player, stack);
                } catch (Exception ex) {
                    TFClient.LOGGER.error("TF Pad: un regalo para {} no se pudo leer: {}", player.getGameProfile().getName(), ex.getMessage());
                    continue;
                }
            }
            receive(player, from, what);
        }
    }
}
