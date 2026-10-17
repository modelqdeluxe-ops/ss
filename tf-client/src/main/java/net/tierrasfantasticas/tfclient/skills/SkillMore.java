package net.tierrasfantasticas.tfclient.skills;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Meta;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;

/**
 * Mecánicas de MythicMobs que faltaban (JEFES.md §11.3), con la semántica de su wiki: lo que se ve en la pantalla del
 * jugador (blackscreen, bloodyscreen, bossborder, skybox, sendtoast), efectos (enderbeam, hologram, blockunmask,
 * summonareaeffectcloud), lo que cambian de quien las recibe (consume, oxygen, shieldbreak, setgliding, setpose,
 * setmobcolor, setpathfindingmalus, swap, teleportin, resetai), mensajes (randommessage), cuentas (variablemath) y
 * el control (switch). Ninguna toca los bloques del mundo: lo que «cambia» un jugador (su borde, su cielo) es solo lo
 * que ve él, con los paquetes de Minecraft, y se le devuelve como estaba.
 */
final class SkillMore {
    private SkillMore() {}

    // ------------------------------------------------------------------------------------------- daño y vida

    /**
     * consume{damage=1;heal=1} (y lo de damage): daña a cada objetivo y quien lanza se cura «heal» por cada uno al que
     * ha hecho daño. En un jefe, la cura va en la escala de su vida (como heal).
     */
    static void consume(Mech m, Ctx ctx, List<Tgt> targets) {
        double heal = SkillRuntime.num(m.arg("1", "heal", "h"), 1);
        Mech d = m.copyWith(new HashMap<>(m.a), m.ta);
        d.a.put("amount", m.arg("1", "damage", "d", "dmg"));
        LivingEntity self = ctx.caster.entity instanceof LivingEntity l ? l : null;
        int hurt = 0;
        for (Tgt t : targets) {
            LivingEntity le = t.living();
            if (le == null || le == self) continue;
            float before = le.getHealth() + le.getAbsorptionAmount();
            boolean alive = le.isAlive();
            SkillRuntime.damage(d, ctx, List.of(t), false);
            if (alive && (!le.isAlive() || le.getHealth() + le.getAbsorptionAmount() < before)) hurt++;
        }
        if (hurt == 0 || self == null) return;
        SkillMobs.Host h = SkillMobs.host(self);
        self.heal((float) (heal * hurt * (h != null ? h.healthScale() : 1.0)));
    }

    /** oxygen{amount=1}: aire para el jugador (en ticks, como el de Minecraft; sin pasar de su máximo). */
    static void oxygen(Mech m, List<Tgt> targets) {
        int a = (int) SkillRuntime.num(m.arg("1", "amount", "a"), 1);
        for (Tgt t : targets) {
            if (t.entity() instanceof Player p) p.setAirSupply(Math.max(-20, Math.min(p.getMaxAirSupply(), p.getAirSupply() + a)));
        }
    }

    /** shieldbreak{duration=100}: si el jugador se cubre con un escudo, se le rompe la guardia y no puede usarlo en un rato. */
    static void shieldBreak(Mech m, List<Tgt> targets) {
        int ticks = (int) Math.max(0, SkillRuntime.num(m.arg("100", "duration", "d"), 100));
        for (Tgt t : targets) {
            if (!(t.entity() instanceof Player p) || !p.isBlocking()) continue;
            p.getCooldowns().addCooldown(p.getUseItem().getItem(), ticks);
            p.stopUsingItem();
            p.level().broadcastEntityEvent(p, (byte) 30); // el ruido y la animación del escudo roto
        }
    }

    // ------------------------------------------------------------------------------------------- lo que ve el jugador

    /** blackscreen{duration}: la pantalla del jugador a negro (un aura de MythicMobs que se repite cada 10 ticks). */
    static void blackScreen(Mech m, List<Tgt> targets) {
        int d = (int) Math.max(1, SkillRuntime.num(m.arg("200", "duration", "d", "ticks", "t"), 200));
        int ticks = (int) Math.ceil(d / 10.0) * 10;
        for (Tgt t : targets) if (t.entity() instanceof ServerPlayer p) SkillNet.toPlayer(p, new SkillNet.Screen("black", "", "", ticks));
    }

    /** bloodyscreen{duration=20;cancel}: el borde rojo de «cerca del borde del mundo» (con gráficos detallados). */
    static void bloodyScreen(Mech m, List<Tgt> targets) {
        int d = SkillRuntime.bool(m.arg("false", "cancel", "c")) ? 0 : (int) Math.max(1, SkillRuntime.num(m.arg("20", "duration", "d"), 20));
        for (Tgt t : targets) if (t.entity() instanceof ServerPlayer p) SkillNet.toPlayer(p, new SkillNet.Screen("blood", "", "", d));
    }

    /**
     * sendtoast{icon=diamond_sword;message;frame=challenge|task|goal}: el aviso de logro de Minecraft (solo a jugadores).
     * El icono: un material o un objeto del pack.
     */
    static void sendToast(Mech m, Ctx ctx, List<Tgt> targets) {
        String icon = SkillMeg.itemId(ctx, m.arg("diamond_sword", "icon", "i"));
        String msg = SkillRuntime.unquote(m.arg("", "message", "msg", "m"));
        if (msg.equalsIgnoreCase("NONE")) msg = "";
        String frame = m.arg("challenge", "frame", "f").toLowerCase(Locale.ROOT);
        int f = frame.equals("task") ? 0 : frame.equals("goal") ? 2 : 1;
        for (Tgt t : targets) if (t.entity() instanceof ServerPlayer p) SkillNet.toPlayer(p, new SkillNet.Screen("toast", icon, msg, f));
    }

    /**
     * skybox{skybox=0}: el cielo que ve el jugador. Mayor que 0: el de lluvia (el «nivel de lluvia» de Minecraft, que con
     * más de 1 oscurece el cielo aún más); 0 o menos: el de verdad. Solo lo ve él: hasta que cambie el tiempo del mundo.
     */
    static void skybox(Mech m, List<Tgt> targets) {
        double s = SkillRuntime.num(m.arg("0", "skybox", "sky", "s", "type", "t", "environment", "env", "e"), 0);
        for (Tgt t : targets) {
            if (!(t.entity() instanceof ServerPlayer p)) continue;
            ServerLevel level = p.serverLevel();
            if (s >= 1) {
                p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0F));
                p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, (float) s));
            } else {
                if (!level.isRaining()) p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0F));
                p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1F)));
                p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1F)));
            }
        }
    }

    /** Bordes de bossborder puestos: el jugador, quién se lo puso y en qué mundo. */
    private record Border(SkillRuntime.Who caster, ServerLevel level) {}

    private static final Map<UUID, Border> BORDERS = new HashMap<>();

    /**
     * bossborder{radius=32}: encierra al jugador en un borde del mundo de Minecraft alrededor de quien lanza. Es solo
     * suyo (su cliente no le deja salir; el borde de verdad no cambia): se le quita al morir o irse quien lo puso, o si
     * el jugador cambia de mundo.
     */
    static void bossBorder(Mech m, Ctx ctx, List<Tgt> targets) {
        double r = Math.max(1, SkillRuntime.num(m.arg("32", "radius", "r"), 32));
        Vec3 c = ctx.caster.pos();
        for (Tgt t : targets) {
            if (!(t.entity() instanceof ServerPlayer p)) continue;
            WorldBorder real = p.serverLevel().getWorldBorder();
            WorldBorder wb = new WorldBorder();
            wb.setCenter(c.x, c.z);
            wb.setSize(r * 2);
            wb.setWarningBlocks(real.getWarningBlocks());
            wb.setWarningTime(real.getWarningTime());
            wb.setDamagePerBlock(0);
            wb.setAbsoluteMaxSize(real.getAbsoluteMaxSize());
            p.connection.send(new ClientboundInitializeBorderPacket(wb));
            BORDERS.put(p.getUUID(), new Border(ctx.caster, p.serverLevel()));
        }
    }

    /** Cada segundo: los bordes de bossborder cuyo jefe ya no está vuelven a ser los de verdad. */
    static void tick(MinecraftServer server) {
        if (BORDERS.isEmpty() || server.getTickCount() % 20 != 0) return;
        Iterator<Map.Entry<UUID, Border>> it = BORDERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Border> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            Border b = e.getValue();
            SkillMobs.Host h = b.caster().entity == null ? null : SkillMobs.host(b.caster().entity);
            boolean gone = !b.caster().alive() || h != null && h.dying() || p.serverLevel() != b.level();
            if (!gone) continue;
            p.connection.send(new ClientboundInitializeBorderPacket(p.serverLevel().getWorldBorder()));
            it.remove();
        }
    }

    // ------------------------------------------------------------------------------------------- efectos

    /**
     * enderbeam{duration=60;yoffset=0}: un cristal del End en quien lanza (más yoffset) con su rayo hacia el objetivo,
     * «duration» ticks. Aquí solo se dibuja (el de MythicMobs es un cristal de verdad que se puede hacer explotar y
     * romper bloques: el servidor tiene construcciones).
     */
    static void enderBeam(Mech m, Ctx ctx, List<Tgt> targets) {
        int d = (int) Math.max(1, SkillRuntime.num(m.arg("60", "duration", "d"), 60));
        double y = SkillRuntime.num(m.arg("0", "yoffset", "y", "yo"), 0);
        Vec3 at = ctx.caster.pos().add(0, y, 0);
        for (Tgt t : targets) {
            SkillActor a = SkillRuntime.spawnModel(ctx, null, null, 1F, at, ctx.caster.yaw(), 0F);
            if (a == null) return;
            Entity e = t.entity();
            Vec3 p = t.pos();
            a.prop("beam", e != null ? "e:" + e.getId() : "p:" + p.x + "," + p.y + "," + p.z, null, null);
            SkillRuntime.later(d, a::remove);
        }
    }

    /** hologram{text;stay=100}: un texto flotando en el sitio, «stay» ticks. */
    static void hologram(Mech m, Ctx ctx, List<Tgt> targets) {
        String text = SkillRuntime.unquote(m.arg("Hologram Text Missing", "text", "t"));
        int stay = (int) Math.max(1, SkillRuntime.num(m.arg("100", "stay", "time", "staytime"), 100));
        for (Tgt t : targets) {
            SkillActor a = SkillRuntime.spawnModel(ctx, null, null, 1F, t.pos(), 0F, 0F);
            if (a == null) return;
            a.setText(text);
            SkillRuntime.later(stay, a::remove);
        }
    }

    /** blockunmask{radius;shape=SPHERE|CUBE}: deshace los blockmask (lo que se ve cambiado) alrededor de cada sitio. */
    static void blockUnmask(Mech m, Ctx ctx, List<Tgt> targets) {
        double r = Math.max(0, SkillRuntime.num(m.arg("0", "radius", "r"), 0));
        boolean cube = m.arg("SPHERE", "shape", "s").toUpperCase(Locale.ROOT).equals("CUBE");
        for (Tgt t : targets) {
            Vec3 p = t.pos();
            SkillNet.near(ctx.caster.level(), p, new SkillNet.Fx(7, "", p.x, p.y, p.z, 0, 0, 0, 0, (float) r, cube ? 1 : 0, null, 0, 0, 0, 0));
        }
    }

    /**
     * summonareaeffectcloud{particle=reddust;type=SLOW;potionduration=100;level=1;duration=200;radius=2;
     * durationreductiononuse;radiusreductiononuse;radiusreductionontick}: la nube de una poción persistente de Minecraft.
     */
    static void areaEffectCloud(Mech m, Ctx ctx, List<Tgt> targets) {
        MobEffect effect = SkillRuntime.potionType(m.arg("SLOW", "type", "effect", "t"));
        int pd = (int) SkillRuntime.num(m.arg("100", "potionduration", "pd"), 100);
        int lvl = (int) Math.max(1, SkillRuntime.num(m.arg("1", "level", "lvl", "l"), 1));
        int d = (int) Math.max(1, SkillRuntime.num(m.arg("200", "duration", "d", "cloudduration"), 200));
        float r = (float) SkillRuntime.num(m.arg("2", "radius", "r"), 2);
        int drou = (int) SkillRuntime.num(m.arg("0", "durationreudctiononuse", "durationreductiononuse", "drou"), 0);
        float rrou = (float) SkillRuntime.num(m.arg("0", "radiusreductiononuse", "rrou"), 0);
        float rrot = (float) SkillRuntime.num(m.arg("0", "radiusreductionontick", "rrot"), 0);
        ParticleOptions particle = particle(m.arg("reddust", "particle", "p"));
        for (Tgt t : targets) {
            Vec3 p = t.pos();
            AreaEffectCloud cloud = new AreaEffectCloud(ctx.caster.level(), p.x, p.y, p.z);
            if (ctx.caster.entity instanceof LivingEntity owner) cloud.setOwner(owner);
            cloud.setRadius(r);
            cloud.setDuration(d);
            cloud.setDurationOnUse(-drou);
            cloud.setRadiusOnUse(-rrou);
            cloud.setRadiusPerTick(-rrot);
            if (particle != null) cloud.setParticle(particle);
            if (effect != null) cloud.addEffect(new MobEffectInstance(effect, pd, lvl - 1));
            ctx.caster.level().addFreshEntity(cloud);
        }
    }

    /** Partícula del servidor por su nombre de Bukkit o de Minecraft (reddust = polvo rojo). */
    private static ParticleOptions particle(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (n.equals("reddust") || n.equals("redstone") || n.equals("dust")) return new DustParticleOptions(new org.joml.Vector3f(1F, 0F, 0F), 1F);
        n = switch (n) {
            case "smoke_normal" -> "smoke";
            case "smoke_large" -> "large_smoke";
            case "spell_witch" -> "witch";
            case "spell_mob" -> "entity_effect";
            case "spell" -> "effect";
            case "spell_instant" -> "instant_effect";
            case "flame" -> "flame";
            default -> n;
        };
        ResourceLocation rl = ResourceLocation.tryParse(n);
        var type = rl == null ? null : BuiltInRegistries.PARTICLE_TYPE.get(rl);
        return type instanceof SimpleParticleType s ? s : null;
    }

    // ------------------------------------------------------------------------------------------- cuerpo y sitio

    /** setgliding{gliding=true}: un jugador en el aire con élitros planea (o deja de planear). */
    static void setGliding(Mech m, List<Tgt> targets) {
        boolean g = SkillRuntime.bool(m.arg("true", "gliding", "g"));
        for (Tgt t : targets) {
            if (!(t.entity() instanceof ServerPlayer p)) continue;
            if (!g) {
                p.stopFallFlying();
            } else if (!p.onGround() && !p.isInWater() && p.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(p)) {
                p.startFallFlying();
            }
        }
    }

    /** setpose{pose}: la postura de Minecraft (STANDING, SLEEPING, SWIMMING, DYING, SITTING...). */
    static void setPose(Mech m, List<Tgt> targets) {
        Pose pose;
        try {
            pose = Pose.valueOf(m.arg("STANDING", "pose", "p").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        for (Tgt t : targets) if (t.entity() != null) t.entity().setPose(pose);
    }

    /** setmobcolor{color}: el color de los mobs que lo tienen (oveja, shulker; el collar de lobos y gatos). */
    static void setMobColor(Mech m, List<Tgt> targets) {
        DyeColor c = DyeColor.byName(m.arg("", "color", "c").toLowerCase(Locale.ROOT), null);
        if (c == null) return;
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e instanceof net.minecraft.world.entity.animal.Sheep s) s.setColor(c);
            else if (e instanceof net.minecraft.world.entity.monster.Shulker s) s.setVariant(java.util.Optional.of(c));
            else if (e instanceof net.minecraft.world.entity.animal.Wolf w) w.setCollarColor(c);
            else if (e instanceof net.minecraft.world.entity.animal.Cat cat) cat.setCollarColor(c);
        }
    }

    /** setpathfindingmalus{malus=OPEN;value=0}: lo que le cuesta a un mob pasar por ese tipo de sitio al buscar camino. */
    static void setPathfindingMalus(Mech m, List<Tgt> targets) {
        BlockPathTypes type;
        try {
            type = BlockPathTypes.valueOf(m.arg("OPEN", "malus", "type", "malustype").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        float v = (float) SkillRuntime.num(m.arg("0", "value", "amount"), 0);
        for (Tgt t : targets) if (t.entity() instanceof Mob mob) mob.setPathfindingMalus(type, v);
    }

    /** swap: quien lanza y su objetivo se cambian el sitio (con cada objetivo, uno detrás de otro). */
    static void swap(Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            if (t.who == null || t.who.same(ctx.caster)) continue;
            Vec3 a = ctx.caster.pos(), b = t.pos();
            place(ctx.caster, b, ctx.caster.yaw());
            place(t.who, a, t.who.yaw());
        }
    }

    /**
     * teleportin{vector=x,y,z;yaw=0;targetasorigin=false}: lleva al objetivo a ese punto según hacia dónde mira quien
     * lanza (x delante, y arriba, z a la derecha), desde quien lanza (o desde el propio objetivo); el objetivo queda
     * mirando hacia donde quien lanza más «yaw».
     */
    static void teleportIn(Mech m, Ctx ctx, List<Tgt> targets) {
        String[] v = m.arg("0,0,0", "vector", "direction", "dir", "d", "v").split(",");
        double fx = SkillRuntime.num(v[0], 0), up = v.length > 1 ? SkillRuntime.num(v[1], 0) : 0, right = v.length > 2 ? SkillRuntime.num(v[2], 0) : 0;
        float yawMod = (float) SkillRuntime.num(m.arg("0", "yaw", "y"), 0);
        boolean fromTarget = SkillRuntime.bool(m.arg("false", "targetasorigin", "tao"));
        float yaw = ctx.caster.yaw();
        Vec3 fwd = SkillRuntime.dir(yaw, 0F), side = SkillRuntime.dir(yaw + 90F, 0F);
        Vec3 off = fwd.scale(fx).add(0, up, 0).add(side.scale(right));
        for (Tgt t : targets) {
            if (t.who == null) continue;
            Vec3 base = fromTarget ? t.pos() : ctx.caster.pos();
            place(t.who, base.add(off), yaw + yawMod);
        }
    }

    private static void place(SkillRuntime.Who w, Vec3 at, float yaw) {
        if (w.actor != null) {
            w.actor.moveTo(at, yaw, w.actor.pitch);
        } else if (w.entity instanceof ServerPlayer p) {
            p.teleportTo(p.serverLevel(), at.x, at.y, at.z, yaw, p.getXRot());
        } else if (w.entity != null) {
            w.entity.teleportTo(at.x, at.y, at.z);
            w.entity.setYRot(yaw);
            w.entity.setYHeadRot(yaw);
        }
    }

    /** resetai: la IA de quien lanza vuelve a ser la de su tipo (un jefe vuelve a perseguir y mirar). */
    static void resetAi(Ctx ctx) {
        SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        if (h != null) h.resetAi();
        else if (ctx.caster.actor != null) ctx.caster.actor.ai = true;
    }

    // ------------------------------------------------------------------------------------------- mensajes y cuentas

    /** randommessage{messages="a","b","c"}: a cada jugador, uno de ellos al azar. */
    static void randomMessage(Mech m, List<Tgt> targets) {
        List<String> msgs = quotedList(m.arg("", "messages", "m", "message", "msg", "msgs"));
        if (msgs.isEmpty()) return;
        for (Tgt t : targets) {
            if (!(t.entity() instanceof ServerPlayer)) continue;
            Mech one = m.copyWith(new HashMap<>(m.a), m.ta);
            one.a.put("message", msgs.get(ThreadLocalRandom.current().nextInt(msgs.size())));
            SkillRuntime.message(one, List.of(t));
        }
    }

    /** «"uno","dos, con coma","tres"» → [uno, dos, con coma, tres] (sin comillas: separados por comas). */
    static List<String> quotedList(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quote = false;
        for (char ch : s.toCharArray()) {
            if (ch == '"') {
                quote = !quote;
            } else if (ch == ',' && !quote) {
                if (!cur.toString().isBlank()) out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (!cur.toString().isBlank()) out.add(cur.toString().trim());
        return out;
    }

    /**
     * variablemath{var;equation="x";...lo de setvariable}: la variable pasa a valer la cuenta, donde «x» es lo que vale
     * ahora (0 si no tiene). Con las operaciones y funciones de la librería de cuentas de MythicMobs (ver
     * {@link SkillVars#math}).
     */
    static void variableMath(Mech m, Ctx ctx, List<Tgt> targets) {
        String var = m.arg(null, "var", "variable", "name", "key", "k");
        if (var == null) return;
        String eq = SkillRuntime.unquote(m.arg("x", "equation", "eq", "e"));
        int duration = (int) SkillRuntime.num(m.arg("0", "duration", "d"), 0);
        String name = SkillRuntime.varName(var);
        List<Tgt> scopes = var.toLowerCase(Locale.ROOT).startsWith("target.") ? targets : java.util.Collections.singletonList(null);
        for (Tgt t : scopes) {
            Map<String, String> scope = SkillRuntime.varScope(var, ctx, t);
            double x = SkillRuntime.num(scope.get(name), 0);
            Double v = SkillVars.math(eq, x);
            if (v == null || v.isNaN() || v.isInfinite()) continue;
            SkillRuntime.putVar(scope, name, SkillRuntime.fmt(v), duration);
        }
    }

    /**
     * switch{condition=X{t=&lt;case&gt;};cases=case A=[...] case DEFAULT=[...];unique=true}: con cada objetivo, el primer
     * caso cuya condición (la del switch con &lt;case&gt; = su nombre; la compila tools/skills/compile.py como condición
     * de objetivo de la skill del caso) cumple; con unique=false, todos los que cumplan. DEFAULT, si no cumple ninguno.
     */
    static void switchCases(Mech m, Ctx ctx, List<Tgt> targets) {
        boolean unique = SkillRuntime.bool(m.arg("true", "unique", "first"));
        List<String[]> cases = new ArrayList<>();
        for (String c : m.arg("", "cases").split(",")) {
            int eq = c.indexOf('=');
            if (eq > 0) cases.add(new String[] {c.substring(0, eq).trim(), c.substring(eq + 1).trim()});
        }
        for (Tgt t : targets) {
            boolean any = false;
            Meta fallback = null;
            for (String[] c : cases) {
                Meta meta = ctx.cls.meta(c[1]);
                if (meta == null) continue;
                if (c[0].equalsIgnoreCase("DEFAULT")) {
                    fallback = meta;
                    continue;
                }
                if (!SkillTargets.passes(meta.targetConditions, t, ctx)) continue;
                any = true;
                run(meta, ctx, t);
                if (unique) break;
            }
            if (!any && fallback != null) run(fallback, ctx, t);
        }
    }

    // ------------------------------------------------------------------------------------------- 1.3.82

    /** teleportY{y}: quien lanza sube o baja a esa altura (y absoluta), en el mismo sitio. */
    static void teleportY(Mech m, Ctx ctx) {
        double y = SkillRuntime.num(m.arg("0", "y"), 0);
        Vec3 p = ctx.caster.pos();
        place(ctx.caster, new Vec3(p.x, y, p.z), ctx.caster.yaw());
    }

    /** matchrotation{target=@X} @objetivos: los objetivos miran igual que el primero de @X (su giro y su cabeceo). */
    static void matchRotation(Mech m, Ctx ctx, List<Tgt> targets) {
        String spec = m.arg(null, "target", "to", "t", "location", "loc", "l", "of");
        List<Tgt> ref = spec == null ? (ctx.trigger != null ? List.of(Tgt.of(ctx.trigger)) : List.of()) : SkillTargets.fromString(spec, ctx);
        if (ref.isEmpty() || ref.get(0).who == null) return;
        SkillRuntime.Who w = ref.get(0).who;
        float yaw = w.yaw(), pitch = w.pitch();
        for (Tgt t : targets) {
            if (t.who == null) continue;
            if (t.who.actor != null) {
                t.who.actor.moveTo(t.who.actor.pos, yaw, pitch);
                continue;
            }
            SkillMobs.Host h = t.who.entity == null ? null : SkillMobs.host(t.who.entity);
            if (h != null) {
                h.turn(yaw, false);
            } else if (t.who.entity != null) {
                t.who.entity.setYRot(yaw);
                t.who.entity.setXRot(pitch);
                t.who.entity.setYHeadRot(yaw);
            }
        }
    }

    /** tracklocation @sitio: guarda ese sitio como el de quien lanza (@TrackedLocation, distanceFromTrackedLocation). */
    static void trackLocation(Ctx ctx, List<Tgt> targets) {
        if (targets.isEmpty()) return;
        SkillRuntime.state(ctx.caster).tracked = targets.get(0).pos();
    }

    /**
     * setDisplayEntityItem{item}: el objeto que enseña un item_display del pack (el conversor lo pasa a su modelo de
     * ítem: «itemmodel»; sin él, nada).
     */
    static void setDisplayItem(Mech m, List<Tgt> targets) {
        String model = m.arg("", "itemmodel");
        String scale = m.arg("1", "scale");
        for (Tgt t : targets) {
            if (t.who != null && t.who.actor != null) t.who.actor.prop("item", model, scale, null);
        }
    }

    /**
     * speak{message;radius=12;offset=1;chatprefix=&lt;caster.name&gt;&amp;f: ;sendchatmessage=true;duration}: quien lanza
     * «habla»: el texto en un bocadillo encima de él (MESSAGE LENGTH × 4 ticks si no lo dice) y en el chat de quien esté
     * a «radius» bloques.
     */
    static void speak(Mech m, Ctx ctx, List<Tgt> targets) {
        String msg = SkillRuntime.unquote(m.arg("", "message", "m", "msg")).replace("\\n", " ");
        if (msg.isBlank()) return;
        double r = SkillRuntime.num(m.arg("12", "radius", "r"), 12);
        double off = SkillRuntime.num(m.arg("1", "offset", "o", "yo", "yoffset"), 1);
        String prefix = SkillVars.text(SkillRuntime.unquote(m.arg("<caster.name>&f<&co> ", "chatprefix", "cp")), ctx, Tgt.of(ctx.caster))
                .replace("<&co>", ":");
        String linePrefix = SkillRuntime.unquote(m.arg("&f", "lineprefix", "lp"));
        int d = (int) SkillRuntime.num(m.arg(String.valueOf(msg.length() * 4), "duration", "d", "ticks", "time", "t"), msg.length() * 4);
        boolean chat = SkillRuntime.bool(m.arg("true", "sendchatmessage", "chatmessage", "chat"));
        Vec3 at = ctx.caster.pos();
        double height = ctx.caster.entity != null ? ctx.caster.entity.getBbHeight() : 1.8;
        SkillActor a = SkillRuntime.spawnModel(ctx, null, null, 1F, at.add(0, height + off, 0), 0F, 0F);
        if (a != null) {
            a.setText(strip(linePrefix + msg));
            SkillRuntime.later(Math.max(1, d), a::remove);
        }
        if (!chat) return;
        net.minecraft.network.chat.Component line = net.minecraft.network.chat.Component.literal(colors(prefix + msg));
        for (ServerPlayer p : ctx.caster.level().players()) {
            if (p.position().distanceToSqr(at) <= r * r) p.sendSystemMessage(line);
        }
    }

    private static String strip(String s) {
        return s.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
    }

    /** Los colores «&amp;x» de los packs como los de Minecraft («§x»). */
    private static String colors(String s) {
        return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    /**
     * raytraceTo{entityskill;locationskill;headshotskill;syo;tyo;fo;uel;sfo;sso;md=50;rw=0.2;ie;hn;vn;rc} @sitio: un rayo
     * desde quien lanza (sus ojos con uel, el origen con fo, con sus offsets) hacia cada objetivo, que se para en el primer
     * bloque; si antes toca a un ser (a «rw» de la línea, que cumpla rc), entityskill con él (headshotskill si le da en la
     * cabeza); si no, locationskill donde acaba el rayo (wiki de MythicMobs, Premium).
     */
    static void rayTraceTo(Mech m, Ctx ctx, List<Tgt> targets) {
        String es = m.arg(null, "entityskill", "eskill"), ls = m.arg(null, "locationskill", "lskill"),
                hs = m.arg(null, "headshotskill", "hsskill");
        double syo = SkillRuntime.num(m.arg("0", "startyoffset", "syo", "ystartoffset", "ys"), 0);
        double tyo = SkillRuntime.num(m.arg("0", "targetyoffset", "tyo", "ytargetoffset", "yt"), 0);
        double sfo = SkillRuntime.num(m.arg("0", "forwardoffset", "startfoffset", "sfo"), 0);
        double sso = SkillRuntime.num(m.arg("0", "sideoffset", "soffset", "sso"), 0);
        double md = SkillRuntime.num(m.arg("50", "maxdistance", "distance", "md", "d"), 50);
        double rw = SkillRuntime.num(m.arg("0.2", "raywidth", "rw", "w"), 0.2);
        boolean ignoreEntities = SkillRuntime.bool(m.arg("false", "ignoreentities", "ignoreentity", "ie"));
        double hn = SkillRuntime.num(m.arg("0", "horizontalnoise", "hn"), 0), vn = SkillRuntime.num(m.arg("0", "verticalnoise", "vn"), 0);
        boolean fromOrigin = SkillRuntime.bool(m.arg("false", "fromorigin", "fo"));
        boolean eye = SkillRuntime.bool(m.arg("false", "useeyelocation", "uel"));
        ServerLevel level = ctx.caster.level();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Tgt t : targets) {
            Vec3 base = fromOrigin ? SkillTargets.origin(ctx) : eye ? ctx.caster.eye() : ctx.caster.pos();
            Vec3 end = t.pos().add(0, tyo, 0);
            if (hn > 0 || vn > 0) end = end.add(rnd.nextGaussian() * hn, rnd.nextGaussian() * vn, rnd.nextGaussian() * hn);
            Vec3 flat = end.subtract(base);
            float yaw = flat.lengthSqr() > 1e-6 ? SkillRuntime.yawOf(flat) : ctx.caster.yaw();
            Vec3 start = base.add(0, syo, 0).add(SkillRuntime.dir(yaw, 0F).scale(sfo)).add(SkillRuntime.dir(yaw + 90F, 0F).scale(sso));
            Vec3 d = end.subtract(start);
            if (d.lengthSqr() < 1e-6) continue;
            Vec3 stop = start.add(d.normalize().scale(Math.min(md, d.length())));
            var bh = level.clip(new net.minecraft.world.level.ClipContext(start, stop, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, ctx.caster.entity));
            if (bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) stop = bh.getLocation();
            LivingEntity hit = null;
            Vec3 hitAt = null;
            if (!ignoreEntities) {
                double best = Double.MAX_VALUE;
                var box = new net.minecraft.world.phys.AABB(start, stop).inflate(rw + 1);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (!e.isAlive() || e.isSpectator() || e == ctx.caster.entity || e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
                    var clip = e.getBoundingBox().inflate(rw).clip(start, stop);
                    if (clip.isEmpty()) continue;
                    if (!m.hc.isEmpty() && !SkillTargets.passes(m.hc, Tgt.of(SkillRuntime.Who.of(e)), ctx)) continue;
                    double dd = clip.get().distanceToSqr(start);
                    if (dd < best) {
                        best = dd;
                        hit = e;
                        hitAt = clip.get();
                    }
                }
            }
            Ctx c = ctx.copy();
            if (hit != null) {
                boolean head = hitAt.y >= hit.getEyeY() - 0.25;
                String skill = head && hs != null ? hs : es;
                c.origin = hitAt;
                if (skill != null) SkillRuntime.callNamed(skill, c, List.of(Tgt.of(SkillRuntime.Who.of(hit))));
            } else if (ls != null) {
                c.origin = stop;
                SkillRuntime.callNamed(ls, c, List.of(Tgt.at(stop)));
            }
        }
    }

    /** playblockstepsound: el ruido de pisar el bloque que hay en cada sitio (o bajo él). */
    static void blockStepSound(Mech m, Ctx ctx, List<Tgt> targets) {
        ServerLevel level = ctx.caster.level();
        for (Tgt t : targets) {
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(t.pos());
            var st = level.getBlockState(bp);
            if (st.isAir()) {
                bp = bp.below();
                st = level.getBlockState(bp);
            }
            if (st.isAir()) continue;
            var sound = st.getSoundType().getStepSound();
            level.playSound(null, bp, sound, net.minecraft.sounds.SoundSource.HOSTILE,
                    (float) SkillRuntime.num(m.arg("1", "volume", "v"), 1), (float) SkillRuntime.num(m.arg("1", "pitch", "p"), 1));
        }
    }

    /** Hasta cuándo dura el tiempo que ven los jugadores de cada mundo (weather) y su número de orden. */
    private static final Map<ServerLevel, Long> WEATHER_UNTIL = new HashMap<>();

    /**
     * weather{type=sunny|rainy|stormy;duration=500}: el tiempo del mundo de quien lanza, «duration» ticks. Aquí solo lo
     * ven sus jugadores (como skybox: paquetes del tiempo de Minecraft): el tiempo de verdad del servidor no cambia
     * (decisión del dueño: nada de lo que hagan los jefes cambia el mundo). Al acabar, vuelven a ver el de verdad.
     */
    static void weather(Mech m, Ctx ctx) {
        String type = m.arg("sunny", "type", "t").toLowerCase(Locale.ROOT);
        int d = (int) Math.max(1, SkillRuntime.num(m.arg("500", "duration", "d"), 500));
        boolean rain = type.startsWith("rain") || type.startsWith("storm") || type.startsWith("thunder");
        boolean thunder = type.startsWith("storm") || type.startsWith("thunder");
        ServerLevel level = ctx.caster.level();
        for (ServerPlayer p : level.players()) {
            p.connection.send(new ClientboundGameEventPacket(rain ? ClientboundGameEventPacket.START_RAINING
                    : ClientboundGameEventPacket.STOP_RAINING, 0F));
            p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain ? 1F : 0F));
            p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder ? 1F : 0F));
        }
        long until = level.getGameTime() + d;
        WEATHER_UNTIL.put(level, until);
        SkillRuntime.later(d, () -> {
            if (WEATHER_UNTIL.getOrDefault(level, 0L) != until) return; // otro weather la alargó
            WEATHER_UNTIL.remove(level);
            for (ServerPlayer p : level.players()) {
                p.connection.send(new ClientboundGameEventPacket(level.isRaining() ? ClientboundGameEventPacket.START_RAINING
                        : ClientboundGameEventPacket.STOP_RAINING, 0F));
                p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1F)));
                p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1F)));
            }
        });
    }

    /**
     * setmobscore{objective;value} (de quien lanza), settargetscore{objective;value} y modifytargetscore{objective;action;
     * value} (de los objetivos): la puntuación de un mob o efecto. Se guarda en memoria con el mob (la leen score y sus
     * skills igual que en MythicMobs; el marcador del servidor no se toca). La de un jugador no se escribe (pendiente del
     * dueño: el auditor lo marca).
     */
    static void score(Mech m, Ctx ctx, List<Tgt> targets, boolean self, boolean modify) {
        String obj = m.arg(null, "objective", "obj", "o");
        if (obj == null) return;
        double v = SkillRuntime.num(m.arg("0", "value", "val", "v"), 0);
        String action = m.arg("SET", "action", "a", "mode").toUpperCase(Locale.ROOT);
        for (Tgt t : self ? List.of(Tgt.of(ctx.caster)) : targets) {
            if (t.who == null || t.who.entity instanceof Player) continue;
            Map<String, Double> scores = SkillRuntime.state(t.who).scores;
            double now = scores.getOrDefault(obj, 0.0);
            scores.put(obj, !modify ? v : switch (action) {
                case "ADD" -> now + v;
                case "SUBTRACT", "SUB" -> now - v;
                case "MULTIPLY", "MULT" -> now * v;
                case "DIVIDE", "DIV" -> v == 0 ? now : now / v;
                default -> v;
            });
        }
    }

    /**
     * spin{velocity=18;duration=200} a un mob (efecto:spin): gira sobre sí mismo «velocity» grados por tick (un aura de
     * MythicMobs); con 0, se queda quieto mirando a donde miraba. Los efectos (actores) giran a su manera (SkillRuntime).
     */
    static void spinEntity(Mech m, Ctx ctx, Tgt t) {
        Entity e = t.entity();
        if (e == null) return;
        float v = (float) SkillRuntime.num(m.arg("18", "velocity", "v"), 18);
        int d = (int) Math.max(1, Math.min(6000, SkillRuntime.num(m.arg("200", "duration", "d", "ticks", "t"), 200)));
        SkillMobs.Host h = SkillMobs.host(e);
        float start = h != null ? h.lookYaw() : e.getYRot();
        int[] step = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!e.isAlive()) return;
            step[0]++;
            float yaw = v == 0 ? start : start + v * step[0];
            if (h != null) h.turn(yaw, false);
            else {
                e.setYRot(yaw);
                e.setYHeadRot(yaw);
            }
            if (step[0] < d) SkillRuntime.later(1, ctx.cast, tick[0]);
        };
        SkillRuntime.later(1, ctx.cast, tick[0]);
    }

    private static void run(Meta meta, Ctx ctx, Tgt t) {
        Ctx c = ctx.copy();
        c.targets = List.of(t);
        SkillRuntime.runMeta(meta, c);
    }

    /**
     * bindhitbox{mid;p;t} de ModelEngine: saca el mob «t» y lo ata a la caja ob_ «p» del modelo de quien lanza: va
     * siempre en esa caja y lo que golpee esa caja le hace daño a él, no al jefe (las tres cabezas de la Tormenta del
     * Wither, con su vida y su ~onDamaged). Si el modelo no tiene esa caja, como en ModelEngine, no hace nada.
     */
    static void bindHitbox(Mech m, Ctx ctx) {
        SkillMobs.Host h = ctx.caster.entity == null ? null : SkillMobs.host(ctx.caster.entity);
        if (h == null || SkillMobs.world == null) return;
        String part = m.arg(null, "partid", "p", "pid", "part");
        String type = m.arg("SKELETON", "type", "t", "mob");
        SkillDefs.MobDef def = ctx.cls.mob(type);
        Vec3 at = SkillMobs.isHitboxBone(h, part) ? SkillMobs.bonePos(h, part) : null;
        if (def == null || at == null) return;
        SkillMobs.Host b = SkillMobs.world.spawnMinion(h.boss(), def, ctx.caster.level(), at, ctx.caster.yaw());
        if (b != null) h.bindHitbox(part, b);
    }
}
