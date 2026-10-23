/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.server.PadStats;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Comunidad en el servidor: guarda las fotos que publican los jugadores (&lt;mundo&gt;/tfclient/comunidad/&lt;id&gt;.png) y sus
 * datos (comunidad.json: autor, texto, fecha, likes, reacciones, comentarios y denuncias), y se las manda a quien las mira.
 * <ul>
 *   <li>Solo PNG de hasta 1920x1080 y 4,2 MB; una cada 90 s y como mucho 40 por jugador (las más viejas no se tocan).</li>
 *   <li>Comentarios (1.3.120): «comentarios»: [{id, autor, nombre, texto, fecha, reacciones}], como mucho
 *   {@link #MAX_COMMENTS} por foto y uno cada {@link #COMMENT_COOLDOWN} ms por jugador. Los borra quien lo escribió, el
 *   dueño de la foto o el staff. Reacciones como en las fotos: una por jugador y comentario.</li>
 *   <li>Like: uno por jugador y foto (también a las tuyas). El autor suma en sus estadísticas los likes de los demás.</li>
 *   <li>Borrar: el autor o el staff (nivel 2). Denunciar: avisa al staff conectado; con 5 denuncias se oculta.</li>
 * </ul>
 */
public final class PadCommunityServer {
    private static final int PER_PAGE = 10;
    private static final int MAX_PER_PLAYER = 40;
    private static final long POST_COOLDOWN = 90_000;
    private static final int HIDE_AT = 5;
    private static final int MAX_W = 1920, MAX_H = 1080;
    private static final int MAX_COMMENTS = 300;
    private static final long COMMENT_COOLDOWN = 4_000;
    private static final Map<UUID, Long> LAST_COMMENT = new HashMap<>();

    /** Una subida a medias. */
    private static final class Up {
        final int id, total;
        final byte[][] parts;
        String caption = "";
        int got;
        final long started = System.currentTimeMillis();

        Up(int id, int total) {
            this.id = id;
            this.total = total;
            this.parts = new byte[total][];
        }
    }

    private static final Map<UUID, Up> UPLOADS = new HashMap<>();
    private static final Map<UUID, long[]> IMG_RATE = new HashMap<>();
    private static final long IMG_BYTES_PER_10S = 12_000_000L;
    private static final Map<UUID, long[]> RATE = new HashMap<>();
    /** «liker:foto» a los que ya se avisó al autor (para que quitar y dar like no llene su pantalla). */
    private static final Set<String> LIKE_TOLD = new HashSet<>();
    private static final Map<UUID, Long> LAST_POST = new HashMap<>();
    private static JsonObject root = new JsonObject();
    private static boolean dirty;
    private static MinecraftServer server;

    static final PadServer.Store STORE = new PadServer.Store() {
        @Override
        public void load(MinecraftServer s) {
            server = s;
            Path file = TFJson.worldFile(s, "comunidad.json");
            JsonObject r = TFJson.read(file);
            if (r == null && Files.exists(file)) {
                // ilegible: se copia aparte antes de empezar de cero (nunca se pisa sin copia)
                try {
                    Files.copy(file, file.resolveSibling("comunidad-roto-" + System.currentTimeMillis() + ".json"));
                } catch (Exception e) {
                    TFClient.LOGGER.error("Comunidad: comunidad.json no se pudo leer ni copiar", e);
                }
            }
            root = r == null ? new JsonObject() : r;
            if (!root.has("publicaciones") || !root.get("publicaciones").isJsonArray()) root.add("publicaciones", new JsonArray());
            dirty = false;
            migrateReactions();
            UPLOADS.clear();
            RATE.clear();
            LIKE_TOLD.clear();
            LAST_COMMENT.clear();
        }

        @Override
        public void save(MinecraftServer s) {
            dirty = false;
            TFJson.write(TFJson.worldFile(s, "comunidad.json"), root);
        }

        @Override
        public boolean dirty() {
            return dirty;
        }
    };

    private PadCommunityServer() {}

    /** Como mucho 6 peticiones de lista o acciones por segundo y jugador. */
    private static boolean rate(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long[] r = RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0});
        if (now - r[0] > 1000) {
            r[0] = now;
            r[1] = 0;
        }
        return ++r[1] <= 6;
    }

    /** Las subidas que llevan más de 2 minutos a medias se tiran. */
    static void dropStaleUploads() {
        long now = System.currentTimeMillis();
        UPLOADS.values().removeIf(up -> now - up.started > 120_000);
    }

    /** Al salir se olvida lo que tuviera a medias. */
    static void logout(UUID uuid) {
        UPLOADS.remove(uuid);
        RATE.remove(uuid);
        IMG_RATE.remove(uuid);
    }

    private static JsonArray posts() {
        return root.getAsJsonArray("publicaciones");
    }

    private static Path dir() {
        return TFJson.worldFile(server, "comunidad");
    }

    private static JsonObject find(String id) {
        for (JsonElement e : posts()) if (TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        return null;
    }

    private static Set<String> set(JsonObject p, String key) {
        Set<String> s = new HashSet<>();
        if (p.has(key)) for (JsonElement e : p.getAsJsonArray(key)) s.add(e.getAsString());
        return s;
    }

    private static void put(JsonObject p, String key, Set<String> s) {
        JsonArray a = new JsonArray();
        s.forEach(a::add);
        p.add(key, a);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Reacciones: «reacciones»: {emoji: [uuid, ...]} en el orden en que se pusieron
    // ---------------------------------------------------------------------------------------------------------------

    /** Emojis distintos (con alguien) de una foto. */
    private static int kinds(JsonObject re) {
        int n = 0;
        for (Map.Entry<String, JsonElement> e : re.entrySet()) if (e.getValue().isJsonArray() && !e.getValue().getAsJsonArray().isEmpty()) n++;
        return n;
    }

    /**
     * Pone o quita la reacción emoji (cualquiera, validado aquí) de un jugador en una foto o un comentario: UNA por
     * jugador; el mismo emoji la quita, otro la cambia. Como mucho max emojis distintos.
     */
    private static void react(ServerPlayer player, JsonObject p, String reaction, int max, String what) {
        String emoji = PadEmojis.canonical(reaction);
        if (emoji == null) return;
        String me = player.getUUID().toString();
        if (!p.has("reacciones") || !p.get("reacciones").isJsonObject()) p.add("reacciones", new JsonObject());
        JsonObject re = p.getAsJsonObject("reacciones");
        Set<String> who = set(re, emoji);
        if (who.remove(me)) {
            if (who.isEmpty()) re.remove(emoji);
            else put(re, emoji, who);
        } else {
            // quita la reacción que tuviera antes ahí
            for (String other : new java.util.ArrayList<>(re.keySet())) {
                Set<String> was = set(re, other);
                if (!was.remove(me)) continue;
                if (was.isEmpty()) re.remove(other);
                else put(re, other, was);
            }
            if (who.isEmpty() && kinds(re) >= max) {
                TFPadNet.notice(player, what + " ya tiene " + max + " emojis distintos.");
                dirty = true;
                return;
            }
            who.add(me);
            put(re, emoji, who);
        }
        dirty = true;
    }

    /** Las reacciones de una foto o un comentario para un jugador (solo emojis que existen, como mucho max). */
    private static List<PadCommunityNet.Reaction> reactions(JsonObject p, String me, int max) {
        List<PadCommunityNet.Reaction> out = new ArrayList<>();
        JsonObject re = TFJson.obj(p, "reacciones");
        if (re == null) return out;
        for (Map.Entry<String, JsonElement> e : re.entrySet()) {
            if (out.size() >= max) break;
            String emoji = PadEmojis.canonical(e.getKey());
            if (emoji == null || !e.getValue().isJsonArray()) continue;
            Set<String> who = set(re, e.getKey());
            if (!who.isEmpty()) out.add(new PadCommunityNet.Reaction(emoji, who.size(), who.contains(me)));
        }
        return out;
    }

    /**
     * Las fotos de antes (1.3.29-1.3.55) guardaban las reacciones con su nombre viejo (risa, wow, triste, fuego, top) y
     * una por jugador: se pasan a su emoji nuevo (joy, open_mouth, cry, fire, thumbsup) al cargar. Lo que no se conoce
     * se deja tal cual (no se borra nada; simplemente no se enseña).
     */
    private static void migrateReactions() {
        int changed = 0;
        for (JsonElement el : posts()) {
            if (!el.isJsonObject()) continue;
            JsonObject p = el.getAsJsonObject();
            if (!p.has("reacciones") || !p.get("reacciones").isJsonObject()) continue;
            JsonObject old = p.getAsJsonObject("reacciones");
            boolean legacy = false;
            for (String k : old.keySet()) {
                String c = PadEmojis.canonical(k);
                if (c != null && !c.equals(k)) legacy = true;
            }
            if (!legacy) continue;
            JsonObject now = new JsonObject();
            for (Map.Entry<String, JsonElement> e : old.entrySet()) {
                String c = PadEmojis.canonical(e.getKey());
                String key = c == null ? e.getKey() : c;
                Set<String> who = now.has(key) ? set(now, key) : new java.util.LinkedHashSet<>();
                if (e.getValue().isJsonArray()) for (JsonElement w : e.getValue().getAsJsonArray()) who.add(w.getAsString());
                if (!who.isEmpty()) put(now, key, who);
            }
            p.add("reacciones", now);
            changed++;
        }
        if (changed > 0) {
            dirty = true;
            TFClient.LOGGER.info("Comunidad: reacciones de {} fotos pasadas a los emojis nuevos", changed);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Publicar
    // ---------------------------------------------------------------------------------------------------------------

    static void upload(ServerPlayer player, PadCommunityNet.Upload m) {
        UUID me = player.getUUID();
        if (m.total() <= 0 || m.total() > MAX_PHOTO_PARTS || m.index() < 0 || m.index() >= m.total()) return;
        Up up = UPLOADS.get(me);
        if (m.index() == 0 || up == null || up.id != m.upload()) {
            if (m.index() != 0) return;
            Long last = LAST_POST.get(me);
            if (last != null && System.currentTimeMillis() - last < POST_COOLDOWN && !player.hasPermissions(2)) {
                fail(player, "Espera un poco antes de publicar otra foto.");
                return;
            }
            if (count(me) >= MAX_PER_PLAYER) {
                fail(player, "Ya tienes " + MAX_PER_PLAYER + " fotos publicadas. Borra alguna en Comunidad › Mías.");
                return;
            }
            up = new Up(m.upload(), m.total());
            up.caption = clean(m.caption());
            UPLOADS.put(me, up);
        }
        if (System.currentTimeMillis() - up.started > 120_000) {
            UPLOADS.remove(me);
            fail(player, "La subida tardó demasiado.");
            return;
        }
        if (m.total() != up.total) {
            UPLOADS.remove(me);
            fail(player, "La foto llegó dañada.");
            return;
        }
        if (up.parts[m.index()] == null) {
            up.parts[m.index()] = m.data();
            up.got++;
        }
        if (up.got < up.total) return;
        UPLOADS.remove(me);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : up.parts) out.writeBytes(part);
        byte[] png = out.toByteArray();
        String why = check(png);
        if (why != null) {
            fail(player, why);
            return;
        }
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
        try {
            Files.createDirectories(dir());
            Files.write(dir().resolve(id + ".png"), png);
        } catch (Exception e) {
            TFClient.LOGGER.error("Comunidad: no se pudo guardar una foto", e);
            fail(player, "No se pudo guardar la foto en el servidor.");
            return;
        }
        JsonObject p = new JsonObject();
        p.addProperty("id", id);
        p.addProperty("autor", me.toString());
        p.addProperty("nombre", player.getGameProfile().getName());
        p.addProperty("texto", up.caption);
        p.addProperty("fecha", System.currentTimeMillis());
        p.add("likes", new JsonArray());
        p.add("denuncias", new JsonArray());
        posts().add(p);
        dirty = true;
        LAST_POST.put(me, System.currentTimeMillis());
        PadStats.add(player, PadStats.PHOTOS, 1);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.5F, 1.5F);
        PadCommunityNet.toPlayer(player, new PadCommunityNet.Result(true, "Foto publicada."));
        TFClient.LOGGER.info("Comunidad: {} publicó la foto {}", player.getGameProfile().getName(), id);
    }

    static final int MAX_PHOTO_PARTS = PadCommunityNet.MAX_PHOTO / PadCommunityNet.UP_CHUNK + 2;

    private static void fail(ServerPlayer player, String why) {
        PadCommunityNet.toPlayer(player, new PadCommunityNet.Result(false, why));
    }

    private static String clean(String s) {
        return clean(s, 120);
    }

    private static String clean(String s, int max) {
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    /** Que sea un PNG de verdad y del tamaño permitido (se lee la cabecera IHDR, sin decodificar). */
    static String check(byte[] png) {
        if (png.length > PadCommunityNet.MAX_PHOTO) return "La foto es demasiado grande.";
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (png.length < 33 || !Arrays.equals(Arrays.copyOf(png, 8), sig)) return "Eso no es una foto PNG.";
        if (png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') return "Eso no es una foto PNG.";
        int w = ((png[16] & 255) << 24) | ((png[17] & 255) << 16) | ((png[18] & 255) << 8) | (png[19] & 255);
        int h = ((png[20] & 255) << 24) | ((png[21] & 255) << 16) | ((png[22] & 255) << 8) | (png[23] & 255);
        if (w <= 0 || h <= 0 || w > MAX_W || h > MAX_H) return "La foto tiene un tamaño raro.";
        return null;
    }

    private static int count(UUID uuid) {
        int n = 0;
        for (JsonElement e : posts()) if (TFJson.str(e.getAsJsonObject(), "autor", "").equals(uuid.toString())) n++;
        return n;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ver
    // ---------------------------------------------------------------------------------------------------------------

    static void requestFeed(ServerPlayer player, String tab, int page) {
        if (rate(player)) sendFeed(player, tab, page);
    }

    static void sendFeed(ServerPlayer player, String tab, int page) {
        boolean staff = player.hasPermissions(2);
        String me = player.getUUID().toString();
        List<JsonObject> list = new ArrayList<>();
        long monthAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
        for (JsonElement e : posts()) {
            JsonObject p = e.getAsJsonObject();
            boolean mine = TFJson.str(p, "autor", "").equals(me);
            if (tab.equals("mias") && !mine) continue;
            if (!mine && !staff && set(p, "denuncias").size() >= HIDE_AT) continue;
            if (tab.equals("populares") && TFJson.num(p, "fecha", 0) < monthAgo) continue;
            list.add(p);
        }
        Comparator<JsonObject> newest = Comparator.comparingLong((JsonObject p) -> TFJson.num(p, "fecha", 0)).reversed();
        if (tab.equals("populares")) {
            Map<JsonObject, Integer> likes = new java.util.IdentityHashMap<>();
            for (JsonObject p : list) likes.put(p, p.has("likes") && p.get("likes").isJsonArray() ? p.getAsJsonArray("likes").size() : 0);
            list.sort(Comparator.comparingInt((JsonObject p) -> likes.get(p)).reversed().thenComparing(newest));
        } else {
            list.sort(newest);
        }
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        List<PadCommunityNet.Post> out = new ArrayList<>();
        for (int i = pg * PER_PAGE; i < Math.min(list.size(), (pg + 1) * PER_PAGE); i++) {
            JsonObject p = list.get(i);
            Set<String> likes = set(p, "likes");
            boolean mine = TFJson.str(p, "autor", "").equals(me);
            UUID author;
            try {
                author = UUID.fromString(TFJson.str(p, "autor", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            List<PadCommunityNet.Reaction> reactions = reactions(p, me, PadCommunityNet.MAX_REACTIONS);
            out.add(new PadCommunityNet.Post(TFJson.str(p, "id", ""), author, TFJson.str(p, "nombre", "?"), TFJson.str(p, "texto", ""),
                    TFJson.num(p, "fecha", 0), likes.size(), likes.contains(me), mine, mine || staff, reactions, comments(p).size()));
        }
        PadCommunityNet.toPlayer(player, new PadCommunityNet.Feed(tab, pg, pages, list.size(), out));
    }

    static void sendImage(ServerPlayer player, String id) {
        if (!id.matches("[a-z0-9]{1,20}") || find(id) == null) return;
        long now = System.currentTimeMillis();
        // como mucho 40 fotos y 12 MB cada 10 s por jugador
        long[] r = IMG_RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0, 0});
        if (now - r[0] > 10_000) {
            r[0] = now;
            r[1] = 0;
            r[2] = 0;
        }
        if (++r[1] > 40 || r[2] > IMG_BYTES_PER_10S) return;
        byte[] data;
        try {
            data = Files.readAllBytes(dir().resolve(id + ".png"));
        } catch (Exception e) {
            return;
        }
        r[2] += data.length;
        int total = (data.length + PadCommunityNet.DOWN_CHUNK - 1) / PadCommunityNet.DOWN_CHUNK;
        for (int i = 0; i < total; i++) {
            byte[] part = Arrays.copyOfRange(data, i * PadCommunityNet.DOWN_CHUNK, Math.min(data.length, (i + 1) * PadCommunityNet.DOWN_CHUNK));
            PadCommunityNet.toPlayer(player, new PadCommunityNet.Img(id, i, total, part));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Like, borrar, denunciar
    // ---------------------------------------------------------------------------------------------------------------

    static void act(ServerPlayer player, PadCommunityNet.Act m) {
        if (!rate(player)) return;
        JsonObject p = find(m.id());
        if (p != null) {
            String me = player.getUUID().toString();
            String author = TFJson.str(p, "autor", "");
            // «react:fire»: el emoji va detrás de los dos puntos
            String action = m.action(), reaction = "";
            if (action.startsWith("react:")) {
                reaction = action.substring(6);
                action = "react";
            }
            switch (action) {
                case "like" -> { // uno por jugador y foto, también en las tuyas (esos no suman a tus estadísticas ni avisan)
                    Set<String> likes = set(p, "likes");
                    UUID au = null;
                    try {
                        au = author.equals(me) ? null : UUID.fromString(author);
                    } catch (IllegalArgumentException ignored) {
                        // autor raro
                    }
                    if (likes.remove(me)) {
                        if (au != null && PadStats.get(au, PadStats.LIKES) > 0) PadStats.add(au, PadStats.LIKES, -1);
                    } else {
                        likes.add(me);
                        if (au != null) {
                            ServerPlayer a = server.getPlayerList().getPlayer(au);
                            if (a != null) {
                                PadStats.add(a, PadStats.LIKES, 1);
                                if (LIKE_TOLD.add(me + ":" + m.id())) a.displayClientMessage(Component.literal("❤ A " + player.getGameProfile().getName() + " le gustó tu foto")
                                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                            } else {
                                PadStats.add(au, PadStats.LIKES, 1);
                            }
                        }
                    }
                    put(p, "likes", likes);
                    dirty = true;
                }
                case "react" -> react(player, p, reaction, PadCommunityNet.MAX_REACTIONS, "Esa foto");
                case "borrar" -> {
                    if (!author.equals(me) && !player.hasPermissions(2)) break;
                    remove(m.id());
                    TFPadNet.notice(player, "Foto borrada.");
                }
                case "denunciar" -> {
                    if (author.equals(me)) break;
                    Set<String> rep = set(p, "denuncias");
                    if (rep.add(me)) {
                        put(p, "denuncias", rep);
                        dirty = true;
                        for (ServerPlayer s : server.getPlayerList().getPlayers()) {
                            if (s.hasPermissions(2)) {
                                s.sendSystemMessage(Component.literal("[Comunidad] " + player.getGameProfile().getName() + " denunció una foto de "
                                        + TFJson.str(p, "nombre", "?") + " (" + rep.size() + " denuncias). Revísala en el pad de administrador: Comunidad.")
                                        .withStyle(ChatFormatting.RED));
                            }
                        }
                    }
                    TFPadNet.notice(player, "Gracias: el staff revisará la foto.");
                }
                default -> {
                }
            }
        }
        if (m.page() >= 0) sendFeed(player, m.tab(), m.page());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Comentarios
    // ---------------------------------------------------------------------------------------------------------------

    /** Los comentarios de una foto (del más viejo al más nuevo); vacío si no tiene. */
    private static JsonArray comments(JsonObject p) {
        return p.has("comentarios") && p.get("comentarios").isJsonArray() ? p.getAsJsonArray("comentarios") : new JsonArray();
    }

    private static JsonObject findComment(JsonObject p, String id) {
        for (JsonElement e : comments(p)) {
            if (e.isJsonObject() && TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        }
        return null;
    }

    /** ¿La ve este jugador? (con {@link #HIDE_AT} denuncias, solo su autor y el staff, como en la lista) */
    private static boolean visible(ServerPlayer player, JsonObject p) {
        return player.hasPermissions(2) || TFJson.str(p, "autor", "").equals(player.getUUID().toString()) || set(p, "denuncias").size() < HIDE_AT;
    }

    /** Los datos de la skin de un jugador («textures» en base64): el conectado, el bot o la última que se le vio; "" si no. */
    private static String skin(UUID uuid) {
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            for (com.mojang.authlib.properties.Property pr : online.getGameProfile().getProperties().get("textures")) return pr.getValue();
            return "";
        }
        net.tierrasfantasticas.tfclient.server.TFBots.Bot bot = net.tierrasfantasticas.tfclient.server.TFBots.ofId(uuid);
        if (bot != null) return bot.value();
        String[] s = PadStats.skinOf(uuid);
        return s == null ? "" : s[0];
    }

    static void requestComments(ServerPlayer player, String post) {
        if (rate(player)) sendComments(player, post);
    }

    static void sendComments(ServerPlayer player, String post) {
        JsonObject p = find(post);
        if (p == null || !visible(player, p)) {
            PadCommunityNet.toPlayer(player, new PadCommunityNet.Comments(post, 0, List.of()));
            return;
        }
        String me = player.getUUID().toString();
        boolean mod = player.hasPermissions(2) || TFJson.str(p, "autor", "").equals(me);
        JsonArray all = comments(p);
        List<PadCommunityNet.Comment> out = new ArrayList<>();
        Map<UUID, String> skins = new HashMap<>();
        for (int i = Math.max(0, all.size() - PadCommunityNet.COMMENTS_SENT); i < all.size(); i++) {
            if (!all.get(i).isJsonObject()) continue;
            JsonObject c = all.get(i).getAsJsonObject();
            UUID author;
            try {
                author = UUID.fromString(TFJson.str(c, "autor", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            boolean mine = author.toString().equals(me);
            out.add(new PadCommunityNet.Comment(TFJson.str(c, "id", ""), author, TFJson.str(c, "nombre", "?"), skins.computeIfAbsent(author, PadCommunityServer::skin),
                    clean(TFJson.str(c, "texto", ""), PadCommunityNet.COMMENT_MAX), TFJson.num(c, "fecha", 0),
                    reactions(c, me, PadCommunityNet.COMMENT_REACTIONS), mine, mine || mod));
        }
        PadCommunityNet.toPlayer(player, new PadCommunityNet.Comments(post, all.size(), out));
    }

    /** Escribir, reaccionar o borrar un comentario; siempre se le contesta con los comentarios como quedan. */
    static void commentAct(ServerPlayer player, PadCommunityNet.CommentAct m) {
        if (!rate(player)) return;
        JsonObject p = find(m.post());
        if (p != null && visible(player, p)) {
            UUID uuid = player.getUUID();
            String me = uuid.toString(), name = player.getGameProfile().getName();
            String action = m.action();
            if (action.equals("add")) {
                String text = clean(m.text(), PadCommunityNet.COMMENT_MAX);
                Long last = LAST_COMMENT.get(uuid);
                long now = System.currentTimeMillis();
                if (text.isEmpty()) {
                    TFPadNet.notice(player, "Escribe algo para comentar.");
                } else if (last != null && now - last < COMMENT_COOLDOWN && !player.hasPermissions(2)) {
                    TFPadNet.notice(player, "Espera un momento antes de comentar otra vez.");
                } else if (comments(p).size() >= MAX_COMMENTS) {
                    TFPadNet.notice(player, "Esta foto ya tiene " + MAX_COMMENTS + " comentarios.");
                } else {
                    if (!p.has("comentarios") || !p.get("comentarios").isJsonArray()) p.add("comentarios", new JsonArray());
                    JsonObject c = new JsonObject();
                    c.addProperty("id", Long.toString(now, 36) + Integer.toString((int) (Math.random() * 1296), 36));
                    c.addProperty("autor", me);
                    c.addProperty("nombre", name);
                    c.addProperty("texto", text);
                    c.addProperty("fecha", now);
                    p.getAsJsonArray("comentarios").add(c);
                    dirty = true;
                    LAST_COMMENT.put(uuid, now);
                    // al dueño de la foto, si está conectado (y no es él)
                    String author = TFJson.str(p, "autor", "");
                    if (!author.equals(me)) {
                        try {
                            ServerPlayer a = server.getPlayerList().getPlayer(UUID.fromString(author));
                            if (a != null) a.displayClientMessage(Component.literal("✎ " + name + " comentó tu foto: «"
                                    + (text.length() > 40 ? text.substring(0, 40) + "…" : text) + "»").withStyle(ChatFormatting.AQUA), true);
                        } catch (IllegalArgumentException ignored) {
                            // autor raro
                        }
                    }
                }
            } else if (action.startsWith("react:")) {
                JsonObject c = findComment(p, m.comment());
                if (c != null) react(player, c, action.substring(6), PadCommunityNet.COMMENT_REACTIONS, "Ese comentario");
            } else if (action.equals("borrar")) {
                JsonObject c = findComment(p, m.comment());
                boolean may = c != null && (TFJson.str(c, "autor", "").equals(me) || TFJson.str(p, "autor", "").equals(me) || player.hasPermissions(2));
                if (may) {
                    JsonArray keep = new JsonArray();
                    for (JsonElement e : comments(p)) if (e != c) keep.add(e);
                    p.add("comentarios", keep);
                    dirty = true;
                }
            }
        }
        sendComments(player, m.post());
    }

    /**
     * (La prueba en el juego, BossTest «pad») Una foto de prueba (un atardecer de 640x360) de otro jugador con likes,
     * reacciones y cuatro comentarios (uno tuyo, uno largo, con emojis y con reacciones). others: quiénes (uuid y nombre;
     * los bots que han entrado, con su skin). Devuelve su id.
     */
    public static String testPost(ServerPlayer player, List<UUID> others, List<String> names) {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(640, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 360; y++) {
                for (int x = 0; x < 640; x++) {
                    double t = y / 360.0, sun = Math.hypot(x - 420, y - 210) / 70.0;
                    int r = (int) (255 * Math.min(1, 0.35 + t * 0.75)), g = (int) (255 * Math.min(1, 0.25 + t * 0.4)), b = (int) (255 * (0.55 - t * 0.35));
                    if (sun < 1) {
                        r = 255;
                        g = 236;
                        b = 160;
                    }
                    if (y > 250 + 12 * Math.sin(x / 40.0)) {
                        r /= 4;
                        g /= 3;
                        b /= 3;
                    }
                    img.setRGB(x, y, (Math.max(0, r) << 16) | (Math.max(0, g) << 8) | Math.max(0, b));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            String id = Long.toString(System.currentTimeMillis(), 36) + "t";
            Files.createDirectories(dir());
            Files.write(dir().resolve(id + ".png"), out.toByteArray());
            String me = player.getUUID().toString();
            String[] who = new String[3], name = new String[3];
            for (int i = 0; i < 3; i++) {
                who[i] = i < others.size() ? others.get(i).toString() : UUID.nameUUIDFromBytes(("prueba" + i).getBytes()).toString();
                name[i] = i < names.size() ? names.get(i) : new String[] {"Luna", "Kai", "Nico"}[i];
            }
            long now = System.currentTimeMillis();
            JsonObject p = new JsonObject();
            p.addProperty("id", id);
            p.addProperty("autor", who[0]);
            p.addProperty("nombre", name[0]);
            p.addProperty("texto", "Atardecer desde el lago del spawn :sunglasses:");
            p.addProperty("fecha", now - 3_600_000L);
            put(p, "likes", new java.util.LinkedHashSet<>(List.of(who[1], who[2], me)));
            p.add("denuncias", new JsonArray());
            JsonObject re = new JsonObject();
            put(re, "fire", new java.util.LinkedHashSet<>(List.of(who[1], who[2])));
            put(re, "heart_eyes", new java.util.LinkedHashSet<>(List.of(me)));
            p.add("reacciones", re);
            JsonArray cs = new JsonArray();
            String[][] comments = {
                    {who[1], name[1], "¡Qué foto más bonita! :heart_eyes:", "joy:" + me + "," + who[2]},
                    {who[2], name[2], "Ese atardecer está brutal, ¿dónde es? Quiero ir a construir cerca de ahí con mi clan este fin de semana :fire:",
                            "fire:" + who[1] + ";thumbsup:" + me},
                    {me, player.getGameProfile().getName(), "En el spawn, al lado del lago :sunglasses:", ""},
                    {who[0], name[0], "jajaja :joy:", ""}};
            for (int i = 0; i < comments.length; i++) {
                JsonObject c = new JsonObject();
                c.addProperty("id", id + "c" + i);
                c.addProperty("autor", comments[i][0]);
                c.addProperty("nombre", comments[i][1]);
                c.addProperty("texto", comments[i][2]);
                c.addProperty("fecha", now - (comments.length - i) * 600_000L);
                if (!comments[i][3].isEmpty()) {
                    JsonObject cr = new JsonObject();
                    for (String part : comments[i][3].split(";")) {
                        String[] kv = part.split(":");
                        put(cr, kv[0], new java.util.LinkedHashSet<>(List.of(kv[1].split(","))));
                    }
                    c.add("reacciones", cr);
                }
                cs.add(c);
            }
            p.add("comentarios", cs);
            posts().add(p);
            dirty = true;
            return id;
        } catch (Exception e) {
            TFClient.LOGGER.error("Comunidad: no se pudo hacer la foto de prueba", e);
            return "NO SE HIZO: " + e;
        }
    }

    /** Una publicación para el pad de administrador. */
    public record PostInfo(String id, String name, String caption, long time, int likes, int reports) {}

    /** Las publicaciones (las denunciadas primero si reportedOnly, si no las más nuevas), como mucho max. */
    public static java.util.List<PostInfo> adminPosts(boolean reportedOnly, int max) {
        java.util.List<PostInfo> out = new java.util.ArrayList<>();
        for (JsonElement e : posts()) {
            JsonObject p = e.getAsJsonObject();
            int r = set(p, "denuncias").size();
            if (reportedOnly && r == 0) continue;
            out.add(new PostInfo(TFJson.str(p, "id", ""), TFJson.str(p, "nombre", "?"), TFJson.str(p, "texto", ""),
                    TFJson.num(p, "fecha", 0), set(p, "likes").size(), r));
        }
        out.sort(reportedOnly ? java.util.Comparator.comparingInt(PostInfo::reports).reversed()
                : java.util.Comparator.comparingLong(PostInfo::time).reversed());
        return out.size() > max ? out.subList(0, max) : out;
    }

    public static boolean adminDelete(String id) {
        return remove(id);
    }

    /** Quita las denuncias de una foto (el staff la revisó y está bien). */
    public static void adminClearReports(String id) {
        JsonObject p = find(id);
        if (p == null) return;
        p.add("denuncias", new JsonArray());
        dirty = true;
    }

    private static boolean remove(String id) {
        JsonArray keep = new JsonArray();
        boolean found = false;
        for (JsonElement e : posts()) {
            if (TFJson.str(e.getAsJsonObject(), "id", "").equals(id)) found = true;
            else keep.add(e);
        }
        root.add("publicaciones", keep);
        dirty = true;
        try {
            Files.deleteIfExists(dir().resolve(id + ".png"));
        } catch (Exception ignored) {
            // si no está, nada
        }
        return found;
    }

    /** /tf web comunidad borrar &lt;id&gt; | denuncias (staff). */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("comunidad").requires(s -> s.hasPermission(2))
                .then(Commands.literal("borrar").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    boolean ok = remove(id);
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Foto " + id + " borrada." : "No hay ninguna foto " + id + "."), true);
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("denuncias").executes(ctx -> {
                    int n = 0;
                    for (JsonElement e : posts()) {
                        JsonObject p = e.getAsJsonObject();
                        int r = set(p, "denuncias").size();
                        if (r == 0) continue;
                        n++;
                        ctx.getSource().sendSuccess(() -> Component.literal(TFJson.str(p, "id", "") + " · " + TFJson.str(p, "nombre", "?") + " · "
                                + r + " denuncias · «" + TFJson.str(p, "texto", "") + "»"), false);
                    }
                    if (n == 0) ctx.getSource().sendSuccess(() -> Component.literal("No hay fotos denunciadas."), false);
                    return n;
                }));
    }
}
