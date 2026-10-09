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
 * datos (comunidad.json: autor, texto, fecha, likes y denuncias), y se las manda a quien las mira.
 * <ul>
 *   <li>Solo PNG de hasta 640x360 y 600 KB; una cada 90 s y como mucho 40 por jugador (las más viejas no se tocan).</li>
 *   <li>Like: uno por jugador y foto (no a las tuyas). El autor suma likes en sus estadísticas.</li>
 *   <li>Borrar: el autor o el staff (nivel 2). Denunciar: avisa al staff conectado; con 5 denuncias se oculta.</li>
 * </ul>
 */
public final class PadCommunityServer {
    private static final int PER_PAGE = 10;
    private static final int MAX_PER_PLAYER = 40;
    private static final long POST_COOLDOWN = 90_000;
    private static final int HIDE_AT = 5;
    private static final int MAX_W = 1280, MAX_H = 720;

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
            UPLOADS.clear();
            RATE.clear();
            LIKE_TOLD.clear();
            dirty = false;
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
        String t = s == null ? "" : s.replaceAll("[\\p{Cntrl}§]", "").trim();
        return t.length() > 120 ? t.substring(0, 120) : t;
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
            out.add(new PadCommunityNet.Post(TFJson.str(p, "id", ""), author, TFJson.str(p, "nombre", "?"), TFJson.str(p, "texto", ""),
                    TFJson.num(p, "fecha", 0), likes.size(), likes.contains(me), mine, mine || staff));
        }
        PadCommunityNet.toPlayer(player, new PadCommunityNet.Feed(tab, pg, pages, list.size(), out));
    }

    static void sendImage(ServerPlayer player, String id) {
        if (!id.matches("[a-z0-9]{1,20}") || find(id) == null) return;
        long now = System.currentTimeMillis();
        long[] r = IMG_RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0});
        if (now - r[0] > 10_000) {
            r[0] = now;
            r[1] = 0;
        }
        if (++r[1] > 40) return;
        byte[] data;
        try {
            data = Files.readAllBytes(dir().resolve(id + ".png"));
        } catch (Exception e) {
            return;
        }
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
            switch (m.action()) {
                case "like" -> {
                    if (author.equals(me)) break;
                    Set<String> likes = set(p, "likes");
                    UUID au = null;
                    try {
                        au = UUID.fromString(author);
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
