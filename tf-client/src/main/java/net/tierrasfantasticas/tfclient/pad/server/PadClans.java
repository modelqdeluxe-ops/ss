package net.tierrasfantasticas.tfclient.pad.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Clanes: fundar uno (nombre y etiqueta), invitar, aceptar, roles (líder, oficial, miembro), echar, ascender, salir y
 * disolver; color de la etiqueta y fuego amigo. La etiqueta sale junto al nombre del jugador (PadTitles.onName).
 * Datos en &lt;mundo&gt;/tfclient/clanes.json.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadClans {
    static final int MAX_MEMBERS = 20;
    private static final String[] COLORS = {"gold", "aqua", "green", "light_purple", "red", "yellow", "blue", "white"};
    private static final String[] COLOR_NAMES = {"Oro", "Celeste", "Verde", "Rosa", "Rojo", "Amarillo", "Azul", "Blanco"};

    static final JsonStore STORE_IMPL = new JsonStore("clanes.json");
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.App APP = new App();

    /** Invitaciones: jugador → clanes que lo invitaron (se pierden al reiniciar, como debe ser). */
    private static final Map<UUID, Set<String>> INVITES = new HashMap<>();
    /** Nombre elegido mientras se pide la etiqueta. */
    private static final Map<UUID, String> CREATING = new HashMap<>();
    /** Doble clic para disolver, salir o pasar el clan: guarda qué acción espera confirmación y desde cuándo. */
    private static final Map<UUID, Map.Entry<String, Long>> CONFIRM = new HashMap<>();

    private PadClans() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Datos
    // ---------------------------------------------------------------------------------------------------------------

    static JsonObject clans() {
        return STORE_IMPL.obj(STORE_IMPL.root, "clanes");
    }

    static JsonObject clan(String id) {
        JsonElement e = clans().get(id);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    static String clanIdOf(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        String id = p == null ? "" : TFJson.str(p, "clan", "");
        return !id.isEmpty() && clan(id) != null ? id : null;
    }

    /** «[TAG]» con su color, o null si no tiene clan. */
    public static Component tagOf(UUID uuid) {
        String id = clanIdOf(uuid);
        if (id == null) return null;
        JsonObject c = clan(id);
        ChatFormatting color = ChatFormatting.getByName(TFJson.str(c, "color", "gold"));
        return Component.literal("[" + TFJson.str(c, "etiqueta", "?") + "]").withStyle(color == null ? ChatFormatting.GOLD : color);
    }

    public static String clanNameOf(UUID uuid) {
        String id = clanIdOf(uuid);
        if (id == null) return "";
        JsonObject c = clan(id);
        return TFJson.str(c, "nombre", "") + " [" + TFJson.str(c, "etiqueta", "") + "]";
    }

    static List<UUID> members(JsonObject c) {
        List<UUID> out = new ArrayList<>();
        if (c.has("miembros")) for (JsonElement e : c.getAsJsonArray("miembros")) out.add(UUID.fromString(e.getAsString()));
        return out;
    }

    static String role(JsonObject c, UUID uuid) {
        if (TFJson.str(c, "lider", "").equals(uuid.toString())) return "lider";
        if (c.has("oficiales")) for (JsonElement e : c.getAsJsonArray("oficiales")) if (e.getAsString().equals(uuid.toString())) return "oficial";
        return "miembro";
    }

    private static void setMembers(JsonObject c, List<UUID> list) {
        JsonArray a = new JsonArray();
        for (UUID u : list) a.add(u.toString());
        c.add("miembros", a);
    }

    private static void setOfficer(JsonObject c, UUID uuid, boolean on) {
        JsonArray a = c.has("oficiales") ? c.getAsJsonArray("oficiales") : new JsonArray();
        JsonArray out = new JsonArray();
        for (JsonElement e : a) if (!e.getAsString().equals(uuid.toString())) out.add(e);
        if (on) out.add(uuid.toString());
        c.add("oficiales", out);
    }

    private static void join(UUID uuid, String id) {
        STORE_IMPL.player(uuid).addProperty("clan", id);
        STORE_IMPL.changed();
    }

    private static void leave(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        if (p != null) p.remove("clan");
        STORE_IMPL.changed();
    }

    private static void refreshName(UUID uuid) {
        var server = PadServer.server();
        if (server == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) {
            p.refreshDisplayName();
            p.refreshTabListName();
        }
    }

    private static void tellClan(JsonObject c, String text) {
        var server = PadServer.server();
        if (server == null) return;
        for (UUID u : members(c)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(Component.literal("[Clan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
        }
    }

    static ItemStack head(UUID uuid, String name) {
        ItemStack s = new ItemStack(Items.PLAYER_HEAD);
        CompoundTag owner = new CompoundTag();
        owner.putUUID("Id", uuid);
        owner.putString("Name", name);
        s.getOrCreateTag().put("SkullOwner", owner);
        return s;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Vista
    // ---------------------------------------------------------------------------------------------------------------

    static final class App implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            PadStats.name(player);
            String id = clanIdOf(player.getUUID());
            return id == null ? noClan(player) : inClan(player, id, tab);
        }

        private PadView noClan(ServerPlayer player) {
            UUID me = player.getUUID();
            String creating = CREATING.get(me);
            PadView.Builder b = PadView.of("clanes");
            if (creating != null) {
                b.header("Fundando «" + creating + "». Ahora la etiqueta: 2 a 4 letras o números (sale junto a tu nombre).");
                b.input("etiqueta", "Etiqueta, por ejemplo TF", 4, "FUNDAR");
                b.footer(PadView.Btn.of("CANCELAR", "cancelar", PadView.RED));
                return b.build();
            }
            b.header("Sin clan.");
            for (String cid : INVITES.getOrDefault(me, Set.of())) {
                JsonObject c = clan(cid);
                if (c == null) continue;
                b.row(new PadView.Row(new ItemStack(Items.WHITE_BANNER), TFJson.str(c, "nombre", "?") + " [" + TFJson.str(c, "etiqueta", "?") + "]",
                        0x1E7C2C, List.of("Te invitó a unirte · " + members(c).size() + " miembros"), -1, "",
                        PadView.Btn.of("UNIRSE", "unirse:" + cid, PadView.GREEN), PadView.Btn.of("NO", "rechazar:" + cid, PadView.RED)));
            }
            List<Map.Entry<String, JsonElement>> all = new ArrayList<>(clans().entrySet());
            all.sort(Comparator.comparingInt((Map.Entry<String, JsonElement> e) -> members(e.getValue().getAsJsonObject()).size()).reversed());
            for (int i = 0; i < Math.min(8, all.size()); i++) {
                JsonObject c = all.get(i).getValue().getAsJsonObject();
                b.row(new PadView.Row(new ItemStack(Items.WHITE_BANNER), TFJson.str(c, "nombre", "?") + " [" + TFJson.str(c, "etiqueta", "?") + "]",
                        0x18265C, List.of(members(c).size() + " miembros · líder " + PadStats.nameOf(UUID.fromString(TFJson.str(c, "lider", new UUID(0, 0).toString())))),
                        -1, "", null, null));
            }
            b.empty("No hay clanes.");
            b.input("crear", "Nombre de tu clan (3 a 20 letras)", 20, "FUNDAR");
            return b.build();
        }

        private PadView inClan(ServerPlayer player, String id, String tab) {
            JsonObject c = clan(id);
            UUID me = player.getUUID();
            String myRole = role(c, me);
            boolean boss = myRole.equals("lider"), officer = boss || myRole.equals("oficial");
            List<UUID> list = members(c);
            PadView.Builder b = PadView.of("clanes").tab("miembros", "MIEMBROS");
            if (boss) b.tab("ajustes", "AJUSTES");
            boolean settings = boss && tab.equals("ajustes");
            b.selected(settings ? "ajustes" : "miembros");
            b.header(TFJson.str(c, "nombre", "?") + " [" + TFJson.str(c, "etiqueta", "?") + "] · " + list.size() + "/" + MAX_MEMBERS
                    + " miembros · tú: " + roleName(myRole));
            if (settings) {
                int ci = colorIndex(TFJson.str(c, "color", "gold"));
                b.row(new PadView.Row(new ItemStack(Items.ORANGE_DYE), "Color de la etiqueta", 0x18265C, List.of("Ahora: " + COLOR_NAMES[ci]), -1, "",
                        PadView.Btn.of("CAMBIAR", "color", PadView.BLUE), null));
                boolean ff = TFJson.bool(c, "fuegoAmigo", false);
                b.row(new PadView.Row(new ItemStack(Items.IRON_SWORD), "Fuego amigo", 0x18265C,
                        List.of(ff ? "Los del clan se pueden hacer daño." : "Los del clan no se hacen daño entre ellos."), -1, "",
                        PadView.Btn.of(ff ? "SÍ" : "NO", "fuego", ff ? PadView.RED : PadView.GREEN), null));
                boolean sure = confirming(me, "disolver");
                b.footer(PadView.Btn.of(sure ? "¿SEGURO? DISOLVER" : "DISOLVER CLAN", "disolver", PadView.RED));
                return b.build();
            }
            var server = player.getServer();
            list.sort(Comparator.comparingInt((UUID u) -> roleOrder(role(c, u))).thenComparing(PadStats::nameOf, String.CASE_INSENSITIVE_ORDER));
            for (UUID u : list) {
                String name = PadStats.nameOf(u);
                String r = role(c, u);
                boolean online = server.getPlayerList().getPlayer(u) != null;
                PadView.Btn b1 = null, b2 = null;
                if (!u.equals(me)) {
                    if (boss && r.equals("oficial")) {
                        // a un oficial se le puede pasar el clan (doble clic) o bajarlo a miembro
                        b1 = PadView.Btn.of(confirming(me, "lider:" + u) ? "¿SEGURO?" : "LÍDER", "lider:" + u, PadView.GOLD);
                        b2 = PadView.Btn.of("BAJAR", "bajar:" + u, PadView.BLUE);
                    } else if (r.equals("miembro")) {
                        if (boss) b1 = PadView.Btn.of("ASCENDER", "subir:" + u, PadView.BLUE);
                        if (officer) b2 = PadView.Btn.of("ECHAR", "echar:" + u, PadView.RED);
                    }
                }
                b.row(new PadView.Row(head(u, name), name, online ? 0x1E7C2C : 0x18265C,
                        List.of(roleName(r) + (online ? " · conectado" : "")), -1, "", b1, b2));
            }
            if (officer) b.input("invitar", "Nombre para invitar", 16, "INVITAR");
            if (!boss) b.footer(PadView.Btn.of(confirming(me, "salir") ? "¿SEGURO?" : "SALIR", "salir", PadView.RED));
            return b.build();
        }

        // -----------------------------------------------------------------------------------------------------------

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            UUID me = player.getUUID();
            String id = clanIdOf(me);
            if (id == null) return noClanAction(player, action, text);
            JsonObject c = clan(id);
            String myRole = role(c, me);
            boolean boss = myRole.equals("lider"), officer = boss || myRole.equals("oficial");
            int colon = action.indexOf(':');
            String verb = colon < 0 ? action : action.substring(0, colon);
            UUID target = null;
            if (colon > 0) {
                try {
                    target = UUID.fromString(action.substring(colon + 1));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            List<UUID> list = members(c);
            switch (verb) {
                case "invitar" -> {
                    if (!officer) return null;
                    if (list.size() >= MAX_MEMBERS) {
                        TFPadNet.notice(player, "El clan está lleno (" + MAX_MEMBERS + " miembros).");
                        return null;
                    }
                    ServerPlayer other = player.getServer().getPlayerList().getPlayerByName(text.trim());
                    if (other == null) {
                        TFPadNet.notice(player, "No hay nadie conectado con ese nombre.");
                        return null;
                    }
                    if (clanIdOf(other.getUUID()) != null) {
                        TFPadNet.notice(player, other.getGameProfile().getName() + " ya está en un clan.");
                        return null;
                    }
                    INVITES.computeIfAbsent(other.getUUID(), k -> new HashSet<>()).add(id);
                    other.sendSystemMessage(Component.literal("[Clan] ").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(player.getGameProfile().getName() + " te invita a " + TFJson.str(c, "nombre", "?")
                                    + ". Abre el pad (C) → Clanes para unirte.").withStyle(ChatFormatting.YELLOW)));
                    other.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.6F, 1.2F);
                    TFPadNet.notice(player, "Invitación enviada a " + other.getGameProfile().getName() + ".");
                }
                case "subir", "bajar" -> {
                    if (!boss || target == null || !list.contains(target)) return null;
                    setOfficer(c, target, verb.equals("subir"));
                    STORE_IMPL.changed();
                    tellClan(c, PadStats.nameOf(target) + (verb.equals("subir") ? " ahora es oficial." : " vuelve a ser miembro."));
                }
                case "echar" -> {
                    if (target == null || !list.contains(target) || target.equals(me)) return null;
                    String r = role(c, target);
                    if (!officer || !r.equals("miembro")) return null;
                    list.remove(target);
                    setMembers(c, list);
                    setOfficer(c, target, false);
                    leave(target);
                    refreshName(target);
                    tellClan(c, PadStats.nameOf(target) + " ya no está en el clan.");
                    ServerPlayer t = player.getServer().getPlayerList().getPlayer(target);
                    if (t != null) t.sendSystemMessage(Component.literal("[Clan] Te echaron de " + TFJson.str(c, "nombre", "?") + ".").withStyle(ChatFormatting.RED));
                }
                case "lider" -> {
                    if (!boss || target == null || !list.contains(target) || !role(c, target).equals("oficial")) return null;
                    if (!confirm(me, action)) return null;
                    c.addProperty("lider", target.toString());
                    setOfficer(c, target, false);
                    setOfficer(c, me, true);
                    STORE_IMPL.changed();
                    tellClan(c, PadStats.nameOf(target) + " es el nuevo líder del clan.");
                }
                case "color" -> {
                    if (!boss) return null;
                    int i = (colorIndex(TFJson.str(c, "color", "gold")) + 1) % COLORS.length;
                    c.addProperty("color", COLORS[i]);
                    STORE_IMPL.changed();
                    for (UUID u : list) refreshName(u);
                }
                case "fuego" -> {
                    if (!boss) return null;
                    c.addProperty("fuegoAmigo", !TFJson.bool(c, "fuegoAmigo", false));
                    STORE_IMPL.changed();
                }
                case "disolver" -> {
                    if (!boss) return null;
                    if (!confirm(me, "disolver")) return null;
                    tellClan(c, "El clan " + TFJson.str(c, "nombre", "?") + " se disolvió.");
                    for (UUID u : list) {
                        leave(u);
                        refreshName(u);
                    }
                    clans().remove(id);
                    STORE_IMPL.changed();
                    return "";
                }
                case "salir" -> {
                    if (boss) {
                        TFPadNet.notice(player, "Eres el líder: asciende a otro y pásale el clan, o disuélvelo en Ajustes.");
                        return null;
                    }
                    if (!confirm(me, "salir")) return null;
                    list.remove(me);
                    setMembers(c, list);
                    setOfficer(c, me, false);
                    leave(me);
                    refreshName(me);
                    tellClan(c, player.getGameProfile().getName() + " salió del clan.");
                    return "";
                }
                default -> {
                }
            }
            return null;
        }

        private String noClanAction(ServerPlayer player, String action, String text) {
            UUID me = player.getUUID();
            switch (action.contains(":") ? action.substring(0, action.indexOf(':')) : action) {
                case "crear" -> {
                    String name = text.trim().replaceAll("\\s+", " ");
                    if (name.length() < 3 || name.length() > 20 || !name.matches("[\\p{L}\\p{N} ]+")) {
                        TFPadNet.notice(player, "El nombre: de 3 a 20 letras, números o espacios.");
                        return null;
                    }
                    for (JsonElement e : clans().entrySet().stream().map(Map.Entry::getValue).toList()) {
                        if (TFJson.str(e.getAsJsonObject(), "nombre", "").equalsIgnoreCase(name)) {
                            TFPadNet.notice(player, "Ya hay un clan con ese nombre.");
                            return null;
                        }
                    }
                    CREATING.put(me, name);
                }
                case "cancelar" -> CREATING.remove(me);
                case "etiqueta" -> {
                    String name = CREATING.get(me);
                    String tag = text.trim().toUpperCase(java.util.Locale.ROOT);
                    if (name == null) return null;
                    if (!tag.matches("[A-Z0-9]{2,4}")) {
                        TFPadNet.notice(player, "La etiqueta: de 2 a 4 letras o números, sin tildes.");
                        return null;
                    }
                    for (JsonElement e : clans().entrySet().stream().map(Map.Entry::getValue).toList()) {
                        if (TFJson.str(e.getAsJsonObject(), "etiqueta", "").equals(tag)) {
                            TFPadNet.notice(player, "Ya hay un clan con la etiqueta " + tag + ".");
                            return null;
                        }
                        if (TFJson.str(e.getAsJsonObject(), "nombre", "").equalsIgnoreCase(name)) {
                            CREATING.remove(me);
                            TFPadNet.notice(player, "Ya hay un clan con ese nombre.");
                            return null;
                        }
                    }
                    String id = UUID.randomUUID().toString().substring(0, 8);
                    JsonObject c = new JsonObject();
                    c.addProperty("nombre", name);
                    c.addProperty("etiqueta", tag);
                    c.addProperty("color", "gold");
                    c.addProperty("lider", me.toString());
                    c.addProperty("creado", System.currentTimeMillis());
                    c.addProperty("fuegoAmigo", false);
                    setMembers(c, new ArrayList<>(List.of(me)));
                    clans().add(id, c);
                    join(me, id);
                    CREATING.remove(me);
                    INVITES.remove(me);
                    refreshName(me);
                    PadStats.add(player, PadStats.CLAN_FOUNDED, 1);
                    player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.0F);
                    TFPadNet.notice(player, "Clan " + name + " [" + tag + "] fundado.");
                }
                case "unirse" -> {
                    String cid = action.substring(7);
                    JsonObject c = clan(cid);
                    if (c == null || !INVITES.getOrDefault(me, Set.of()).contains(cid)) return null;
                    List<UUID> list = members(c);
                    if (list.size() >= MAX_MEMBERS) {
                        TFPadNet.notice(player, "Ese clan ya está lleno.");
                        return null;
                    }
                    list.add(me);
                    setMembers(c, list);
                    join(me, cid);
                    INVITES.remove(me);
                    refreshName(me);
                    tellClan(c, player.getGameProfile().getName() + " se unió al clan.");
                }
                case "rechazar" -> {
                    Set<String> inv = INVITES.get(me);
                    if (inv != null) inv.remove(action.substring(9));
                }
                default -> {
                }
            }
            return null;
        }
    }

    private static boolean confirming(UUID uuid, String action) {
        Map.Entry<String, Long> at = CONFIRM.get(uuid);
        return at != null && at.getKey().equals(action) && System.currentTimeMillis() - at.getValue() < 6000;
    }

    /** Primer clic: pide confirmar; segundo clic en la misma acción (en 6 s): sí. */
    private static boolean confirm(UUID uuid, String action) {
        if (confirming(uuid, action)) {
            CONFIRM.remove(uuid);
            return true;
        }
        CONFIRM.put(uuid, Map.entry(action, System.currentTimeMillis()));
        return false;
    }

    private static int colorIndex(String c) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(c)) return i;
        return 0;
    }

    private static int roleOrder(String r) {
        return r.equals("lider") ? 0 : r.equals("oficial") ? 1 : 2;
    }

    private static String roleName(String r) {
        return r.equals("lider") ? "Líder" : r.equals("oficial") ? "Oficial" : "Miembro";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fuego amigo
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) return;
        String a = clanIdOf(attacker.getUUID()), v = clanIdOf(victim.getUUID());
        if (a != null && a.equals(v) && !TFJson.bool(clan(a), "fuegoAmigo", false)) event.setCanceled(true);
    }
}
