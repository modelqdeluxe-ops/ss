/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.skills;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Cond;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Who;

/** Las condiciones de MythicMobs que usan los packs. Las que no existen aquí se dan por cumplidas. */
final class SkillConds {
    private SkillConds() {}

    /** ¿Se cumple la condición para «who» (o el punto «at» si el objetivo es un sitio)? */
    static boolean test(Cond c, Who who, Ctx ctx, Vec3 at) {
        if (c.m().equals("or") || c.m().equals("and")) {
            // Compuesta: cada parte cumple si da lo que pide (true/false)
            boolean any = false, all = true;
            for (Cond p : c.parts()) {
                boolean ok = test(p, who, ctx, at) == !"false".equals(p.action());
                any |= ok;
                all &= ok;
            }
            return c.m().equals("or") ? any : all;
        }
        Map<String, String> a = c.a();
        // Variables en los argumentos (hasaura{aura=<caster.name>TARGET})
        for (String v : a.values()) {
            if (v.indexOf('<') >= 0) {
                Map<String, String> r = new java.util.HashMap<>(a.size());
                SkillRuntime.Tgt tg = who != null ? SkillRuntime.Tgt.of(who) : at != null ? SkillRuntime.Tgt.at(at) : null;
                a.forEach((k, x) -> r.put(k, x.indexOf('<') >= 0 ? SkillVars.text(x, ctx, tg) : x));
                a = r;
                break;
            }
        }
        Entity e = who != null ? who.entity : null;
        LivingEntity le = e instanceof LivingEntity l ? l : null;
        return switch (c.m()) {
            case "mythicmobtype", "mmt", "mobtype" -> {
                String types = arg(a, "", "types", "type", "t", "mobtypes", "m");
                SkillMobs.Host mh = e == null ? null : SkillMobs.host(e);
                String name = who != null && who.actor != null ? who.actor.name() : mh != null ? mh.def().name : "";
                boolean hit = false;
                for (String t : types.split(",")) if (t.trim().equalsIgnoreCase(name)) hit = true;
                yield hit;
            }
            case "owner", "isowner" -> who != null && ctx.caster.owner() != null && ctx.caster.owner().same(who)
                    || who != null && who.entity != null && who.entity == ctx.caster.player() && ctx.caster.actor != null;
            case "isparent", "parent" -> who != null && ctx.caster.owner() != null && ctx.caster.owner().same(who);
            case "hasaura", "hasbuff", "hasaurastacks", "aurastacks" -> {
                String name = arg(a, null, "auraname", "aura", "name", "n", "buffname", "b");
                if (who == null || name == null) yield false;
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(who);
                SkillAuras.Aura aura = st == null ? null : st.auras.get(name.toLowerCase(Locale.ROOT));
                int stacks = aura == null || !aura.alive ? 0 : aura.stacks;
                String range = arg(a, null, "stacks", "s", "amount", "a");
                yield range == null ? stacks > 0 : inRange(stacks, range);
            }
            case "crouching", "sneaking", "issneaking", "iscrouching" -> e != null && e.isCrouching();
            // isflying: el jugador vuela (Player.isFlying de Bukkit: el vuelo de creativo o el que le den, no el élitro)
            case "isflying", "flying" -> e instanceof Player p && p.getAbilities().flying;
            case "sprinting", "issprinting" -> e != null && e.isSprinting();
            case "onground", "grounded" -> e != null ? e.onGround() : who == null || who.actor == null || !who.actor.physics || who.actor.onGround;
            case "inblock", "blocktype", "inside", "standingon" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                BlockPos pos = BlockPos.containing(p);
                if (c.m().equals("standingon")) pos = pos.below();
                String types = arg(a, "", "types", "type", "t", "material", "m").toLowerCase(Locale.ROOT);
                String id = BuiltInRegistries.BLOCK.getKey(ctx.caster.level().getBlockState(pos).getBlock()).getPath();
                boolean hit = false;
                for (String t : types.split(",")) {
                    String tt = t.trim().replace("minecraft:", "");
                    if (tt.equals(id)) hit = true;
                }
                yield hit;
            }
            case "variableisset", "varisset", "varset" -> {
                String var = arg(a, null, "var", "variable", "name", "key", "k");
                yield var != null && SkillRuntime.varScope(var, ctx, who == null ? null : SkillRuntime.Tgt.of(who))
                        .containsKey(SkillRuntime.varName(var));
            }
            case "variableequals", "varequals", "variableeq", "vareq" -> {
                String var = arg(a, null, "var", "variable", "name", "key", "k");
                if (var == null) yield false;
                String v = SkillRuntime.varScope(var, ctx, who == null ? null : SkillRuntime.Tgt.of(who)).get(SkillRuntime.varName(var));
                String want = SkillRuntime.unquote(arg(a, "", "value", "val", "v"));
                if (v == null) v = "UNDEFINED"; // como MythicMobs: una variable sin poner vale «UNDEFINED»
                yield v.equalsIgnoreCase(want) || numEq(v, want);
            }
            case "variableinrange", "varinrange", "varrange" -> {
                String var = arg(a, null, "var", "variable", "name", "key", "k");
                if (var == null) yield false;
                String v = SkillRuntime.varScope(var, ctx, who == null ? null : SkillRuntime.Tgt.of(who)).get(SkillRuntime.varName(var));
                yield v != null && inRange(SkillRuntime.num(v, 0), arg(a, ">0", "value", "val", "v", "range"));
            }
            case "itemissimilar", "holding" -> e instanceof net.minecraft.world.entity.player.Player p
                    && SkillItems.holds(p, ctx.cls, arg(a, "", "i", "item", "material", "m"));
            case "offgcd", "gcd" -> {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(who != null ? who : ctx.caster);
                yield st == null || st.gcdUntil <= SkillRuntime.clock;
            }
            case "stance" -> {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(who != null ? who : ctx.caster);
                String want = arg(a, "", "stance", "s");
                yield st != null && st.stance != null && st.stance.equalsIgnoreCase(want);
            }
            case "faction" -> {
                SkillRuntime.WhoState st = who == null ? null : SkillRuntime.stateIfAny(who);
                String want = arg(a, "", "faction", "f");
                yield st != null && st.faction != null && st.faction.equalsIgnoreCase(want);
            }
            case "hastag", "tag" -> {
                String tag = arg(a, "", "tag", "t").toLowerCase(Locale.ROOT);
                SkillRuntime.WhoState st = who == null ? null : SkillRuntime.stateIfAny(who);
                yield st != null && st.tags.contains(tag) || e != null && e.getTags().contains(arg(a, "", "tag", "t"));
            }
            case "hastarget" -> who != null && who.actor != null ? who.actor.target != null : ctx.aim != null;
            case "mmocantarget", "cantarget" -> e instanceof LivingEntity l2 && SkillRuntime.canHurt(ctx, l2);
            case "iscaster" -> who != null && who.same(ctx.caster);
            // isChild: el objetivo es algo invocado (tiene dueño). Sirve igual desde el jugador (sus lanzas del Dragón) que
            // desde su esbirro con sudoskill (el guardián del Invocador comprobándose a sí mismo)
            case "ischild", "children", "child" -> who != null && who.actor != null && who.actor.owner != null;
            case "modelhasdriver" -> {
                Who w = who != null ? who : ctx.caster;
                yield w.actor != null && w.actor.rider != null;
            }
            case "drivingmodel", "isdriving" -> {
                Who w = who != null ? who : ctx.caster;
                yield w.entity != null && SkillModels.mountedBy(w.entity) != null;
            }
            case "ownerisonline" -> ctx.caster.player() != null && !ctx.caster.player().hasDisconnected();
            case "ismoving", "moving" -> e != null && SkillServer.moving(e);
            case "pitch" -> inRange(who != null ? who.pitch() : ctx.caster.pitch(), arg(a, ">0", "pitch", "p", "range"));
            case "onblock" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                BlockPos pos = BlockPos.containing(p.x, p.y - 0.2, p.z);
                String id = BuiltInRegistries.BLOCK.getKey(ctx.caster.level().getBlockState(pos).getBlock()).getPath();
                boolean hit = false;
                for (String t : arg(a, "", "material", "m", "types", "t", "b").toLowerCase(Locale.ROOT).split(",")) {
                    String tt = t.trim().replace("minecraft:", "");
                    if (tt.equals(id) || tt.equals("air") && (id.equals("cave_air") || id.equals("void_air"))) hit = true;
                }
                yield hit;
            }
            case "hasitem" -> {
                if (!(e instanceof Player p)) yield false;
                String item = arg(a, "", "item", "i", "material", "m").toLowerCase(Locale.ROOT).replace("minecraft:", "");
                int n = 0;
                for (net.minecraft.world.item.ItemStack st : p.getInventory().items) {
                    if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals(item)) n += st.getCount();
                }
                yield inRange(n, arg(a, ">0", "amount", "a"));
            }
            case "mobsinradius", "mir", "entitiesinradius", "eir", "playersinradius", "pir" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                double r = SkillRuntime.num(arg(a, "5", "radius", "r"), 5);
                String types = arg(a, null, "types", "type", "t", "mobtypes");
                boolean players = c.m().startsWith("players") || c.m().equals("pir");
                // mobsInRadius de MythicMobs solo cuenta los mobs de «types»: sin tipos no cuenta ninguno (el remolino
                // del Arquero pide «mobsinradius{r=5;a=<1}» y debe salir aunque haya enemigos)
                boolean mobs = c.m().equals("mobsinradius") || c.m().equals("mir");
                int n = 0;
                if (mobs && types == null) {
                    n = 0;
                } else if (types != null && !players) {
                    for (SkillActor ac : SkillRuntime.ACTORS) {
                        if (!ac.alive || ac.level != ctx.caster.level() || ac.pos.distanceToSqr(p) > r * r) continue;
                        for (String ty : types.split(",")) if (ty.trim().equalsIgnoreCase(ac.name())) n++;
                    }
                    // Los mobs de verdad de un jefe (los retoños de Astwood)
                    for (LivingEntity le2 : ctx.caster.level().getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(p, p).inflate(r))) {
                        SkillMobs.Host mh = SkillMobs.host(le2);
                        if (mh == null || !le2.isAlive() || le2.position().distanceToSqr(p) > r * r) continue;
                        for (String ty : types.split(",")) if (ty.trim().equalsIgnoreCase(mh.def().name)) n++;
                    }
                } else {
                    for (LivingEntity le2 : ctx.caster.level().getEntitiesOfClass(LivingEntity.class,
                            new net.minecraft.world.phys.AABB(p, p).inflate(r))) {
                        if (!le2.isAlive() || le2 instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
                        if (le2.position().distanceToSqr(p) > r * r) continue;
                        if (players != le2 instanceof Player) continue;
                        n++;
                    }
                }
                yield inRange(n, arg(a, ">0", "amount", "a"));
            }
            case "incombat" -> {
                // Un mob de jefe: si tiene a quién atacar; lo demás (jugadores, efectos), siempre
                SkillMobs.Host mh = SkillMobs.host(e != null ? e : ctx.caster.entity);
                yield mh == null || mh.target() != null && mh.target().isAlive();
            }
            case "playerwithin", "playernotwithin", "playerswithin" -> {
                // playerWithin{d}: algún jugador (que no sea espectador) a d bloques de quien lanza; como en MythicMobs,
                // también en creativo (la Gárgola y el Esqueleto despiertan aunque los pruebe el staff).
                // playerNotWithin{d}: ninguno
                double d = SkillRuntime.num(arg(a, "5", "distance", "d"), 5);
                Vec3 from = who != null ? who.pos() : ctx.caster.pos();
                boolean any = false;
                for (Player p : ctx.caster.level().players()) {
                    if (!p.isSpectator() && p.position().distanceToSqr(from) <= d * d) {
                        any = true;
                        break;
                    }
                }
                yield any != c.m().equals("playernotwithin");
            }
            case "fieldofview", "fov", "infieldofview" -> {
                // fieldOfView{angle;rotation}: el objetivo está dentro del ángulo, centrado en el giro de quien lanza
                // menos «rotation» (r=90: a su izquierda, r=270: a su derecha, r=180: detrás)
                double angle = SkillRuntime.num(arg(a, "90", "angle", "a"), 90);
                double rot = SkillRuntime.num(arg(a, "0", "rotation", "r"), 0);
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                Vec3 to = p.subtract(ctx.caster.pos());
                if (to.x * to.x + to.z * to.z < 1e-6) yield true;
                double want = SkillRuntime.yawOf(to);
                double center = ctx.caster.yaw() - rot;
                double diff = net.minecraft.util.Mth.wrapDegrees(want - center);
                yield Math.abs(diff) <= angle / 2;
            }
            case "boundingboxesoverlap", "bbo" -> {
                Entity self = ctx.caster.entity;
                yield self != null && e != null && e != self && self.getBoundingBox().intersects(e.getBoundingBox());
            }
            case "damagecause", "damagetype" -> {
                String want = arg(a, "", "cause", "c", "type", "t").toUpperCase(Locale.ROOT);
                String now = ctx.damageCause == null ? "" : ctx.damageCause;
                boolean hit = false;
                for (String w : want.split(",")) if (w.trim().equals(now)) hit = true;
                yield hit;
            }
            case "directionalvelocity", "velocity" -> {
                Vec3 v = e != null ? e.getDeltaMovement() : who != null && who.actor != null ? who.actor.vel : Vec3.ZERO;
                boolean ok = true;
                if (a.containsKey("x")) ok &= inRange(v.x, a.get("x"));
                if (a.containsKey("y")) ok &= inRange(v.y, a.get("y"));
                if (a.containsKey("z")) ok &= inRange(v.z, a.get("z"));
                yield ok;
            }
            case "ydiff", "ydifference" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                yield inRange(p.y - ctx.caster.pos().y, arg(a, "0", "diff", "d", "y"));
            }
            case "modelhaspassengers", "hasmodelpassengers", "modelhaspassenger" -> {
                SkillMobs.Host mh = SkillMobs.host(e != null ? e : ctx.caster.entity);
                yield mh != null && SkillMobs.world != null && !SkillMobs.world.seated(mh, arg(a, null, "p", "pbone", "seat", "part")).isEmpty();
            }
            case "blocking", "isblocking" -> le != null && le.isBlocking();
            // wearing{armorslot=HEAD;material=DIRT}: lo que lleva en ese hueco (un material de Minecraft o un objeto del pack)
            case "wearing", "iswearing", "wielding", "iswielding" -> {
                if (le == null) yield false;
                net.minecraft.world.item.ItemStack st = le.getItemBySlot(slot(arg(a, "HEAD", "armorslot", "slot", "s")));
                if (st.isEmpty()) yield false;
                String have = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                boolean hit = false;
                for (String m : arg(a, "DIRT", "material", "mat", "m", "item", "i", "t", "type", "types").split(",")) {
                    if (SkillMeg.itemId(ctx, m.trim()).equals(have)) hit = true;
                }
                yield hit;
            }
            // haspermission{permission}: el permiso de Bukkit (Mohist); sin Bukkit, los op
            case "haspermission" -> e instanceof net.minecraft.server.level.ServerPlayer sp
                    && SkillServer.hasPermission(sp, arg(a, "", "permission", "perm", "p"));
            // score{objective;entry;value}: su puntuación en el marcador del servidor (la entrada: la suya si no la dice);
            // globalscore{objective;value}: la de la entrada global de MythicMobs («__GLOBAL__»). Sin puntuación, 0
            case "score" -> {
                String obj = arg(a, null, "objective", "obj", "o");
                String entry = arg(a, null, "entry", "ent", "e");
                if (obj == null) yield false;
                // La de un mob o efecto (setmobscore, settargetscore): en memoria, con él
                if (entry == null && who != null && !(e instanceof Player)) {
                    SkillRuntime.WhoState st = SkillRuntime.stateIfAny(who);
                    yield inRange(st == null ? 0 : st.scores.getOrDefault(obj, 0.0), arg(a, "0", "value", "val", "v"));
                }
                if (entry == null && e != null) entry = e.getScoreboardName();
                yield entry != null && inRange(score(ctx, obj, entry), arg(a, "0", "value", "val", "v"));
            }
            case "globalscore", "scoreglobal" -> {
                String obj = arg(a, null, "objective", "obj", "o");
                yield obj != null && inRange(score(ctx, obj, "__GLOBAL__"), arg(a, "0", "value", "val", "v"));
            }
            // itemrecharging: el arma que lleva en la mano está recargándose (su enfriamiento de objeto: el escudo tras un
            // hacha, la perla...)
            case "itemrecharging" -> e instanceof Player p && !p.getMainHandItem().isEmpty()
                    && p.getCooldowns().isOnCooldown(p.getMainHandItem().getItem());
            // mobsinchunk{amount}: los mobs (seres vivos que no son jugadores, y los efectos del pack, que en MythicMobs eran
            // mobs) del chunk de ese sitio
            case "mobsinchunk" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                int cx = net.minecraft.util.Mth.floor(p.x) >> 4, cz = net.minecraft.util.Mth.floor(p.z) >> 4;
                var level = ctx.caster.level();
                var box = new net.minecraft.world.phys.AABB(cx * 16, level.getMinBuildHeight(), cz * 16, cx * 16 + 16, level.getMaxBuildHeight(), cz * 16 + 16);
                int n = 0;
                for (LivingEntity x : level.getEntitiesOfClass(LivingEntity.class, box)) if (x.isAlive() && !(x instanceof Player)) n++;
                for (SkillActor ac : SkillRuntime.ACTORS) {
                    if (!ac.alive || ac.level != level || ac.def == null || ac.def.isDisplay()) continue;
                    if (net.minecraft.util.Mth.floor(ac.pos.x) >> 4 == cx && net.minecraft.util.Mth.floor(ac.pos.z) >> 4 == cz) n++;
                }
                yield inRange(n, arg(a, "0", "amount", "a"));
            }
            // playerkills{kills}: cuántos jugadores ha matado ese mob de jefe
            case "playerkills" -> {
                SkillMobs.Host mh = e == null ? null : SkillMobs.host(e);
                yield inRange(mh == null ? 0 : SkillMobs.playerKills(mh), arg(a, "0", "kills", "k"));
            }
            case "hasai" -> {
                SkillMobs.Host mh = e == null ? null : SkillMobs.host(e);
                yield mh != null ? mh.ai() : !(e instanceof net.minecraft.world.entity.Mob mob) || !mob.isNoAi();
            }
            case "hasowner" -> {
                SkillMobs.Host mh = e == null ? null : SkillMobs.host(e);
                yield who != null && who.owner() != null || mh != null && mh.boss() != mh;
            }
            case "lineofsight", "los" -> {
                // ¿Lo ve quien lanza? (de ojos a ojos, sin bloques que tapen)
                Vec3 to = e != null ? e.getEyePosition() : at != null ? at : who != null ? who.pos() : null;
                yield to == null || sees(ctx, to);
            }
            case "targetinlineofsight" -> {
                Entity t = ctx.aim;
                yield t != null && t.isAlive() && sees(ctx, t.getEyePosition());
            }
            case "world", "dimension" -> {
                // Como en Bukkit: «world», «world_nether», «world_the_end» (o NORMAL/NETHER/THE_END, o el id de Minecraft)
                var key = ctx.caster.level().dimension().location();
                String path = key.getPath();
                String bukkit = path.equals("overworld") ? "world" : "world_" + path;
                String env = path.equals("overworld") ? "normal" : path.equals("the_nether") ? "nether" : path;
                boolean hit = false;
                for (String w : arg(a, "", "world", "w", "dimension", "d", "environment", "env").toLowerCase(Locale.ROOT).split(",")) {
                    w = w.trim();
                    if (w.equals(path) || w.equals(key.toString()) || w.equals(bukkit) || w.equals(env)) hit = true;
                }
                yield hit;
            }
            case "biome" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                var b = ctx.caster.level().getBiome(BlockPos.containing(p)).unwrapKey();
                if (b.isEmpty()) yield false;
                String id = b.get().location().getPath();
                boolean hit = false;
                for (String w : arg(a, "", "biome", "b").toLowerCase(Locale.ROOT).split(",")) {
                    w = w.trim();
                    if (w.startsWith("minecraft:")) w = w.substring(10);
                    if (w.equals(id)) hit = true;
                }
                yield hit;
            }
            case "moonphase", "lunarphase" -> inRange(ctx.caster.level().getMoonPhase(), arg(a, "0", "phase", "p"));
            case "day", "night", "dusk", "dawn" -> {
                long t = Math.floorMod(ctx.caster.level().getDayTime(), 24000L);
                yield switch (c.m()) {
                    case "day" -> t < 12300;
                    case "dusk" -> t >= 12300 && t < 13800;
                    case "night" -> t >= 13800 && t < 22200;
                    default -> t >= 22200;
                };
            }
            case "raining" -> ctx.caster.level().isRaining();
            case "thundering" -> ctx.caster.level().isThundering();
            case "sunny" -> !ctx.caster.level().isRaining();
            case "outside" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                yield ctx.caster.level().canSeeSky(BlockPos.containing(p.x, p.y + 1, p.z));
            }
            case "lightlevel" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                yield inRange(ctx.caster.level().getMaxLocalRawBrightness(BlockPos.containing(p)), arg(a, ">0", "level", "l"));
            }
            case "distancefromspawn" -> {
                // Desde donde apareció (un jefe: su grieta)
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                SkillMobs.Host mh = SkillMobs.host(e != null ? e : ctx.caster.entity);
                Vec3 spawn = mh != null ? mh.spawnPos() : ctx.caster.level().getSharedSpawnPos().getCenter();
                yield inRange(p.distanceTo(spawn), arg(a, ">0", "distance", "d"));
            }
            case "heightabove", "heightbelow" -> {
                // Más alto / más bajo que esa Y
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                double h = SkillRuntime.num(arg(a, "0", "height", "h", "y"), 0);
                yield c.m().equals("heightabove") ? p.y > h : p.y < h;
            }
            case "lastdamagecause" -> {
                String want = arg(a, "", "cause", "c", "type", "t", "damagecause").toUpperCase(Locale.ROOT);
                String now = ctx.damageCause != null ? ctx.damageCause
                        : le != null && le.getLastDamageSource() != null ? SkillServer.damageType(le.getLastDamageSource()) : "";
                boolean hit = false;
                for (String w : want.split(",")) if (w.trim().equals(now)) hit = true;
                yield hit;
            }
            case "mounted", "isriding" -> e != null && e.isPassenger();
            case "haspassenger" -> e != null && e.isVehicle();
            case "isfrozen" -> e != null && e.isFullyFrozen();
            case "gliding" -> le != null && le.isFallFlying();
            case "isclimbing" -> le != null && le.onClimbable();
            case "motiony", "fallspeed" -> {
                // fallSpeed: lo rápido que cae (positivo); motionY: la velocidad vertical tal cual
                Vec3 v = e != null ? e.getDeltaMovement() : who != null && who.actor != null ? who.actor.vel : Vec3.ZERO;
                double y = c.m().equals("fallspeed") ? -v.y : v.y;
                yield inRange(y, arg(a, ">0", "speed", "s", "velocity", "v", "y", "amount", "a"));
            }
            case "level" -> e instanceof Player p && inRange(p.experienceLevel, arg(a, ">0", "level", "l"));
            case "time" -> inRange(Math.floorMod(ctx.caster.level().getDayTime(), 24000L), arg(a, ">0", "time", "t"));
            case "targets" -> inRange(ctx.targets == null ? 0 : ctx.targets.size(), arg(a, ">0", "amount", "a"));
            case "playersonline" -> inRange(ctx.caster.level().getServer().getPlayerCount(), arg(a, ">0", "amount", "a"));
            case "mobsinworld" -> {
                int n = 0;
                for (Entity x : ctx.caster.level().getAllEntities()) if (x instanceof net.minecraft.world.entity.Mob) n++;
                yield inRange(n, arg(a, ">0", "amount", "a"));
            }
            case "targetwithin", "targetnotwithin" -> {
                Entity t = ctx.aim;
                double d = SkillRuntime.num(arg(a, "16", "distance", "d"), 16);
                boolean within = t != null && t.isAlive() && t.position().distanceTo(ctx.caster.pos()) <= d;
                yield c.m().equals("targetnotwithin") != within;
            }
            // distanceFromPin{pin;distance} (MythicMobs): a qué distancia está del pin (sin el pin en este mundo: no)
            case "distancefrompin" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                Vec3 pin = SkillSaved.pinIn(arg(a, null, "pin", "p", "name", "n"), ctx.caster.level());
                yield pin != null && inRange(p.distanceTo(pin), arg(a, ">0", "distance", "d"));
            }
            case "distance", "distancefromorigin" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                Vec3 from = c.m().equals("distance") ? ctx.caster.pos() : SkillTargets.origin(ctx);
                yield inRange(p.distanceTo(from), arg(a, ">0", "distance", "d"));
            }
            case "health", "hp" -> {
                // Un mob de jefe: en la vida del pack (como sus otras cifras de vida: el pack no sabe de dificultades)
                SkillMobs.Host mh = le == null ? null : SkillMobs.host(le);
                double hp = le == null ? 0 : le.getHealth() / (mh != null ? Math.max(0.01, mh.healthScale()) : 1.0);
                yield le != null && inRange(hp, arg(a, ">0", "health", "h", "amount", "a"));
            }
            case "healthpercent", "hppercent", "hpp" -> le != null && inRange(le.getHealth() / Math.max(1, le.getMaxHealth()),
                    arg(a, ">0", "percent", "p", "health", "h"));
            case "entitytype", "type", "entitytypes" -> {
                // Un efecto del pack (actor): el tipo que tenía su mob (ARMOR_STAND, ITEM_DISPLAY...)
                String id = e != null ? BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()
                        : who != null && who.actor != null && who.actor.def != null && who.actor.def.type != null
                        ? who.actor.def.type.toLowerCase(Locale.ROOT) : null;
                if (id == null) yield false;
                boolean hit = false;
                for (String t : arg(a, "", "types", "type", "t").split(",")) if (t.trim().equalsIgnoreCase(id)) hit = true;
                yield hit;
            }
            case "isplayer", "player" -> e instanceof Player;
            case "ismonster", "monster" -> e instanceof Enemy;
            case "isliving", "living" -> le != null;
            case "isburning", "burning", "onfire" -> e != null && e.isOnFire();
            case "inwater", "isinwater" -> e != null && e.isInWater();
            case "haspotioneffect", "haspotion" -> {
                var effect = SkillRuntime.potionType(arg(a, null, "type", "t"));
                yield le != null && effect != null && le.hasEffect(effect);
            }
            case "isskill", "chance" -> java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                    < SkillRuntime.num(arg(a, "1", "chance", "c"), 1);
            case "altitude", "height" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                if (ctx.host != null) {
                    // En los jefes, como MythicMobs: la altura sobre el primer suelo que haya debajo, por lejos que esté
                    // (antes se buscaba solo 8 bloques: más arriba salía 0,5 y el Contemplador, que sube mientras esté a
                    // menos de 6, subía sin parar hasta 60 bloques)
                    yield inRange(p.y - floorBelow(ctx.caster.level(), p), arg(a, ">0", "height", "h", "a"));
                }
                yield inRange(p.y, arg(a, ">0", "height", "h", "a"));
            }
            case "dead", "isdead" -> who != null && !who.alive();
            // (1.3.82) packs nuevos
            case "samefaction" -> {
                String mine = SkillTargets.faction(ctx.caster), theirs = SkillTargets.faction(who);
                yield mine != null && mine.equalsIgnoreCase(theirs);
            }
            case "lastsignal" -> {
                SkillRuntime.WhoState st = who == null ? null : SkillRuntime.stateIfAny(who);
                String want = arg(a, "", "signal", "s");
                yield st != null && st.lastSignal != null && st.lastSignal.equalsIgnoreCase(want);
            }
            // stringEquals{val1;val2}: dos textos iguales (con sus marcadores ya puestos)
            case "stringequals", "stringeq", "streq" -> SkillRuntime.unquote(arg(a, "", "value1", "val1", "v1"))
                    .equals(SkillRuntime.unquote(arg(a, "", "value2", "val2", "v2")));
            // damageAmount{amount}: el golpe del ~onDamaged/~onAttack que la lanza, en ese rango
            case "damageamount" -> ctx.vars != null && ctx.vars.containsKey("damage-amount")
                    && inRange(SkillRuntime.num(ctx.vars.get("damage-amount"), 0), arg(a, ">0", "amount", "a"));
            // ridingModel: va montado en un modelo (un asiento de jefe o el de un efecto)
            case "ridingmodel" -> e != null && (SkillModels.mountedBy(e) != null || e.getVehicle() instanceof net.tierrasfantasticas.tfclient.bosses.SeatEntity);
            case "skilloncooldown" -> {
                SkillDefs.Meta meta = ctx.cls.meta(arg(a, "", "skill", "s"));
                SkillRuntime.WhoState st = who == null ? null : SkillRuntime.stateIfAny(who);
                Long until = st == null || meta == null ? null : st.cooldowns.get(meta.name);
                yield until != null && until > SkillRuntime.clock;
            }
            case "blocktypeinradius" -> {
                Vec3 p = at != null ? at : who != null ? who.pos() : ctx.caster.pos();
                int r = (int) Math.max(0, Math.min(16, SkillRuntime.num(arg(a, "1", "radius", "r"), 1)));
                java.util.Set<String> types = new java.util.HashSet<>();
                for (String t : arg(a, "", "types", "type", "t", "material", "m").toLowerCase(Locale.ROOT).split(",")) {
                    types.add(t.trim().replace("minecraft:", ""));
                }
                BlockPos c0 = BlockPos.containing(p);
                int n = 0;
                for (BlockPos q : BlockPos.betweenClosed(c0.offset(-r, -r, -r), c0.offset(r, r, r))) {
                    if (!ctx.caster.level().isLoaded(q)) continue;
                    if (types.contains(BuiltInRegistries.BLOCK.getKey(ctx.caster.level().getBlockState(q).getBlock()).getPath())) n++;
                }
                yield inRange(n, arg(a, ">0", "amount", "a"));
            }
            // distanceFromTrackedLocation{d}: de su sitio guardado (tracklocation) a quien lanza
            case "distancefromtrackedlocation", "distancefromtl" -> {
                SkillRuntime.WhoState st = SkillRuntime.stateIfAny(ctx.caster);
                yield st != null && st.tracked != null && inRange(st.tracked.distanceTo(ctx.caster.pos()), arg(a, ">0", "distance", "d"));
            }
            // hitbox / lasthitbox{h} de ModelEngine: la caja (hueso ob_/b_) del mob que se golpeó la última vez
            case "hitbox", "lasthitbox" -> {
                SkillMobs.Host mh = e == null ? null : SkillMobs.host(e);
                String last = mh == null ? null : SkillMobs.lastHitbox(mh);
                boolean hit = false;
                for (String h : arg(a, "", "hitbox", "h", "part", "p").split(",")) if (last != null && h.trim().equalsIgnoreCase(last)) hit = true;
                yield hit;
            }
            default -> {
                SkillRuntime.warn("condición " + c.m());
                yield true;
            }
        } != c.not();
    }

    /** El hueco de equipo de Bukkit (HEAD, CHEST, LEGS, FEET, HAND, OFF_HAND y sus otros nombres). */
    private static net.minecraft.world.entity.EquipmentSlot slot(String s) {
        return switch (s.trim().toUpperCase(Locale.ROOT)) {
            case "CHEST", "CHESTPLATE", "BODY" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "LEGS", "LEGGINGS" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "FEET", "BOOTS" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            case "HAND", "MAINHAND", "MAIN_HAND", "WEAPON" -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            case "OFFHAND", "OFF_HAND" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
            default -> net.minecraft.world.entity.EquipmentSlot.HEAD;
        };
    }

    /** La puntuación de «entry» en el objetivo «objective» del marcador del servidor (0 si no tiene). */
    private static int score(Ctx ctx, String objective, String entry) {
        var sb = ctx.caster.level().getServer().getScoreboard();
        var o = sb.getObjective(objective);
        if (o == null) return 0;
        var s = sb.getPlayerScores(entry).get(o);
        return s == null ? 0 : s.getScore();
    }

    /** ¿Ve quien lanza ese punto? (de sus ojos al punto, sin bloques que tapen la vista) */
    private static boolean sees(Ctx ctx, Vec3 to) {
        Vec3 from = ctx.caster.eye();
        var hit = ctx.caster.level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
                ctx.caster.entity));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static String arg(Map<String, String> a, String def, String... keys) {
        for (String k : keys) {
            String v = a.get(k);
            if (v != null) return v;
        }
        return def;
    }

    private static boolean numEq(String a, String b) {
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Rango de MythicMobs: «3», «1to5», «>0», «<2», «>=1», «<=4», «!=0». */
    static boolean inRange(double v, String range) {
        if (range == null) return true;
        String r = range.trim().replace(" ", "");
        try {
            if (r.startsWith(">=")) return v >= Double.parseDouble(r.substring(2));
            if (r.startsWith("<=")) return v <= Double.parseDouble(r.substring(2));
            if (r.startsWith("!=")) return v != Double.parseDouble(r.substring(2));
            if (r.startsWith(">")) return v > Double.parseDouble(r.substring(1));
            if (r.startsWith("<")) return v < Double.parseDouble(r.substring(1));
            if (r.startsWith("=")) return v == Double.parseDouble(r.substring(1));
            int to = r.indexOf("to");
            if (to > 0) return v >= Double.parseDouble(r.substring(0, to)) && v <= Double.parseDouble(r.substring(to + 2));
            int dash = r.indexOf('-', 1);
            if (dash > 0) return v >= Double.parseDouble(r.substring(0, dash)) && v <= Double.parseDouble(r.substring(dash + 1));
            return v == Double.parseDouble(r);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** La altura del primer bloque sólido bajo un punto (el fondo del mundo si no hay ninguno). */
    static double floorBelow(net.minecraft.server.level.ServerLevel level, Vec3 p) {
        net.minecraft.core.BlockPos.MutableBlockPos q = net.minecraft.core.BlockPos.containing(p).mutable();
        int min = level.getMinBuildHeight();
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, q.getX(), q.getZ());
        if (q.getY() >= top) return top;
        for (; q.getY() >= min; q.move(net.minecraft.core.Direction.DOWN)) {
            net.minecraft.world.phys.shapes.VoxelShape sh = level.getBlockState(q).getCollisionShape(level, q);
            if (!sh.isEmpty()) return q.getY() + sh.max(net.minecraft.core.Direction.Axis.Y);
        }
        return min;
    }
}
