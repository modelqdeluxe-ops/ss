package net.tierrasfantasticas.tfclient.skills;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Las clases de skills en el servidor: qué clase tiene cada jugador (una sola; la compra en la web o se la da el
 * staff), las teclas que pulsa, los cooldowns, las pasivas que se lanzan solas y la barra que ve cada uno.
 *
 * <p>config/tfclient/skills.json (se crea solo): activado, si las skills dañan a jugadores (además del PvP del
 * servidor) y un multiplicador del daño. Las clases de los jugadores van en &lt;mundo&gt;/tfclient/skills.json.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class SkillServer {
    /** La clase, cuándo la puso la web y de qué clase se le dio ya el equipo. */
    private record Owned(String cls, long webAt, String given) {}

    private static MinecraftServer server;
    private static final Map<UUID, Owned> CLASSES = new HashMap<>();
    /** Hasta cuándo (tick) no se puede volver a lanzar cada skill: jugador → id de skill → tick. */
    private static final Map<UUID, Map<String, Long>> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Long> NO_FALL = new HashMap<>();
    /** Desde cuándo cuentan sus pasivas por tiempo (al entrar o al tener la clase saltan ya, y luego cada «timer»). */
    private static final Map<UUID, Long> PASSIVE_START = new HashMap<>();
    private static boolean dirty;

    private static boolean enabled = true;
    private static boolean pvp = true;
    private static double damageScale = 1.0;
    /** Lo mínimo entre dos usos de la misma skill (ticks), para que no se pueda aporrear la tecla. */
    private static final int MIN_COOLDOWN = 4;

    private SkillServer() {}

    static boolean pvp() {
        return pvp;
    }

    static double damageScale() {
        return damageScale;
    }

    static void noFall(UUID player, int ticks) {
        NO_FALL.merge(player, SkillRuntime.clock + ticks, Math::max);
    }

    // ------------------------------------------------------------------------------------------- arranque y datos

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        CLASSES.clear();
        COOLDOWN.clear();
        SkillRuntime.clear();
        loadConfig();
        load();
        SkillDefs.all();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (dirty) save();
        SkillRuntime.clear();
        SkillModels.clear();
        server = null;
    }

    private static Path configFile() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("skills.json", "tfclient-skills.json");
    }

    /** Vuelve a leer config/tfclient/skills.json (/tf reload). */
    public static void reloadConfig() {
        loadConfig();
    }

    private static void loadConfig() {
        JsonObject json = TFJson.read(configFile());
        if (json == null) json = new JsonObject();
        enabled = TFJson.bool(json, "activado", true);
        pvp = TFJson.bool(json, "skillsDanJugadores", true);
        damageScale = Math.max(0, TFJson.dec(json, "multiplicadorDano", 1.0));
        if (!json.has("activado")) {
            JsonObject out = new JsonObject();
            out.addProperty("activado", enabled);
            out.addProperty("skillsDanJugadores", pvp);
            out.addProperty("multiplicadorDano", damageScale);
            TFJson.write(configFile(), out);
        }
    }

    private static Path dataFile() {
        return TFJson.worldFile(server, "skills.json");
    }

    private static void load() {
        JsonObject json = TFJson.read(dataFile());
        if (json == null) return;
        JsonObject players = TFJson.obj(json, "jugadores");
        for (String key : players.keySet()) {
            try {
                JsonObject p = players.getAsJsonObject(key);
                String cls = TFJson.str(p, "clase", null);
                if (cls != null && SkillDefs.get(cls) == null) cls = null;
                CLASSES.put(UUID.fromString(key), new Owned(cls, TFJson.num(p, "web", 0), TFJson.str(p, "equipo", null)));
            } catch (Exception ignored) {
                // entrada rota
            }
        }
    }

    private static void save() {
        if (server == null) return;
        JsonObject players = new JsonObject();
        CLASSES.forEach((uuid, o) -> {
            JsonObject p = new JsonObject();
            if (o.cls() != null) p.addProperty("clase", o.cls());
            if (o.webAt() > 0) p.addProperty("web", o.webAt());
            if (o.given() != null) p.addProperty("equipo", o.given());
            players.add(uuid.toString(), p);
        });
        JsonObject json = new JsonObject();
        json.add("jugadores", players);
        TFJson.write(dataFile(), json);
        dirty = false;
    }

    /** La clase del jugador (null = ninguna). */
    public static String classOf(UUID uuid) {
        Owned o = CLASSES.get(uuid);
        return o == null ? null : o.cls();
    }

    /**
     * Lo que manda la web en cada consulta: [{uuid, clase, at}] de los conectados. at = cuándo se compró o cambió; si
     * el staff la cambió después aquí, gana lo del staff.
     */
    public static void applyWeb(MinecraftServer srv, JsonArray list) {
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            UUID uuid;
            try {
                uuid = UUID.fromString(TFJson.str(o, "uuid", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            long at = TFJson.num(o, "at", 0);
            Owned old = CLASSES.get(uuid);
            if (old != null && at <= old.webAt()) continue;
            String cls = TFJson.str(o, "clase", null);
            if (cls != null && SkillDefs.get(cls) == null) continue; // clase de una tanda que este mod aún no trae
            set(srv, uuid, cls, at, true);
        }
    }

    private static void set(MinecraftServer srv, UUID uuid, String cls, long webAt, boolean tell) {
        Owned old = CLASSES.get(uuid);
        CLASSES.put(uuid, new Owned(cls, Math.max(webAt, old == null ? 0 : old.webAt()), old == null ? null : old.given()));
        COOLDOWN.remove(uuid);
        dirty = true;
        save();
        ServerPlayer player = srv.getPlayerList().getPlayer(uuid);
        PASSIVE_START.remove(uuid);
        if (player == null) return;
        SkillRuntime.forget(uuid);
        SkillModels.forget(uuid);
        equipment(player);
        sync(player);
        if (tell && cls != null && (old == null || !cls.equals(old.cls()))) {
            SkillDefs.ClassDef def = SkillDefs.get(cls);
            player.sendSystemMessage(Component.literal("✦ Ya eres de la clase ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(def.name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(". Tus skills van en las teclas de la barra (se cambian en Controles).")
                            .withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Manda al jugador su barra: la clase y lo que le queda a cada skill. */
    public static void sync(ServerPlayer player) {
        String cls = classOf(player.getUUID());
        SkillDefs.ClassDef def = SkillDefs.get(cls);
        if (def == null || !enabled) {
            SkillNet.toPlayer(player, new SkillNet.State("", new int[0], new int[0]));
            return;
        }
        List<SkillDefs.SkillDef> actives = def.actives();
        int[] left = new int[actives.size()];
        int[] total = new int[actives.size()];
        Map<String, Long> cd = COOLDOWN.get(player.getUUID());
        for (int i = 0; i < actives.size(); i++) {
            total[i] = cooldownTicks(actives.get(i));
            Long until = cd == null ? null : cd.get(actives.get(i).id());
            left[i] = until == null ? 0 : (int) Math.max(0, until - SkillRuntime.clock);
        }
        SkillNet.toPlayer(player, new SkillNet.State(def.id, left, total));
    }

    private static int cooldownTicks(SkillDefs.SkillDef s) {
        return (int) Math.max(MIN_COOLDOWN, Math.round(s.cooldown() * 20));
    }

    // ------------------------------------------------------------------------------------------- lanzar

    /** El jugador pulsó la tecla de la skill «slot» de su barra. */
    static void castSlot(ServerPlayer player, int slot) {
        if (!enabled || server == null || !player.isAlive() || player.isSpectator()) return;
        SkillDefs.ClassDef def = SkillDefs.get(classOf(player.getUUID()));
        if (def == null) return;
        List<SkillDefs.SkillDef> actives = def.actives();
        if (slot < 0 || slot >= actives.size()) return;
        SkillDefs.SkillDef s = actives.get(slot);
        Map<String, Long> cd = COOLDOWN.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long until = cd.get(s.id());
        if (until != null && until > SkillRuntime.clock) return; // aún no (la barra ya lo enseña)
        if (s.entry() == null) {
            player.displayClientMessage(Component.literal("Esta skill aún no está lista.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean ok;
        try {
            ok = SkillRuntime.cast(player, def, s);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Skills: error al lanzar {} de {}", s.id(), def.id, t);
            ok = false;
        }
        if (ok) {
            cd.put(s.id(), SkillRuntime.clock + cooldownTicks(s));
            sync(player);
        }
    }

    // ------------------------------------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        try {
            SkillRuntime.tick(server);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Skills: error en el tick", t);
        }
        if (!enabled) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.phys.Vec3 now = player.position();
            net.minecraft.world.phys.Vec3 before = LAST_POS.put(player.getUUID(), now);
            if (before != null && (now.x - before.x) * (now.x - before.x) + (now.z - before.z) * (now.z - before.z) > 0.0009) {
                MOVED_AT.put(player.getUUID(), SkillRuntime.clock);
            }
        }
        // Pasivas por tiempo (TIMER): cada «timer» ticks (como MythicLib: el aura de 20 ticks del Clérigo o de Thor
        // con timer 10 está siempre puesta; el rastro de hielo de Glacia sale cada 5)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            SkillDefs.ClassDef def = SkillDefs.get(classOf(player.getUUID()));
            if (def == null) continue;
            for (SkillDefs.SkillDef s : def.skills) {
                if (s.passive() == null || !s.passive().type().equals("TIMER") || s.entry() == null) continue;
                int every = (int) Math.max(1, Math.round(s.passive().timer() > 0 ? s.passive().timer() : 20));
                long start = PASSIVE_START.computeIfAbsent(player.getUUID(), k -> SkillRuntime.clock + 20);
                if (SkillRuntime.clock < start || (SkillRuntime.clock - start) % every != 0) continue;
                passive(player, def, s);
            }
        }
        if (dirty && SkillRuntime.clock % 200 == 0) save();
        if (SkillRuntime.clock % 100 == 0) NO_FALL.values().removeIf(t -> t < SkillRuntime.clock);
    }

    private static void passive(ServerPlayer player, SkillDefs.ClassDef def, SkillDefs.SkillDef s) {
        Map<String, Long> cd = COOLDOWN.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long until = cd.get(s.id());
        if (until != null && until > SkillRuntime.clock) return;
        try {
            if (SkillRuntime.cast(player, def, s) && s.cooldown() > 0) cd.put(s.id(), SkillRuntime.clock + cooldownTicks(s));
        } catch (Throwable t) {
            TFClient.LOGGER.debug("TF Skills: error en la pasiva {}", s.id(), t);
        }
    }

    /**
     * Auras de evento antes del golpe: onDamaged de quien lo recibe (paradas, inmunidad al caer: cancelEvent y
     * damageMods) y onAttack de quien pega.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!enabled || event.getEntity().level().isClientSide()) return;
        net.minecraft.world.entity.LivingEntity victim = event.getEntity();
        net.minecraft.world.entity.Entity src = event.getSource().getEntity();
        try {
            boolean cancel = SkillAuras.damaged(victim, damageType(event.getSource()), src);
            if (src != null && !SkillRuntime.inDamage && SkillAuras.attacking(src, victim)) cancel = true;
            if (cancel) event.setCanceled(true);
        } catch (Throwable t) {
            TFClient.LOGGER.debug("TF Skills: error en un aura de evento", t);
        }
    }

    @SubscribeEvent
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!enabled || event.getEntity().level().isClientSide()) return;
        try {
            SkillAuras.died(event.getEntity());
        } catch (Throwable t) {
            TFClient.LOGGER.debug("TF Skills: error en un aura onDeath", t);
        }
    }

    /** El tipo de daño con el nombre de Bukkit que usan los packs (FALL, PROJECTILE, ENTITY_ATTACK...). */
    static String damageType(net.minecraft.world.damagesource.DamageSource src) {
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return "FALL";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return "PROJECTILE";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return src.getEntity() != null ? "ENTITY_EXPLOSION" : "BLOCK_EXPLOSION";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) return "LIGHTNING";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)) return "DROWNING";
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return "FIRE";
        return switch (src.getMsgId()) {
            case "magic", "indirectMagic" -> "MAGIC";
            case "sonic_boom" -> "SONIC_BOOM";
            case "wither", "witherSkull" -> "WITHER";
            case "thorns" -> "THORNS";
            case "inWall", "cramming" -> "SUFFOCATION";
            case "cactus", "sweetBerryBush", "stalagmite" -> "CONTACT";
            case "outOfWorld", "genericKill" -> "VOID";
            case "starve" -> "STARVATION";
            case "freeze" -> "FREEZE";
            case "mob", "player", "mobAttack", "playerAttack", "mobAttackNoAggro" -> "ENTITY_ATTACK";
            default -> "CUSTOM";
        };
    }

    // ¿Se está moviendo? (?isMoving): dónde estaba cada jugador hace un momento
    private static final Map<UUID, net.minecraft.world.phys.Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> MOVED_AT = new HashMap<>();

    static boolean moving(net.minecraft.world.entity.Entity e) {
        if (e instanceof ServerPlayer p) {
            Long at = MOVED_AT.get(p.getUUID());
            return at != null && SkillRuntime.clock - at <= 3;
        }
        net.minecraft.world.phys.Vec3 d = e.getDeltaMovement();
        return d.x * d.x + d.z * d.z > 1e-4;
    }

    /** Auras que cambian el daño (onDamaged / onAttack de MythicMobs: el traje del Dragón Rojo recibe la mitad). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurtScale(LivingHurtEvent event) {
        if (!enabled || event.getAmount() <= 0) return;
        double m = SkillAuras.multiplier(event.getEntity(), true);
        net.minecraft.world.entity.Entity src = event.getSource().getEntity();
        if (src != null) m *= SkillAuras.multiplier(src, false);
        if (m != 1.0) event.setAmount((float) Math.max(0, event.getAmount() * m));
    }

    /** Pasivas al recibir daño (DAMAGED). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!enabled || SkillRuntime.inDamage || !(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        SkillDefs.ClassDef def = SkillDefs.get(classOf(player.getUUID()));
        if (def == null) return;
        for (SkillDefs.SkillDef s : def.skills) {
            if (s.passive() != null && s.passive().type().equals("DAMAGED") && s.entry() != null) passive(player, def, s);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        Long until = NO_FALL.get(event.getEntity().getUUID());
        if (until != null && SkillRuntime.clock <= until) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            equipment(player);
            sync(player);
        }
    }

    /** Quita el equipo de otras clases y, si aún no se le dio el de la suya, se lo da (una vez por clase). */
    private static void equipment(ServerPlayer player) {
        Owned o = CLASSES.get(player.getUUID());
        String cls = o == null ? null : o.cls();
        try {
            SkillItems.purge(player, cls);
            if (cls != null && !cls.equals(o.given())) {
                SkillItems.give(player, SkillDefs.get(cls));
                CLASSES.put(player.getUUID(), new Owned(cls, o.webAt(), cls));
                dirty = true;
            }
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Skills: no se pudo dar el equipo de la clase", t);
        }
    }

    /** /tf web clases equipo: vuelve a dar el equipo de su clase (si lo perdió). */
    private static int regive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int n = 0;
        for (ServerPlayer p : targets) {
            String cls = classOf(p.getUUID());
            if (cls == null) continue;
            SkillItems.give(p, SkillDefs.get(cls));
            n++;
        }
        int done = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Equipo de clase entregado a " + done + " jugador(es)."), true);
        return n;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        SkillRuntime.forget(uuid);
        SkillModels.forget(uuid);
        NO_FALL.remove(uuid);
        LAST_POS.remove(uuid);
        MOVED_AT.remove(uuid);
        PASSIVE_START.remove(uuid);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillModels.forget(player.getUUID());
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillRuntime.forget(player.getUUID());
            SkillModels.forget(player.getUUID());
        }
    }

    // ------------------------------------------------------------------------------------------- comandos

    /**
     * /tf web clases dar &lt;jugadores&gt; &lt;clase|ninguna&gt;, /tf web clases lista, /tf web clases ver &lt;jugador&gt;,
     * /tf web clases equipo &lt;jugadores&gt; (vuelve a dar las armas y la armadura de su clase).
     */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("clases")
                .then(Commands.literal("lista").executes(SkillServer::list))
                .then(Commands.literal("dar")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("clase", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            List<String> ids = new ArrayList<>(SkillDefs.all().keySet());
                                            ids.add("ninguna");
                                            return SharedSuggestionProvider.suggest(ids, b);
                                        })
                                        .executes(SkillServer::give))))
                .then(Commands.literal("ver")
                        .then(Commands.argument("target", EntityArgument.player()).executes(SkillServer::show)))
                .then(Commands.literal("equipo")
                        .then(Commands.argument("targets", EntityArgument.players()).executes(SkillServer::regive)));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (SkillDefs.ClassDef c : SkillDefs.all().values()) {
            StringBuilder sb = new StringBuilder();
            for (SkillDefs.SkillDef s : c.skills) {
                if (s.hidden()) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.name()).append(s.passive() != null ? " (pasiva)" : "");
            }
            ctx.getSource().sendSuccess(() -> Component.literal(c.id + " — " + c.name + ": ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY)), false);
        }
        return SkillDefs.all().size();
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String id = StringArgumentType.getString(ctx, "clase");
        String cls = id.equals("ninguna") ? null : id;
        if (cls != null && SkillDefs.get(cls) == null) {
            ctx.getSource().sendFailure(Component.literal("No existe la clase " + id + " (mira /tf web clases lista)."));
            return 0;
        }
        for (ServerPlayer p : targets) set(ctx.getSource().getServer(), p.getUUID(), cls, 0, true);
        ctx.getSource().sendSuccess(() -> Component.literal("Clase " + (cls == null ? "quitada" : cls) + " para "
                + targets.size() + " jugador(es)."), true);
        return targets.size();
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(ctx, "target");
        String cls = classOf(p.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(p.getGameProfile().getName() + ": "
                + (cls == null ? "sin clase" : SkillDefs.get(cls).name)), false);
        return 1;
    }
}
