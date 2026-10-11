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
 * staff), la skill que tiene elegida (teclas 5-0) y cuándo la usa (clic izquierdo; las clases de arco, al soltar la
 * flecha), los cooldowns, las pasivas que se lanzan solas y la barra que ve cada uno. Las clases solo dan skills: ni
 * armas ni armadura (las que se dieron antes se quitan al entrar).
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
    /** Las clases que ha comprado cada uno (lo dice la web en cada consulta, de los conectados). */
    private static final Map<UUID, List<String>> OWNED = new HashMap<>();
    /** La skill elegida de cada uno (índice en la barra); se lanza con el siguiente clic o flecha. */
    private static final Map<UUID, Integer> SELECTED = new HashMap<>();
    /** Los que tienen puesto su ataque básico: después de usar otra skill vuelven a él. */
    private static final java.util.Set<UUID> BASIC_ON = new java.util.HashSet<>();
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

    /** Las clases que ha comprado en la web (vacío si la web aún no lo ha dicho). */
    public static List<String> ownedOf(UUID uuid) {
        return OWNED.getOrDefault(uuid, List.of());
    }

    /** ¿Ya dijo la web qué clases tiene? */
    public static boolean ownedKnown(UUID uuid) {
        return OWNED.containsKey(uuid);
    }

    /**
     * Desde el TF Pad: pone una de sus clases como activa (o ninguna, con null). Se aplica ya y se guarda en la web en
     * la siguiente consulta. false si no es suya.
     */
    public static boolean chooseFromPad(ServerPlayer player, String cls) {
        UUID uuid = player.getUUID();
        if (cls != null && (SkillDefs.get(cls) == null || !(ownedOf(uuid).contains(cls) || cls.equals(classOf(uuid))))) return false;
        set(player.getServer(), uuid, cls, System.currentTimeMillis(), true);
        net.tierrasfantasticas.tfclient.server.TFBridge.padSkill(uuid, cls);
        return true;
    }

    /** La clase del jugador (null = ninguna). */
    public static String classOf(UUID uuid) {
        Owned o = CLASSES.get(uuid);
        return o == null ? null : o.cls();
    }

    /**
     * Lo que manda la web en cada consulta: [{uuid, clase, at, owned}] de los conectados. at = cuándo se compró o
     * cambió la activa; si el staff la cambió después aquí, gana lo del staff. owned = las clases que ha comprado.
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
            if (o.has("owned") && o.get("owned").isJsonArray()) {
                List<String> owned = new ArrayList<>();
                for (JsonElement c : o.getAsJsonArray("owned")) {
                    if (c.isJsonPrimitive() && SkillDefs.get(c.getAsString()) != null) owned.add(c.getAsString());
                }
                List<String> old = OWNED.put(uuid, owned);
                ServerPlayer p = srv.getPlayerList().getPlayer(uuid);
                if (p != null && !owned.equals(old)) {
                    net.tierrasfantasticas.tfclient.pad.PadServer.refresh(p, "efectos",
                            net.tierrasfantasticas.tfclient.pad.PadServer.get(p, "efectos.tab", ""));
                }
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
        SELECTED.remove(uuid);
        BASIC_ON.remove(uuid);
        if (player == null) return;
        SkillRuntime.forget(uuid);
        SkillModels.forget(uuid);
        SkillItems.forget(uuid);
        equipment(player);
        sync(player);
        if (tell && cls != null && (old == null || !cls.equals(old.cls()))) {
            SkillDefs.ClassDef def = SkillDefs.get(cls);
            player.sendSystemMessage(Component.literal("✦ Ya eres de la clase ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(def.name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(def.bow()
                            ? ". Elige una skill con 5, 6, 7, 8, 9 o 0 y sale al disparar con tu arco."
                            : ". Elige una skill con 5, 6, 7, 8, 9 o 0 y úsala con el clic izquierdo.")
                            .withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Manda al jugador su barra: la clase y lo que le queda a cada skill. */
    public static void sync(ServerPlayer player) {
        String cls = classOf(player.getUUID());
        SkillDefs.ClassDef def = SkillDefs.get(cls);
        if (def == null || !enabled) {
            SkillNet.toPlayer(player, new SkillNet.State("", new int[0], new int[0], -1, false));
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
        Integer sel = SELECTED.get(player.getUUID());
        SkillNet.toPlayer(player, new SkillNet.State(def.id, left, total, sel == null ? -1 : sel, BASIC_ON.contains(player.getUUID())));
    }

    private static int cooldownTicks(SkillDefs.SkillDef s) {
        return (int) Math.max(MIN_COOLDOWN, Math.round(s.cooldown() * 20));
    }

    // ------------------------------------------------------------------------------------------- lanzar

    /**
     * Eligió la skill «slot» de su barra (teclas 5-0) o ninguna (-1): aún no se lanza. basic: tiene puesto su ataque
     * básico (se queda elegido; las demás skills se usan una vez y se vuelve a él).
     */
    static void select(ServerPlayer player, int slot, boolean basic) {
        SkillDefs.ClassDef def = SkillDefs.get(classOf(player.getUUID()));
        UUID uuid = player.getUUID();
        if (basic && def != null && def.basicSlot() >= 0) BASIC_ON.add(uuid);
        else BASIC_ON.remove(uuid);
        if (def == null || slot < 0 || slot >= def.actives().size()) {
            SELECTED.remove(uuid);
        } else {
            SELECTED.put(uuid, slot);
        }
        sync(player);
    }

    /**
     * Usa la skill elegida: con el clic izquierdo (bow = false) o al soltar una flecha (bow = true), según la clase.
     * Deja de estar elegida. false si no había ninguna elegida para ese gesto (entonces el clic es un golpe normal).
     */
    static boolean useSelected(ServerPlayer player, boolean bow) {
        SkillDefs.ClassDef def = SkillDefs.get(classOf(player.getUUID()));
        UUID uuid = player.getUUID();
        Integer slot = SELECTED.get(uuid);
        if (def == null || slot == null || def.bow() != bow) return false;
        // Aún no está lista (si es el ataque básico, el clic es un golpe normal y sigue elegido)
        Map<String, Long> cd = COOLDOWN.get(uuid);
        Long until = slot < def.actives().size() && cd != null ? cd.get(def.actives().get(slot).id()) : null;
        if (until != null && until > SkillRuntime.clock) {
            sync(player); // por si el cliente creía que ya estaba lista
            return false;
        }
        // Después: el ataque básico sigue elegido; cualquier otra se usa una vez y se vuelve al básico (si lo tiene puesto)
        int basic = def.basicSlot();
        if (slot != basic) {
            if (BASIC_ON.contains(uuid) && basic >= 0) SELECTED.put(uuid, basic);
            else SELECTED.remove(uuid);
        }
        castSlot(player, slot);
        sync(player);
        return true;
    }

    /** Lanza la skill «slot» de su barra. */
    private static void castSlot(ServerPlayer player, int slot) {
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
            boolean timer = "TIMER".equals(s.passive().type());
            if (SkillRuntime.cast(player, def, s, timer) && s.cooldown() > 0) cd.put(s.id(), SkillRuntime.clock + cooldownTicks(s));
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

    /** Clases de arco: la skill elegida sale al soltar una flecha (con munición y el arco algo tensado). */
    @SubscribeEvent
    public static void onArrowLoose(net.minecraftforge.event.entity.player.ArrowLooseEvent event) {
        if (!enabled || !(event.getEntity() instanceof ServerPlayer player) || !event.hasAmmo()) return;
        if (net.minecraft.world.item.BowItem.getPowerForTime(event.getCharge()) < 0.1F) return;
        useSelected(player, true);
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

    /** Las clases ya no dan objetos: se quitan las armas y armaduras de clase que se dieron antes. */
    private static void equipment(ServerPlayer player) {
        try {
            SkillItems.purge(player);
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Skills: no se pudieron quitar los objetos de clase", t);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        SkillRuntime.forget(uuid);
        SkillModels.forget(uuid);
        NO_FALL.remove(uuid);
        SELECTED.remove(uuid);
        BASIC_ON.remove(uuid);
        OWNED.remove(uuid);
        SkillFx.forget(uuid);
        SkillItems.forget(uuid);
        LAST_POS.remove(uuid);
        MOVED_AT.remove(uuid);
        PASSIVE_START.remove(uuid);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SkillModels.forget(player.getUUID());
            SkillItems.forget(player.getUUID());
            SELECTED.remove(player.getUUID());
            BASIC_ON.remove(player.getUUID());
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
     * /tf web clases dar &lt;jugadores&gt; &lt;clase|ninguna&gt;, /tf web clases lista, /tf web clases ver &lt;jugador&gt;.
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
                        .then(Commands.argument("target", EntityArgument.player()).executes(SkillServer::show)));
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
