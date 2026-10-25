/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.rastreo;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import net.tierrasfantasticas.tfclient.items.TFRevocations;

/**
 * (1.3.142, el dueño: «a uno le robaron el kit de un cofre; quiero un módulo rastreador») El rastreador: apunta quién
 * saca o mete qué en cada contenedor (cofre, barril, shulker, horno, los de los mods, vagonetas, burros…) y quién
 * rompe un contenedor con cosas dentro. Va en &lt;mundo&gt;/tfclient/rastreador/&lt;día&gt;.log (60 días). Se consulta
 * con /tf rastreo (staff):
 * <ul>
 *   <li>{@code jugador <nombre> [días]}: lo último que hizo ese jugador en contenedores.</li>
 *   <li>{@code aqui [radio] [días]}: lo último que pasó en los contenedores de alrededor.</li>
 *   <li>{@code objeto <texto> [días]}: por objeto, dueño o pedido de la web (p. ej. «mecha», «HitGp», «TFHSY…»).</li>
 * </ul>
 * Cómo: al abrir un contenedor se cuenta lo que tiene (sin el inventario del jugador) y al cerrarlo se compara. Dónde:
 * el bloque o la entidad que el jugador acaba de tocar. /tf buscar (TFSearch) dice dónde está ahora cada cosa.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFTracker {
    static final int KEEP_DAYS = 60;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TF Rastreador");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Lo último que tocó cada jugador (para saber dónde está el contenedor que abre). */
    private record Touch(String world, BlockPos pos, String what, long at) {}

    /** Lo que había en el contenedor al abrirlo: clave del objeto → cantidad. */
    private record Open(AbstractContainerMenu menu, String world, BlockPos pos, String what, Map<String, Integer> before) {}

    private static final Map<UUID, Touch> TOUCH = new HashMap<>();
    private static final Map<UUID, Open> OPEN = new HashMap<>();
    private static MinecraftServer server;

    private TFTracker() {}

    static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("tfclient").resolve("rastreador");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        Path dir = dir(server);
        IO.execute(() -> {
            // fuera lo de hace más de KEEP_DAYS días
            try (var files = Files.list(dir)) {
                LocalDate limit = LocalDate.now().minusDays(KEEP_DAYS);
                files.forEach(f -> {
                    String n = f.getFileName().toString();
                    try {
                        if (n.endsWith(".log") && LocalDate.parse(n.substring(0, 10)).isBefore(limit)) Files.delete(f);
                    } catch (Exception ignored) {
                        // otro archivo
                    }
                });
            } catch (IOException ignored) {
                // aún no hay carpeta
            }
        });
    }

    // ------------------------------------------------------------------------------------------- qué toca

    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = event.getPos();
        String what = String.valueOf(ForgeRegistries.BLOCKS.getKey(p.level().getBlockState(pos).getBlock()));
        TOUCH.put(p.getUUID(), new Touch(world(p.serverLevel()), pos.immutable(), what, p.serverLevel().getGameTime()));
    }

    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var e = event.getTarget();
        TOUCH.put(p.getUUID(), new Touch(world(p.serverLevel()), e.blockPosition(),
                String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())), p.serverLevel().getGameTime()));
    }

    // ------------------------------------------------------------------------------------------- abrir y cerrar

    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        try {
            AbstractContainerMenu menu = event.getContainer();
            Map<String, Integer> before = count(menu);
            if (before == null) return; // solo su inventario
            Touch t = TOUCH.get(p.getUUID());
            boolean recent = t != null && p.serverLevel().getGameTime() - t.at < 40;
            OPEN.put(p.getUUID(), new Open(menu, recent ? t.world : world(p.serverLevel()), recent ? t.pos : p.blockPosition(),
                    recent ? t.what : "?(" + menu.getClass().getSimpleName() + ")", before));
        } catch (Throwable e) {
            TFClient.LOGGER.warn("TF Rastreador: no se pudo mirar un contenedor: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Open open = OPEN.remove(p.getUUID());
        if (open == null || open.menu != event.getContainer()) return;
        try {
            Map<String, Integer> after = count(open.menu);
            if (after == null) return;
            Map<String, Integer> all = new LinkedHashMap<>(open.before);
            after.keySet().forEach(k -> all.putIfAbsent(k, 0));
            for (Map.Entry<String, Integer> e : all.entrySet()) {
                int diff = after.getOrDefault(e.getKey(), 0) - e.getValue();
                if (diff == 0) continue;
                log(p, open.world, open.pos, open.what, diff < 0 ? "SACA" : "METE", e.getKey(), Math.abs(diff));
            }
        } catch (Throwable e) {
            TFClient.LOGGER.warn("TF Rastreador: no se pudo comparar un contenedor: {}", e.toString());
        }
    }

    /** Lo que hay en los huecos que no son del inventario del jugador; null si solo hay inventario. */
    private static Map<String, Integer> count(AbstractContainerMenu menu) {
        Map<String, Integer> out = new LinkedHashMap<>();
        boolean any = false;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            any = true;
            ItemStack s = slot.getItem();
            if (!s.isEmpty()) out.merge(key(s), s.getCount(), Integer::sum);
        }
        return any ? out : null;
    }

    /** «objeto\tdueño\tpedido» */
    static String key(ItemStack s) {
        String owner = TFBinding.ownerName(s);
        String order = s.getTag() != null && s.getTag().contains(TFRevocations.ORDER) ? s.getTag().getString(TFRevocations.ORDER) : "";
        return ForgeRegistries.ITEMS.getKey(s.getItem()) + "\t" + (owner == null ? "" : owner) + "\t" + order;
    }

    // ------------------------------------------------------------------------------------------- romper

    @SubscribeEvent(priority = EventPriority.MONITOR)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer p) || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockEntity be = level.getBlockEntity(event.getPos());
        if (be == null) return;
        Map<String, Integer> inside = new LinkedHashMap<>();
        try {
            if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) inside.merge(key(c.getItem(i)), c.getItem(i).getCount(), Integer::sum);
            } else {
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(h -> {
                    for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) inside.merge(key(h.getStackInSlot(i)), h.getStackInSlot(i).getCount(), Integer::sum);
                });
            }
        } catch (Throwable ignored) {
            // contenedor raro
        }
        String what = String.valueOf(ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()));
        for (Map.Entry<String, Integer> e : inside.entrySet()) log(p, world(level), event.getPos(), what, "ROMPE", e.getKey(), e.getValue());
    }

    // ------------------------------------------------------------------------------------------- registro

    static String world(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** Para la prueba en el juego: las líneas que se apuntan. */
    public static final List<String> TEST_LINES = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Lo que el staff recuperó con el Rastreador (de dónde y qué). */
    static void logAdmin(MinecraftServer srv, TFSearch.Found f, List<ItemStack> got) {
        if (server == null) server = srv;
        for (ItemStack s : got) {
            write(LocalTime.now().format(TIME) + "\tstaff\t-\t" + (f.dim().isEmpty() ? f.world() : f.dim()) + "\t" + f.x() + " " + f.y() + " " + f.z()
                    + "\t" + f.holder() + "\tRECUPERA\t" + key(s) + "\t" + s.getCount());
        }
    }

    private static void log(ServerPlayer p, String world, BlockPos pos, String what, String action, String key, int count) {
        if (server == null) server = p.getServer();
        write(LocalTime.now().format(TIME) + "\t" + p.getGameProfile().getName() + "\t" + p.getUUID() + "\t" + world + "\t"
                + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "\t" + what + "\t" + action + "\t" + key + "\t" + count);
    }

    private static void write(String line) {
        if (TEST_LINES.size() < 200) TEST_LINES.add(line);
        Path file = dir(server).resolve(LocalDate.now() + ".log");
        IO.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                TFClient.LOGGER.warn("TF Rastreador: no se pudo escribir {}: {}", file, e.toString());
            }
        });
    }

    // ------------------------------------------------------------------------------------------- consultas

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("rastreo").requires(s -> s.hasPermission(2))
                .then(Commands.literal("jugador").then(Commands.argument("nombre", StringArgumentType.word())
                        .executes(c -> byPlayer(c.getSource(), StringArgumentType.getString(c, "nombre"), 3))
                        .then(Commands.argument("dias", IntegerArgumentType.integer(1, KEEP_DAYS))
                                .executes(c -> byPlayer(c.getSource(), StringArgumentType.getString(c, "nombre"), IntegerArgumentType.getInteger(c, "dias"))))))
                .then(Commands.literal("aqui")
                        .executes(c -> here(c.getSource(), 8, 3))
                        .then(Commands.argument("radio", IntegerArgumentType.integer(1, 128))
                                .executes(c -> here(c.getSource(), IntegerArgumentType.getInteger(c, "radio"), 3))
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, KEEP_DAYS))
                                        .executes(c -> here(c.getSource(), IntegerArgumentType.getInteger(c, "radio"), IntegerArgumentType.getInteger(c, "dias"))))))
                .then(Commands.literal("objeto").then(Commands.argument("texto", StringArgumentType.word())
                        .executes(c -> byText(c.getSource(), StringArgumentType.getString(c, "texto"), 7))
                        .then(Commands.argument("dias", IntegerArgumentType.integer(1, KEEP_DAYS))
                                .executes(c -> byText(c.getSource(), StringArgumentType.getString(c, "texto"), IntegerArgumentType.getInteger(c, "dias"))))));
    }

    private static int byPlayer(CommandSourceStack src, String name, int days) {
        query(src, days, "de " + name, f -> f.length > 1 && f[1].equalsIgnoreCase(name));
        return 1;
    }

    private static int here(CommandSourceStack src, int radius, int days) {
        BlockPos at = BlockPos.containing(src.getPosition());
        String world = world(src.getLevel());
        query(src, days, "a " + radius + " bloques de " + at.toShortString(), f -> {
            if (f.length < 5 || !f[3].equals(world)) return false;
            String[] xyz = f[4].split(" ");
            try {
                return Math.abs(Integer.parseInt(xyz[0]) - at.getX()) <= radius && Math.abs(Integer.parseInt(xyz[1]) - at.getY()) <= radius
                        && Math.abs(Integer.parseInt(xyz[2]) - at.getZ()) <= radius;
            } catch (Exception e) {
                return false;
            }
        });
        return 1;
    }

    private static int byText(CommandSourceStack src, String text, int days) {
        String t = text.toLowerCase(java.util.Locale.ROOT);
        query(src, days, "con «" + text + "»", f -> f.length > 9 && (f[7].toLowerCase(java.util.Locale.ROOT).contains(t)
                || f[8].equalsIgnoreCase(text) || f[9].equalsIgnoreCase(text)));
        return 1;
    }

    /**
     * Las últimas líneas del registro de los últimos días que cumplen match (para la app Rastreador): cada una con su
     * día delante («2026-10-25 15:02:11\tjugador…»). Los archivos son pequeños: se leen al momento.
     */
    public static List<String[]> recent(MinecraftServer srv, int days, Predicate<String[]> match, int max) {
        List<String[]> hits = new ArrayList<>();
        Path dir = dir(srv);
        for (int d = days - 1; d >= 0; d--) {
            LocalDate day = LocalDate.now().minusDays(d);
            Path f = dir.resolve(day + ".log");
            if (!Files.exists(f)) continue;
            try {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String[] parts = (day + " " + line).split("\t", -1);
                    if (parts.length >= 11 && match.test(parts)) hits.add(parts);
                }
            } catch (IOException ignored) {
                // otro día
            }
        }
        return hits.size() > max ? new ArrayList<>(hits.subList(hits.size() - max, hits.size())) : hits;
    }

    /** «25/10 15:02 kwns_700 SACA 1× Alas Dragón Mecha (de HitGp) · Cofre en minecraft:overworld 120 64 -30» */
    public static String describe(String[] f) {
        String[] head = f[0].split(" ");
        String date = head[0].length() >= 10 ? head[0].substring(8, 10) + "/" + head[0].substring(5, 7) : head[0];
        String time = head.length > 1 && head[1].length() >= 5 ? head[1].substring(0, 5) : "";
        String verb = switch (f[6]) {
            case "SACA" -> "sacó";
            case "METE" -> "metió";
            case "ROMPE" -> "rompió con";
            case "RECUPERA" -> "recuperó";
            default -> f[6];
        };
        return date + " " + time + " " + f[1] + " " + verb + " " + f[10] + "× " + TFSearch.itemName(f[7])
                + (f[8].isEmpty() ? "" : " (de " + f[8] + ")") + " · " + TFSearch.blockName(f[5]) + " " + f[3] + " " + f[4];
    }

    /** Lee los últimos días en el hilo del rastreador y manda las 20 últimas líneas que encajan. */
    private static void query(CommandSourceStack src, int days, String what, Predicate<String[]> match) {
        MinecraftServer srv = src.getServer();
        Path dir = dir(srv);
        IO.execute(() -> {
            List<String> hits = new ArrayList<>();
            for (int d = days - 1; d >= 0; d--) {
                LocalDate day = LocalDate.now().minusDays(d);
                Path f = dir.resolve(day + ".log");
                if (!Files.exists(f)) continue;
                try {
                    for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                        String[] parts = line.split("\t", -1);
                        if (match.test(parts)) hits.add(day + " " + line);
                    }
                } catch (IOException ignored) {
                    // otro día
                }
            }
            List<String> last = hits.size() > 20 ? hits.subList(hits.size() - 20, hits.size()) : hits;
            srv.execute(() -> {
                src.sendSuccess(() -> Component.literal("Rastreador " + what + " (" + days + " días): " + hits.size()
                        + (hits.size() > 20 ? " (las 20 últimas)" : "")).withStyle(ChatFormatting.GOLD), false);
                for (String l : last) src.sendSuccess(() -> pretty(l), false);
                if (hits.isEmpty()) src.sendSuccess(() -> Component.literal("Nada.").withStyle(ChatFormatting.GRAY), false);
            });
        });
    }

    /** «2026-10-25 15:02:11 kwns_700 SACA 1× tfclient:mecha_wing (de HitGp, pedido TFHSY…) en cofre minecraft:overworld 120 64 -30» */
    private static Component pretty(String l) {
        String[] f = l.split("\t", -1);
        if (f.length < 11) return Component.literal(l);
        String[] head = f[0].split(" ");
        String item = f[7];
        ResourceLocation rl = ResourceLocation.tryParse(item);
        var it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        String name = it == null ? item : new ItemStack(it).getHoverName().getString();
        ChatFormatting c = f[6].equals("SACA") ? ChatFormatting.RED : f[6].equals("ROMPE") ? ChatFormatting.DARK_RED : ChatFormatting.GREEN;
        return Component.literal(head[0] + " " + head[1] + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(f[1] + " ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(f[6] + " " + f[10] + "× " + name).withStyle(c))
                .append(Component.literal((f[8].isEmpty() ? "" : " (de " + f[8] + ")") + (f[9].isEmpty() ? "" : " [pedido " + f[9] + "]")
                        + " · " + f[5] + " en " + f[3] + " " + f[4]).withStyle(ChatFormatting.GRAY));
    }
}
