package net.tierrasfantasticas.tfclient.skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Las skins del Armario en el servidor. Cada jugador tiene su colección (como mucho {@link #MAX_SKINS}) en
 * &lt;mundo&gt;/tfclient/skins/&lt;uuid&gt;/: skins.json (la puesta y, de cada una, id, nombre, brazos, huella, de dónde
 * salió y cuándo) y un &lt;huella&gt;.png por skin (64x64, 16 KB como mucho, ya normalizada: {@link SkinImages}). La
 * que lleva puesta se cuenta a todos los conectados (SkinNet.Active) y cada cliente la dibuja; la imagen se pide por su
 * huella (SkinNet.Want) y solo se da si la lleva alguien conectado o es de la colección de quien la pide.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class SkinServer {
    public static final int MAX_SKINS = 16;
    public static final int MAX_NAME = 24;
    /** El prefijo de las imágenes de las vistas previas (texturas que crea el cliente: SkinClient). */
    public static final String PREVIEW = "tfclient:skinprev/";

    /** Una skin de la colección. from: «archivo» o «jugador:Nombre». */
    public record Skin(String id, String name, boolean slim, String hash, String from, long added) {}

    private static final class Library {
        String worn = "";
        final List<Skin> skins = new ArrayList<>();

        Skin get(String id) {
            for (Skin s : skins) if (s.id.equals(id)) return s;
            return null;
        }

        Skin byHash(String hash) {
            for (Skin s : skins) if (s.hash.equals(hash)) return s;
            return null;
        }
    }

    private record Worn(String hash, boolean slim) {}

    private record Pending(SkinNet.Local local, long at) {}

    private static final Map<UUID, Library> LIBS = new HashMap<>();
    /** Lo que lleva cada conectado (solo los que llevan una de su colección). */
    private static final Map<UUID, Worn> WORN = new HashMap<>();
    /** Dónde está cada imagen conocida (huella → archivo). */
    private static final Map<String, Path> FILES = new HashMap<>();
    /** Las últimas imágenes leídas (para no leer el disco con cada petición). */
    private static final Map<String, byte[]> CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 64;
        }
    };
    /** Imágenes ya mandadas a cada uno (para las vistas previas: una vez por conexión). */
    private static final Map<UUID, Set<String>> SENT = new HashMap<>();
    /** La lista de la carpeta de cada uno (null: aún no ha llegado) y cuántos PNG no valían. */
    private static final Map<UUID, List<SkinNet.Local>> LOCALS = new HashMap<>();
    private static final Map<UUID, Integer> LOCAL_BAD = new HashMap<>();
    private static final Map<UUID, Long> SCANNING = new HashMap<>();
    private static final Map<UUID, Pending> UPLOADS = new HashMap<>();
    /** Quién está trayendo una skin de Mojang (y de quién). */
    private static final Map<UUID, String> FETCHING = new HashMap<>();
    private static final Map<UUID, long[]> RATE = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static MinecraftServer server;

    private SkinServer() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        clearAll();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        server = null;
        clearAll();
    }

    private static void clearAll() {
        LIBS.clear();
        WORN.clear();
        FILES.clear();
        CACHE.clear();
        SENT.clear();
        LOCALS.clear();
        LOCAL_BAD.clear();
        SCANNING.clear();
        UPLOADS.clear();
        FETCHING.clear();
        RATE.clear();
        LAST.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || server == null) return;
        Library lib = lib(player.getUUID());
        Skin s = lib.worn.isEmpty() ? null : lib.get(lib.worn);
        if (s != null) WORN.put(player.getUUID(), new Worn(s.hash, s.slim));
        // al que entra, las de los conectados; a todos, la suya (también «ninguna», por si alguien la recordaba)
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            Worn w = WORN.get(other.getUUID());
            if (w != null && other != player) SkinNet.send(player, new SkinNet.Active(other.getUUID(), w.hash, w.slim));
        }
        broadcast(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        boolean wore = WORN.remove(uuid) != null;
        LIBS.remove(uuid);
        SENT.remove(uuid);
        LOCALS.remove(uuid);
        LOCAL_BAD.remove(uuid);
        SCANNING.remove(uuid);
        UPLOADS.remove(uuid);
        RATE.remove(uuid);
        LAST.remove(uuid);
        if (wore && server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!p.getUUID().equals(uuid)) SkinNet.send(p, new SkinNet.Active(uuid, "", false));
            }
        }
    }

    /** A todos los conectados, la skin que lleva uuid. */
    private static void broadcast(UUID uuid) {
        if (server == null) return;
        Worn w = WORN.get(uuid);
        SkinNet.Active msg = new SkinNet.Active(uuid, w == null ? "" : w.hash, w != null && w.slim);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) SkinNet.send(p, msg);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Disco
    // ---------------------------------------------------------------------------------------------------------------

    private static Path dir(UUID uuid) {
        return TFJson.worldFile(server, "skins").resolve(uuid.toString());
    }

    private static Library lib(UUID uuid) {
        Library lib = LIBS.get(uuid);
        if (lib != null) return lib;
        lib = new Library();
        LIBS.put(uuid, lib);
        if (server == null) return lib;
        Path dir = dir(uuid);
        JsonObject json = TFJson.read(dir.resolve("skins.json"));
        if (json == null) return lib;
        if (json.has("skins") && json.get("skins").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("skins")) {
                if (!el.isJsonObject() || lib.skins.size() >= MAX_SKINS) continue;
                JsonObject o = el.getAsJsonObject();
                String id = TFJson.str(o, "id", ""), hash = TFJson.str(o, "huella", "");
                if (!id.matches("[a-z0-9]{1,12}") || !SkinImages.validHash(hash) || lib.get(id) != null) continue;
                Path png = dir.resolve(hash + ".png");
                if (!Files.isRegularFile(png)) continue;
                FILES.put(hash, png);
                lib.skins.add(new Skin(id, cleanName(TFJson.str(o, "nombre", "Skin")), TFJson.bool(o, "finos", false), hash,
                        TFJson.str(o, "origen", "archivo"), TFJson.num(o, "fecha", 0)));
            }
        }
        String worn = TFJson.str(json, "puesta", "");
        lib.worn = lib.get(worn) != null ? worn : "";
        return lib;
    }

    private static void save(UUID uuid) {
        Library lib = LIBS.get(uuid);
        if (lib == null || server == null) return;
        JsonArray list = new JsonArray();
        for (Skin s : lib.skins) {
            JsonObject o = new JsonObject();
            o.addProperty("id", s.id);
            o.addProperty("nombre", s.name);
            o.addProperty("finos", s.slim);
            o.addProperty("huella", s.hash);
            o.addProperty("origen", s.from);
            o.addProperty("fecha", s.added);
            list.add(o);
        }
        JsonObject json = new JsonObject();
        json.addProperty("puesta", lib.worn);
        json.add("skins", list);
        TFJson.write(dir(uuid).resolve("skins.json"), json);
    }

    private static byte[] bytes(String hash) {
        byte[] b = CACHE.get(hash);
        if (b != null) return b;
        Path p = FILES.get(hash);
        if (p == null) return null;
        try {
            if (Files.size(p) > SkinImages.MAX_PNG) return null;
            b = Files.readAllBytes(p);
        } catch (Exception e) {
            FILES.remove(hash);
            return null;
        }
        CACHE.put(hash, b);
        return b;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que usa el Armario (PadWardrobe)
    // ---------------------------------------------------------------------------------------------------------------

    public static List<Skin> skins(UUID uuid) {
        return List.copyOf(lib(uuid).skins);
    }

    public static Skin skin(UUID uuid, String id) {
        return lib(uuid).get(id);
    }

    /** El id de la que lleva puesta ("" = la suya de siempre). */
    public static String worn(UUID uuid) {
        return lib(uuid).worn;
    }

    /** La imagen de la vista previa (PadView: image) de una skin con sus brazos. */
    public static String preview(String hash, boolean slim) {
        return PREVIEW + hash + (slim ? "_f" : "_n");
    }

    /** La skin como muñeco 3D en el pad (PadSkinModel): solo la skin, sin armadura, objetos ni cosméticos. */
    public static String model(String hash, boolean slim) {
        return "tfclient:skinmodel/" + hash + (slim ? "_f" : "_n");
    }

    /** Manda las imágenes que el jugador aún no tiene (para dibujar las vistas previas del Armario). */
    public static void sendImages(ServerPlayer player, Collection<String> hashes) {
        Set<String> sent = SENT.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        for (String h : hashes) {
            if (sent.contains(h)) continue;
            byte[] b = bytes(h);
            if (b == null) continue;
            sent.add(h);
            SkinNet.send(player, new SkinNet.Png(h, b));
        }
    }

    /** ¿Tiene el TF Client con el canal de las skins? */
    public static boolean hasClient(ServerPlayer player) {
        return SkinNet.has(player);
    }

    /** Ponerse una skin de su colección (id "" = volver a la suya de siempre). */
    public static boolean wear(ServerPlayer player, String id) {
        UUID uuid = player.getUUID();
        Library lib = lib(uuid);
        Skin s = id.isEmpty() ? null : lib.get(id);
        if (!id.isEmpty() && s == null) return false;
        lib.worn = s == null ? "" : s.id;
        if (s == null) WORN.remove(uuid);
        else WORN.put(uuid, new Worn(s.hash, s.slim));
        save(uuid);
        broadcast(uuid);
        return true;
    }

    /** Brazos finos (Alex) o normales (Steve). */
    public static boolean setSlim(ServerPlayer player, String id, boolean slim) {
        UUID uuid = player.getUUID();
        Library lib = lib(uuid);
        Skin s = lib.get(id);
        if (s == null) return false;
        replace(lib, s, new Skin(s.id, s.name, slim, s.hash, s.from, s.added));
        save(uuid);
        if (lib.worn.equals(id)) {
            WORN.put(uuid, new Worn(s.hash, slim));
            broadcast(uuid);
        }
        return true;
    }

    /** Le cambia el nombre. Devuelve el nombre que se quedó (o null si no existe). */
    public static String rename(ServerPlayer player, String id, String name) {
        UUID uuid = player.getUUID();
        Library lib = lib(uuid);
        Skin s = lib.get(id);
        if (s == null) return null;
        String clean = uniqueName(lib, cleanName(name), id);
        replace(lib, s, new Skin(s.id, clean, s.slim, s.hash, s.from, s.added));
        save(uuid);
        return clean;
    }

    public static boolean delete(ServerPlayer player, String id) {
        UUID uuid = player.getUUID();
        Library lib = lib(uuid);
        Skin s = lib.get(id);
        if (s == null) return false;
        lib.skins.remove(s);
        if (lib.worn.equals(id)) {
            lib.worn = "";
            WORN.remove(uuid);
            broadcast(uuid);
        }
        save(uuid);
        try {
            Path png = dir(uuid).resolve(s.hash + ".png");
            Files.deleteIfExists(png);
            if (png.equals(FILES.get(s.hash))) FILES.remove(s.hash);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Skins: no se pudo borrar {}: {}", s.hash, e.getMessage());
        }
        return true;
    }

    /** Manda el PNG al jugador para que lo guarde en su carpeta de exportadas. Devuelve el nombre del archivo. */
    public static String export(ServerPlayer player, String id) {
        Skin s = lib(player.getUUID()).get(id);
        byte[] b = s == null ? null : bytes(s.hash);
        if (b == null) return null;
        String file = fileName(s.name);
        SkinNet.send(player, new SkinNet.Export(file, b));
        return file + ".png";
    }

    public static void openFolder(ServerPlayer player) {
        SkinNet.send(player, new SkinNet.OpenFolder(false));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Importar: de la carpeta del jugador o de un jugador de Minecraft
    // ---------------------------------------------------------------------------------------------------------------

    /** Pide al cliente la lista de su carpeta (llega en {@link #localList}). */
    public static void scan(ServerPlayer player) {
        if (!SkinNet.has(player)) return;
        Long at = SCANNING.get(player.getUUID());
        if (at != null && System.currentTimeMillis() - at < 3000) return;
        SCANNING.put(player.getUUID(), System.currentTimeMillis());
        SkinNet.send(player, new SkinNet.Scan());
    }

    /** null: aún no ha llegado la lista de su carpeta. */
    public static List<SkinNet.Local> locals(UUID uuid) {
        return LOCALS.get(uuid);
    }

    /** Cuántos PNG de su carpeta no son skins. */
    public static int localBad(UUID uuid) {
        return LOCAL_BAD.getOrDefault(uuid, 0);
    }

    /** ¿Tiene ya en la colección esa huella? (para marcar «YA LA TIENES»). */
    public static boolean owns(UUID uuid, String hash) {
        return lib(uuid).byHash(hash) != null;
    }

    /** Si está trayendo una skin de Mojang, de quién (o null). */
    public static String fetching(UUID uuid) {
        return FETCHING.get(uuid);
    }

    static void localList(ServerPlayer player, List<SkinNet.Local> list, int bad) {
        List<SkinNet.Local> ok = new ArrayList<>();
        for (SkinNet.Local l : list) {
            if (!SkinImages.validHash(l.hash())) continue;
            ok.add(new SkinNet.Local(cleanName(l.name()), l.hash(), l.slim()));
        }
        LOCALS.put(player.getUUID(), ok);
        LOCAL_BAD.put(player.getUUID(), Math.max(0, Math.min(999, bad)));
        SCANNING.remove(player.getUUID());
        refreshPad(player);
    }

    /** Importar la skin n de la lista de su carpeta: se le pide el PNG (llega en {@link #uploaded}). */
    public static String importLocal(ServerPlayer player, int n) {
        UUID uuid = player.getUUID();
        List<SkinNet.Local> list = LOCALS.get(uuid);
        if (list == null || n < 0 || n >= list.size()) return "Esa skin ya no está en tu carpeta. Pulsa ACTUALIZAR.";
        SkinNet.Local l = list.get(n);
        Skin have = lib(uuid).byHash(l.hash());
        if (have != null) return "Ya la tienes: " + have.name + ".";
        if (lib(uuid).skins.size() >= MAX_SKINS) return full();
        UPLOADS.put(uuid, new Pending(l, System.currentTimeMillis()));
        SkinNet.send(player, new SkinNet.UploadAsk(l.hash()));
        return null;
    }

    static void uploaded(ServerPlayer player, String hash, byte[] png) {
        UUID uuid = player.getUUID();
        Pending p = UPLOADS.remove(uuid);
        if (p == null || !p.local.hash().equals(hash) || System.currentTimeMillis() - p.at > 20_000) return;
        SkinImages.Result r;
        try {
            r = SkinImages.normalize(png);
        } catch (SkinImages.Bad bad) {
            TFPadNet.notice(player, bad.getMessage());
            return;
        }
        String err = add(player, r, p.local.name(), p.local.slim(), "archivo");
        TFPadNet.notice(player, err != null ? err : "Skin importada: " + lib(uuid).byHash(r.hash()).name + ". Pulsa en ella para ponértela.");
        if (err == null) PadServer.put(player, "armario.skins", "");
        refreshPad(player);
    }

    /** Traer la skin de un jugador de Minecraft por su nombre. Devuelve un aviso (o null si empezó a buscarla). */
    public static String importPlayer(ServerPlayer player, String name) {
        UUID uuid = player.getUUID();
        String n = name.trim();
        if (!SkinMojang.NAME.matcher(n).matches()) return "Escribe un nombre de Minecraft (letras, números y _).";
        if (FETCHING.containsKey(uuid)) return "Espera: aún estoy trayendo la de " + FETCHING.get(uuid) + ".";
        if (lib(uuid).skins.size() >= MAX_SKINS) return full();
        Long last = LAST.get(uuid);
        if (last != null && System.currentTimeMillis() - last < 4000) return "Espera un momento antes de traer otra.";
        LAST.put(uuid, System.currentTimeMillis());
        FETCHING.put(uuid, n);
        MinecraftServer srv = server;
        SkinMojang.fetch(n, r -> {
            if (srv == null) return;
            srv.execute(() -> {
                FETCHING.remove(uuid);
                ServerPlayer p = srv.getPlayerList().getPlayer(uuid);
                if (p == null || server != srv) return;
                if (r.skin() == null) {
                    TFPadNet.notice(p, r.error());
                } else {
                    String err = add(p, r.skin(), r.name(), r.slim(), "jugador:" + r.name());
                    if (err == null) PadServer.put(p, "armario.skins", "");
                    TFPadNet.notice(p, err != null ? err : "Skin de " + r.name() + " guardada en tu armario.");
                }
                refreshPad(p);
            });
        });
        return null;
    }

    /** Guarda una skin nueva en la colección. Devuelve el error (o null si se guardó). */
    private static String add(ServerPlayer player, SkinImages.Result r, String name, boolean slim, String from) {
        UUID uuid = player.getUUID();
        Library lib = lib(uuid);
        Skin have = lib.byHash(r.hash());
        if (have != null) return "Ya la tienes: " + have.name + ".";
        if (lib.skins.size() >= MAX_SKINS) return full();
        Path png = dir(uuid).resolve(r.hash() + ".png");
        try {
            Files.createDirectories(png.getParent());
            Files.write(png, r.png());
        } catch (Exception e) {
            TFClient.LOGGER.error("TF Skins: no se pudo guardar {}", png, e);
            return "No se pudo guardar la skin. Avisa al staff.";
        }
        FILES.put(r.hash(), png);
        CACHE.put(r.hash(), r.png());
        String id;
        do {
            id = Long.toString(RANDOM.nextLong() & Long.MAX_VALUE, 36);
            id = id.substring(0, Math.min(8, id.length()));
        } while (lib.get(id) != null);
        lib.skins.add(new Skin(id, uniqueName(lib, cleanName(name), null), slim, r.hash(), from, System.currentTimeMillis()));
        save(uuid);
        return null;
    }

    private static String full() {
        return "Tu armario de skins está lleno (" + MAX_SKINS + "). Borra una para hacer sitio.";
    }

    /** Si tiene el Armario abierto en SKINS, se le vuelve a dibujar. */
    private static void refreshPad(ServerPlayer player) {
        String tab = PadServer.get(player, "armario.tab", "");
        if (tab.startsWith("skins")) PadServer.refresh(player, "armario", tab);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Imágenes para los demás
    // ---------------------------------------------------------------------------------------------------------------

    /** Un cliente pide una imagen: solo las que lleva alguien conectado o las de su colección; 40 cada 10 s. */
    static void want(ServerPlayer player, String hash) {
        if (!SkinImages.validHash(hash)) return;
        long now = System.currentTimeMillis();
        long[] r = RATE.computeIfAbsent(player.getUUID(), k -> new long[] {now, 0});
        if (now - r[0] > 10_000) {
            r[0] = now;
            r[1] = 0;
        }
        if (++r[1] > 40) return;
        boolean allowed = lib(player.getUUID()).byHash(hash) != null;
        for (Worn w : WORN.values()) allowed |= w.hash.equals(hash);
        if (!allowed) return;
        byte[] b = bytes(hash);
        if (b != null) SkinNet.send(player, new SkinNet.Png(hash, b));
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static void replace(Library lib, Skin old, Skin now) {
        int i = lib.skins.indexOf(old);
        if (i >= 0) lib.skins.set(i, now);
    }

    /** Un nombre que se puede enseñar: sin códigos de color ni caracteres raros, de MAX_NAME como mucho. */
    public static String cleanName(String name) {
        StringBuilder b = new StringBuilder();
        for (char c : (name == null ? "" : name).toCharArray()) {
            if (c == '§' || Character.isISOControl(c)) continue;
            b.append(c);
        }
        String s = b.toString().replaceAll("\\s+", " ").trim();
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME).trim();
        return s.isEmpty() ? "Skin" : s;
    }

    private static String uniqueName(Library lib, String name, String except) {
        String n = name;
        for (int i = 2; taken(lib, n, except); i++) {
            String suffix = " " + i;
            n = (name.length() + suffix.length() > MAX_NAME ? name.substring(0, MAX_NAME - suffix.length()) : name) + suffix;
        }
        return n;
    }

    private static boolean taken(Library lib, String name, String except) {
        for (Skin s : lib.skins) if (!s.id.equals(except) && s.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** El nombre del archivo exportado: solo letras, números, espacios, - y _. */
    private static String fileName(String name) {
        StringBuilder b = new StringBuilder();
        for (char c : name.toCharArray()) b.append(Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '_' ? c : '_');
        String s = b.toString().trim();
        if (s.length() > 40) s = s.substring(0, 40);
        return s.isEmpty() ? "skin" : s;
    }
}
