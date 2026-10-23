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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.data.ClaimManager;
import net.tierrasfantasticas.tfclient.pad.PadConfig;
import net.tierrasfantasticas.tfclient.pad.PadServer;
import net.tierrasfantasticas.tfclient.pad.PadView;
import net.tierrasfantasticas.tfclient.pad.TFPadNet;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Viajes (spawn, tu cama y los puntos del servidor) y Explorar (un sitio seguro al azar del mundo normal).
 * Antes de viajar hay una cuenta atrás de 3 s que se cancela si te mueves o te hacen daño; luego, un tiempo de espera.
 * Los puntos los pone el staff: /tf web viajes poner &lt;id&gt; [nombre] (donde está), quitar &lt;id&gt;, lista.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class PadTravel {
    // esperas y distancias: config/tfclient/pad.json (se cambian en el pad de administrador)
    private static long travelCooldown() {
        return PadConfig.get("viajes.espera");
    }

    private static int warmupTicks() {
        return (int) PadConfig.get("viajes.cuentaAtras") * 20;
    }

    private static long exploreCooldown() {
        return PadConfig.get("explorar.espera");
    }

    private static int exploreMin() {
        return (int) Math.min(PadConfig.get("explorar.min"), PadConfig.get("explorar.max") - 50);
    }

    private static int exploreMax() {
        return (int) PadConfig.get("explorar.max");
    }

    /** Un punto de viaje del staff; icon: id del objeto que sale en su tarjeta. */
    record Point(String id, String name, String dim, double x, double y, double z, float yaw, float pitch, String icon) {}

    /** Un viaje esperando su cuenta atrás. */
    private record Pending(Runnable go, Vec3 from, float health, int startTick, String what) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static int tick;

    static final JsonStore STORE_IMPL = new JsonStore("viajes.json");
    public static final PadServer.Store STORE = STORE_IMPL;
    public static final PadServer.App TRAVEL = new Travel();
    public static final PadServer.App EXPLORE = new Explore();

    private PadTravel() {}

    static List<Point> points() {
        List<Point> out = new ArrayList<>();
        JsonElement e = STORE_IMPL.root.get("puntos");
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement p : e.getAsJsonArray()) {
            JsonObject o = p.getAsJsonObject();
            out.add(new Point(TFJson.str(o, "id", ""), TFJson.str(o, "nombre", "?"), TFJson.str(o, "dim", "minecraft:overworld"),
                    TFJson.dec(o, "x", 0), TFJson.dec(o, "y", 64), TFJson.dec(o, "z", 0), (float) TFJson.dec(o, "yaw", 0), (float) TFJson.dec(o, "pitch", 0),
                    TFJson.str(o, "icono", "minecraft:filled_map")));
        }
        return out;
    }

    static long left(ServerPlayer player, String key, long seconds) {
        JsonObject p = STORE_IMPL.playerIfAny(player.getUUID());
        if (p == null || !p.has(key)) return 0;
        return Math.max(0, (p.get(key).getAsLong() + seconds * 1000 - System.currentTimeMillis()) / 1000);
    }

    static void used(ServerPlayer player, String key) {
        STORE_IMPL.player(player.getUUID()).addProperty(key, System.currentTimeMillis());
        STORE_IMPL.changed();
    }

    /** Empieza la cuenta atrás y cierra el pad para que se vea. */
    static void warmup(ServerPlayer player, String what, Runnable go) {
        if (player.hasPermissions(2) || warmupTicks() <= 0) { // el staff viaja al momento
            TFPadNet.close(player);
            go.run();
            return;
        }
        PENDING.put(player.getUUID(), new Pending(go, player.position(), player.getHealth(), tick, what));
        TFPadNet.close(player);
        player.displayClientMessage(Component.literal(what + " en " + warmupTicks() / 20 + "… no te muevas").withStyle(ChatFormatting.AQUA), true);
    }

    /** Cualquier daño cancela la cuenta atrás (aunque lo tape la absorción o la regeneración). */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PENDING.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.literal("Viaje cancelado: te hicieron daño.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick++;
        if (PENDING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Pending p = e.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            if (player.position().distanceToSqr(p.from) > 0.36 || player.getHealth() < p.health) {
                it.remove();
                player.displayClientMessage(Component.literal("Viaje cancelado: te moviste o te hicieron daño.").withStyle(ChatFormatting.RED), true);
                continue;
            }
            int elapsed = tick - p.startTick;
            if (elapsed >= warmupTicks()) {
                it.remove();
                p.go.run();
            } else if (elapsed % 20 == 0) {
                player.displayClientMessage(Component.literal(p.what + " en " + (warmupTicks() / 20 - elapsed / 20) + "… no te muevas").withStyle(ChatFormatting.AQUA), true);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 0.5F, 1.4F);
            }
        }
    }

    static void arrive(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, yaw, pitch);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Viajes
    // ---------------------------------------------------------------------------------------------------------------

    static final class Travel implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            long wait = left(player, "viaje", travelCooldown());
            int countdown = warmupTicks() / 20;
            PadView.Builder b = PadView.of("viajes").header(wait > 0 ? "Puedes volver a viajar en " + PadKits.time(wait) + "."
                    : countdown > 0 ? "Pulsa un destino y quédate quieto " + countdown + " s." : "Pulsa un destino.");
            String go = wait > 0 ? "" : "ir:";
            ServerLevel spawnAt = spawnLevel(player.getServer());
            b.card(new ItemStack(Items.COMPASS), "Spawn", 0x3496FA, spawnAt != null ? dimName(spawnAt.dimension().location().toString())
                    : "Al spawn", wait > 0 ? "" : go + "spawn", false);
            BlockPos bed = player.getRespawnPosition();
            if (bed != null) b.card(new ItemStack(Items.RED_BED), "Tu cama", 0xE83446, dimName(player.getRespawnDimension().location().toString()),
                    wait > 0 ? "" : go + "cama", false);
            for (Point p : points()) {
                b.card(icon(p.icon), p.name, 0xF6B628, dimName(p.dim), wait > 0 ? "" : go + "punto:" + p.id, false);
            }
            if (PadConfig.on("viajes.warps")) {
                for (EssentialsWarps.Warp w : EssentialsWarps.list(player.getServer())) {
                    if (PadConfig.hiddenWarps().contains(w.id())) continue;
                    b.card(new ItemStack(Items.ENDER_PEARL), w.name(), 0x9A5CF0, "Warp", wait > 0 ? "" : go + "warp:" + w.id(), false);
                }
            }
            if (PadConfig.on("explorar.activado")) {
                long explore = left(player, "explorar", exploreCooldown());
                b.card(new ItemStack(Items.SPYGLASS), "Explorar", 0x40C850, explore > 0 ? "en " + PadKits.time(explore) : "Al azar",
                        explore > 0 ? "" : "explorar", false);
            }
            if (PadConfig.appOn("hogares")) b.card(new ItemStack(Items.OAK_DOOR), "Tus hogares", 0xC27A10, "Abrir", "§open:hogares", false);
            return b.build();
        }

        private static ItemStack icon(String id) {
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
            return item == null || item == Items.AIR ? new ItemStack(Items.FILLED_MAP) : new ItemStack(item);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.equals("explorar")) return EXPLORE.action(player, tab, action, text);
            if (!action.startsWith("ir:") || left(player, "viaje", travelCooldown()) > 0) return null;
            String where = action.substring(3);
            MinecraftServer server = player.getServer();
            if (where.startsWith("warp:")) {
                String id = where.substring(5);
                if (!PadConfig.on("viajes.warps") || PadConfig.hiddenWarps().contains(id) || !id.matches("[A-Za-z0-9_-]{1,40}")) return null;
                // el /warp de EssentialsX, como si lo escribiera el jugador (con sus permisos y su propia espera)
                TFPadNet.close(player);
                used(player, "viaje");
                PadStats.add(player, PadStats.TRAVELS, 1);
                // (1.3.119) en Mohist, como el jugador por Bukkit (así viaja entre los mundos de Multiverse)
                if (net.tierrasfantasticas.tfclient.server.TFBukkit.playerCommand(player, "warp " + id) == null) {
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "warp " + id);
                }
                return null;
            }
            if (where.equals("spawn")) {
                // (1.3.119, Multiverse: el spawn está en otro overworld) el mundo puesto en pad.json («mundos.spawn»); si
                // no, el /spawn de EssentialsX como el jugador (lleva al spawn que puso el staff, en el mundo que sea); si
                // tampoco, el del mundo normal
                ServerLevel set = spawnLevel(server);
                if (set == null && net.tierrasfantasticas.tfclient.server.TFBukkit.hasCommand("spawn")) {
                    TFPadNet.close(player);
                    used(player, "viaje");
                    PadStats.add(player, PadStats.TRAVELS, 1);
                    net.tierrasfantasticas.tfclient.server.TFBukkit.playerCommand(player, "spawn");
                    return null;
                }
                ServerLevel level = set != null ? set : server.overworld();
                BlockPos s = level.getSharedSpawnPos();
                warmup(player, "Viajando al spawn", () -> {
                    used(player, "viaje");
                    PadStats.add(player, PadStats.TRAVELS, 1);
                    BlockPos safe = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s);
                    arrive(player, level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
                });
            } else if (where.equals("cama")) {
                BlockPos bed = player.getRespawnPosition();
                ServerLevel level = server.getLevel(player.getRespawnDimension());
                if (bed == null || level == null) return null;
                warmup(player, "Viajando a tu cama", () -> {
                    // true: no gastar la carga de un ancla de reaparición (solo se gasta al reaparecer de verdad)
                    var spot = net.minecraft.world.entity.player.Player.findRespawnPositionAndUseSpawnBlock(level, bed,
                            player.getRespawnAngle(), player.isRespawnForced(), true);
                    if (spot.isEmpty()) {
                        player.displayClientMessage(Component.literal("Tu cama no está o está tapada.").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    used(player, "viaje");
                    PadStats.add(player, PadStats.TRAVELS, 1);
                    Vec3 v = spot.get();
                    arrive(player, level, v.x, v.y, v.z, player.getYRot(), player.getXRot());
                });
            } else if (where.startsWith("punto:")) {
                for (Point p : points()) {
                    if (!p.id.equals(where.substring(6))) continue;
                    ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(p.dim)));
                    if (level == null) return null;
                    warmup(player, "Viajando a " + p.name, () -> {
                        used(player, "viaje");
                        PadStats.add(player, PadStats.TRAVELS, 1);
                        arrive(player, level, p.x, p.y, p.z, p.yaw, p.pitch);
                    });
                }
            }
            return null;
        }
    }

    static String dimName(String dim) {
        return switch (dim) {
            case "minecraft:overworld" -> "Mundo normal";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "El End";
            case "tfclient:void" -> "The Void";
            default -> {
                // un mundo de Multiverse («minecraft:spawn» → «Spawn»)
                String n = dim.substring(dim.indexOf(':') + 1).replace('_', ' ').replace('-', ' ').trim();
                yield n.isEmpty() ? dim : Character.toUpperCase(n.charAt(0)) + n.substring(1);
            }
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mundos (1.3.119: Multiverse)
    // ---------------------------------------------------------------------------------------------------------------

    /** Los overworlds que se vio que están vacíos (sin suelo donde caer: el del spawn de Multiverse). */
    private static final java.util.Set<ResourceKey<Level>> EMPTY = new java.util.HashSet<>();

    private static ServerLevel level(MinecraftServer server, String id) {
        if (id == null || id.isBlank()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id.toLowerCase(java.util.Locale.ROOT));
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** El mundo del spawn puesto en pad.json, o null (entonces /spawn de EssentialsX o el mundo normal). */
    static ServerLevel spawnLevel(MinecraftServer server) {
        return level(server, PadConfig.spawnWorld());
    }

    private static boolean overworldLike(ServerLevel l) {
        return l.dimensionTypeId().equals(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD)
                && !net.tierrasfantasticas.tfclient.vacio.TFVoid.isVoid(l);
    }

    /**
     * Dónde se explora: el mundo puesto en pad.json («mundos.explorar»); si no, el mundo normal y, si es un mundo vacío
     * (el del spawn con Multiverse), los demás overworlds con terreno (primero los que se llamen «surv…»). Nunca el del
     * spawn puesto en pad.json ni uno que ya se vio vacío.
     */
    static List<ServerLevel> exploreLevels(MinecraftServer server) {
        ServerLevel set = level(server, PadConfig.exploreWorld());
        if (set != null) return List.of(set);
        ServerLevel spawn = spawnLevel(server);
        List<ServerLevel> out = new ArrayList<>();
        ServerLevel main = server.overworld();
        if (main != spawn && !EMPTY.contains(main.dimension())) out.add(main);
        List<ServerLevel> others = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) {
            if (l == main || l == spawn || !overworldLike(l) || EMPTY.contains(l.dimension())) continue;
            others.add(l);
        }
        others.sort(java.util.Comparator.comparing((ServerLevel l) -> !l.dimension().location().getPath().contains("surv"))
                .thenComparing(l -> l.dimension().location().toString()));
        out.addAll(others);
        return out;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Explorar
    // ---------------------------------------------------------------------------------------------------------------

    static final class Explore implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            long wait = left(player, "explorar", exploreCooldown());
            List<ServerLevel> worlds = exploreLevels(player.getServer());
            PadView.Builder b = PadView.of("explorar")
                    .header("Un sitio seguro al azar, lejos del spawn" + (worlds.isEmpty() ? "." : ", en " + dimName(worlds.get(0).dimension().location().toString()) + "."));
            List<String> lines = new ArrayList<>();
            lines.add("Entre " + exploreMin() + " y " + exploreMax() + " bloques del spawn. Nunca en agua, lava ni zonas protegidas.");
            lines.add("Has explorado " + PadStats.get(player.getUUID(), PadStats.EXPLORES) + " veces.");
            PadView.Btn btn = wait > 0 ? PadView.Btn.off("EN " + PadKits.time(wait).toUpperCase()) : PadView.Btn.of("EXPLORAR", "explorar", PadView.GOLD);
            b.row(new PadView.Row(new ItemStack(Items.SPYGLASS), "Explorar el mundo", 0x18265C, lines, -1, "", btn, null));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (!action.equals("explorar") || !PadConfig.on("explorar.activado") || left(player, "explorar", exploreCooldown()) > 0) return null;
            warmup(player, "Explorando", () -> {
                // (1.3.119) nunca al overworld vacío del spawn (Multiverse): exploreLevels
                ServerLevel level = null;
                BlockPos spot = null;
                for (ServerLevel l : exploreLevels(player.getServer())) {
                    spot = findSpot(l);
                    if (spot != null) {
                        level = l;
                        break;
                    }
                }
                if (spot == null) {
                    // un minuto de espera para no buscar sin parar (cada búsqueda genera terreno)
                    STORE_IMPL.player(player.getUUID()).addProperty("explorar", System.currentTimeMillis() - (exploreCooldown() - 60) * 1000);
                    STORE_IMPL.changed();
                    player.displayClientMessage(Component.literal("No hay sitio seguro. Pulsa otra vez.").withStyle(ChatFormatting.RED), true);
                    return;
                }
                used(player, "explorar");
                PadStats.add(player, PadStats.EXPLORES, 1);
                arrive(player, level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0);
                player.displayClientMessage(Component.literal("Explorar: " + spot.getX() + ", " + spot.getZ()).withStyle(ChatFormatting.GREEN), true);
            });
            return null;
        }
    }

    /** Un sitio seco y firme al azar (hasta 6 intentos; los mares y ríos se descartan sin generar terreno). */
    static BlockPos findSpot(ServerLevel level) {
        Random r = new Random();
        BlockPos spawn = level.getSharedSpawnPos();
        var border = level.getWorldBorder();
        int generated = 0, voidHits = 0;
        for (int i = 0; i < 24 && generated < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = exploreMin() + r.nextInt(Math.max(1, exploreMax() - exploreMin()));
            int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
            if (!border.isWithinBounds(x, z)) continue;
            var biome = level.getBiome(new BlockPos(x, 64, z)); // sale del generador, sin generar el chunk
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_DEEP_OCEAN)) continue;
            generated++;
            level.getChunk(x >> 4, z >> 4); // la genera si hace falta
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockState ground = level.getBlockState(feet.below());
            if (y <= level.getMinBuildHeight() + 2) voidHits++;
            if (y <= level.getMinBuildHeight() + 2 || !safe(ground)) continue;
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            if (ClaimManager.getInstance().getClaimAt(level, feet) != null) continue;
            return feet;
        }
        // (1.3.119) todo vacío: es un mundo sin terreno (el spawn de Multiverse): ya no se explora ahí
        if (generated > 0 && voidHits == generated && EMPTY.add(level.dimension())) {
            net.tierrasfantasticas.tfclient.TFClient.LOGGER.info("TF Pad: {} es un mundo vacío: Explorar no lleva ahí", level.dimension().location());
        }
        return null;
    }

    private static boolean safe(BlockState s) {
        if (!s.getFluidState().isEmpty() || s.isAir()) return false;
        if (s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(Blocks.POWDER_SNOW)
                || s.is(BlockTags.FIRE) || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(BlockTags.LEAVES)) return false;
        return s.isSolid();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Comando del staff
    // ---------------------------------------------------------------------------------------------------------------

    /** /tf web viajes poner|quitar|lista. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("viajes").requires(s -> s.hasPermission(3))
                .then(Commands.literal("lista").executes(ctx -> {
                    List<Point> ps = points();
                    ctx.getSource().sendSuccess(() -> Component.literal(ps.isEmpty() ? "No hay puntos de viaje."
                            : "Puntos: " + String.join(", ", ps.stream().map(p -> p.id + " (" + p.name + ")").toList())), false);
                    List<ServerLevel> ex = exploreLevels(ctx.getSource().getServer());
                    ServerLevel sp = spawnLevel(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Explorar: " + (ex.isEmpty() ? "ningún mundo con terreno" : ex.get(0).dimension().location())
                            + (PadConfig.exploreWorld().isEmpty() ? " (automático)" : "") + " · Spawn: "
                            + (sp != null ? sp.dimension().location() : net.tierrasfantasticas.tfclient.server.TFBukkit.hasCommand("spawn") ? "/spawn de EssentialsX" : "el del mundo normal")), false);
                    return ps.size();
                }))
                // (1.3.119) los mundos de Explorar y del Spawn (Multiverse): el de donde estás, o automático
                .then(Commands.literal("explorar")
                        .then(Commands.literal("aqui").executes(ctx -> world(ctx.getSource(), true, true)))
                        .then(Commands.literal("auto").executes(ctx -> world(ctx.getSource(), true, false))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("aqui").executes(ctx -> world(ctx.getSource(), false, true)))
                        .then(Commands.literal("auto").executes(ctx -> world(ctx.getSource(), false, false))))
                .then(Commands.literal("quitar").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    removePoint(id);
                    ctx.getSource().sendSuccess(() -> Component.literal("Punto " + id + " quitado."), true);
                    return 1;
                })))
                .then(Commands.literal("poner").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> put(ctx.getSource(), StringArgumentType.getString(ctx, "id"), ""))
                        .then(Commands.argument("nombre", StringArgumentType.greedyString())
                                .executes(ctx -> put(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "nombre"))))));
    }

    private static int world(CommandSourceStack source, boolean explore, boolean here) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String id = here ? source.getPlayerOrException().level().dimension().location().toString() : "";
        if (explore) PadConfig.worlds(id, null);
        else PadConfig.worlds(null, id);
        EMPTY.clear();
        String what = explore ? "Explorar" : "Spawn";
        source.sendSuccess(() -> Component.literal(what + ": " + (here ? dimName(id) + " (" + id + ")" : "automático")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int put(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        putPoint(source.getPlayerOrException(), id, name);
        source.sendSuccess(() -> Component.literal("Punto de viaje " + id + " puesto aquí.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Para el pad de administrador
    // ---------------------------------------------------------------------------------------------------------------

    static Point point(String id) {
        for (Point p : points()) if (p.id().equals(id)) return p;
        return null;
    }

    /** Pone (o mueve) el punto donde está el jugador; si ya existía conserva su icono y, sin nombre, su nombre. */
    static void putPoint(ServerPlayer player, String id, String name) {
        JsonObject old = null;
        JsonArray keep = new JsonArray();
        for (JsonElement p : pointArray()) {
            if (TFJson.str(p.getAsJsonObject(), "id", "").equals(id)) old = p.getAsJsonObject();
            else keep.add(p);
        }
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nombre", !name.isBlank() ? name : old != null ? TFJson.str(old, "nombre", id) : id);
        o.addProperty("dim", player.level().dimension().location().toString());
        o.addProperty("x", player.getX());
        o.addProperty("y", player.getY());
        o.addProperty("z", player.getZ());
        o.addProperty("yaw", player.getYRot());
        o.addProperty("pitch", player.getXRot());
        if (old != null && old.has("icono")) o.add("icono", old.get("icono"));
        keep.add(o);
        STORE_IMPL.root.add("puntos", keep);
        STORE_IMPL.changed();
    }

    static void editPoint(String id, java.util.function.Consumer<JsonObject> edit) {
        for (JsonElement p : pointArray()) {
            if (TFJson.str(p.getAsJsonObject(), "id", "").equals(id)) {
                edit.accept(p.getAsJsonObject());
                STORE_IMPL.changed();
                return;
            }
        }
    }

    static void removePoint(String id) {
        JsonArray keep = new JsonArray();
        for (JsonElement p : pointArray()) if (!TFJson.str(p.getAsJsonObject(), "id", "").equals(id)) keep.add(p);
        STORE_IMPL.root.add("puntos", keep);
        STORE_IMPL.changed();
    }

    private static JsonArray pointArray() {
        JsonElement e = STORE_IMPL.root.get("puntos");
        if (e == null || !e.isJsonArray()) {
            JsonArray a = new JsonArray();
            STORE_IMPL.root.add("puntos", a);
            return a;
        }
        return e.getAsJsonArray();
    }
}
