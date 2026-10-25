/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.rastreo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import net.tierrasfantasticas.tfclient.items.TFRevocations;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * (1.3.142, el dueño: «a uno le robaron el kit de un cofre; los objetos están marcados: necesito encontrar esas mierdas»)
 * Dónde está ahora cada objeto comprado en la web (marca TFOrder) o ligado a un jugador (TFOwner): en los jugadores
 * conectados (puesto, en la mano, inventario, cofre de ender, accesorios, cursor), en los desconectados (su
 * playerdata) y en todo el mundo guardado en disco (cofres, barriles, shulkers dentro de cofres, hornos, contenedores
 * de los mods, marcos, vagonetas, burros y objetos en el suelo), de todos los mundos (también los de Multiverse y los de
 * los mods). Se lee del disco en un hilo aparte (el servidor no se frena); al escanear a mano se guarda antes el mundo.
 * También recupera: quita lo de una compra de donde esté y se lo da a su dueño (a los desconectados se lo quita al
 * entrar). Lo usan la app Rastreador del pad de administrador (PadAdminTracker) y /tf buscar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFSearch {
    /**
     * Un objeto encontrado. place: puesto, mano, inventario, ender, accesorios, cursor, contenedor, entidad, suelo.
     * holder: el jugador o el bloque/entidad que lo tiene; holderUuid: el del jugador (o ""). dim: el mundo de Minecraft
     * (para ir allí; "" si no se sabe) y world: la carpeta. inside: si va dentro de otro objeto (una shulker…).
     */
    public record Found(String order, String ownerUuid, String ownerName, String item, int count, String place, String holder,
                        String holderUuid, boolean online, String world, String dim, int x, int y, int z, String inside) {
        public boolean withPlayer() {
            return !holderUuid.isEmpty();
        }
    }

    public record Scan(long at, long tookMs, List<Found> found, int regionFiles, int chunks, int players, String note) {}

    private static volatile Scan last;
    private static volatile boolean running;
    private static volatile String progress = "";
    private static final List<Runnable> WHEN_DONE = new ArrayList<>();
    private static MinecraftServer server;
    private static long nextAuto;

    private TFSearch() {}

    public static Scan last() {
        return last;
    }

    public static boolean running() {
        return running;
    }

    public static String progress() {
        return progress;
    }

    // ------------------------------------------------------------------------------------------------ automático

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        last = null;
        nextAuto = System.currentTimeMillis() + 60_000L; // el primero, al minuto de arrancar
        Pending.load(server);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        server = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || running) return;
        if (System.currentTimeMillis() < nextAuto) return;
        nextAuto = System.currentTimeMillis() + 30 * 60_000L; // y cada media hora (con lo guardado por el autoguardado)
        start(server, false, null);
    }

    // ------------------------------------------------------------------------------------------------ escanear

    /**
     * Empieza a buscar (si no estaba ya). save: guardar antes el mundo (lo último que se movió). done: en el hilo del
     * servidor, al acabar.
     */
    public static synchronized boolean start(MinecraftServer srv, boolean save, @Nullable Runnable done) {
        if (done != null) WHEN_DONE.add(done);
        if (running) return false;
        running = true;
        progress = "jugadores conectados";
        long t0 = System.currentTimeMillis();
        if (save) {
            try {
                srv.saveEverything(true, false, true);
            } catch (Throwable e) {
                TFClient.LOGGER.warn("TF Rastreador: no se pudo guardar el mundo antes de buscar: {}", e.toString());
            }
        }
        List<Found> found = new ArrayList<>();
        Set<String> online = new HashSet<>();
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            online.add(p.getUUID().toString());
            try {
                scanOnline(p, found);
            } catch (Throwable e) {
                TFClient.LOGGER.warn("TF Rastreador: no se pudo mirar a {}: {}", p.getGameProfile().getName(), e.toString());
            }
        }
        Path root = srv.getServerDirectory().toPath().toAbsolutePath().normalize();
        Path playerdata = srv.getWorldPath(LevelResource.PLAYER_DATA_DIR).toAbsolutePath().normalize();
        String levelName = srv.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        Map<String, String> dims = new HashMap<>();
        for (ServerLevel l : srv.getAllLevels()) dims.put(l.dimension().location().getPath().toLowerCase(Locale.ROOT), l.dimension().location().toString());
        Map<String, String> names = new HashMap<>();
        for (Found f : found) names.put(f.holderUuid, f.holder);
        TFTracker.IO.execute(() -> {
            // con el mundo recién guardado, dar un momento a que se escriba
            if (save) sleep(2500);
            int[] stats = new int[3];
            String note = "";
            try {
                scanPlayerdata(playerdata, online, found, stats);
                scanWorlds(root, levelName, dims, found, stats);
            } catch (Throwable e) {
                note = "error: " + e;
                TFClient.LOGGER.error("TF Rastreador: fallo buscando en el mundo", e);
            }
            Scan scan = new Scan(System.currentTimeMillis(), System.currentTimeMillis() - t0, List.copyOf(found), stats[0], stats[1], stats[2], note);
            srv.execute(() -> {
                last = scan;
                running = false;
                progress = "";
                TFClient.LOGGER.info("TF Rastreador: {} objetos comprados o ligados encontrados ({} archivos del mundo, {} chunks, {} jugadores, {} ms)",
                        scan.found.size(), scan.regionFiles, scan.chunks, scan.players, scan.tookMs);
                List<Runnable> todo;
                synchronized (TFSearch.class) {
                    todo = new ArrayList<>(WHEN_DONE);
                    WHEN_DONE.clear();
                }
                for (Runnable r : todo) {
                    try {
                        r.run();
                    } catch (Throwable e) {
                        TFClient.LOGGER.warn("TF Rastreador: {}", e.toString());
                    }
                }
            });
        });
        return true;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------------------------------------ qué cuenta

    /** ¿Es un objeto comprado o ligado? Devuelve {pedido, uuid del dueño, nombre del dueño} o null. */
    @Nullable
    static String[] marks(@Nullable CompoundTag tag) {
        if (tag == null) return null;
        String order = tag.contains(TFRevocations.ORDER, Tag.TAG_STRING) ? tag.getString(TFRevocations.ORDER) : "";
        String owner = "";
        if (tag.contains("TFOwner", Tag.TAG_INT_ARRAY)) {
            try {
                owner = NbtUtils.loadUUID(tag.get("TFOwner")).toString();
            } catch (Exception ignored) {
                // mal guardado
            }
        }
        if (order.isEmpty() && owner.isEmpty()) return null;
        return new String[] {order, owner, tag.contains("TFOwnerName", Tag.TAG_STRING) ? tag.getString("TFOwnerName") : ""};
    }

    /** Lo que se encuentra al recorrer un NBT: cada objeto marcado (y lo que lleva dentro, como una shulker). */
    private interface Sink {
        void item(String[] marks, String id, int count, String inside);
    }

    private static void visit(Tag t, String inside, Sink sink) {
        if (t instanceof CompoundTag c) {
            if (c.contains("id", Tag.TAG_STRING) && c.contains("Count", Tag.TAG_ANY_NUMERIC)) {
                CompoundTag tag = c.contains("tag", Tag.TAG_COMPOUND) ? c.getCompound("tag") : null;
                String[] m = marks(tag);
                String id = c.getString("id");
                if (m != null) sink.item(m, id, c.getInt("Count"), inside);
                if (tag != null) visit(tag, inside.isEmpty() ? short_(id) : inside + " › " + short_(id), sink);
                return;
            }
            for (String k : c.getAllKeys()) {
                Tag child = c.get(k);
                if (child instanceof CompoundTag || child instanceof ListTag) visit(child, inside, sink);
            }
        } else if (t instanceof ListTag l) {
            if (l.getElementType() != Tag.TAG_COMPOUND && l.getElementType() != Tag.TAG_LIST) return;
            for (Tag e : l) visit(e, inside, sink);
        }
    }

    private static String short_(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    // ------------------------------------------------------------------------------------------------ conectados

    private static void scanOnline(ServerPlayer p, List<Found> out) {
        String name = p.getGameProfile().getName(), uuid = p.getUUID().toString();
        var inv = p.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            onlineStack(inv.items.get(i), i == inv.selected ? "mano" : "inventario", name, uuid, p, out);
        }
        for (ItemStack s : inv.armor) onlineStack(s, "puesto", name, uuid, p, out);
        for (ItemStack s : inv.offhand) onlineStack(s, "mano", name, uuid, p, out);
        var ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) onlineStack(ender.getItem(i), "ender", name, uuid, p, out);
        onlineStack(p.containerMenu.getCarried(), "cursor", name, uuid, p, out);
        // los huecos de accesorios (Accessories / Curios), normales y cosméticos
        List<ItemStack> worn = new ArrayList<>();
        Predicate<ItemStack> any = s -> !s.isEmpty();
        try {
            if (net.minecraftforge.fml.ModList.get().isLoaded("accessories")) {
                net.tierrasfantasticas.tfclient.items.TFAccessoryLookup.fromAccessories(p, any, worn);
                net.tierrasfantasticas.tfclient.items.TFAccessoryLookup.cosmeticsFromAccessories(p, any, worn);
            }
        } catch (Throwable ignored) {
            // otra versión
        }
        try {
            if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                net.tierrasfantasticas.tfclient.items.TFAccessoryLookup.fromCurios(p, any, worn, false);
                net.tierrasfantasticas.tfclient.items.TFAccessoryLookup.cosmeticsFromCurios(p, any, worn);
            }
        } catch (Throwable ignored) {
            // otra versión
        }
        for (ItemStack s : worn) onlineStack(s, "accesorios", name, uuid, p, out);
    }

    private static void onlineStack(ItemStack s, String place, String name, String uuid, ServerPlayer p, List<Found> out) {
        if (s.isEmpty()) return;
        String world = TFTracker.world(p.serverLevel());
        BlockPos at = p.blockPosition();
        visit(s.save(new CompoundTag()), "", (m, id, count, inside) -> out.add(new Found(m[0], m[1], m[2], id, count, place, name, uuid, true,
                world, world, at.getX(), at.getY(), at.getZ(), inside)));
    }

    // ------------------------------------------------------------------------------------------------ desconectados

    private static void scanPlayerdata(Path dir, Set<String> online, List<Found> out, int[] stats) {
        if (!Files.isDirectory(dir)) return;
        progress = "jugadores desconectados";
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString();
                if (!n.endsWith(".dat")) continue;
                String uuid = n.substring(0, n.length() - 4);
                if (online.contains(uuid)) continue;
                CompoundTag root;
                try (InputStream in = Files.newInputStream(f)) {
                    root = NbtIo.readCompressed(in);
                } catch (Exception e) {
                    continue;
                }
                stats[2]++;
                String name = root.contains("bukkit", Tag.TAG_COMPOUND) ? root.getCompound("bukkit").getString("lastKnownName") : "";
                if (name.isEmpty()) name = nameOf(uuid);
                String world = root.contains("Dimension", Tag.TAG_STRING) ? root.getString("Dimension") : "";
                ListTag pos = root.getList("Pos", Tag.TAG_DOUBLE);
                int x = pos.size() == 3 ? (int) Math.floor(pos.getDouble(0)) : 0, y = pos.size() == 3 ? (int) Math.floor(pos.getDouble(1)) : 0,
                        z = pos.size() == 3 ? (int) Math.floor(pos.getDouble(2)) : 0;
                String who = name;
                for (String key : root.getAllKeys()) {
                    Tag t = root.get(key);
                    if (!(t instanceof CompoundTag) && !(t instanceof ListTag)) continue;
                    if (key.equals("Inventory") && t instanceof ListTag inv) {
                        for (Tag e : inv) {
                            if (!(e instanceof CompoundTag c)) continue;
                            int slot = c.getByte("Slot");
                            String place = slot >= 100 && slot <= 103 ? "puesto" : slot == -106 ? "mano" : "inventario";
                            visit(c, "", (m, id, count, inside) -> out.add(new Found(m[0], m[1], m[2], id, count, place, who, uuid, false,
                                    world, world, x, y, z, inside)));
                        }
                        continue;
                    }
                    String place = key.equals("EnderItems") ? "ender" : key.equals("Inventory") ? "inventario" : "accesorios";
                    visit(t, "", (m, id, count, inside) -> out.add(new Found(m[0], m[1], m[2], id, count, place, who, uuid, false,
                            world, world, x, y, z, inside)));
                }
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Rastreador: no se pudo leer playerdata: {}", e.toString());
        }
    }

    static String nameOf(String uuid) {
        try {
            String n = net.tierrasfantasticas.tfclient.pad.server.PadStats.nameOf(UUID.fromString(uuid));
            if (!"?".equals(n)) return n;
            MinecraftServer srv = server;
            if (srv != null && srv.getProfileCache() != null) {
                var p = srv.getProfileCache().get(UUID.fromString(uuid));
                if (p.isPresent()) return p.get().getName();
            }
        } catch (Exception ignored) {
            // uuid raro
        }
        return uuid.substring(0, Math.min(8, uuid.length()));
    }

    // ------------------------------------------------------------------------------------------------ el mundo en disco

    private static final Set<String> SKIP = Set.of("mods", "config", "logs", "crash-reports", "libraries", "plugins", "backups", "backup",
            "automodpack", "defaultconfigs", "kubejs", "cache", ".git", "playerdata", "stats", "advancements", "datapacks");

    private static void scanWorlds(Path root, String levelName, Map<String, String> dims, List<Found> out, int[] stats) throws Exception {
        List<Path> folders = new ArrayList<>();
        findFolders(root, 0, folders);
        List<Path> files = new ArrayList<>();
        for (Path d : folders) {
            try (Stream<Path> s = Files.list(d)) {
                s.filter(f -> f.getFileName().toString().endsWith(".mca")).forEach(files::add);
            }
        }
        int done = 0;
        for (Path f : files) {
            done++;
            progress = "mundo " + (done * 100 / Math.max(1, files.size())) + " % (" + done + " de " + files.size() + " archivos)";
            Path folder = f.getParent();
            String label = root.relativize(folder.getParent()).toString().replace('\\', '/');
            String dim = dimOf(label, levelName, dims);
            boolean entities = folder.getFileName().toString().equals("entities");
            try {
                stats[1] += readRegion(f, entities, label, dim, out);
                stats[0]++;
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Rastreador: no se pudo leer {}: {}", f, e.toString());
            }
        }
    }

    private static void findFolders(Path dir, int depth, List<Path> out) {
        if (depth > 5) return;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isDirectory(p)) continue;
                String n = p.getFileName().toString();
                if (SKIP.contains(n.toLowerCase(Locale.ROOT))) continue;
                if (n.equals("region") || n.equals("entities")) {
                    out.add(p);
                    continue;
                }
                findFolders(p, depth + 1, out);
            }
        } catch (Exception ignored) {
            // sin permiso
        }
    }

    /** El mundo de Minecraft de una carpeta (world → overworld, world_nether/DIM-1 → nether, dimensions/ns/x → ns:x…). */
    static String dimOf(String label, String levelName, Map<String, String> dims) {
        int d = label.indexOf("dimensions/");
        if (d >= 0) {
            String[] p = label.substring(d + 11).split("/", 2);
            if (p.length == 2) return p[0] + ":" + p[1];
        }
        String[] parts = label.split("/");
        String lastPart = parts[parts.length - 1];
        String base = parts.length > 1 ? parts[parts.length - 2] : lastPart;
        if (lastPart.equals("DIM-1")) return base.equals(levelName) || base.equals(levelName + "_nether") ? "minecraft:the_nether" : dims.getOrDefault(base.toLowerCase(Locale.ROOT), "");
        if (lastPart.equals("DIM1")) return base.equals(levelName) || base.equals(levelName + "_the_end") ? "minecraft:the_end" : dims.getOrDefault(base.toLowerCase(Locale.ROOT), "");
        if (lastPart.equals(levelName)) return "minecraft:overworld"; // «world» (o «saves/Mundo» en un juego local)
        return dims.getOrDefault(lastPart.toLowerCase(Locale.ROOT), "");
    }

    /** Lee un archivo de región (.mca) sin tocarlo: sus chunks, y en ellos los contenedores o las entidades. */
    private static int readRegion(Path f, boolean entities, String label, String dim, List<Found> out) throws Exception {
        byte[] all = Files.readAllBytes(f);
        if (all.length < 8192) return 0;
        int chunks = 0;
        for (int i = 0; i < 1024; i++) {
            int off = ((all[i * 4] & 255) << 16) | ((all[i * 4 + 1] & 255) << 8) | (all[i * 4 + 2] & 255);
            int sectors = all[i * 4 + 3] & 255;
            if (off < 2 || sectors == 0) continue;
            int pos = off * 4096;
            if (pos + 5 > all.length) continue;
            int len = ((all[pos] & 255) << 24) | ((all[pos + 1] & 255) << 16) | ((all[pos + 2] & 255) << 8) | (all[pos + 3] & 255);
            int type = all[pos + 4] & 255;
            InputStream raw;
            if ((type & 128) != 0) {
                // chunk grande: va aparte, en c.X.Z.mcc
                String[] rp = f.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(rp[1]), rz = Integer.parseInt(rp[2]);
                Path ext = f.resolveSibling("c." + (rx * 32 + i % 32) + "." + (rz * 32 + i / 32) + ".mcc");
                if (!Files.exists(ext)) continue;
                raw = Files.newInputStream(ext);
            } else {
                if (len <= 1 || pos + 4 + len > all.length) continue;
                raw = new ByteArrayInputStream(all, pos + 5, len - 1);
            }
            int kind = type & 127;
            InputStream in = kind == 1 ? new GZIPInputStream(raw) : kind == 2 ? new InflaterInputStream(raw) : kind == 3 ? raw : null;
            if (in == null) continue;
            CompoundTag chunk;
            try (DataInputStream data = new DataInputStream(new BufferedInputStream(in))) {
                chunk = NbtIo.read(data);
            } catch (Exception e) {
                continue;
            }
            chunks++;
            if (entities) readEntities(chunk.getList("Entities", Tag.TAG_COMPOUND), label, dim, out);
            else {
                ListTag bes = chunk.contains("block_entities", Tag.TAG_LIST) ? chunk.getList("block_entities", Tag.TAG_COMPOUND)
                        : chunk.getCompound("Level").getList("TileEntities", Tag.TAG_COMPOUND);
                for (Tag t : bes) {
                    CompoundTag be = (CompoundTag) t;
                    String id = be.getString("id");
                    int x = be.getInt("x"), y = be.getInt("y"), z = be.getInt("z");
                    visit(be, "", (m, item, count, inside) -> out.add(new Found(m[0], m[1], m[2], item, count, "contenedor", id, "", false,
                            label, dim, x, y, z, inside)));
                }
                // los de antes de 1.17 llevaban las entidades en el chunk
                if (chunk.contains("Level", Tag.TAG_COMPOUND)) readEntities(chunk.getCompound("Level").getList("Entities", Tag.TAG_COMPOUND), label, dim, out);
            }
        }
        return chunks;
    }

    private static void readEntities(ListTag list, String label, String dim, List<Found> out) {
        for (Tag t : list) {
            CompoundTag e = (CompoundTag) t;
            String id = e.getString("id");
            ListTag pos = e.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() != 3) continue;
            int x = (int) Math.floor(pos.getDouble(0)), y = (int) Math.floor(pos.getDouble(1)), z = (int) Math.floor(pos.getDouble(2));
            String place = id.equals("minecraft:item") ? "suelo" : "entidad";
            visit(e, "", (m, item, count, inside) -> out.add(new Found(m[0], m[1], m[2], item, count, place, id, "", false, label, dim, x, y, z, inside)));
        }
    }

    // ------------------------------------------------------------------------------------------------ recuperar

    /** Qué recuperar: lo de un pedido (o, sin pedido, lo ligado a un dueño), de un objeto o de todos (item = ""). */
    public record Crit(String order, String owner, String item) {
        boolean test(ItemStack s) {
            if (s.isEmpty()) return false;
            String[] m = marks(s.getTag());
            if (m == null) return false;
            if (!item.isEmpty() && !item.equals(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem())))) return false;
            return !order.isEmpty() ? order.equals(m[0]) : !owner.isEmpty() && owner.equals(m[1]);
        }
    }

    /**
     * Quita de donde estén (menos de su dueño) las copias encontradas que cumplen crit y se las da a «to». Lo de
     * jugadores desconectados se le quita al entrar. Devuelve {recuperados, pendientes, sin poder}.
     */
    public static int[] recover(MinecraftServer srv, Crit crit, UUID to) {
        int[] r = new int[3];
        Scan scan = last;
        if (scan == null) return r;
        Set<String> doneSpots = new HashSet<>();
        for (Found f : scan.found) {
            if (!(crit.order.isEmpty() || crit.order.equals(f.order)) || !(crit.item.isEmpty() || crit.item.equals(f.item))) continue;
            if (crit.order.isEmpty() && !crit.owner.equals(f.ownerUuid)) continue;
            if (f.withPlayer() && f.holderUuid.equals(to.toString())) continue; // ya lo tiene él
            String spot = f.withPlayer() ? "jugador|" + f.holderUuid : f.place + "|" + f.dim + "|" + f.x + "|" + f.y + "|" + f.z;
            if (!doneSpots.add(spot)) continue;
            List<ItemStack> got = new ArrayList<>();
            try {
                if (f.withPlayer()) {
                    ServerPlayer p = srv.getPlayerList().getPlayer(UUID.fromString(f.holderUuid));
                    if (p != null) takeFromPlayer(p, crit, got);
                    else {
                        Pending.add(srv, f.holderUuid, crit, to);
                        r[1]++;
                        continue;
                    }
                } else {
                    ServerLevel level = levelOf(srv, f.dim);
                    if (level == null) {
                        r[2]++;
                        continue;
                    }
                    BlockPos pos = new BlockPos(f.x, f.y, f.z);
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // la carga si no lo estaba
                    if (f.place.equals("contenedor")) takeFromBlock(level, pos, crit, got);
                    else takeFromEntities(level, pos, crit, got);
                }
            } catch (Throwable e) {
                TFClient.LOGGER.warn("TF Rastreador: no se pudo recuperar en {}: {}", spot, e.toString());
            }
            if (got.isEmpty()) r[2]++;
            for (ItemStack s : got) {
                TFBinding.returnTo(srv, to, s);
                r[0] += s.getCount();
            }
            TFTracker.logAdmin(srv, f, got);
        }
        return r;
    }

    @Nullable
    static ServerLevel levelOf(MinecraftServer srv, String dim) {
        if (dim == null || dim.isEmpty()) return null;
        for (ServerLevel l : srv.getAllLevels()) if (l.dimension().location().toString().equals(dim)) return l;
        return null;
    }

    static void takeFromPlayer(ServerPlayer p, Crit crit, List<ItemStack> got) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) inv.setItem(i, take(inv.getItem(i), crit, got));
        var ender = p.getEnderChestInventory();
        for (int i = 0; i < ender.getContainerSize(); i++) ender.setItem(i, take(ender.getItem(i), crit, got));
        ItemStack carried = p.containerMenu.getCarried();
        if (!carried.isEmpty()) p.containerMenu.setCarried(take(carried, crit, got));
        p.containerMenu.broadcastChanges();
    }

    private static void takeFromBlock(ServerLevel level, BlockPos pos, Crit crit, List<ItemStack> got) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        if (be instanceof Container c) {
            for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, take(c.getItem(i), crit, got));
            c.setChanged();
            return;
        }
        be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
            if (!(h instanceof IItemHandlerModifiable mh)) return;
            for (int i = 0; i < mh.getSlots(); i++) mh.setStackInSlot(i, take(mh.getStackInSlot(i), crit, got));
            be.setChanged();
        });
    }

    private static void takeFromEntities(ServerLevel level, BlockPos pos, Crit crit, List<ItemStack> got) {
        for (Entity e : level.getEntities((Entity) null, new AABB(pos).inflate(2.5), x -> true)) {
            if (e instanceof ItemEntity ie) {
                ItemStack rest = take(ie.getItem(), crit, got);
                if (rest.isEmpty()) ie.discard();
                else ie.setItem(rest);
            } else if (e instanceof ItemFrame fr) {
                fr.setItem(take(fr.getItem(), crit, got), false);
            } else if (e instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) c.setItem(i, take(c.getItem(i), crit, got));
            }
        }
    }

    /** Lo que queda de ese montón tras quitar lo que cumple crit (también lo que lleva dentro, como una shulker). */
    static ItemStack take(ItemStack s, Crit crit, List<ItemStack> got) {
        if (s.isEmpty()) return s;
        if (crit.test(s)) {
            got.add(s.copy());
            return ItemStack.EMPTY;
        }
        CompoundTag tag = s.getTag();
        if (tag != null && tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            CompoundTag bet = tag.getCompound("BlockEntityTag");
            if (bet.contains("Items", Tag.TAG_LIST)) {
                ListTag items = bet.getList("Items", Tag.TAG_COMPOUND);
                for (int i = items.size() - 1; i >= 0; i--) {
                    ItemStack inner = ItemStack.of(items.getCompound(i));
                    if (crit.test(inner)) {
                        got.add(inner);
                        items.remove(i);
                    }
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------------------------------------------ al entrar

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Pending.apply(p);
    }

    /** Lo que hay que quitarle a un jugador desconectado al entrar (y a quién dárselo). */
    static final class Pending {
        private static final Map<String, List<String[]>> TODO = new HashMap<>();

        static Path file(MinecraftServer srv) {
            return TFJson.worldFile(srv, "rastreador-pendiente.json");
        }

        static void load(MinecraftServer srv) {
            TODO.clear();
            JsonObject root = TFJson.read(file(srv));
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                List<String[]> list = new ArrayList<>();
                for (JsonElement x : e.getValue().getAsJsonArray()) {
                    JsonObject o = x.getAsJsonObject();
                    list.add(new String[] {TFJson.str(o, "pedido", ""), TFJson.str(o, "dueno", ""), TFJson.str(o, "objeto", ""), TFJson.str(o, "para", "")});
                }
                TODO.put(e.getKey(), list);
            }
        }

        static void add(MinecraftServer srv, String holder, Crit crit, UUID to) {
            TODO.computeIfAbsent(holder, k -> new ArrayList<>()).add(new String[] {crit.order, crit.owner, crit.item, to.toString()});
            save(srv);
        }

        static void apply(ServerPlayer p) {
            List<String[]> list = TODO.remove(p.getUUID().toString());
            if (list == null) return;
            for (String[] c : list) {
                List<ItemStack> got = new ArrayList<>();
                takeFromPlayer(p, new Crit(c[0], c[1], c[2]), got);
                for (ItemStack s : got) TFBinding.returnTo(p.server, UUID.fromString(c[3]), s);
                if (!got.isEmpty()) TFClient.LOGGER.warn("TF Rastreador: a {} se le quitaron {} objetos que eran de otro (pedido {})",
                        p.getGameProfile().getName(), got.size(), c[0]);
            }
            save(p.server);
        }

        static void save(MinecraftServer srv) {
            JsonObject root = new JsonObject();
            TODO.forEach((k, v) -> {
                JsonArray a = new JsonArray();
                for (String[] c : v) {
                    JsonObject o = new JsonObject();
                    o.addProperty("pedido", c[0]);
                    o.addProperty("dueno", c[1]);
                    o.addProperty("objeto", c[2]);
                    o.addProperty("para", c[3]);
                    a.add(o);
                }
                root.add(k, a);
            });
            TFJson.write(file(srv), root);
        }
    }

    // ------------------------------------------------------------------------------------------------ /tf buscar

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("buscar").requires(s -> s.hasPermission(2))
                .then(Commands.argument("quien", StringArgumentType.word()).executes(c -> {
                    CommandSourceStack src = c.getSource();
                    String who = StringArgumentType.getString(c, "quien");
                    src.sendSuccess(() -> Component.literal("Buscando en todo el mundo lo de «" + who + "»… (puede tardar un poco)").withStyle(ChatFormatting.GOLD), false);
                    start(src.getServer(), true, () -> report(src, who));
                    return 1;
                }));
    }

    private static void report(CommandSourceStack src, String who) {
        Scan scan = last;
        if (scan == null) return;
        List<Found> mine = new ArrayList<>();
        for (Found f : scan.found) {
            if (f.order.equalsIgnoreCase(who) || f.ownerName.equalsIgnoreCase(who) || f.ownerUuid.equalsIgnoreCase(who)) mine.add(f);
        }
        src.sendSuccess(() -> Component.literal(mine.size() + " objetos de «" + who + "» (más detalle en el pad de administrador › RASTREADOR):")
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < Math.min(40, mine.size()); i++) {
            String line = describe(mine.get(i));
            src.sendSuccess(() -> Component.literal(" • " + line), false);
        }
    }

    /** «Espada Dragón Mecha ×1 · en un cofre (minecraft:chest) · world 120 64 -30» */
    public static String describe(Found f) {
        return itemName(f.item) + " ×" + f.count + " · " + where(f);
    }

    public static String itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        var it = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
        return it == null || it == net.minecraft.world.item.Items.AIR ? id : new ItemStack(it).getHoverName().getString();
    }

    public static String where(Found f) {
        String in = f.inside.isEmpty() ? "" : " (dentro de " + f.inside + ")";
        String at = " · " + (f.dim.isEmpty() ? f.world : f.dim) + " " + f.x + " " + f.y + " " + f.z;
        return switch (f.place) {
            case "puesto" -> "puesto por " + f.holder + (f.online ? " (conectado)" : "") + in;
            case "mano" -> "en la mano de " + f.holder + in;
            case "inventario" -> "en el inventario de " + f.holder + in;
            case "ender" -> "en el cofre de ender de " + f.holder + in;
            case "accesorios" -> "puesto en accesorios por " + f.holder + in;
            case "cursor" -> "en el cursor de " + f.holder + in;
            case "contenedor" -> "en " + blockName(f.holder) + in + at;
            case "suelo" -> "tirado en el suelo" + at;
            default -> "en " + f.holder + in + at;
        };
    }

    public static String blockName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        var b = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(rl);
        return b == null || b == net.minecraft.world.level.block.Blocks.AIR ? id : b.getName().getString();
    }

    /** Para la prueba en el juego. */
    public static void testReset() {
        last = null;
    }
}
