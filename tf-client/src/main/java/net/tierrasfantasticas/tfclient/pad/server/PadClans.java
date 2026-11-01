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
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Clanes: fundar uno (nombre y etiqueta), invitar, aceptar, rangos, echar, ascender, salir y disolver; color de la
 * etiqueta y fuego amigo. (1.3.139, el dueño: «que el líder administre el clan, ascienda a líder, colíder o veterano, y
 * que el colíder también invite») Rangos: líder (todo: rangos, ajustes, pasar el clan), colíder (invita y echa a
 * veteranos y miembros), veterano (invita, si el líder no lo quita en AJUSTES) y miembro. Los «oficiales» de antes son
 * colíderes. La etiqueta sale junto al nombre del jugador (PadTitles.onName).
 * Datos en &lt;mundo&gt;/tfclient/clanes.json. Fundarlo cuesta lo que diga clanes.precio de pad.json (1.500 monedas;
 * el pad de administrador lo cambia en AJUSTES › ECONOMÍA); se cobra al final, cuando el clan se crea.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadClans {
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

    /** Lo que cuesta fundar un clan (pad.json, clanes.precio). */
    private static long price() {
        return PadConfig.get("clanes.precio");
    }

    private PadClans() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Datos
    // ---------------------------------------------------------------------------------------------------------------

    static JsonObject clans() {
        return STORE_IMPL.obj(STORE_IMPL.root, "clanes");
    }

    static JsonObject clan(String id) {
        JsonElement e = clans().get(id);
        if (e == null || !e.isJsonObject()) return null;
        JsonObject c = e.getAsJsonObject();
        if (c.has("oficiales")) {
            // (1.3.139) los oficiales de antes (invitaban y echaban) son los colíderes de ahora
            JsonArray old = c.getAsJsonArray("oficiales");
            c.remove("oficiales");
            for (JsonElement u : old) setRank(c, UUID.fromString(u.getAsString()), "colider");
            STORE_IMPL.changed();
        }
        return c;
    }

    public static String clanIdOf(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        String id = p == null ? "" : TFJson.str(p, "clan", "");
        return !id.isEmpty() && clan(id) != null ? id : null;
    }

    /** «[TAG]» con su color, o null si no tiene clan. */
    public static Component tagOf(UUID uuid) {
        String id = clanIdOf(uuid);
        if (id == null) return null;
        JsonObject c = clan(id);
        return Component.literal("[" + TFJson.str(c, "etiqueta", "?") + "]")
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgbOf(c))));
    }

    public static String clanNameOf(UUID uuid) {
        String id = clanIdOf(uuid);
        if (id == null) return "";
        JsonObject c = clan(id);
        return TFJson.str(c, "nombre", "") + " [" + TFJson.str(c, "etiqueta", "") + "]";
    }

    /**
     * (1.3.117) ¿Puede invitar a ese jugador a su clan? (su rango invita y el otro no está en ninguno). Lo usa Jugadores
     * del pad: «Invitar al clan», si aplica.
     */
    public static boolean canInvite(ServerPlayer player, UUID other) {
        String id = clanIdOf(player.getUUID());
        if (id == null || other.equals(player.getUUID()) || clanIdOf(other) != null) return false;
        return invites(clan(id), player.getUUID());
    }

    /** ¿Puede invitar? El líder y los colíderes siempre; los veteranos si el líder no lo quitó (AJUSTES). */
    static boolean invites(JsonObject c, UUID uuid) {
        String r = role(c, uuid);
        return r.equals("lider") || r.equals("colider") || r.equals("veterano") && TFJson.bool(c, "veteranosInvitan", true);
    }

    /** Invita a ese jugador (o bot) al clan del que invita; se lo dice por el chat si está conectado. */
    public static void invite(ServerPlayer player, UUID other, String otherName) {
        String id = clanIdOf(player.getUUID());
        if (id == null) return;
        JsonObject c = clan(id);
        if (!invites(c, player.getUUID())) return;
        if (members(c).size() >= PadClanProgress.maxMembers(c)) {
            TFPadNet.notice(player, "El clan está lleno (" + PadClanProgress.maxMembers(c) + " miembros: sube de nivel para tener más plazas).");
            return;
        }
        if (clanIdOf(other) != null) {
            TFPadNet.notice(player, otherName + " ya está en un clan.");
            return;
        }
        INVITES.computeIfAbsent(other, k -> new HashSet<>()).add(id);
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(other);
        if (online != null) {
            online.sendSystemMessage(Component.literal("[Clan] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(player.getGameProfile().getName() + " te invita a " + TFJson.str(c, "nombre", "?")
                            + ". Abre el pad (C) → Clanes para unirte.").withStyle(ChatFormatting.YELLOW)));
            online.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.6F, 1.2F);
        }
        TFPadNet.notice(player, "Invitación al clan enviada a " + otherName + ".");
    }

    /**
     * (1.3.156) ¿Gestiona ese jugador el territorio del clan (las protecciones del clan)? El líder y los colíderes, como
     * dueños de la zona (Claim.isOwner).
     */
    public static boolean managesTerritory(String clanId, UUID uuid) {
        JsonObject c = clanId == null ? null : clan(clanId);
        return c != null && members(c).contains(uuid) && PadClanProgress.treasurer(c, uuid);
    }

    /** (1.3.156) Lo que sale al entrar en el territorio de un clan: «Territorio de [TAG]» con su color (null si ya no está). */
    public static Component territoryTitle(String clanId) {
        JsonObject c = clan(clanId);
        if (c == null) return null;
        var color = net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgbOf(c)));
        return Component.literal("Territorio de ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("[" + TFJson.str(c, "etiqueta", "?") + "] ").withStyle(color.withBold(true)))
                .append(Component.literal(TFJson.str(c, "nombre", "")).withStyle(color));
    }

    /** (1.3.156) Los clanes aliados (alianza aceptada por los dos): sus misiones se ayudan. */
    static List<String> allies(JsonObject c) {
        List<String> out = new ArrayList<>();
        for (String a : PadClanViews.ids(c, "aliados")) if (clan(a) != null) out.add(a);
        return out;
    }

    static List<UUID> members(JsonObject c) {
        List<UUID> out = new ArrayList<>();
        if (c.has("miembros")) for (JsonElement e : c.getAsJsonArray("miembros")) out.add(UUID.fromString(e.getAsString()));
        return out;
    }

    /** lider, colider, veterano o miembro. */
    static String role(JsonObject c, UUID uuid) {
        if (TFJson.str(c, "lider", "").equals(uuid.toString())) return "lider";
        if (c.has("rangos") && c.get("rangos").isJsonObject()) {
            String r = TFJson.str(c.getAsJsonObject("rangos"), uuid.toString(), "miembro");
            if (r.equals("colider") || r.equals("veterano")) return r;
        }
        return "miembro";
    }

    private static void setMembers(JsonObject c, List<UUID> list) {
        JsonArray a = new JsonArray();
        for (UUID u : list) a.add(u.toString());
        c.add("miembros", a);
    }

    /** Pone el rango de un miembro (colider, veterano; miembro u otra cosa lo quita). El líder va aparte («lider»). */
    private static void setRank(JsonObject c, UUID uuid, String rank) {
        JsonObject ranks = c.has("rangos") && c.get("rangos").isJsonObject() ? c.getAsJsonObject("rangos") : new JsonObject();
        if (rank.equals("colider") || rank.equals("veterano")) ranks.addProperty(uuid.toString(), rank);
        else ranks.remove(uuid.toString());
        c.add("rangos", ranks);
    }

    /** El rango de encima (miembro → veterano → colíder → líder). */
    private static String above(String r) {
        return r.equals("miembro") ? "veterano" : r.equals("veterano") ? "colider" : "lider";
    }

    /** El de debajo (colíder → veterano → miembro). */
    private static String below(String r) {
        return r.equals("colider") ? "veterano" : "miembro";
    }

    private static void join(UUID uuid, String id) {
        STORE_IMPL.player(uuid).addProperty("clan", id);
        STORE_IMPL.changed();
    }

    private static void leave(UUID uuid) {
        JsonObject p = STORE_IMPL.playerIfAny(uuid);
        if (p != null) p.remove("clan");
        // (1.3.154) sin clan no hay chat del clan
        PadClanChat.forget(uuid);
        STORE_IMPL.changed();
    }

    private static void refreshName(UUID uuid) {
        var server = PadServer.server();
        if (server == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) {
            p.refreshDisplayName();
            if (!tabPlugin(server)) p.refreshTabListName();
            syncSuffix(p);
        }
    }

    /** ¿Está el plugin TAB? Entonces la lista es suya: no se toca (si no, se pisa su formato con el prefijo de LuckPerms). */
    public static boolean tabPlugin(net.minecraft.server.MinecraftServer server) {
        return server.getCommands().getDispatcher().getRoot().getChild("tab") != null;
    }

    /**
     * (1.3.130, el dueño: «al hacer un clan y unirme el prefijo de LuckPerms se jode; no debe cambiar nada del prefijo,
     * solo agregar la etiqueta del clan al lado del nombre») La etiqueta del clan, como sufijo de LuckPerms (lo que TAB y
     * el chat enseñan detrás del nombre); sin clan, se quita. Al entrar y al cambiar de clan.
     */
    public static void syncSuffix(ServerPlayer p) {
        var server = p.getServer();
        if (server == null || !net.tierrasfantasticas.tfclient.server.TFBridge.hasLuckPerms(server)) return;
        String id = clanIdOf(p.getUUID());
        String name = p.getGameProfile().getName();
        if (id == null) {
            net.tierrasfantasticas.tfclient.server.TFLuckPerms.setSuffix(server, p.getUUID(), name, null, 50);
            return;
        }
        JsonObject c = clan(id);
        String tag = TFJson.str(c, "etiqueta", "?").replaceAll("[^A-Za-z0-9_\\-]", "");
        // (1.3.132) por la API (sin mensajes de LuckPerms) y con el color exacto (&#RRGGBB)
        net.tierrasfantasticas.tfclient.server.TFLuckPerms.setSuffix(server, p.getUUID(), name,
                " &" + String.format("#%06X", rgbOf(c)) + "[" + tag + "]", 50);
    }

    private static void tellClan(JsonObject c, String text) {
        var server = PadServer.server();
        if (server == null) return;
        for (UUID u : members(c)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(Component.literal("[Clan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
        }
    }

    /**
     * La cabeza de un jugador con su skin de verdad: el perfil completo (con la propiedad «textures») del jugador
     * conectado o, si no está, la última skin que se le vio. Sin eso, Minecraft pinta una cabeza por defecto.
     */
    static ItemStack head(UUID uuid, String name) {
        ItemStack s = new ItemStack(Items.PLAYER_HEAD);
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, name);
        net.minecraft.server.MinecraftServer server = PadServer.server();
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(uuid);
        net.tierrasfantasticas.tfclient.server.TFBots.Bot bot = net.tierrasfantasticas.tfclient.server.TFBots.ofId(uuid);
        if (online != null) {
            profile.getProperties().putAll(online.getGameProfile().getProperties());
        } else if (bot != null) {
            profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", bot.value(), bot.signature()));
        } else {
            String[] skin = PadStats.skinOf(uuid);
            if (skin != null) {
                profile.getProperties().put("textures", skin[1].isEmpty()
                        ? new com.mojang.authlib.properties.Property("textures", skin[0])
                        : new com.mojang.authlib.properties.Property("textures", skin[0], skin[1]));
            }
        }
        s.getOrCreateTag().put("SkullOwner", net.minecraft.nbt.NbtUtils.writeGameProfile(new CompoundTag(), profile));
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
                long price = price();
                b.header("Fundando «" + creating + "». Ahora la etiqueta: 2 a 4 letras o números (sale junto a tu nombre)."
                        + (price > 0 ? " Al fundarlo se cobran " + TFEconomy.format(price) + "." : ""));
                b.input("etiqueta", "Etiqueta, por ejemplo TF", 4, "FUNDAR");
                b.footer(PadView.Btn.of("CANCELAR", "cancelar", PadView.RED));
                return b.build();
            }
            b.header(price() > 0 ? "Sin clan. Fundar uno cuesta " + TFEconomy.format(price()) + "." : "Sin clan.");
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
            boolean boss = myRole.equals("lider"), officer = boss || myRole.equals("colider");
            List<UUID> list = members(c);
            // (1.3.156) MISIONES del clan, BANCO, GUERRA (y alianzas), TIENDA (Clan Coins) y ESTILO (insignia y color, con la
            // paleta del pad) en lugar de COLOR
            // (1.3.158, el dueño: «que los miembros vean todas esas pestañas menos la de ajustes y estilo; esas solo son para
            // el colíder y el líder») TIENDA la ven todos (compran el líder y los colíderes)
            PadView.Builder b = PadView.of("clanes").tab("miembros", "MIEMBROS").tab("misiones", "MISIONES").tab("banco", "BANCO")
                    .tab("guerra", "GUERRA").tab("tienda", "TIENDA");
            if (officer) b.tab("estilo", "ESTILO");
            if (officer) b.tab("ajustes", "AJUSTES");
            boolean settings = officer && tab.equals("ajustes");
            if (tab.equals("misiones")) {
                b.selected("misiones");
                return PadClanViews.missions(b, player, id, c);
            }
            if (tab.equals("banco") || tab.equals("banco_reg")) {
                b.selected("banco");
                return PadClanBank.view(b, player, id, c, tab.equals("banco_reg"));
            }
            if (tab.equals("guerra")) {
                b.selected("guerra");
                return PadClanWar.view(b, player, id, c);
            }
            if (tab.equals("tienda")) {
                b.selected("tienda");
                return PadClanShop.view(b, player, id, c);
            }
            if (officer && (tab.equals("estilo") || tab.equals("color"))) {
                b.selected("estilo");
                return PadClanViews.style(b, c);
            }
            b.selected(settings ? "ajustes" : "miembros");
            if (!settings) b.hero(PadClanViews.hero(c, me));
            // (1.3.158, el dueño: «frases repetidas») el nombre y los miembros ya salen en la ficha de arriba
            long connected = list.stream().filter(u -> player.getServer().getPlayerList().getPlayer(u) != null).count();
            b.header(settings ? "Ajustes del clan: los cambian el líder y los colíderes."
                    : "Eres " + roleName(myRole).toLowerCase(java.util.Locale.ROOT) + " · " + connected + " conectado" + (connected == 1 ? "" : "s") + " ahora.");
            if (settings) {
                b.row(new PadView.Row(new ItemStack(Items.ORANGE_DYE), "Color e insignia", 0x18265C,
                        List.of("Ahora: " + String.format("#%06X", rgbOf(c)) + " y «" + PadClanProgress.badge(c).name() + "». Cámbialos en ESTILO (arriba)."),
                        -1, "", null, null));
                boolean ff = TFJson.bool(c, "fuegoAmigo", false);
                b.row(new PadView.Row(new ItemStack(Items.IRON_SWORD), "Fuego amigo", 0x18265C,
                        List.of(ff ? "Los del clan se pueden hacer daño." : "Los del clan no se hacen daño entre ellos."), -1, "",
                        PadView.Btn.of(ff ? "SÍ" : "NO", "fuego", ff ? PadView.RED : PadView.GREEN), null));
                boolean vi = TFJson.bool(c, "veteranosInvitan", true);
                b.row(new PadView.Row(new ItemStack(Items.WRITABLE_BOOK), "Los veteranos invitan", 0x18265C,
                        List.of(vi ? "Invitan el líder, los colíderes y los veteranos." : "Solo invitan el líder y los colíderes."), -1, "",
                        PadView.Btn.of(vi ? "SÍ" : "NO", "vetinv", vi ? PadView.GREEN : PadView.RED), null));
                // (1.3.156) el banco del clan: quién saca
                boolean vb = TFJson.bool(c, "veteranosSacan", false);
                b.row(new PadView.Row(new ItemStack(Items.CHEST), "Los veteranos sacan del banco", 0x18265C,
                        List.of(vb ? "Sacan del banco el líder, los colíderes y los veteranos." : "Solo sacan del banco el líder y los colíderes (meten todos)."),
                        -1, "", PadView.Btn.of(vb ? "SÍ" : "NO", "vetbanco", vb ? PadView.GREEN : PadView.RED), null));
                // disolver, solo el líder
                if (boss) {
                    boolean sure = confirming(me, "disolver");
                    b.footer(PadView.Btn.of(sure ? "¿SEGURO? DISOLVER" : "DISOLVER CLAN", "disolver", PadView.RED));
                }
                return b.build();
            }
            var server = player.getServer();
            list.sort(Comparator.comparingInt((UUID u) -> roleOrder(role(c, u))).thenComparing(PadStats::nameOf, String.CASE_INSENSITIVE_ORDER));
            for (UUID u : list) {
                String name = PadStats.nameOf(u);
                String r = role(c, u);
                boolean online = server.getPlayerList().getPlayer(u) != null;
                PadView.Btn b1 = null, b2 = null;
                String hint = "";
                if (!u.equals(me) && !r.equals("lider")) {
                    if (boss) {
                        // el líder: ASCENDER (miembro → veterano → colíder → líder, esto con doble clic) y BAJAR o, a un
                        // miembro, ECHAR
                        String up = above(r);
                        boolean pass = up.equals("lider");
                        b1 = PadView.Btn.of(pass && confirming(me, "lider:" + u) ? "¿SEGURO?" : pass ? "LÍDER" : "ASCENDER",
                                (pass ? "lider:" : "subir:") + u, pass ? PadView.GOLD : PadView.BLUE);
                        b2 = r.equals("miembro") ? PadView.Btn.of("ECHAR", "echar:" + u, PadView.RED)
                                : PadView.Btn.of("BAJAR", "bajar:" + u, PadView.BLUE);
                        hint = pass ? " · LÍDER le pasa el clan (tú quedas de colíder)" : " · ASCENDER lo hace " + roleName(up);
                    } else if (officer && !r.equals("colider")) {
                        b2 = PadView.Btn.of("ECHAR", "echar:" + u, PadView.RED);
                    }
                }
                b.row(new PadView.Row(head(u, name), name, online ? 0x1E7C2C : 0x18265C,
                        List.of(roleName(r) + (online ? " · conectado" : "") + hint), -1, "", b1, b2));
            }
            if (invites(c, me)) b.input("invitar", "Nombre para invitar", 16, "INVITAR");
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
            boolean boss = myRole.equals("lider"), officer = boss || myRole.equals("colider");
            // (1.3.132) el editor de color: una casilla de la paleta («col:#RRGGBB») o el campo («colhex»)
            if (action.startsWith("col:") || action.equals("colhex")) {
                if (!officer) return null;
                String v = (action.equals("colhex") ? (text == null ? "" : text) : action.substring(4)).trim();
                if (!v.startsWith("#")) v = "#" + v;
                if (!v.matches("#[0-9a-fA-F]{6}")) return "Escribe un color como #FF8800.";
                c.addProperty("color", v.toLowerCase(java.util.Locale.ROOT));
                STORE_IMPL.changed();
                for (UUID u : members(c)) refreshName(u);
                return null;
            }
            // (1.3.156) la insignia del clan (ESTILO)
            if (action.startsWith("insignia:")) {
                if (!officer) return null;
                String bid = action.substring(9);
                for (PadClanProgress.Badge bd : PadClanProgress.badges()) {
                    if (bd.id().equals(bid)) {
                        c.addProperty("insignia", bid);
                        STORE_IMPL.changed();
                        tellClan(c, "Insignia nueva del clan: " + bd.name() + ".");
                    }
                }
                return null;
            }
            // (1.3.156) banco, guerra, alianzas y tienda del clan (y la barra de arriba al día: las Clan Coins cambian)
            String routed = null;
            boolean clanAction = true;
            if (action.startsWith("banco.")) routed = PadClanBank.action(player, id, c, action);
            else if (action.startsWith("guerra.")) routed = PadClanWar.action(player, id, c, action);
            else if (action.startsWith("alianza.")) routed = PadClanAlliances.action(player, id, c, action);
            else if (action.startsWith("tienda.")) routed = PadClanShop.action(player, id, c, action);
            else clanAction = false;
            if (clanAction) {
                if (!(player instanceof net.minecraftforge.common.util.FakePlayer)) TFPadNet.sendState(player);
                return routed;
            }
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
                    if (!invites(c, me)) return null;
                    ServerPlayer other = player.getServer().getPlayerList().getPlayerByName(text.trim());
                    if (other == null) {
                        TFPadNet.notice(player, "No hay nadie conectado con ese nombre.");
                        return null;
                    }
                    invite(player, other.getUUID(), other.getGameProfile().getName());
                }
                case "subir", "bajar" -> {
                    if (!boss || target == null || !list.contains(target) || target.equals(me)) return null;
                    String r = role(c, target);
                    String to = verb.equals("subir") ? above(r) : below(r);
                    if (to.equals("lider") || to.equals(r)) return null; // pasar el clan es «lider» (con doble clic)
                    setRank(c, target, to);
                    STORE_IMPL.changed();
                    tellClan(c, PadStats.nameOf(target) + " ahora es " + roleName(to).toLowerCase(java.util.Locale.ROOT) + ".");
                }
                case "vetinv" -> {
                    if (!officer) return null;
                    c.addProperty("veteranosInvitan", !TFJson.bool(c, "veteranosInvitan", true));
                    STORE_IMPL.changed();
                }
                case "vetbanco" -> {
                    if (!officer) return null;
                    c.addProperty("veteranosSacan", !TFJson.bool(c, "veteranosSacan", false));
                    STORE_IMPL.changed();
                }
                case "echar" -> {
                    if (target == null || !list.contains(target) || target.equals(me)) return null;
                    String r = role(c, target);
                    // el líder echa a los miembros (a los demás, primero BAJAR); el colíder, a veteranos y miembros
                    if (!(boss && r.equals("miembro") || myRole.equals("colider") && (r.equals("miembro") || r.equals("veterano")))) return null;
                    list.remove(target);
                    setMembers(c, list);
                    setRank(c, target, "miembro");
                    leave(target);
                    refreshName(target);
                    tellClan(c, PadStats.nameOf(target) + " ya no está en el clan.");
                    ServerPlayer t = player.getServer().getPlayerList().getPlayer(target);
                    if (t != null) t.sendSystemMessage(Component.literal("[Clan] Te echaron de " + TFJson.str(c, "nombre", "?") + ".").withStyle(ChatFormatting.RED));
                }
                case "lider" -> {
                    if (!boss || target == null || !list.contains(target) || target.equals(me)) return null;
                    if (!confirm(me, action)) return null;
                    c.addProperty("lider", target.toString());
                    setRank(c, target, "miembro");
                    setRank(c, me, "colider");
                    STORE_IMPL.changed();
                    tellClan(c, PadStats.nameOf(target) + " es el nuevo líder del clan.");
                }
                case "color" -> {
                    if (!officer) return null;
                    int i = (colorIndex(TFJson.str(c, "color", "gold")) + 1) % COLORS.length;
                    c.addProperty("color", COLORS[i]);
                    STORE_IMPL.changed();
                    for (UUID u : list) refreshName(u);
                }
                case "fuego" -> {
                    if (!officer) return null;
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
                    // (1.3.156) su banco, su guerra y sus alianzas se van con él
                    PadClanBank.forget(id);
                    PadClanWar.clanGone(id);
                    PadClanAlliances.clanGone(id);
                    STORE_IMPL.changed();
                    return "";
                }
                case "salir" -> {
                    if (boss) {
                        TFPadNet.notice(player, "Eres el líder: pásale el clan a otro (ASCENDER hasta LÍDER) o disuélvelo en Ajustes.");
                        return null;
                    }
                    if (!confirm(me, "salir")) return null;
                    list.remove(me);
                    setMembers(c, list);
                    setRank(c, me, "miembro");
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
                    long price = price();
                    java.util.OptionalLong has = TFEconomy.balance(player.server, me);
                    if (price > 0 && has.isPresent() && has.getAsLong() < price) {
                        TFPadNet.notice(player, "Fundar un clan cuesta " + TFEconomy.format(price) + " y tienes " + TFEconomy.format(Math.max(0, has.getAsLong())) + ".");
                        return null;
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
                    long price = price();
                    if (price > 0 && !TFEconomy.take(player, price)) {
                        TFPadNet.notice(player, "Te faltan monedas: fundar un clan cuesta " + TFEconomy.format(price) + ".");
                        return null;
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
                    TFPadNet.notice(player, "Clan " + name + " [" + tag + "] fundado" + (price > 0 ? " (pagaste " + TFEconomy.format(price) + ")." : "."));
                }
                case "unirse" -> {
                    String cid = action.substring(7);
                    JsonObject c = clan(cid);
                    if (c == null || !INVITES.getOrDefault(me, Set.of()).contains(cid)) return null;
                    List<UUID> list = members(c);
                    if (list.size() >= PadClanProgress.maxMembers(c)) {
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

    /**
     * (1.3.139) Para la prueba en el juego (BossTest «oficios»): funda un clan para ese jugador con tres miembros de
     * mentira y prueba los rangos con las acciones del pad (ascender, bajar, quién invita y echa, pasar el clan y los
     * oficiales de antes). Lo deja con el clan y él de líder, para la captura.
     */
    public static String testRanks(ServerPlayer leader) {
        UUID me = leader.getUUID();
        String old = clanIdOf(me);
        if (old != null) clans().remove(old);
        UUID a = UUID.nameUUIDFromBytes("tf-prueba-a".getBytes()), bb = UUID.nameUUIDFromBytes("tf-prueba-b".getBytes()),
                cc = UUID.nameUUIDFromBytes("tf-prueba-c".getBytes()), dd = UUID.nameUUIDFromBytes("tf-prueba-d".getBytes());
        String id = "prueba01";
        JsonObject c = new JsonObject();
        c.addProperty("nombre", "Clan de Prueba");
        c.addProperty("etiqueta", "PRB");
        c.addProperty("color", "#55AAFF");
        c.addProperty("lider", me.toString());
        JsonArray legacy = new JsonArray();
        legacy.add(dd.toString());
        c.add("oficiales", legacy);
        setMembers(c, new ArrayList<>(List.of(me, a, bb, cc, dd)));
        clans().add(id, c);
        join(me, id);
        String[] names = {"Ana_Prueba", "Beto_Prueba", "Caro_Prueba", "Dani_Prueba"};
        List<UUID> fakes = List.of(a, bb, cc, dd);
        for (int i = 0; i < 4; i++) {
            join(fakes.get(i), id);
            PadStats.STORE_IMPL.player(fakes.get(i)).addProperty("nombre", names[i]);
        }
        StringBuilder out = new StringBuilder();
        c = clan(id);
        out.append("oficial de antes → ").append(role(c, dd)).append(c.has("oficiales") ? " (MAL: sigue «oficiales»)" : "");
        App app = (App) APP;
        app.action(leader, "", "subir:" + a, "");
        out.append("; ASCENDER a A → ").append(role(c, a));
        app.action(leader, "", "subir:" + a, "");
        out.append(", otra vez → ").append(role(c, a));
        app.action(leader, "", "subir:" + bb, "");
        out.append("; B → ").append(role(c, bb));
        out.append("; invitan: colíder ").append(invites(c, a)).append(", veterano ").append(invites(c, bb))
                .append(", miembro ").append(invites(c, cc));
        app.action(leader, "", "vetinv", "");
        out.append("; sin «los veteranos invitan»: veterano ").append(invites(c, bb));
        app.action(leader, "", "vetinv", "");
        app.action(leader, "", "bajar:" + bb, "");
        out.append("; BAJAR a B → ").append(role(c, bb));
        app.action(leader, "", "subir:" + bb, "");
        app.action(leader, "", "lider:" + dd, "");
        out.append("; LÍDER a D, un clic → líder ").append(TFJson.str(c, "lider", "").equals(me.toString()) ? "sigo yo (bien)" : "otro (MAL)");
        app.action(leader, "", "lider:" + dd, "");
        out.append(", dos clics → D ").append(role(c, dd)).append(" y yo ").append(role(c, me));
        // vuelvo a ser líder (para la captura)
        c.addProperty("lider", me.toString());
        setRank(c, me, "miembro");
        setRank(c, dd, "colider");
        STORE_IMPL.changed();
        return out.toString();
    }

    /** (1.3.156) Para la prueba en el juego: un clan con ese líder y esos miembros (de mentira o de verdad). */
    public static void testClan(String id, String name, String tag, UUID leader, List<UUID> members, String color) {
        JsonObject c = new JsonObject();
        c.addProperty("nombre", name);
        c.addProperty("etiqueta", tag);
        c.addProperty("color", color);
        c.addProperty("lider", leader.toString());
        List<UUID> all = new ArrayList<>(members);
        if (!all.contains(leader)) all.add(0, leader);
        setMembers(c, all);
        clans().add(id, c);
        for (UUID u : all) join(u, id);
        STORE_IMPL.changed();
    }

    /** (1.3.156) Para la prueba: el nombre de un jugador de mentira (para las listas). */
    public static void testName(UUID uuid, String name) {
        PadStats.STORE_IMPL.player(uuid).addProperty("nombre", name);
        PadStats.STORE_IMPL.changed();
    }

    /** (1.3.156) Para la prueba: quita un clan (y a sus miembros de él). */
    public static void testRemove(String id) {
        JsonObject c = clan(id);
        if (c == null) return;
        for (UUID u : members(c)) leave(u);
        clans().remove(id);
        PadClanBank.forget(id);
        PadClanWar.clanGone(id);
        PadClanAlliances.clanGone(id);
        STORE_IMPL.changed();
    }

    /** (1.3.156) Para la prueba: el color de la etiqueta del clan («#rrggbb»). */
    public static String testColor(String id) {
        JsonObject c = clan(id);
        return c == null ? "" : TFJson.str(c, "color", "");
    }

    /** (1.3.156) Para la prueba: ¿son aliados? */
    public static boolean testAllied(String a, String b) {
        JsonObject c = clan(a);
        return c != null && allies(c).contains(b);
    }

    /** (1.3.156) Para la prueba: una acción de la app de clanes como si la pulsara ese jugador (en esa pestaña). */
    public static String testAction(ServerPlayer player, String tab, String action, String text) {
        return APP.action(player, tab, action, text);
    }

    /**
     * (1.3.158) Para la prueba: las pestañas que ve ese jugador al pedir esa pestaña y la que le sale
     * («miembros,misiones,banco,guerra,tienda → miembros»).
     */
    public static String testTabs(ServerPlayer player, String tab) {
        PadView v = APP.view(player, tab);
        StringBuilder out = new StringBuilder();
        for (PadView.Tab t : v.tabs()) out.append(out.length() == 0 ? "" : ",").append(t.key());
        return out + " → " + v.tab();
    }

    /** (1.3.139) Para la prueba: quita el clan de prueba de testRanks. */
    public static void testCleanup(ServerPlayer leader) {
        String id = clanIdOf(leader.getUUID());
        if (id == null) return;
        for (UUID u : members(clan(id))) leave(u);
        clans().remove(id);
        STORE_IMPL.changed();
        refreshName(leader.getUUID());
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

    /** El color de la etiqueta en RGB: «#rrggbb» (editor de color) o un nombre de los de antes («gold»…). */
    static int rgbOf(JsonObject c) {
        String v = TFJson.str(c, "color", "gold");
        if (v.matches("#[0-9a-fA-F]{6}")) return Integer.parseInt(v.substring(1), 16);
        ChatFormatting f = ChatFormatting.getByName(v);
        return f != null && f.getColor() != null ? f.getColor() : 0xFFAA00;
    }

    private static int colorIndex(String c) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(c)) return i;
        return 0;
    }

    private static int roleOrder(String r) {
        return r.equals("lider") ? 0 : r.equals("colider") ? 1 : r.equals("veterano") ? 2 : 3;
    }

    private static String roleName(String r) {
        return r.equals("lider") ? "Líder" : r.equals("colider") ? "Colíder" : r.equals("veterano") ? "Veterano" : "Miembro";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Fuego amigo
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker == victim) return;
        String a = clanIdOf(attacker.getUUID()), v = clanIdOf(victim.getUUID());
        if (a == null || !a.equals(v)) return;
        // (1.3.156) en el territorio del clan, nunca (aunque el fuego amigo esté puesto)
        var claim = net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance().getClaimAt(victim.level(), victim.blockPosition());
        boolean home = claim != null && a.equals(claim.getClanId());
        if (home || !TFJson.bool(clan(a), "fuegoAmigo", false)) event.setCanceled(true);
    }
}
