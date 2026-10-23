/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * The Void (1.3.88, lo pidió el dueño: «quiero que The Void sea una nueva dimensión, e iré solo con el NPC, el caballero
 * dorado con alas; el cielo será de la dimensión; no quiero errores ni que me tire el servidor, es Mohist»).
 *
 * <ul>
 *   <li>La dimensión {@code tfclient:void} (datos en data/tfclient: dimension, dimension_type, worldgen/biome): la luz del
 *   End (sin luz del cielo, hora fija, el mapa de luz más claro), sin sol ni lluvia, sin criaturas y sin estructuras.
 *   Su generador ({@link VoidIslandGenerator}) pone la isla del mapa del dueño ({@link VoidIsland}).</li>
 *   <li>El Caballero Sacro, Caelthar ({@link KnightEntity}; «Aurelion, caballero de luz» en la 1.3.88): se pone con {@code /tf void npc}; habla, cuenta el lore y lleva al
 *   vacío con una animación de luz ({@link VoidTravel}). En el santuario de la isla hay otro (sale solo) que te
 *   devuelve a donde estabas.</li>
 *   <li>La isla está protegida (solo el staff rompe o pone bloques), las camas y anclas no explotan (no se usan) y quien
 *   cae al vacío vuelve al santuario.</li>
 * </ul>
 */
public final class TFVoid {
    public static final ResourceKey<Level> VOID = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(TFClient.MOD_ID, "void"));

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, TFClient.MOD_ID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> ISLAND_GENERATOR =
            GENERATORS.register("void_island", () -> VoidIslandGenerator.CODEC);

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TFClient.MOD_ID);
    public static final RegistryObject<EntityType<KnightEntity>> KNIGHT = ENTITIES.register("caballero", () ->
            EntityType.Builder.<KnightEntity>of(KnightEntity::new, MobCategory.MISC)
                    .sized(0.9F, 2.9F).clientTrackingRange(10).updateInterval(3).fireImmune().build("caballero"));
    /** (1.3.97) Los caballeros sacros del santuario (SacredKnights). */
    public static final RegistryObject<EntityType<SacredKnight>> SACRED = ENTITIES.register("caballero_sacro", () ->
            EntityType.Builder.<SacredKnight>of(SacredKnight::new, MobCategory.MISC)
                    .sized(0.8F, 2.8F).clientTrackingRange(10).updateInterval(2).fireImmune().build("caballero_sacro"));

    /** Avisos de «la isla está protegida» (uno cada 3 s por jugador). */
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    /** Cuándo se rescató a cada jugador del vacío (para no repetirlo en cada tick: ver onPlayerTick). */
    private static final Map<UUID, Long> RESCUED = new HashMap<>();
    private static int tick;

    private TFVoid() {}

    public static void init(IEventBus modBus) {
        GENERATORS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(TFVoid::attributes);
        MinecraftForge.EVENT_BUS.register(TFVoid.class);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(KNIGHT.get(), KnightEntity.attributes().build());
        event.put(SACRED.get(), SacredKnight.attributes().build());
    }

    public static boolean isVoid(Level level) {
        return level != null && level.dimension() == VOID;
    }

    // ------------------------------------------------------------------------------------------- comandos

    /** /tf void ... (staff): poner y quitar caballeros, ir y volver, la grieta del cielo. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("void").requires(s -> s.hasPermission(2))
                .then(Commands.literal("npc").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    KnightEntity k = KNIGHT.get().create(p.serverLevel());
                    if (k == null) return 0;
                    BlockPos at = p.blockPosition();
                    float yaw = p.getYRot() + 180F;
                    k.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0F);
                    k.setYHeadRot(yaw);
                    k.setYBodyRot(yaw);
                    p.serverLevel().addFreshEntity(k);
                    ctx.getSource().sendSuccess(() -> Component.literal(KnightEntity.NAME + ", el " + KnightEntity.TITLE + ", puesto. Los jugadores le hablan con clic derecho.")
                            .withStyle(ChatFormatting.GOLD), true);
                    // La primera vez que se le pone en un mundo Overworld: el evento de la grieta del cielo (1.3.93)
                    if (SkyRift.state() == SkyRift.OFF && !SkyRift.running() && SkyRift.overworldLike(p.serverLevel())) {
                        SkyRift.start(p.serverLevel(), k.position());
                        ctx.getSource().sendSuccess(() -> Component.literal("Empieza el evento de la grieta del cielo en todos los mundos Overworld "
                                + "(/tf void cielo para controlarla).").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    }
                    return 1;
                }))
                .then(SkyRift.command())
                // (1.3.98) tras caer un jefe la grieta del Void se apaga: el staff enciende la siguiente
                .then(Commands.literal("grieta").then(Commands.literal("encender").executes(ctx -> {
                    String msg = net.tierrasfantasticas.tfclient.bosses.Rifts.voidActivate();
                    ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    return 1;
                })))
                .then(Commands.literal("quitar").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int n = 0;
                    for (KnightEntity k : p.serverLevel().getEntitiesOfClass(KnightEntity.class, p.getBoundingBox().inflate(6))) {
                        k.discard();
                        n++;
                    }
                    int removed = n;
                    ctx.getSource().sendSuccess(() -> Component.literal("Caballeros quitados: " + removed
                            + (removed == 0 ? " (acércate a menos de 6 bloques)" : "")), true);
                    return n;
                }))
                .then(Commands.literal("ir")
                        .executes(ctx -> VoidTravel.adminSend(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()), true))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(ctx -> VoidTravel.adminSend(ctx.getSource(), EntityArgument.getPlayers(ctx, "jugadores"), true))))
                .then(Commands.literal("volver")
                        .executes(ctx -> VoidTravel.adminSend(ctx.getSource(), java.util.List.of(ctx.getSource().getPlayerOrException()), false))
                        .then(Commands.argument("jugadores", EntityArgument.players())
                                .executes(ctx -> VoidTravel.adminSend(ctx.getSource(), EntityArgument.getPlayers(ctx, "jugadores"), false))));
    }

    // ------------------------------------------------------------------------------------------- servidor

    private static boolean staff(Player p) {
        return p != null && (p.hasPermissions(2) || p.isCreative() && p.hasPermissions(1));
    }

    private static void warn(Player p) {
        long now = p.level().getGameTime();
        Long last = WARNED.get(p.getUUID());
        if (last != null && now - last < 60) return;
        WARNED.put(p.getUUID(), now);
        p.displayClientMessage(Component.literal("La isla del Void está protegida por la luz del santuario.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level l && isVoid(l) && !staff(event.getPlayer())) {
            event.setCanceled(true);
            warn(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level l && isVoid(l)) {
            if (event.getEntity() instanceof Player p && staff(p)) return;
            event.setCanceled(true);
            if (event.getEntity() instanceof Player p) warn(p);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        Level l = event.getLevel();
        if (!isVoid(l)) return;
        // Camas y anclas: en el vacío explotarían (como en el End); aquí, simplemente, no se usan
        var state = l.getBlockState(event.getPos());
        if (state.getBlock() instanceof BedBlock || state.getBlock() instanceof RespawnAnchorBlock) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.literal("En el Void no se puede dormir.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        ItemStack it = event.getItemStack();
        if (!staff(event.getEntity()) && (it.getItem() instanceof BucketItem || it.getItem() instanceof FlintAndSteelItem
                || it.getItem() instanceof FireChargeItem)) {
            event.setCanceled(true);
            warn(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || !isVoid(p.level())) return;
        // Quien cae al vacío vuelve al santuario (la isla flota sobre la nada: sin esto, se perdían las cosas).
        // Una vez cada 2 s como mucho (1.3.93): Minecraft deja al jugador donde estaba en el servidor hasta que su
        // cliente confirma el teletransporte, y con uno nuevo en cada tick un cliente lento (<20 fps: en la prueba con
        // shaders, <1) siempre confirmaba uno ya viejo y no se le rescataba nunca. Si no llega, lo reenvía Minecraft.
        if (p.getY() < 2 && !p.isSpectator() && VoidTravel.idle(p)) {
            long now = p.level().getGameTime();
            Long last = RESCUED.get(p.getUUID());
            if (last != null && now - last >= 0 && now - last < 40) return;
            RESCUED.put(p.getUUID(), now);
            Vec3 a = VoidIsland.arrivePos();
            p.teleportTo(p.serverLevel(), a.x, a.y + 0.5, a.z, VoidIsland.arriveYaw(), 0F);
            p.fallDistance = 0;
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
            p.displayClientMessage(Component.literal("La luz del Alba te ha rescatado del vacío y te ha devuelto al santuario.").withStyle(ChatFormatting.GOLD), true);
            VoidNet.fx(p, VoidNet.FX_ARRIVE);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        IslandUpdate.reset();
        LightWells.reset();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        VoidTravel.tick(server);
        SkyRift.tick(server);
        ServerLevel level = server.getLevel(VOID);
        if (level != null) {
            IslandUpdate.tick(level);
            // (1.3.96) las esquirlas de los Pozos de Luz y el soporte del Altar del Vacío, cuando la isla ya está al día
            // (si no, saldrían donde aún no hay suelo)
            if (IslandUpdate.finished()) {
                LightWells.tick(level);
                if (level.getGameTime() % 20 == 0) VoidArmor.tickAltar(level);
            }
        }
        if (++tick % 100 != 0) return;
        if (level == null || level.players().isEmpty()) return;
        ensureHomeKnight(level);
        if (IslandUpdate.finished()) SacredKnights.ensure(level);
    }

    /** El caballero del santuario (el que te devuelve): si no está y su chunk tiene ya sus entidades, sale. */
    static void ensureHomeKnight(ServerLevel level) {
        BlockPos at = VoidIsland.knightPos();
        if (!level.hasChunkAt(at) || !level.areEntitiesLoaded(ChunkPos.asLong(at))) return;
        AABB box = new AABB(at).inflate(8, 6, 8);
        if (!level.getEntitiesOfClass(KnightEntity.class, box, KnightEntity::home).isEmpty()) return;
        KnightEntity k = KNIGHT.get().create(level);
        if (k == null) return;
        k.setHome(true);
        // Mira al sur: hacia la isla, la grieta del centro y donde se llega
        k.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        k.setYHeadRot(0F);
        k.setYBodyRot(0F);
        level.addFreshEntity(k);
        TFClient.LOGGER.info("TF Void: el caballero del santuario aparece en {}", at);
    }
}
