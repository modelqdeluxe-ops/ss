package net.tierrasfantasticas.tfclient.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * VFX en el servidor: qué lleva equipado cada jugador (lo manda la web por el puente o el staff con /tf web vfx),
 * el efecto de kill al matar y las skills pasivas de su paquete. Las skills se lanzan solas (al golpear, con un golpe
 * crítico, golpeando mientras corres o agachado, al recibir daño o cada cierto tiempo en combate) y cada una tiene su
 * cooldown. El servidor hace el daño y los empujes; el efecto visual lo dibuja cada jugador (VfxClient).
 *
 * <p>config/tfclient/vfx.json (se crea solo): activado, efectos de kill también con mobs, skills, skills en PvP y un
 * multiplicador del daño de las skills. Los datos de los jugadores van en &lt;mundo&gt;/tfclient/vfx.json.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VfxServer {
    /** Segundos sin pelear para salir de combate (las skills «en combate» dejan de lanzarse). */
    private static final int COMBAT_TICKS = 100;

    private record Equip(String kill, String pack, long webAt) {}

    private record Task(long at, Runnable run) {}

    private static MinecraftServer server;
    private static long clock;
    private static final Map<UUID, Equip> EQUIP = new HashMap<>();
    private static final Map<UUID, long[]> COOLDOWN = new HashMap<>();
    private static final Map<UUID, int[]> COMBO = new HashMap<>();
    private static final Map<UUID, Long> COMBAT = new HashMap<>();
    private static final Map<UUID, Long> NO_FALL = new HashMap<>();
    private static final Map<UUID, long[]> LAST_KILL_FX = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SKILL = new HashMap<>();
    /** Marcas activas (Runa del Viento...): hasta qué tick y qué pociones dan a los enemigos que golpeas. */
    private static final Map<UUID, List<Mark>> MARKS = new HashMap<>();

    private record Mark(String id, long until, JsonObject effects) {}
    private static final List<Task> TASKS = new ArrayList<>();
    private static boolean inSkill;
    private static boolean dirty;

    // Ajustes
    private static boolean enabled = true;
    private static boolean killsWithMobs = true;
    private static boolean skills = true;
    private static boolean skillsPvp = true;
    private static double damageScale = 1.0;

    private VfxServer() {}

    // ------------------------------------------------------------------------------------------- arranque y datos

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        clock = 0;
        TASKS.clear();
        EQUIP.clear();
        loadConfig();
        load();
        VfxCatalog.kills();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (dirty) save();
        server = null;
        TASKS.clear();
    }

    private static Path configFile() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("vfx.json", "tfclient-vfx.json");
    }

    private static void loadConfig() {
        JsonObject json = TFJson.read(configFile());
        if (json == null) json = new JsonObject();
        enabled = TFJson.bool(json, "activado", true);
        killsWithMobs = TFJson.bool(json, "efectosDeKillConMobs", true);
        skills = TFJson.bool(json, "skills", true);
        skillsPvp = TFJson.bool(json, "skillsEnPvP", true);
        damageScale = Math.max(0, TFJson.dec(json, "multiplicadorDanoSkills", 1.0));
        if (!json.has("activado")) {
            JsonObject out = new JsonObject();
            out.addProperty("activado", enabled);
            out.addProperty("efectosDeKillConMobs", killsWithMobs);
            out.addProperty("skills", skills);
            out.addProperty("skillsEnPvP", skillsPvp);
            out.addProperty("multiplicadorDanoSkills", damageScale);
            TFJson.write(configFile(), out);
        }
    }

    private static Path dataFile() {
        return TFJson.worldFile(server, "vfx.json");
    }

    private static void load() {
        JsonObject json = TFJson.read(dataFile());
        if (json == null) return;
        JsonObject players = TFJson.obj(json, "jugadores");
        for (String key : players.keySet()) {
            try {
                JsonObject p = players.getAsJsonObject(key);
                EQUIP.put(UUID.fromString(key), new Equip(valid(TFJson.str(p, "kill", null), true),
                        valid(TFJson.str(p, "pack", null), false), TFJson.num(p, "web", 0)));
            } catch (Exception ignored) {
                // entrada rota: se ignora
            }
        }
    }

    private static void save() {
        if (server == null) return;
        JsonObject players = new JsonObject();
        EQUIP.forEach((uuid, e) -> {
            JsonObject p = new JsonObject();
            if (e.kill() != null) p.addProperty("kill", e.kill());
            if (e.pack() != null) p.addProperty("pack", e.pack());
            if (e.webAt() > 0) p.addProperty("web", e.webAt());
            players.add(uuid.toString(), p);
        });
        JsonObject json = new JsonObject();
        json.add("jugadores", players);
        TFJson.write(dataFile(), json);
        dirty = false;
    }

    private static String valid(String id, boolean kill) {
        if (id == null || id.isBlank()) return null;
        return kill ? (VfxCatalog.kill(id) != null ? id : null) : (VfxCatalog.pack(id) != null ? id : null);
    }

    private static Equip equip(UUID uuid) {
        return EQUIP.getOrDefault(uuid, new Equip(null, null, 0));
    }

    /** Lo que manda la web en cada consulta: [{uuid, kill, pack, at}] de los jugadores conectados. */
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
            Equip old = equip(uuid);
            if (at <= old.webAt()) continue; // ya aplicado (o el staff lo cambió después)
            set(srv, uuid, valid(TFJson.str(o, "kill", null), true), valid(TFJson.str(o, "pack", null), false), at, true);
        }
    }

    private static void set(MinecraftServer srv, UUID uuid, String kill, String pack, long webAt, boolean tell) {
        Equip old = equip(uuid);
        EQUIP.put(uuid, new Equip(kill, pack, Math.max(webAt, old.webAt())));
        if (pack == null || !pack.equals(old.pack())) {
            COOLDOWN.remove(uuid);
            COMBO.remove(uuid);
        }
        dirty = true;
        save();
        ServerPlayer player = srv.getPlayerList().getPlayer(uuid);
        if (player == null) return;
        sync(player);
        if (!tell) return;
        if (kill != null && !kill.equals(old.kill())) {
            player.sendSystemMessage(Component.literal("✦ Efecto de kill equipado: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(VfxCatalog.kill(kill).name()).withStyle(ChatFormatting.WHITE)));
        }
        if (pack != null && !pack.equals(old.pack())) {
            player.sendSystemMessage(Component.literal("✦ Skills equipadas: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(VfxCatalog.pack(pack).name()).withStyle(ChatFormatting.WHITE)));
        }
    }

    /** Lo equipado: {efecto de kill, paquete de skills} (null = nada). */
    public static String[] equipped(UUID uuid) {
        Equip e = equip(uuid);
        return new String[] {e.kill(), e.pack()};
    }

    /** Equipa (o quita, con null) desde el pad. La web se entera en la siguiente consulta del puente. */
    public static void setFromPad(ServerPlayer player, boolean kill, String id) {
        Equip e = equip(player.getUUID());
        String v = valid(id, kill);
        set(player.getServer(), player.getUUID(), kill ? v : e.kill(), kill ? e.pack() : v, e.webAt(), false);
    }

    /** Manda al jugador su indicador: lo equipado y los cooldowns que le quedan. */
    public static void sync(ServerPlayer player) {
        Equip e = equip(player.getUUID());
        VfxCatalog.Pack pack = VfxCatalog.pack(e.pack());
        int n = pack == null ? 0 : pack.skills().size();
        int[] left = new int[n];
        int[] totals = new int[n];
        long[] until = COOLDOWN.get(player.getUUID());
        for (int i = 0; i < n; i++) {
            totals[i] = pack.skills().get(i).cooldown();
            if (until != null && i < until.length) left[i] = (int) Math.max(0, until[i] - clock);
        }
        int last = LAST_SKILL.getOrDefault(player.getUUID(), 0);
        if (n > 0 && last >= 0 && last < n) {
            // La última usada va primero: es la que enseña el indicador
            int[] l2 = left.clone(), t2 = totals.clone();
            l2[0] = left[last];
            t2[0] = totals[last];
            l2[last] = left[0];
            t2[last] = totals[0];
            left = l2;
            totals = t2;
        }
        VfxNet.send(player, new VfxNet.State(e.kill(), e.pack(), left, totals));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        COMBAT.remove(uuid);
        NO_FALL.remove(uuid);
        COMBO.remove(uuid);
        MARKS.remove(uuid);
    }

    // ------------------------------------------------------------------------------------------- efecto de kill

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!enabled || event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof ArmorStand) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer killer) || killer == victim) return;
        Equip e = equip(killer.getUUID());
        VfxCatalog.Kill kill = VfxCatalog.kill(e.kill());
        if (kill == null) return;
        boolean isPlayer = victim instanceof Player;
        if (!isPlayer && !killsWithMobs) return;
        // Con muchas muertes seguidas (barridos, granjas) no se amontonan: como mucho 4 cada 5 ticks por jugador
        long[] last = LAST_KILL_FX.computeIfAbsent(killer.getUUID(), k -> new long[]{-100, 0});
        if (clock - last[0] >= 5) {
            last[0] = clock;
            last[1] = 0;
        }
        if (++last[1] > 4) return;
        VfxNet.broadcast(level, new VfxNet.Play(kill.fx(), victim.getX(), victim.getY(), victim.getZ(), victim.yBodyRot, 0F,
                killer.getId(), victim.getId(), isPlayer ? victim.getUUID() : null, victim.getRandom().nextInt()));
    }

    // ------------------------------------------------------------------------------------------- disparadores

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (inSkill || !(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) return;
        if (!target.isAlive() || target instanceof ArmorStand || player.isSpectator()) return;
        COMBAT.put(player.getUUID(), clock);
        // Marcas activas (Runa del Viento): también tus golpes normales dan su efecto
        List<Mark> marks = MARKS.get(player.getUUID());
        if (marks != null) {
            marks.removeIf(m -> m.until() < clock);
            for (Mark m : marks) potions(target, m.effects());
        }
        // Solo golpes cargados (como los críticos de Minecraft): no se disparan aporreando el clic
        if (player.getAttackStrengthScale(0.5F) < 0.9F) return;
        boolean crit = player.fallDistance > 0F && !player.onGround() && !player.onClimbable() && !player.isInWater()
                && !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && !player.isSprinting();
        String trigger = player.isCrouching() ? "golpe_agachado" : player.isSprinting() ? "golpe_corriendo" : crit ? "golpe_critico" : "golpe";
        if (!fire(player, trigger, target, null, 0) && !trigger.equals("golpe")) fire(player, "golpe", target, null, 0);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingAttackEvent event) {
        if (inSkill || !(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == player) return;
        COMBAT.put(player.getUUID(), clock);
        LivingEntity target = attacker instanceof LivingEntity le ? le : null;
        fire(player, "dano", target, event::setCanceled, event.getAmount());
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        Long until = NO_FALL.get(event.getEntity().getUUID());
        if (until != null && clock <= until) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null) return;
        clock++;
        if (!TASKS.isEmpty()) {
            List<Task> due = new ArrayList<>();
            Iterator<Task> it = TASKS.iterator();
            while (it.hasNext()) {
                Task t = it.next();
                if (t.at() <= clock) {
                    due.add(t);
                    it.remove();
                }
            }
            for (Task t : due) {
                try {
                    t.run().run();
                } catch (Throwable e) {
                    TFClient.LOGGER.error("TF VFX: error en una skill", e);
                }
            }
        }
        // Skills «en combate»: cada segundo se mira quién sigue peleando
        if (clock % 20 == 0 && skills && enabled) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Long c = COMBAT.get(player.getUUID());
                if (c == null || clock - c > COMBAT_TICKS || !player.isAlive()) continue;
                fire(player, "combate", null, null, 0);
            }
        }
        if (dirty && clock % 200 == 0) save();
    }

    /**
     * Lanza la primera skill del paquete con ese disparador que esté lista. cancel: para «dano», cancela el golpe si
     * la skill esquiva. Devuelve si se lanzó alguna.
     */
    private static boolean fire(ServerPlayer player, String trigger, LivingEntity target, java.util.function.Consumer<Boolean> cancel,
                                float incoming) {
        if (!enabled || !skills || !player.isAlive()) return false;
        VfxCatalog.Pack pack = VfxCatalog.pack(equip(player.getUUID()).pack());
        if (pack == null) return false;
        long[] until = COOLDOWN.computeIfAbsent(player.getUUID(), k -> new long[pack.skills().size()]);
        if (until.length != pack.skills().size()) {
            until = new long[pack.skills().size()];
            COOLDOWN.put(player.getUUID(), until);
        }
        for (int i = 0; i < pack.skills().size(); i++) {
            VfxCatalog.Skill skill = pack.skills().get(i);
            if (!skill.trigger().equals(trigger) || until[i] > clock) continue;
            // «vida»: solo si con este golpe se queda por debajo de esa parte de la vida
            if (skill.health() < 1.0 && (player.getHealth() - incoming) > player.getMaxHealth() * skill.health()) continue;
            if (skill.chance() < 1.0 && player.getRandom().nextDouble() >= skill.chance()) continue;
            if (trigger.equals("combate") && skill.stages().stream().anyMatch(s -> needsTarget(s)) && nearestEnemy(player, 8) == null) continue;
            until[i] = clock + skill.cooldown();
            int[] combo = COMBO.computeIfAbsent(player.getUUID(), k -> new int[pack.skills().size() * 2]);
            int stageIndex = 0;
            if (skill.stages().size() > 1) {
                // Combo: si pasa mucho rato entre golpes vuelve a la primera fase
                if (combo.length >= pack.skills().size() * 2 && clock - combo[i * 2 + 1] > 200) combo[i * 2] = 0;
                stageIndex = combo[i * 2] % skill.stages().size();
                combo[i * 2] = stageIndex + 1;
                combo[i * 2 + 1] = (int) clock;
            }
            VfxCatalog.Stage stage = skill.stages().get(stageIndex);
            if (cancel != null && stage.actions().stream().anyMatch(a -> a.has("esquivar"))) cancel.accept(true);
            LAST_SKILL.put(player.getUUID(), i);
            cast(player, stage, target);
            sync(player);
            return true;
        }
        return false;
    }

    private static boolean needsTarget(VfxCatalog.Stage s) {
        return s.actions().stream().anyMatch(a -> a.has("area") || a.has("proyectil") || a.has("linea"));
    }

    // ------------------------------------------------------------------------------------------- skills

    /** Lanza una fase de skill: el efecto para todos y sus acciones en el servidor, cada una en su tick. */
    public static void cast(ServerPlayer player, VfxCatalog.Stage stage, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (stage.fx() != null) {
            boolean atTarget = "target".equals(stage.fxAt()) && target != null;
            Vec3 at = atTarget ? target.position() : player.position();
            VfxNet.broadcast(level, new VfxNet.Play(stage.fx(), at.x, at.y, at.z, yaw, pitch, player.getId(),
                    target == null ? -1 : target.getId(), player.getUUID(), player.getRandom().nextInt()));
        }
        for (JsonObject action : stage.actions()) {
            // Como mínimo un tick después: el golpe del jugador entra primero y el de la skill no lo anula
            int t = Math.max(1, (int) TFJson.num(action, "t", 0));
            Runnable run = () -> {
                if (player.isRemoved() || !player.isAlive() || player.level() != level) return;
                act(player, level, action, yaw, pitch, target);
            };
            TASKS.add(new Task(clock + t, run));
        }
    }

    private static Vec3 forward(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0, Math.cos(rad));
    }

    private static Vec3 point(Entity from, JsonObject o, float yaw) {
        Vec3 f = forward(yaw);
        Vec3 right = new Vec3(-f.z, 0, f.x); // a la derecha de quien mira (como sideOffset de MythicMobs)
        return from.position().add(f.scale(TFJson.dec(o, "fwd", 0))).add(right.scale(TFJson.dec(o, "side", 0)))
                .add(0, TFJson.dec(o, "up", 0), 0);
    }

    private static void act(ServerPlayer player, ServerLevel level, JsonObject a, float yaw, float pitch, LivingEntity target) {
        if (a.has("self")) {
            JsonObject s = a.getAsJsonObject("self");
            if (s.has("impulso")) {
                JsonArray imp = s.getAsJsonArray("impulso");
                Vec3 v = forward(yaw).scale(imp.get(0).getAsDouble()).add(0, imp.get(1).getAsDouble(), 0);
                player.setDeltaMovement(TFJson.bool(s, "sumar", false) ? player.getDeltaMovement().add(v) : v);
                player.hurtMarked = true;
                NO_FALL.put(player.getUUID(), clock + 60);
            }
            potions(player, s);
            if (s.has("curar")) player.heal((float) TFJson.dec(s, "curar", 0));
            if (s.has("marca")) {
                JsonObject m = s.getAsJsonObject("marca");
                List<Mark> marks = MARKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
                marks.removeIf(old -> old.until() < clock || old.id().equals(TFJson.str(m, "id", "")));
                marks.add(new Mark(TFJson.str(m, "id", ""), clock + TFJson.num(m, "ticks", 40), m));
            }
        }
        if (a.has("area")) {
            JsonObject area = a.getAsJsonObject("area");
            Vec3 c = point(player, area, yaw);
            hitAround(player, level, c, area, a, target);
        }
        if (a.has("linea")) {
            JsonObject line = a.getAsJsonObject("linea");
            Vec3 from = player.position().add(0, 1, 0);
            Vec3 dir = forward(yaw);
            double len = TFJson.dec(line, "len", 4), r = TFJson.dec(line, "r", 1.5);
            List<LivingEntity> hit = new ArrayList<>();
            for (double d = 0; d <= len; d += 0.5) {
                Vec3 c = from.add(dir.scale(d));
                for (LivingEntity e : candidates(player, level, c, r, r, target)) if (!hit.contains(e)) hit.add(e);
            }
            int limit = (int) TFJson.num(line, "limit", 8);
            for (int i = 0; i < Math.min(limit, hit.size()); i++) affect(player, level, hit.get(i), a, player.position());
        }
        if (a.has("proyectil")) projectile(player, level, a, yaw, pitch, target);
        if (a.has("zona")) zone(player, level, a, point(player, a.getAsJsonObject("zona"), yaw), target);
    }

    private static void hitAround(ServerPlayer player, ServerLevel level, Vec3 c, JsonObject area, JsonObject a, LivingEntity target) {
        double r = TFJson.dec(area, "r", 3), vr = TFJson.dec(area, "vr", 2);
        int limit = (int) TFJson.num(area, "limit", 8);
        List<LivingEntity> list = candidates(player, level, c, r, vr, target);
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
        for (int i = 0; i < Math.min(limit, list.size()); i++) affect(player, level, list.get(i), a, c);
    }

    /** Los que puede alcanzar una skill: monstruos, el que estás golpeando y jugadores si hay PvP; nunca tus mascotas. */
    private static List<LivingEntity> candidates(ServerPlayer player, ServerLevel level, Vec3 c, double r, double vr, LivingEntity target) {
        AABB box = new AABB(c.x - r, c.y - vr, c.z - r, c.x + r, c.y + vr + 1.8, c.z + r);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == player || !e.isAlive() || e instanceof ArmorStand || e.isSpectator()) continue;
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz > r * r) continue;
            if (e instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())) continue;
            if (e instanceof Player p) {
                if (!skillsPvp || !server.isPvpAllowed() || !player.canHarmPlayer(p) || p.isCreative()) continue;
            } else if (e != target && !(e instanceof Enemy) && !(e instanceof Mob m && m.getTarget() == player)) {
                continue;
            }
            if (e.isAlliedTo(player)) continue;
            out.add(e);
        }
        return out;
    }

    private static void affect(ServerPlayer player, ServerLevel level, LivingEntity e, JsonObject a, Vec3 center) {
        // El daño de la skill crece con el arma: base × (2 + ataque del jugador / 4). Con una espada de netherita (8)
        // es ×4; con las armas de los sets, más. Así se nota en survival aunque el enemigo lleve armadura.
        double attack = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double damage = TFJson.dec(a, "dano", 0) * damageScale * (2.0 + Math.max(0, attack) / 4.0);
        if (damage > 0) {
            e.invulnerableTime = 0;
            inSkill = true;
            try {
                e.hurt(player.damageSources().playerAttack(player), (float) damage);
            } finally {
                inSkill = false;
            }
        }
        Vec3 motion = e.getDeltaMovement();
        if (a.has("lanzar")) motion = new Vec3(motion.x, TFJson.dec(a, "lanzar", 0), motion.z);
        if (a.has("empuje")) {
            Vec3 away = new Vec3(e.getX() - center.x, 0, e.getZ() - center.z);
            if (away.lengthSqr() < 1e-4) away = forward(player.getYRot());
            motion = motion.add(away.normalize().scale(TFJson.dec(a, "empuje", 0)));
        }
        if (a.has("atraer")) {
            Vec3 to = new Vec3(player.getX() - e.getX(), 0, player.getZ() - e.getZ());
            if (to.lengthSqr() > 1e-4) motion = motion.add(to.normalize().scale(TFJson.dec(a, "atraer", 0)));
        }
        if (a.has("lanzar") || a.has("empuje") || a.has("atraer")) {
            e.setDeltaMovement(motion);
            e.hurtMarked = true;
        }
        if (a.has("aturdir") && (!a.has("probAturdir") || e.getRandom().nextDouble() < TFJson.dec(a, "probAturdir", 1))) {
            // Paralizado: no se mueve ni pega fuerte, con su efecto encima de la cabeza
            int ticks = (int) TFJson.num(a, "aturdir", 20);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false));
            String stunFx = TFJson.str(a, "impactoAturdir", null);
            if (stunFx != null) {
                VfxNet.broadcast(level, new VfxNet.Play(stunFx, e.getX(), e.getY(), e.getZ(), player.getYRot(), 0F, player.getId(),
                        e.getId(), null, e.getRandom().nextInt()));
            }
        }
        if (a.has("fuego")) e.setSecondsOnFire((int) TFJson.num(a, "fuego", 3));
        potions(e, a);
        List<Mark> marks = MARKS.get(player.getUUID());
        if (marks != null) for (Mark m : marks) if (m.until() >= clock) potions(e, m.effects());
        String fx = TFJson.str(a, "impacto", null);
        if (fx != null) {
            VfxNet.broadcast(level, new VfxNet.Play(fx, e.getX(), e.getY(), e.getZ(), player.getYRot(), 0F, player.getId(),
                    e.getId(), null, e.getRandom().nextInt()));
        }
    }

    private static void potions(LivingEntity e, JsonObject o) {
        if (!o.has("pociones")) return;
        for (JsonElement el : o.getAsJsonArray("pociones")) {
            JsonObject p = el.getAsJsonObject();
            ResourceLocation id = ResourceLocation.tryParse(TFJson.str(p, "id", ""));
            MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect == null) continue;
            e.addEffect(new MobEffectInstance(effect, (int) TFJson.num(p, "ticks", 40), (int) TFJson.num(p, "nivel", 0), false, false));
        }
    }

    /** Proyectil: avanza cada tick y golpea a lo primero que toque (o a todo lo que atraviese si atraviesa). */
    private static void projectile(ServerPlayer player, ServerLevel level, JsonObject a, float yaw, float pitch, LivingEntity target) {
        JsonObject p = a.getAsJsonObject("proyectil");
        double v = TFJson.dec(p, "v", 1.0), r = TFJson.dec(p, "r", 1.0);
        int ticks = (int) TFJson.num(p, "ticks", 20);
        boolean pierce = TFJson.bool(p, "atraviesa", false);
        boolean aim = TFJson.bool(p, "apuntar", false);
        double pr = Math.toRadians(aim ? pitch : 0), yr = Math.toRadians(yaw);
        final Vec3[] dir = {new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)).scale(v)};
        double grav = TFJson.dec(p, "grav", 0);
        Vec3 start = point(player, p, yaw);
        List<LivingEntity> done = new ArrayList<>();
        final Vec3[] pos = {start};
        // Cambios de velocidad por el camino (la estrella acelera y luego frena)
        Map<Integer, Double> speeds = new HashMap<>();
        for (JsonElement el : VfxCatalog.array(p, "vels")) {
            JsonObject o = el.getAsJsonObject();
            speeds.put((int) TFJson.num(o, "t", 0), TFJson.dec(o, "v", v));
        }
        for (int i = 1; i <= ticks; i++) {
            final int step = i;
            TASKS.add(new Task(clock + i, () -> {
                if (pos[0] == null) return;
                Double sp = speeds.get(step);
                if (sp != null && dir[0].lengthSqr() > 1e-9) dir[0] = dir[0].normalize().scale(sp);
                dir[0] = dir[0].add(0, -grav, 0);
                Vec3 next = pos[0].add(dir[0]);
                boolean blocked = !level.getBlockState(net.minecraft.core.BlockPos.containing(next)).getCollisionShape(level,
                        net.minecraft.core.BlockPos.containing(next)).isEmpty();
                for (LivingEntity e : candidates(player, level, next, r, r, target)) {
                    if (done.contains(e)) continue;
                    done.add(e);
                    affect(player, level, e, a, next);
                    if (!pierce) {
                        end(player, level, p, next, target);
                        pos[0] = null;
                        return;
                    }
                }
                if (blocked || step == ticks) {
                    end(player, level, p, next, target);
                    pos[0] = null;
                    return;
                }
                pos[0] = next;
            }));
        }
    }

    private static void end(ServerPlayer player, ServerLevel level, JsonObject p, Vec3 at, LivingEntity target) {
        if (!p.has("alFinal")) return;
        JsonObject fin = p.getAsJsonObject("alFinal");
        String fx = TFJson.str(fin, "fx", null);
        if (fx != null) {
            VfxNet.broadcast(level, new VfxNet.Play(fx, at.x, at.y, at.z, player.getYRot(), 0F, player.getId(), -1, null,
                    player.getRandom().nextInt()));
        }
        if (fin.has("zona")) zone(player, level, fin, at, target);
        if (fin.has("area")) hitAround(player, level, at, fin.getAsJsonObject("area"), fin, target);
    }

    /** Zona que dura: cada pocos ticks afecta a lo que tenga dentro (tornados, campos). */
    private static void zone(ServerPlayer player, ServerLevel level, JsonObject a, Vec3 c, LivingEntity target) {
        JsonObject z = a.getAsJsonObject("zona");
        int ticks = (int) TFJson.num(z, "ticks", 60), every = Math.max(1, (int) TFJson.num(z, "cada", 10));
        for (int i = every; i <= ticks; i += every) {
            TASKS.add(new Task(clock + i, () -> {
                if (player.isRemoved() || player.level() != level) return;
                hitAround(player, level, c, z, z, target);
            }));
        }
    }

    private static LivingEntity nearestEnemy(ServerPlayer player, double r) {
        List<LivingEntity> list = candidates(player, player.serverLevel(), player.position(), r, 3, null);
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        return list.isEmpty() ? null : list.get(0);
    }

    // ------------------------------------------------------------------------------------------- comando del staff

    private static final SuggestionProvider<CommandSourceStack> KILLS = (ctx, b) -> {
        List<String> ids = new ArrayList<>(VfxCatalog.kills().keySet());
        ids.add("ninguno");
        return SharedSuggestionProvider.suggest(ids, b);
    };
    private static final SuggestionProvider<CommandSourceStack> PACKS = (ctx, b) -> {
        List<String> ids = new ArrayList<>(VfxCatalog.packs().keySet());
        ids.add("ninguno");
        return SharedSuggestionProvider.suggest(ids, b);
    };

    /** /tf web vfx kill|skills &lt;jugadores&gt; &lt;id|ninguno&gt;, /tf web vfx probar &lt;id&gt;, /tf web vfx lista. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("vfx")
                .then(Commands.literal("lista").executes(VfxServer::list))
                .then(Commands.literal("kill")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(KILLS)
                                        .executes(ctx -> give(ctx, true)))))
                .then(Commands.literal("skills")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(PACKS)
                                        .executes(ctx -> give(ctx, false)))))
                .then(Commands.literal("probar")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    List<String> ids = new ArrayList<>(VfxCatalog.kills().keySet());
                                    VfxCatalog.packs().values().forEach(p -> p.skills().forEach(s -> ids.add(p.id() + "/" + s.id())));
                                    return SharedSuggestionProvider.suggest(ids, b);
                                })
                                .executes(VfxServer::test)));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("Efectos de kill (" + VfxCatalog.kills().size() + "): ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(String.join(", ", VfxCatalog.kills().keySet())).withStyle(ChatFormatting.GRAY)), false);
        for (VfxCatalog.Pack p : VfxCatalog.packs().values()) {
            StringBuilder sb = new StringBuilder();
            for (VfxCatalog.Skill s : p.skills()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.name()).append(" (").append(s.trigger()).append(", ").append(s.cooldown() / 20.0).append(" s)");
            }
            src.sendSuccess(() -> Component.literal("Skills " + p.id() + " — " + p.name() + ": ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(sb.toString()).withStyle(ChatFormatting.GRAY)), false);
        }
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, boolean kill) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String id = StringArgumentType.getString(ctx, "id");
        String value = id.equals("ninguno") ? null : valid(id, kill);
        if (!id.equals("ninguno") && value == null) {
            ctx.getSource().sendFailure(Component.literal("No existe " + (kill ? "el efecto de kill " : "el paquete de skills ") + id));
            return 0;
        }
        for (ServerPlayer p : targets) {
            Equip e = equip(p.getUUID());
            set(ctx.getSource().getServer(), p.getUUID(), kill ? value : e.kill(), kill ? e.pack() : value, 0, true);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("VFX actualizado para " + targets.size() + " jugador(es)."), true);
        return targets.size();
    }

    /** Lanza un efecto delante del staff para verlo (un efecto de kill o una skill concreta, paquete/skill). */
    private static int test(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "id");
        VfxCatalog.Kill kill = VfxCatalog.kill(id);
        if (kill != null) {
            Vec3 at = player.position().add(forward(player.getYRot()).scale(3));
            VfxNet.broadcast(player.serverLevel(), new VfxNet.Play(kill.fx(), at.x, at.y, at.z, player.getYRot() + 180F, 0F,
                    player.getId(), -1, player.getUUID(), player.getRandom().nextInt()));
            return 1;
        }
        String[] parts = id.split("/", 2);
        VfxCatalog.Pack pack = VfxCatalog.pack(parts[0]);
        if (pack != null && parts.length == 2) {
            for (VfxCatalog.Skill s : pack.skills()) {
                if (!s.id().equals(parts[1])) continue;
                int[] combo = COMBO.computeIfAbsent(player.getUUID(), k -> new int[64]);
                int idx = pack.skills().indexOf(s);
                int stage = combo.length > idx * 2 ? combo[idx * 2] % s.stages().size() : 0;
                if (combo.length > idx * 2) combo[idx * 2] = stage + 1;
                cast(player, s.stages().get(stage), nearestEnemy(player, 8));
                return 1;
            }
        }
        ctx.getSource().sendFailure(Component.literal("No existe el efecto " + id));
        return 0;
    }
}
