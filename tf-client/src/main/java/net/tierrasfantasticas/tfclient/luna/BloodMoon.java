/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.luna;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.server.TFBirthday;
import net.tierrasfantasticas.tfclient.server.TFBotPlayer;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;

/**
 * (1.3.175) La Luna de Sangre. El dueño: «la gente me pide más dificultad… hagamos algo con los mobs, me gusta la noche
 * de sangre con modificadores en los mobs», «una vez por cada 10 noches dentro del juego», «el mínimo para que se active
 * la luna de sangre son 3 personas en simultáneo; si no, se skipea la noche y cuenta regresiva otra vez; si hay 3
 * simultáneas se activa cuando toque, pero justo antes de que se active si solo hay 2 no se activa el evento»; «mobs con
 * ojos rojos brillantes», «mobs fuertes con daño mágico que ignoren la armadura», «una musiquita baja tenebrosa durante
 * el evento» y «una escena para todos con el nacimiento de la luna».
 *
 * <ul>
 *   <li><b>Cuándo</b>: al anochecer (hora 13000 del Overworld principal) cuenta una noche más, solo si hay alguien
 *       conectado ({@link BloodMoonConfig#contarNochesVacias}). La que hace {@code cadaNoches} (10): si en ese momento
 *       hay {@code minJugadores} (3) o más, sale la luna; si no, se avisa y la cuenta vuelve a empezar. Si la anterior
 *       fue hace menos de {@code minMinutosEntre} (2 h reales), se espera a la noche siguiente. Avisos al anochecer en
 *       las últimas noches («faltan N noches») y la noche de antes. Dura hasta el amanecer (23200).</li>
 *   <li><b>Qué pasa</b>: la escena para todos ({@code client.BloodMoonScene}, 16 s, nadie recibe daño de los mobs
 *       mientras tanto), y luego: cielo y luna rojos, ojos rojos que brillan, música, nadie duerme, más mobs hostiles
 *       alrededor de cada jugador (no dentro de las protecciones), más agresivos, con modificadores y los fuertes con
 *       daño mágico ({@link BloodMobs}). Más experiencia. Al amanecer, los que puso el evento se deshacen.</li>
 *   <li>Solo en los mundos con cielo de Overworld que estén de noche (los de Multiverse cuentan; el Nether, el End y el
 *       Void no). La configuración: config/tfclient/luna_sangre.json; el estado (cuenta atrás), en el mundo:
 *       tfclient/luna_sangre_estado.json.</li>
 *   <li>Staff: {@code /tf luna estado | empezar [sinescena] | parar | noches <n>}.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class BloodMoon {
    public static final String FILE = "luna_sangre.json";
    /** La luna sale a las 13000 (ya asoma por el este: la escena la ve nacer) y se va al amanecer. */
    static final long DUSK = 13000, DAWN = 23200;
    /** La escena dura 16 s: mientras, los mobs no hacen daño a nadie ni aparecen los extra. */
    public static final int SCENE_TICKS = 320;

    private static BloodMoonConfig cfg = new BloodMoonConfig();
    private static Path configPath;
    private static Path statePath;
    private static int nightsLeft = -1;
    private static long lastStartMs;
    private static long seenNight = Long.MIN_VALUE;
    private static boolean active;
    private static long startTick;
    /** Para la prueba: lo último que pasó al anochecer. */
    public static String lastDusk = "";

    private BloodMoon() {}

    public static boolean active() {
        return active;
    }

    public static int nightsLeft() {
        return nightsLeft;
    }

    public static BloodMoonConfig config() {
        return cfg;
    }

    // ------------------------------------------------------------------------------------------------- arranque

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        configPath = TFConfigDir.dir().resolve(FILE);
        cfg = BloodMoonConfig.load(configPath);
        statePath = event.getServer().getWorldPath(LevelResource.ROOT).resolve("tfclient").resolve("luna_sangre_estado.json");
        nightsLeft = cfg.cadaNoches;
        try {
            if (Files.exists(statePath)) {
                JsonObject o = JsonParser.parseString(Files.readString(statePath, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("nochesQuedan")) nightsLeft = Math.max(0, o.get("nochesQuedan").getAsInt());
                if (o.has("ultimaMs")) lastStartMs = o.get("ultimaMs").getAsLong();
                if (o.has("nocheVista")) seenNight = o.get("nocheVista").getAsLong();
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Luna de Sangre: no se pudo leer {} (la cuenta empieza de nuevo)", statePath, e);
        }
        active = false;
        TFClient.LOGGER.info("TF Luna de Sangre: {} noches para la próxima (cada {}, mínimo {} jugadores)", nightsLeft, cfg.cadaNoches, cfg.minJugadores);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        active = false;
        BloodMobs.LIVE.clear();
    }

    /** /tf reload. */
    public static List<String> reload() throws Exception {
        if (configPath == null) throw new IllegalStateException("el servidor aún no la ha cargado");
        cfg = BloodMoonConfig.read(configPath);
        cfg.save(configPath);
        return List.of();
    }

    private static void saveState() {
        if (statePath == null) return;
        try {
            Files.createDirectories(statePath.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("nochesQuedan", nightsLeft);
            o.addProperty("ultimaMs", lastStartMs);
            o.addProperty("nocheVista", seenNight);
            Files.writeString(statePath, BloodMoonConfig.GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Luna de Sangre: no se pudo guardar el estado", e);
        }
    }

    // ------------------------------------------------------------------------------------------------- noches

    static boolean night(Level level) {
        long t = Math.floorMod(level.getDayTime(), 24000L);
        return t >= DUSK && t < DAWN;
    }

    /** Un mundo donde se ve la luna esta noche: cielo de Overworld (también los de Multiverse) y de noche. */
    public static boolean here(Level level) {
        return active && TFBirthday.skyHere(level) && night(level);
    }

    /** Jugadores de verdad (los bots de la lista no cuentan; las dianas de la prueba en el juego, sí). */
    public static int online(MinecraftServer server) {
        int n = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!(p instanceof TFBotPlayer) || TFBotPlayer.isTarget(p)) n++;
        }
        return n;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        ServerLevel over = server.overworld();
        long gameTime = over.getGameTime();
        if (gameTime % 20 == 0) checkNight(server, over);
        if (!active) return;
        if (!night(over)) {
            stop(server, true);
            return;
        }
        if (gameTime % 10 == 0) BloodMobs.tick(gameTime);
        if (gameTime - startTick > SCENE_TICKS && gameTime % (20L * cfg.segundosEntreOleadas) == 0) spawnWave(server);
        if (gameTime % 100 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, false);
        }
    }

    private static void checkNight(MinecraftServer server, ServerLevel over) {
        long idx = Math.floorDiv(over.getDayTime() - DUSK, 24000L);
        if (seenNight == Long.MIN_VALUE || idx < seenNight) {
            seenNight = idx;
            saveState();
            return;
        }
        if (idx == seenNight) return;
        seenNight = idx;
        // la noche pasó sin que se viera (se durmió antes de las 13000 o el tiempo saltó): cuenta, pero la luna no sale
        // hasta la próxima noche de verdad
        if (!night(over)) {
            skipped(server);
            saveState();
            return;
        }
        dusk(server);
        saveState();
    }

    /** Una noche que se saltó (dormir antes de que salga la luna): cuenta igual, sin bajar de 0. */
    static void skipped(MinecraftServer server) {
        if (active || !cfg.activa) return;
        if (online(server) == 0 && !cfg.contarNochesVacias) return;
        nightsLeft = Math.max(0, nightsLeft - 1);
        lastDusk = "noche saltada (durmiendo): quedan " + nightsLeft;
    }

    /** Anochece en el Overworld principal. */
    static void dusk(MinecraftServer server) {
        if (active || !cfg.activa) return;
        int players = online(server);
        if (players == 0 && !cfg.contarNochesVacias) {
            lastDusk = "noche vacía: no cuenta (quedan " + nightsLeft + ")";
            return;
        }
        nightsLeft = Math.max(0, nightsLeft - 1);
        if (nightsLeft > 0) {
            lastDusk = "faltan " + nightsLeft;
            if (nightsLeft == 1) {
                broadcast(server, Component.literal("☾ ").withStyle(ChatFormatting.DARK_RED)
                        .append(Component.literal("La luna empieza a teñirse de rojo… ").withStyle(ChatFormatting.RED))
                        .append(Component.literal("Mañana por la noche sale la Luna de Sangre (si hay al menos " + cfg.minJugadores + " conectados).")
                                .withStyle(ChatFormatting.GRAY)));
            } else if (nightsLeft <= cfg.avisarDesde) {
                broadcast(server, Component.literal("☾ ").withStyle(ChatFormatting.DARK_RED)
                        .append(Component.literal("Faltan " + nightsLeft + " noches para la Luna de Sangre.").withStyle(ChatFormatting.GRAY)));
            }
            return;
        }
        // toca: ¿hay gente suficiente justo ahora?
        if (players < cfg.minJugadores) {
            nightsLeft = cfg.cadaNoches;
            lastDusk = "no sale: " + players + " de " + cfg.minJugadores + " jugadores; la cuenta vuelve a " + nightsLeft;
            broadcast(server, Component.literal("☾ ").withStyle(ChatFormatting.DARK_RED)
                    .append(Component.literal("La Luna de Sangre no sale esta noche: ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("hacen falta " + cfg.minJugadores + " jugadores conectados y hay " + players
                            + ". La cuenta vuelve a empezar: " + nightsLeft + " noches.").withStyle(ChatFormatting.GRAY)));
            TFClient.LOGGER.info("TF Luna de Sangre: no sale ({} de {} jugadores); la cuenta vuelve a {}", players, cfg.minJugadores, nightsLeft);
            return;
        }
        long now = System.currentTimeMillis();
        if (lastStartMs > 0 && now - lastStartMs < cfg.minMinutosEntre * 60_000L) {
            nightsLeft = 1;
            lastDusk = "espera: la anterior fue hace " + (now - lastStartMs) / 60_000L + " min";
            TFClient.LOGGER.info("TF Luna de Sangre: la anterior fue hace {} min (mínimo {}): se espera a la noche siguiente",
                    (now - lastStartMs) / 60_000L, cfg.minMinutosEntre);
            return;
        }
        lastDusk = "sale con " + players + " jugadores";
        start(server, cfg.escena);
    }

    // ------------------------------------------------------------------------------------------------- empezar y acabar

    public static void start(MinecraftServer server, boolean scene) {
        active = true;
        startTick = server.overworld().getGameTime();
        lastStartMs = System.currentTimeMillis();
        nightsLeft = cfg.cadaNoches;
        saveState();
        TFClient.LOGGER.info("TF Luna de Sangre: ¡empieza! ({} jugadores)", online(server));
        broadcast(server, Component.literal("\n")
                .append(Component.literal("  ☾ LA LUNA DE SANGRE SE ALZA ☾\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal("  Los monstruos son más agresivos y peligrosos. Los más fuertes atraviesan tu armadura.\n")
                        .withStyle(ChatFormatting.RED))
                .append(Component.literal("  Esta noche nadie duerme. Sobrevive hasta el amanecer.\n").withStyle(ChatFormatting.GRAY)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            send(p, scene);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.25F, 0.5F);
        }
        // los mobs que ya están cerca de alguien también se despiertan
        for (ServerLevel level : server.getAllLevels()) {
            if (!here(level)) continue;
            List<Mob> mobs = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (BloodMobs.eligible(e)) mobs.add((Mob) e);
            }
            for (Mob m : mobs) BloodMobs.empower(m, level, cfg);
        }
    }

    public static void stop(MinecraftServer server, boolean dawn) {
        if (!active) return;
        active = false;
        TFClient.LOGGER.info("TF Luna de Sangre: termina ({})", dawn ? "amanece" : "la para el staff");
        broadcast(server, Component.literal("☀ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(dawn ? "Amanece. La Luna de Sangre se oculta… hasta la próxima." : "La Luna de Sangre se ha ocultado.")
                        .withStyle(ChatFormatting.YELLOW)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) send(p, false);
        for (ServerLevel level : server.getAllLevels()) {
            List<Mob> mobs = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mob m && BloodMobs.marked(m)) mobs.add(m);
            }
            for (Mob m : mobs) BloodMobs.clean(m);
        }
        BloodMobs.LIVE.clear();
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    /** Durante la escena nadie recibe daño de los mobs. */
    static boolean inScene(Level level) {
        return active && level.getServer() != null && level.getServer().overworld().getGameTime() - startTick < SCENE_TICKS;
    }

    // ------------------------------------------------------------------------------------------------- mobs extra

    private static void spawnWave(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.isCreative() || p instanceof TFBotPlayer && !TFBotPlayer.isTarget(p)) continue;
            if (!(p.level() instanceof ServerLevel level) || !here(level)) continue;
            int near = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m instanceof Enemy).size();
            int want = Math.min(cfg.porOleada, cfg.mobsPorJugador - near);
            for (int i = 0; i < want; i++) trySpawn(level, p);
        }
    }

    /** Cuántos ha puesto el evento (para la prueba). */
    public static int spawned;

    private static void trySpawn(ServerLevel level, ServerPlayer p) {
        RandomSource r = level.getRandom();
        for (int tries = 0; tries < 6; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 22 + r.nextDouble() * 18;
            int x = Mth.floor(p.getX() + Math.cos(a) * d), z = Mth.floor(p.getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(pos)) continue;
            if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
            if (!level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above()) || !level.getFluidState(pos.below()).isEmpty()) continue;
            if (level.getNearestPlayer(x + 0.5, y, z + 0.5, 18, false) != null) continue;
            if (!cfg.aparecerEnProtecciones && claimed(level, pos)) continue;
            EntityType<? extends Mob> type = pick(level, pos, r);
            Mob mob = type.create(level);
            if (mob == null) return;
            mob.moveTo(x + 0.5, y, z + 0.5, r.nextFloat() * 360F, 0F);
            if (!level.noCollision(mob)) continue;
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putBoolean("extra", true);
            mob.getPersistentData().put(BloodMobs.TAG, t);
            if (!level.addFreshEntity(mob)) return;
            mob.setTarget(p);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5, y + 0.4, z + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
            spawned++;
            return;
        }
    }

    private static boolean claimed(ServerLevel level, BlockPos pos) {
        try {
            return net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance().getClaimAt(level, pos) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static EntityType<? extends Mob> pick(ServerLevel level, BlockPos pos, RandomSource r) {
        Biome biome = level.getBiome(pos).value();
        float temp = biome.getBaseTemperature();
        boolean cold = temp < 0.15F, hot = temp > 1.5F && !biome.hasPrecipitation();
        int x = r.nextInt(100);
        if (x < 34) return hot ? EntityType.HUSK : EntityType.ZOMBIE;
        if (x < 60) return cold ? EntityType.STRAY : EntityType.SKELETON;
        if (x < 76) return EntityType.SPIDER;
        if (x < 90) return EntityType.CREEPER;
        if (x < 96) return EntityType.ZOMBIE_VILLAGER;
        return EntityType.WITCH;
    }

    // ------------------------------------------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (BloodMobs.marked(mob) && !here(level)) {
            // se carga de día (o ya no hay luna): fuera lo de la luna (los del evento se deshacen)
            level.getServer().execute(() -> {
                if (!mob.isRemoved()) BloodMobs.clean(mob);
            });
            return;
        }
        if (here(level) && (BloodMobs.eligible(mob) || BloodMobs.marked(mob))) BloodMobs.empower(mob, level, cfg);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) BloodMobs.LIVE.remove(mob);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Player && event.getSource().getEntity() instanceof Enemy && inScene(event.getEntity().level())) {
            event.setCanceled(true);
            return;
        }
        BloodMobs.Data victim = BloodMobs.data(event.getEntity());
        if (victim != null && BloodMobs.blocksProjectile((Mob) event.getEntity(), victim, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (BloodMobs.convertToMagic(event.getEntity(), event.getSource(), event.getAmount())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getSource().getEntity() instanceof Mob attacker) {
            BloodMobs.Data d = BloodMobs.data(attacker);
            if (d != null && event.getEntity() != attacker) event.setAmount(BloodMobs.onDealt(attacker, d, event.getEntity(), event.getAmount()));
        }
        if (event.getEntity() instanceof Mob victim) {
            BloodMobs.Data d = BloodMobs.data(victim);
            if (d != null) BloodMobs.onTaken(victim, d, event.getSource(), event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        BloodMobs.Data d = BloodMobs.data(mob);
        if (d != null) BloodMobs.onDeath(mob, d);
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent event) {
        BloodMobs.Data d = BloodMobs.data(event.getEntity());
        if (d != null) event.setDroppedExperience(BloodMobs.experience(d, event.getDroppedExperience(), cfg));
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide() || p.level().getServer() == null) return;
        if (here(p.level())) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.literal("☾ La Luna de Sangre no te deja dormir.").withStyle(ChatFormatting.RED), true);
            return;
        }
        // la tarde en que toca la luna (y con gente suficiente), tampoco: nadie se la salta durmiendo
        MinecraftServer server = p.level().getServer();
        long t = Math.floorMod(server.overworld().getDayTime(), 24000L);
        if (!active && cfg.activa && nightsLeft <= 1 && t >= 12000 && t < DUSK && TFBirthday.skyHere(p.level())
                && online(server) >= cfg.minJugadores) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            p.displayClientMessage(Component.literal("☾ Esta noche sale la Luna de Sangre: no se puede dormir.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            send(p, false);
            if (active) {
                p.sendSystemMessage(Component.literal("☾ ").withStyle(ChatFormatting.DARK_RED)
                        .append(Component.literal("¡Esta noche hay Luna de Sangre! Los monstruos son más peligrosos.").withStyle(ChatFormatting.RED)));
            }
        }
    }

    @SubscribeEvent
    public static void onChangeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) send(p, false);
    }

    // ------------------------------------------------------------------------------------------------- comando

    /** /tf luna estado | empezar [sinescena] | parar | noches &lt;n&gt;. */
    public static LiteralArgumentBuilder<CommandSourceStack> command(String literal) {
        return Commands.literal(literal).requires(s -> s.hasPermission(2))
                .then(Commands.literal("estado").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    long t = Math.floorMod(server.overworld().getDayTime(), 24000L);
                    MutableComponent m = Component.literal("Luna de Sangre: ").withStyle(ChatFormatting.DARK_RED)
                            .append(Component.literal(active ? "¡ACTIVA!" : "faltan " + nightsLeft + " noches").withStyle(ChatFormatting.RED))
                            .append(Component.literal(" · conectados " + online(server) + " (mínimo " + cfg.minJugadores + ") · hora " + t
                                    + (night(server.overworld()) ? " (noche)" : " (día)") + " · cada " + cfg.cadaNoches + " noches"
                                    + (cfg.activa ? "" : " · APAGADA en la config") + (lastDusk.isEmpty() ? "" : " · último anochecer: " + lastDusk))
                                    .withStyle(ChatFormatting.GRAY));
                    ctx.getSource().sendSuccess(() -> m, false);
                    return 1;
                }))
                .then(Commands.literal("empezar").executes(ctx -> manualStart(ctx.getSource(), true))
                        .then(Commands.literal("sinescena").executes(ctx -> manualStart(ctx.getSource(), false))))
                .then(Commands.literal("parar").executes(ctx -> {
                    if (!active) {
                        ctx.getSource().sendFailure(Component.literal("No hay Luna de Sangre ahora."));
                        return 0;
                    }
                    stop(ctx.getSource().getServer(), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("Luna de Sangre parada."), true);
                    return 1;
                }))
                .then(Commands.literal("noches").then(Commands.argument("n", IntegerArgumentType.integer(1, 1000)).executes(ctx -> {
                    nightsLeft = IntegerArgumentType.getInteger(ctx, "n");
                    saveState();
                    ctx.getSource().sendSuccess(() -> Component.literal("Faltan " + nightsLeft + " noches para la Luna de Sangre."), true);
                    return 1;
                })));
    }

    private static int manualStart(CommandSourceStack src, boolean scene) {
        MinecraftServer server = src.getServer();
        if (active) {
            src.sendFailure(Component.literal("La Luna de Sangre ya está activa."));
            return 0;
        }
        if (!night(server.overworld())) {
            // si es de día, se pone la hora del anochecer (sale con la luna)
            long day = server.overworld().getDayTime();
            long to = day - Math.floorMod(day, 24000L) + DUSK + 50;
            server.overworld().setDayTime(to);
            seenNight = Math.floorDiv(to - DUSK, 24000L);
        }
        start(server, scene);
        src.sendSuccess(() -> Component.literal("¡Luna de Sangre en marcha!" + (scene ? "" : " (sin escena)")), true);
        return 1;
    }

    // ------------------------------------------------------------------------------------------------- red

    private static void send(ServerPlayer p, boolean scene) {
        boolean on = here(p.level());
        int age = active ? (int) Math.min(Integer.MAX_VALUE, p.serverLevel().getServer().overworld().getGameTime() - startTick) : 0;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new State(on, scene && on, nightsLeft, age));
    }

    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "luna"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(State.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(State::write).decoder(State::read).consumerMainThread(State::handle).add();
    }

    /** Si aquí hay luna, si toca la escena, cuántas noches faltan y cuánto lleva (ticks). */
    public record State(boolean active, boolean scene, int nightsLeft, int age) {
        static void write(State m, FriendlyByteBuf buf) {
            buf.writeBoolean(m.active);
            buf.writeBoolean(m.scene);
            buf.writeVarInt(m.nightsLeft);
            buf.writeVarInt(m.age);
        }

        static State read(FriendlyByteBuf buf) {
            return new State(buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt());
        }

        static void handle(State m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.tierrasfantasticas.tfclient.luna.client.BloodMoonClient.state(m.active, m.scene, m.nightsLeft, m.age));
        }
    }
}
