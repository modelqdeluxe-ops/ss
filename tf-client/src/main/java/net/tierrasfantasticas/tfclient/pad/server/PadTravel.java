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
    private static final int WARMUP_TICKS = 60;
    private static final long TRAVEL_COOLDOWN = 30;
    private static final long EXPLORE_COOLDOWN = 300;
    private static final int EXPLORE_MIN = 800, EXPLORE_MAX = 6000;

    record Point(String id, String name, String dim, double x, double y, double z, float yaw, float pitch) {}

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
                    TFJson.dec(o, "x", 0), TFJson.dec(o, "y", 64), TFJson.dec(o, "z", 0), (float) TFJson.dec(o, "yaw", 0), (float) TFJson.dec(o, "pitch", 0)));
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
        if (player.hasPermissions(2)) { // el staff viaja al momento
            go.run();
            return;
        }
        PENDING.put(player.getUUID(), new Pending(go, player.position(), player.getHealth(), tick, what));
        TFPadNet.close(player);
        player.displayClientMessage(Component.literal(what + " en 3… no te muevas").withStyle(ChatFormatting.AQUA), true);
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
            if (elapsed >= WARMUP_TICKS) {
                it.remove();
                p.go.run();
            } else if (elapsed % 20 == 0) {
                player.displayClientMessage(Component.literal(p.what + " en " + (3 - elapsed / 20) + "… no te muevas").withStyle(ChatFormatting.AQUA), true);
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
            long wait = left(player, "viaje", TRAVEL_COOLDOWN);
            PadView.Builder b = PadView.of("viajes").header(wait > 0 ? "Puedes volver a viajar en " + wait + " s." : "Elige a dónde ir. 3 s sin moverte y ¡listo!");
            b.row(row(new ItemStack(Items.COMPASS), "Spawn", "El corazón del servidor", "spawn", wait));
            BlockPos bed = player.getRespawnPosition();
            if (bed != null) b.row(row(new ItemStack(Items.RED_BED), "Tu cama", "Donde reapareces", "cama", wait));
            for (Point p : points()) b.row(row(new ItemStack(Items.FILLED_MAP), p.name, dimName(p.dim), "punto:" + p.id, wait));
            // Explorar va aquí dentro: un sitio seguro al azar, con su propia espera
            long explore = left(player, "explorar", EXPLORE_COOLDOWN);
            PadView.Btn eb = explore > 0 ? PadView.Btn.off("EN " + PadKits.time(explore).toUpperCase()) : PadView.Btn.of("EXPLORAR", "explorar", PadView.GOLD);
            b.row(new PadView.Row(new ItemStack(Items.SPYGLASS), "Explorar", 0x18265C,
                    List.of("Un sitio seguro al azar, a " + EXPLORE_MIN + "-" + EXPLORE_MAX + " bloques del spawn"), -1, "", eb, null));
            b.row(new PadView.Row(new ItemStack(Items.RED_BED), "Tus hogares", 0x18265C, List.of("Los sitios que guardaste"), -1, "",
                    PadView.Btn.of("VER", "§open:hogares", PadView.BLUE), null).clickable("§open:hogares"));
            return b.build();
        }

        private static PadView.Row row(ItemStack icon, String title, String line, String action, long wait) {
            PadView.Btn btn = wait > 0 ? PadView.Btn.off("ESPERA") : PadView.Btn.of("VIAJAR", "ir:" + action, PadView.BLUE);
            return new PadView.Row(icon, title, 0x18265C, List.of(line), -1, "", btn, null);
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (action.equals("explorar")) return EXPLORE.action(player, tab, action, text);
            if (!action.startsWith("ir:") || left(player, "viaje", TRAVEL_COOLDOWN) > 0) return null;
            String where = action.substring(3);
            MinecraftServer server = player.getServer();
            if (where.equals("spawn")) {
                ServerLevel level = server.overworld();
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
            default -> dim;
        };
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Explorar
    // ---------------------------------------------------------------------------------------------------------------

    static final class Explore implements PadServer.App {
        @Override
        public PadView view(ServerPlayer player, String tab) {
            long wait = left(player, "explorar", EXPLORE_COOLDOWN);
            PadView.Builder b = PadView.of("explorar")
                    .header("Te lleva a un sitio seguro al azar del mundo normal, lejos del spawn.");
            List<String> lines = new ArrayList<>();
            lines.add("Entre " + EXPLORE_MIN + " y " + EXPLORE_MAX + " bloques del spawn. Nunca en agua, lava ni zonas protegidas.");
            lines.add("Has explorado " + PadStats.get(player.getUUID(), PadStats.EXPLORES) + " veces.");
            PadView.Btn btn = wait > 0 ? PadView.Btn.off("EN " + PadKits.time(wait).toUpperCase()) : PadView.Btn.of("EXPLORAR", "explorar", PadView.GOLD);
            b.row(new PadView.Row(new ItemStack(Items.SPYGLASS), "Explorar el mundo", 0x18265C, lines, -1, "", btn, null));
            return b.build();
        }

        @Override
        public String action(ServerPlayer player, String tab, String action, String text) {
            if (!action.equals("explorar") || left(player, "explorar", EXPLORE_COOLDOWN) > 0) return null;
            warmup(player, "Explorando", () -> {
                ServerLevel level = player.getServer().overworld();
                BlockPos spot = findSpot(level);
                if (spot == null) {
                    // un minuto de espera para no buscar sin parar (cada búsqueda genera terreno)
                    STORE_IMPL.player(player.getUUID()).addProperty("explorar", System.currentTimeMillis() - (EXPLORE_COOLDOWN - 60) * 1000);
                    STORE_IMPL.changed();
                    player.displayClientMessage(Component.literal("No encontré un sitio seguro esta vez. Vuelve a probar.").withStyle(ChatFormatting.RED), true);
                    return;
                }
                used(player, "explorar");
                PadStats.add(player, PadStats.EXPLORES, 1);
                arrive(player, level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(), 0);
                player.displayClientMessage(Component.literal("¡A explorar! " + spot.getX() + ", " + spot.getZ()).withStyle(ChatFormatting.GREEN), true);
            });
            return null;
        }
    }

    /** Un sitio seco y firme al azar (hasta 6 intentos; los mares y ríos se descartan sin generar terreno). */
    static BlockPos findSpot(ServerLevel level) {
        Random r = new Random();
        BlockPos spawn = level.getSharedSpawnPos();
        var border = level.getWorldBorder();
        for (int i = 0, generated = 0; i < 24 && generated < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = EXPLORE_MIN + r.nextInt(EXPLORE_MAX - EXPLORE_MIN);
            int x = spawn.getX() + (int) (Math.cos(a) * d), z = spawn.getZ() + (int) (Math.sin(a) * d);
            if (!border.isWithinBounds(x, z)) continue;
            var biome = level.getBiome(new BlockPos(x, 64, z)); // sale del generador, sin generar el chunk
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_DEEP_OCEAN)) continue;
            generated++;
            level.getChunk(x >> 4, z >> 4); // la genera si hace falta
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockState ground = level.getBlockState(feet.below());
            if (y <= level.getMinBuildHeight() + 2 || !safe(ground)) continue;
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            if (ClaimManager.getInstance().getClaimAt(level, feet) != null) continue;
            return feet;
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
                    return ps.size();
                }))
                .then(Commands.literal("quitar").then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> {
                    String id = StringArgumentType.getString(ctx, "id");
                    JsonArray keep = new JsonArray();
                    JsonElement e = STORE_IMPL.root.get("puntos");
                    if (e != null && e.isJsonArray()) for (JsonElement p : e.getAsJsonArray()) if (!TFJson.str(p.getAsJsonObject(), "id", "").equals(id)) keep.add(p);
                    STORE_IMPL.root.add("puntos", keep);
                    STORE_IMPL.changed();
                    ctx.getSource().sendSuccess(() -> Component.literal("Punto " + id + " quitado."), true);
                    return 1;
                })))
                .then(Commands.literal("poner").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> put(ctx.getSource(), StringArgumentType.getString(ctx, "id"), ""))
                        .then(Commands.argument("nombre", StringArgumentType.greedyString())
                                .executes(ctx -> put(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "nombre"))))));
    }

    private static int put(CommandSourceStack source, String id, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        JsonArray keep = new JsonArray();
        JsonElement e = STORE_IMPL.root.get("puntos");
        if (e != null && e.isJsonArray()) for (JsonElement p : e.getAsJsonArray()) if (!TFJson.str(p.getAsJsonObject(), "id", "").equals(id)) keep.add(p);
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("nombre", name.isBlank() ? id : name);
        o.addProperty("dim", player.level().dimension().location().toString());
        o.addProperty("x", player.getX());
        o.addProperty("y", player.getY());
        o.addProperty("z", player.getZ());
        o.addProperty("yaw", player.getYRot());
        o.addProperty("pitch", player.getXRot());
        keep.add(o);
        STORE_IMPL.root.add("puntos", keep);
        STORE_IMPL.changed();
        source.sendSuccess(() -> Component.literal("Punto de viaje " + id + " puesto aquí.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
