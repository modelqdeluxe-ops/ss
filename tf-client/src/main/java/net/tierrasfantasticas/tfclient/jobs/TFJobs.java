/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.economy.TFEconomy;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Job;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Mission;
import net.tierrasfantasticas.tfclient.jobs.TFJobsConfig.Reward;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.JobProgress;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.MissionState;
import net.tierrasfantasticas.tfclient.jobs.TFJobsData.PlayerJobs;
import net.tierrasfantasticas.tfclient.server.TFBridge;
import net.tierrasfantasticas.tfclient.server.TFServerConfig;
import net.tierrasfantasticas.tfclient.shop.TFCoinShop;
import net.tierrasfantasticas.tfclient.util.TFChat;

/**
 * Oficios: el jugador elige uno en el pad (app Oficios) y gana experiencia del oficio y monedas al hacer su trabajo (romper,
 * cosechar, pescar...). Sube de nivel, cumple misiones con recompensa y puede dejarlo cuando quiera sin perder nada.
 * Todo se ajusta en config/tfclient/oficios.json ({@link TFJobsConfig}).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFJobs {
    private static MinecraftServer server;
    /** Monedas ganadas aún sin pagar (se pagan juntas cada pagoCadaSegundos). */
    private static final Map<UUID, Double> pending = new HashMap<>();
    /** Lo ganado desde el último aviso en la barra de acción. */
    /** Encantamientos hechos (estadística del juego) para saber cuándo encanta alguien. */
    private static final Map<UUID, Integer> enchants = new HashMap<>();
    private static final String SPAWNER_TAG = "tfclient_generador";

    private TFJobs() {}

    public static TFJobsData data() {
        return TFJobsData.get(server);
    }

    // --- Ciclo del servidor ---

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        net.tierrasfantasticas.tfclient.util.TFConfigDir.writeGuide();
        TFJobsConfig.load();
        TFPlacedBlocks.load(server);
        // (1.3.143) avisa en la consola de las misiones que no se pueden hacer (un objetivo mal escrito, una etiqueta vacía…)
        TFMissionCheck.logProblems(server);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        if (server != null) payAll(true);
        TFJobsData.unload();
        TFPlacedBlocks.save();
        pending.clear();
        enchants.clear();
        server = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        int tick = server.getTickCount();
        if (!picks.isEmpty()) checkPicks();
        if (tick % 10 == 0) {
            checkEnchants();
        }
        if (tick % (TFJobsConfig.paySeconds * 20) == 0) payAll(false);
        if (tick % 1200 == 0) {
            data().save();
            TFPlacedBlocks.save();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (server == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        pay(player.getUUID(), player.getGameProfile().getName(), true);
        enchants.remove(player.getUUID());
    }

    // --- Lo que hace el jugador ---

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !playing(player)) return;
        BlockState state = event.getState();
        String level = event.getLevel() instanceof net.minecraft.world.level.Level l ? l.dimension().location().toString() : "";
        boolean placed = TFJobsConfig.placedDontCount && TFPlacedBlocks.placed(level, event.getPos());
        // Cosechar: los cultivos (con edad) solo cuentan maduros, aunque los plantara el jugador; el resto, si no los puso él.
        Integer ripe = ripeness(state);
        boolean harvest = ripe != null ? ripe == 1 : !placed;
        if (harvest) record(player, "cosechar", t -> t.block(state), 1);
        if (!placed) record(player, "romper", t -> t.block(state), 1);
    }

    /** 1 si es un cultivo maduro, 0 si está verde, null si no es un cultivo (caña, cactus... cuentan siempre). */
    private static Integer ripeness(BlockState state) {
        var block = state.getBlock();
        boolean crop = block instanceof net.minecraft.world.level.block.CropBlock || block instanceof net.minecraft.world.level.block.StemBlock
                || block instanceof net.minecraft.world.level.block.NetherWartBlock || block instanceof net.minecraft.world.level.block.CocoaBlock
                || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || block instanceof net.minecraft.world.level.block.PitcherCropBlock;
        if (!crop) return null;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && age.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return state.getValue(age) >= max ? 1 : 0;
            }
        }
        return null;
    }

    /**
     * (1.3.143, el dueño: «verifica todas las misiones») Las bayas dulces y las luminosas se cogen con clic derecho, sin
     * romper la planta: se mira al final del tick si de verdad se cogieron (la planta ya no tiene bayas) y entonces cuenta
     * como cosechar esa planta madura.
     */
    private record Pick(ServerPlayer player, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, BlockState state) {}
    private static final List<Pick> picks = new java.util.ArrayList<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClick(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || !playing(player)
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        BlockState state = level.getBlockState(event.getPos());
        if (berries(state) && picks.size() < 256) picks.add(new Pick(player, level, event.getPos().immutable(), state));
    }

    private static boolean berries(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.SweetBerryBushBlock)
            return state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) >= 2;
        return state.getBlock() instanceof net.minecraft.world.level.block.CaveVines
                && state.hasProperty(net.minecraft.world.level.block.CaveVines.BERRIES)
                && state.getValue(net.minecraft.world.level.block.CaveVines.BERRIES);
    }

    private static void checkPicks() {
        for (Pick p : List.copyOf(picks)) {
            BlockState now = p.level().getBlockState(p.pos());
            if (now.getBlock() == p.state().getBlock() && !berries(now) && p.player().isAlive()) {
                // la planta, como si estuviera madura: así cuenta para «cosechar minecraft:sweet_berry_bush»
                record(p.player(), "cosechar", t -> t.block(p.state()), 1);
                if (TEST) TEST_LOG.add("cosechar " + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(p.state().getBlock()) + " 1");
            }
        }
        picks.clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof net.minecraft.world.level.Level l)) return;
        boolean fresh = TFPlacedBlocks.place(l.dimension().location().toString(), event.getPos());
        if (!playing(player) || (TFJobsConfig.placedDontCount && !fresh)) return;
        BlockState state = event.getPlacedBlock();
        record(player, "colocar", t -> t.block(state), 1);
    }

    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() == MobSpawnType.SPAWNER) event.getEntity().getPersistentData().putBoolean(SPAWNER_TAG, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || victim instanceof Player || !playing(player)) return;
        if (!TFJobsConfig.spawnersCount && victim.getPersistentData().getBoolean(SPAWNER_TAG)) return;
        record(player, "matar", t -> t.entity(victim), 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFish(ItemFishedEvent event) {
        if (!(event.getHookEntity().getPlayerOwner() instanceof ServerPlayer player) || !playing(player)) return;
        for (ItemStack stack : event.getDrops()) record(player, "pescar", t -> t.item(stack), stack.getCount());
    }

    /**
     * (1.3.139, el dueño: «una misión me pide fundir hierro y cuando fundo no me cuenta») Lo fabricado (mesa de trabajo,
     * inventario) y lo fundido (horno, ahumador, alto horno): lo cuentan mixin/jobs/ResultSlotJobsMixin y
     * FurnaceResultSlotJobsMixin con lo que de verdad se saca del hueco del resultado. Antes eran los eventos de Forge
     * (ItemCraftedEvent, ItemSmeltedEvent), que con mayúsculas + clic llegan con el montón ya vacío («aire»: no contaba).
     */
    public static void made(ServerPlayer player, String type, ItemStack stack, int amount) {
        if (!playing(player) || stack.isEmpty() || amount <= 0) return;
        ItemStack item = stack.copy();
        if (TEST) TEST_LOG.add(type + " " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem()) + " " + amount);
        record(player, type, t -> t.item(item), amount);
    }

    /** (1.3.139) Para la prueba en el juego (BossTest «oficios»): lo que se contó de fabricar, fundir y preparar. */
    private static final boolean TEST = System.getenv("TFCLIENT_PRUEBA_JEFES") != null;
    public static final List<String> TEST_LOG = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Mientras se sacan pociones con mayúsculas + clic (las cuenta BrewingStandMenuJobsMixin, no el evento). */
    public static boolean quickBrew;

    @SubscribeEvent
    public static void onBrew(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !playing(player) || quickBrew) return;
        ItemStack stack = event.getStack();
        if (stack.isEmpty()) return;
        if (TEST) TEST_LOG.add("preparar " + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()) + " " + stack.getCount());
        record(player, "preparar", t -> t.item(stack), 1);
    }

    @SubscribeEvent
    public static void onRepair(AnvilRepairEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !playing(player)) return;
        ItemStack stack = event.getOutput();
        record(player, "reparar", t -> t.item(stack), 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player) || !playing(player)) return;
        Entity parent = event.getParentA();
        record(player, "criar", t -> t.entity(parent), 1);
    }

    /** La mesa de encantamientos no avisa: se mira la estadística «objetos encantados» de los jugadores. */
    private static void checkEnchants() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int now = player.getStats().getValue(Stats.CUSTOM.get(Stats.ENCHANT_ITEM));
            Integer before = enchants.put(player.getUUID(), now);
            if (before != null && now > before && playing(player)) record(player, "encantar", t -> t.item(ItemStack.EMPTY), now - before);
        }
    }

    /** Cuenta para las misiones y cazas del pad (cualquier jugador en supervivencia o aventura). */
    private static boolean playing(ServerPlayer player) {
        return server != null && !player.isCreative() && !player.isSpectator();
    }

    private static boolean counts(ServerPlayer player) {
        return server != null && TFJobsConfig.enabled && !player.isCreative() && !player.isSpectator()
                && !data().player(player.getUUID()).active.isEmpty();
    }

    // --- Experiencia, monedas y misiones ---

    /** Algo que hizo el jugador: paga según las acciones de sus oficios y avanza sus misiones. */
    private static void record(ServerPlayer player, String type, Predicate<TFJobsConfig.Target> match, int amount) {
        // Las misiones y cazas del pad cuentan lo mismo (con las mismas reglas: nada de bloques puestos ni spawners)
        net.tierrasfantasticas.tfclient.pad.server.PadMissions.record(player, type, match, amount);
        if (!counts(player)) return;
        PlayerJobs p = data().player(player.getUUID());
        boolean changed = false;
        for (String jobId : List.copyOf(p.active)) {
            Job job = TFJobsConfig.job(jobId);
            if (job == null) continue;
            JobProgress jp = p.job(jobId);
            double xp = 0;
            double coins = 0;
            for (TFJobsConfig.Action action : job.actions()) {
                if (!action.type().equals(type) || !match.test(action.target())) continue;
                xp += action.xp() * amount;
                coins += action.coins() * amount * (1 + TFJobsConfig.coinBonusPerLevel * (jp.level - 1));
                break; // la primera acción que encaja (de más concreta a más general, como estén en la config)
            }
            for (Mission mission : TFJobsConfig.missionsFor(job, player.getUUID(), jp)) {
                if (!mission.type().equals(type) || jp.level < mission.level() || !match.test(mission.target())) continue;
                MissionState ms = refresh(jp, mission);
                if (ms.done || ms.claimedDay >= 0) continue;
                ms.progress = Math.min(mission.amount(), ms.progress + amount);
                changed = true;
                if (ms.progress >= mission.amount()) {
                    ms.done = true;
                    ms.name = mission.name();
                    ms.coins = mission.reward().coins();
                    ms.xp = mission.reward().xp();
                    missionDone(player, job, mission);
                }
            }
            if (xp > 0 || coins > 0) {
                changed = true;
                pending.merge(player.getUUID(), coins, Double::sum);
                addXp(player, job, jp, xp);
            }
        }
        // (1.3.139, el dueño: «quiero cobrar al instante») lo ganado se paga al momento (en cuanto suma una moneda)
        if (pending.getOrDefault(player.getUUID(), 0.0) >= 1) pay(player.getUUID(), player.getGameProfile().getName(), false);
        if (changed) data().changed();
    }

    /** Las misiones diarias vuelven al día siguiente de cobrarlas. */
    public static MissionState refresh(JobProgress jp, Mission mission) {
        MissionState ms = jp.mission(mission.id());
        if (mission.repeat() == TFJobsConfig.Repeat.DAILY && ms.claimedDay >= 0 && ms.claimedDay < TFJobsData.today()) {
            ms.progress = 0;
            ms.done = false;
            ms.claimedDay = -1;
        }
        return ms;
    }

    /** Experiencia de un oficio dada por el staff (puede subir de nivel con sus recompensas). */
    public static void giveXp(ServerPlayer player, Job job, double xp) {
        addXp(player, job, data().player(player.getUUID()).job(job.id()), xp);
        data().changed();
    }

    private static void addXp(ServerPlayer player, Job job, JobProgress jp, double xp) {
        if (jp.level >= TFJobsConfig.maxLevel) {
            jp.xp = 0;
            return;
        }
        jp.xp += xp;
        while (jp.level < TFJobsConfig.maxLevel && jp.xp >= TFJobsConfig.xpFor(jp.level)) {
            jp.xp -= TFJobsConfig.xpFor(jp.level);
            jp.level++;
            levelUp(player, job, jp.level);
        }
        if (jp.level >= TFJobsConfig.maxLevel) jp.xp = 0;
    }

    private static void levelUp(ServerPlayer player, Job job, int level) {
        String name = player.getGameProfile().getName();
        long coins = TFJobsConfig.levelUpCoins(level);
        Reward milestone = TFJobsConfig.milestones.get(level);
        if (milestone != null) coins += Math.round(milestone.coins());
        TFEconomy.give(server, player.getUUID(), name, coins);
        for (String command : TFJobsConfig.levelCommands) runCommand(command, player, job, level);
        if (milestone != null) giveExtras(player, job, level, milestone);
        Style color = Style.EMPTY.withColor(TextColor.fromRgb(job.color()));
        // sin título ni avisos en pantalla: una línea de chat corta («[Oficios] Minero nivel 12 · +35 monedas») y el sonido
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8F, 1.0F);
        StringBuilder gained = new StringBuilder(coins > 0 ? "+" + TFEconomy.format(coins) : "");
        if (milestone != null) for (String item : milestone.items()) gained.append(gained.isEmpty() ? "" : TFChat.SEP).append(itemLabel(item));
        TFChat.send(player, "Oficios", ChatFormatting.GOLD, Component.literal(job.name()).withStyle(color),
                TFChat.hl(" nivel " + level), gained.isEmpty() ? null : TFChat.SEP, TFChat.money(gained.toString()));
        if (TFJobsConfig.announceEvery > 0 && level % TFJobsConfig.announceEvery == 0) {
            Component msg = TFChat.line("Oficios", ChatFormatting.GOLD, TFChat.hl(name), " llegó a ",
                    Component.literal(job.name()).withStyle(color), TFChat.hl(" nivel " + level));
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    private static void missionDone(ServerPlayer player, Job job, Mission mission) {
        TFChat.send(player, "Oficios", ChatFormatting.GOLD, "Misión completada: ", TFChat.hl(mission.name()),
                TFChat.SEP + "cóbrala en el pad (C)");
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.5F, 1.2F);
    }

    /**
     * (1.3.143, el dueño: «y los jobs, que todos cobren») Para la prueba en el juego: con cada oficio (al nivel 50, para
     * que salgan todas), hace las misiones de hoy y las fijas con algo de verdad de su objetivo, las cobra y mira que el
     * saldo suba lo que pagan; y que cada acción que paga cuente. Al final vuelve a dejar los oficios que tenía.
     */
    public static String testAllJobs(ServerPlayer player) {
        PlayerJobs p = data().player(player.getUUID());
        List<String> before = List.copyOf(p.active);
        int ok = 0, actions = 0;
        List<String> bad = new java.util.ArrayList<>();
        for (Job job : TFJobsConfig.jobs.values()) {
            p.active.clear();
            p.active.add(job.id());
            JobProgress jp = p.job(job.id());
            int level = jp.level;
            jp.level = TFJobsConfig.maxLevel;
            jp.rotDay = -1;
            for (TFJobsConfig.Action a : job.actions()) {
                var sample = TFMissionCheck.sample(server, a.type(), a.target().raw());
                if (sample == null || !sample.test(a.target())) bad.add(job.id() + " acción " + a.type() + " " + a.target().raw() + ": no cuenta");
                else actions++;
            }
            for (Mission m : TFJobsConfig.missionsFor(job, player.getUUID(), jp)) {
                MissionState ms = refresh(jp, m);
                if (ms.claimedDay >= 0) continue;
                var sample = TFMissionCheck.sample(server, m.type(), m.target().raw());
                if (sample == null) {
                    bad.add(job.id() + " " + m.id() + ": sin nada con qué hacerla");
                    continue;
                }
                record(player, m.type(), sample, m.amount());
                long b0 = TFEconomy.balance(server, player.getUUID()).orElse(-1);
                String err = claim(player, job, m);
                long b1 = TFEconomy.balance(server, player.getUUID()).orElse(-1);
                long want = Math.round(m.reward().coins());
                // lo de más es el premio de subir de nivel (al nivel máximo no hay)
                if (err != null || b1 - b0 < want) bad.add(job.id() + " " + m.id() + " (" + m.type() + " " + m.target().raw() + "): progreso "
                        + ms.progress + "/" + m.amount() + (err != null ? ", " + err : ", el saldo subió " + (b1 - b0) + " de " + want));
                else ok++;
            }
            jp.level = level;
            jp.rotDay = -1;
        }
        p.active.clear();
        p.active.addAll(before);
        data().changed();
        return ok + " misiones de oficio hechas y cobradas, " + actions + " acciones que cuentan"
                + (bad.isEmpty() ? " (todas bien)" : "; MAL " + bad.size() + ": " + String.join(" | ", bad));
    }

    /** Cobrar una misión hecha. Devuelve el mensaje de error o null si se cobró. */
    public static String claim(ServerPlayer player, Job job, Mission mission) {
        PlayerJobs p = data().player(player.getUUID());
        JobProgress jp = p.job(job.id());
        MissionState ms = refresh(jp, mission);
        if (!ms.done || ms.claimedDay >= 0) return "Esa misión aún no está completada.";
        Reward r = mission.reward();
        // (1.3.139, el dueño: «no me deja cobrar, no cobra nada») primero se paga; si la economía falla, la misión sigue
        // sin cobrar (antes se marcaba cobrada aunque no llegaran las monedas)
        long coins = Math.round(r.coins());
        if (coins > 0 && !TFEconomy.give(server, player.getUUID(), player.getGameProfile().getName(), coins)) {
            TFClient.LOGGER.warn("TF Oficios: no se pudieron pagar {} a {} por «{}» (economía {})", coins,
                    player.getGameProfile().getName(), mission.name(), TFEconomy.mode());
            return "No se pudo pagar ahora mismo. Inténtalo en un momento (la misión sigue sin cobrar).";
        }
        ms.claimedDay = TFJobsData.today();
        if (mission.repeat() == TFJobsConfig.Repeat.ALWAYS) {
            ms.progress = 0;
            ms.done = false;
            ms.claimedDay = -1;
        }
        giveExtras(player, job, jp.level, r);
        if (r.xp() > 0) addXp(player, job, jp, r.xp());
        data().changed();
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.5F);
        TFChat.send(player, "Oficios", ChatFormatting.GOLD, "Cobraste ", TFChat.hl(mission.name()), TFChat.SEP,
                TFChat.money(r.coins() > 0 ? "+" + rewardText(r) : rewardText(r)));
        return null;
    }

    public static String rewardText(Reward r) {
        StringBuilder out = new StringBuilder();
        if (r.coins() > 0) out.append(TFEconomy.format(Math.round(r.coins())));
        if (r.xp() > 0) out.append(out.isEmpty() ? "" : " · ").append(Math.round(r.xp())).append(" xp");
        for (String item : r.items()) out.append(out.isEmpty() ? "" : " · ").append(itemLabel(item));
        return out.isEmpty() ? "—" : out.toString();
    }

    /** «minecraft:diamond 3» → «3× Diamante» */
    public static String itemLabel(String spec) {
        ItemStack stack = parseItem(spec);
        return stack.isEmpty() ? spec : stack.getCount() + "× " + stack.getHoverName().getString();
    }

    private static void giveExtras(ServerPlayer player, Job job, int level, Reward r) {
        for (String spec : r.items()) {
            ItemStack stack = parseItem(spec);
            if (!stack.isEmpty()) TFCoinShop.give(player, stack);
            else TFClient.LOGGER.warn("TF Oficios: objeto de recompensa no válido «{}»", spec);
        }
        for (String command : r.commands()) runCommand(command, player, job, level);
    }

    /** «minecraft:diamond 3» o «minecraft:diamond_sword{Damage:0} 1» */
    public static ItemStack parseItem(String spec) {
        try {
            String s = spec.trim();
            int count = 1;
            int space = s.lastIndexOf(' ');
            if (space > 0 && s.substring(space + 1).matches("\\d+")) {
                count = Integer.parseInt(s.substring(space + 1));
                s = s.substring(0, space).trim();
            }
            ItemParser.ItemResult result = ItemParser.parseForItem(server.registryAccess().lookupOrThrow(Registries.ITEM),
                    new com.mojang.brigadier.StringReader(s));
            ItemStack stack = new ItemStack(result.item(), Math.max(1, count));
            if (result.nbt() != null) stack.setTag(result.nbt());
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static void runCommand(String command, ServerPlayer player, Job job, int level) {
        String cmd = command.replace("{player}", player.getGameProfile().getName()).replace("{uuid}", player.getStringUUID())
                .replace("{job}", job.id()).replace("{level}", Integer.toString(level));
        List<String> errors = TFBridge.run(server, cmd);
        if (!errors.isEmpty()) TFClient.LOGGER.warn("TF Oficios: «{}» falló: {}", cmd, errors.get(0));
    }

    // --- Pagos y avisos ---

    private static void payAll(boolean everything) {
        for (UUID uuid : List.copyOf(pending.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            String name = player != null ? player.getGameProfile().getName() : null;
            if (name == null) {
                var profile = server.getProfileCache() == null ? null : server.getProfileCache().get(uuid).orElse(null);
                name = profile != null ? profile.getName() : null;
            }
            if (name != null) pay(uuid, name, everything);
        }
    }

    private static void pay(UUID uuid, String name, boolean everything) {
        Double amount = pending.get(uuid);
        if (amount == null) return;
        long whole = (long) Math.floor(amount);
        if (whole > 0 && TFEconomy.give(server, uuid, name, whole)) amount -= whole;
        if (amount < 0.0001 || everything) pending.remove(uuid);
        else pending.put(uuid, amount);
    }

    /** Monedas ganadas que se pagarán en el próximo pago. */
    public static double pendingCoins(UUID uuid) {
        return pending.getOrDefault(uuid, 0.0);
    }

    static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.05 ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    // --- Unirse y dejar ---

    /** Devuelve el error o null si se unió. */
    public static String join(ServerPlayer player, Job job) {
        if (!TFJobsConfig.enabled) return "Los oficios están desactivados.";
        PlayerJobs p = data().player(player.getUUID());
        if (p.active.contains(job.id())) return "Ya trabajas de " + job.name() + ".";
        if (p.active.size() >= TFJobsConfig.maxJobs) {
            return TFJobsConfig.maxJobs == 1 ? "Ya tienes un oficio. Abandónalo primero (no perderás su progreso)."
                    : "Ya tienes " + TFJobsConfig.maxJobs + " oficios. Abandona uno primero.";
        }
        String wait = waitText(p);
        if (wait != null) return wait;
        p.active.add(job.id());
        p.lastJoin = System.currentTimeMillis();
        p.job(job.id());
        data().changed();
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.6F, 1.8F);
        TFChat.send(player, "Oficios", ChatFormatting.GOLD, "Ahora trabajas de ",
                Component.literal(job.name()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(job.color()))), ".");
        return null;
    }

    /** Cambia de oficio (deja los demás). El progreso de todos se queda guardado. */
    public static String switchTo(ServerPlayer player, Job job) {
        PlayerJobs p = data().player(player.getUUID());
        String wait = waitText(p);
        if (wait != null) return wait;
        for (String other : List.copyOf(p.active)) leave(player, other, false);
        return join(player, job);
    }

    public static void leave(ServerPlayer player, String jobId, boolean message) {
        PlayerJobs p = data().player(player.getUUID());
        if (!p.active.remove(jobId)) return;
        data().changed();
        pay(player.getUUID(), player.getGameProfile().getName(), true);
        Job job = TFJobsConfig.job(jobId);
        if (message) {
            TFChat.send(player, "Oficios", ChatFormatting.GOLD, "Dejaste ", TFChat.hl(job != null ? job.name() : jobId),
                    TFChat.SEP + "tu progreso queda guardado.");
        }
    }

    private static String waitText(PlayerJobs p) {
        long waitMs = TFJobsConfig.switchWaitMinutes * 60_000L - (System.currentTimeMillis() - p.lastJoin);
        if (p.lastJoin <= 0 || waitMs <= 0) return null;
        long minutes = Math.max(1, (waitMs + 59_999) / 60_000);
        return "Acabas de cambiar de oficio: espera " + minutes + (minutes == 1 ? " minuto." : " minutos.");
    }
}
