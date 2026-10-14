package net.tierrasfantasticas.tfclient.skills;

import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;

/**
 * Las mecánicas de ModelEngine 4 que no tocan la pelea sino cómo se ve y se mueve el modelo (wiki de ModelEngine,
 * «Skills/Mechanics»): modifystate, statetoggle, headconfig, lockmodelhead, headtarget, jiggleimpulse, hitboxconfig,
 * setitemmodel, linkitembone, setmodeltag, setmodeltagvisible, enchant, billboard, glowbone, cycleparts y
 * modelplayerskin. El servidor solo manda qué hay que hacer («props» del actor del modelo, que se repiten a quien llega
 * tarde); lo hace el cliente (SkillClient.Actor). Los nombres de los atributos y sus alias, los de la wiki.
 */
final class SkillMeg {
    private SkillMeg() {}

    private static String part(Mech m) {
        return m.arg("", "partid", "p", "pid", "part", "b", "bone");
    }

    private static boolean exact(Mech m) {
        return SkillRuntime.bool(m.arg("true", "exactmatch", "em", "exact", "match"));
    }

    private static String flags(Mech m) {
        return (exact(m) ? "1" : "0") + (SkillRuntime.bool(m.arg("false", "child", "c")) ? "1" : "0");
    }

    /** «» si la línea no lo trae (el cliente deja el valor que tuviera). */
    private static String opt(Mech m, String... keys) {
        String v = m.arg(null, keys);
        return v == null ? "" : String.valueOf(SkillRuntime.num(v, 0));
    }

    // ------------------------------------------------------------------------------------------- animaciones

    /** modifystate{m;s;p;speed;li;lo;loop;ov}: cambia una animación que ya suena (sin volver a empezarla). */
    static void modifyState(Mech m, Ctx ctx, List<Tgt> targets) {
        String s = m.arg(null, "state", "s");
        if (s == null) return;
        int prio = m.arg(null, "priority", "p", "pr") == null ? -1 : (int) SkillRuntime.num(m.arg("1", "priority", "p", "pr"), 1);
        double tempo = SkillRuntime.tempo(ctx);
        float speed = m.arg(null, "speed", "sp") == null ? Float.NaN : (float) (SkillRuntime.num(m.arg("1", "speed", "sp"), 1) * tempo);
        int li = m.arg(null, "lerpin", "li") == null ? -1 : (int) Math.round(SkillRuntime.num(m.arg("0", "lerpin", "li"), 0) / tempo);
        int lo = m.arg(null, "lerpout", "lo") == null ? -1 : (int) Math.round(SkillRuntime.num(m.arg("0", "lerpout", "lo"), 0) / tempo);
        String loopS = m.arg("", "loop", "l").toUpperCase(Locale.ROOT);
        int loop = switch (loopS) {
            case "ONCE" -> 0;
            case "HOLD" -> 1;
            case "LOOP" -> 2;
            case "RESET" -> 3;
            default -> -1;
        };
        String ovS = m.arg("", "override", "ov").toUpperCase(Locale.ROOT);
        Boolean ov = ovS.isEmpty() ? null : !(ovS.equals("FALSE") || ovS.equals("NONE") || ovS.equals("ADDITIVE") || ovS.equals("0"));
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a == null || a.layers == null) continue;
            if (a.layers.modify(s, prio, a.age, speed, li, lo, loop, ov)) {
                a.prop("lmod", s, prio + "|" + speed + "|" + li + "|" + lo + "|" + loop + "|" + (ov == null ? "" : ov ? "1" : "0"), null);
            }
        }
    }

    /** statetoggle{m;s;speed;li;lo;force;ignorelerp;p}: si suena, la quita; si no, la pone. */
    static void stateToggle(Mech m, Ctx ctx, List<Tgt> targets) {
        String s = m.arg(null, "state", "s");
        if (s == null) return;
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a == null || a.layers == null) continue;
            boolean on = a.layers.playing(s, a.age);
            java.util.Map<String, String> args = new java.util.HashMap<>(m.a);
            if (on) args.put("r", "true");
            else args.putIfAbsent("f", m.arg("true", "force", "f"));
            SkillModels.state(m.copyWith(args, m.ta), ctx, List.of(t));
        }
    }

    // ------------------------------------------------------------------------------------------- cabezas

    /** headconfig{p;yaw;pitch;minyaw;maxyaw;minpitch;maxpitch;speed;em}: límites y velocidad de giro de las cabezas. */
    static void headConfig(Mech m, Ctx ctx, List<Tgt> targets) {
        String yaw = opt(m, "yaw"), pitch = opt(m, "pitch");
        String minYaw = opt(m, "minyaw"), maxYaw = opt(m, "maxyaw"), minPitch = opt(m, "minpitch"), maxPitch = opt(m, "maxpitch");
        if (!yaw.isEmpty()) {
            float v = Math.abs(Float.parseFloat(yaw));
            if (minYaw.isEmpty()) minYaw = String.valueOf(-v);
            if (maxYaw.isEmpty()) maxYaw = String.valueOf(v);
        }
        if (!pitch.isEmpty()) {
            float v = Math.abs(Float.parseFloat(pitch));
            if (minPitch.isEmpty()) minPitch = String.valueOf(-v);
            if (maxPitch.isEmpty()) maxPitch = String.valueOf(v);
        }
        String cfg = minYaw + "|" + maxYaw + "|" + minPitch + "|" + maxPitch + "|" + opt(m, "speed", "s");
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("headcfg", part(m), cfg, exact(m) ? "1" : "0");
        }
    }

    /** lockmodelhead{lockpitch;lockyaw}: la cabeza deja de seguir la mirada (en ese eje). */
    static void lockHead(Mech m, Ctx ctx, List<Tgt> targets) {
        String lp = m.arg(null, "lockpitch", "lpitch", "lp"), ly = m.arg(null, "lockyaw", "lyaw", "ly");
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("headlock", lp == null ? "" : SkillRuntime.bool(lp) ? "1" : "0", ly == null ? "" : SkillRuntime.bool(ly) ? "1" : "0", null);
        }
    }

    /** headtarget{p;y;follow;clear} @objetivo: las cabezas miran a ese objetivo (o a ese sitio). La lanza el propio mob. */
    static void headTarget(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null) return;
        boolean clear = SkillRuntime.bool(m.arg("false", "clear", "c"));
        if (clear || targets.isEmpty()) {
            a.prop("headtgt", "", "", "clear");
            return;
        }
        Tgt t = targets.get(0);
        double yo = SkillRuntime.num(m.arg("0", "yoffset", "y", "yo"), 0);
        boolean follow = SkillRuntime.bool(m.arg("true", "follow", "f"));
        Entity e = t.entity();
        Vec3 p = t.pos();
        a.prop("headtgt", follow && e != null ? String.valueOf(e.getId()) : "", p.x + "|" + (p.y + yo) + "|" + p.z + "|" + yo, "");
    }

    // ------------------------------------------------------------------------------------------- física y cajas

    /** jiggleimpulse{p;strength;x;y;z;em} @objetivo: empuja los huesos jig_ (lejos del objetivo y en x, y, z). */
    static void jiggleImpulse(Mech m, Ctx ctx, List<Tgt> targets) {
        float s = (float) SkillRuntime.num(m.arg("0", "strength", "s"), 0);
        String v = s + "|" + SkillRuntime.num(m.arg("0", "x"), 0) + "|" + SkillRuntime.num(m.arg("0", "y"), 0) + "|"
                + SkillRuntime.num(m.arg("0", "z"), 0);
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null) return;
        Vec3 from = targets.isEmpty() ? a.pos : targets.get(0).pos();
        a.prop("jig", part(m), v + "|" + from.x + "|" + from.y + "|" + from.z, exact(m) ? "1" : "0");
    }

    /** hitboxconfig{p;pass}: cuánto daño deja pasar esa caja de golpe (0 = ninguno). Lo aplica la entidad del jefe. */
    static void hitboxConfig(Mech m, Ctx ctx, List<Tgt> targets) {
        String p = part(m);
        double pass = SkillRuntime.num(m.arg("1", "pass"), 1);
        for (Tgt t : targets) {
            SkillMobs.Host h = t.who == null || t.who.entity == null ? null : SkillMobs.host(t.who.entity);
            if (h != null) h.hitboxPass(p, pass);
        }
    }

    // ------------------------------------------------------------------------------------------- IK y física

    /**
     * ik{p;enabled;alwaysactive;lockrotation;weight;blendticks;limits;minx..maxz;stiffness;damping;gravity;maxangle;
     * repeat;em}: la cadena (por su nulo o su punta), los límites de una articulación y la física de un hueso jig_.
     */
    static void ik(Mech m, Ctx ctx, List<Tgt> targets) {
        StringBuilder cfg = new StringBuilder();
        String[][] bools = {{"enabled", "enabled", "e", "en"}, {"always", "alwaysactive", "a", "always"},
                {"lock", "lockrotation", "l", "lock"}, {"limits", "limits", "lim", "limit"}};
        for (String[] k : bools) {
            String v = m.arg(null, java.util.Arrays.copyOfRange(k, 1, k.length));
            if (v != null) cfg.append(k[0]).append('=').append(SkillRuntime.bool(v) ? 1 : 0).append(';');
        }
        String[][] nums = {{"weight", "weight", "w"}, {"blend", "blendticks", "bt", "blend"}, {"minx", "minx"}, {"maxx", "maxx"},
                {"miny", "miny"}, {"maxy", "maxy"}, {"minz", "minz"}, {"maxz", "maxz"}, {"stiff", "stiffness", "stiff"},
                {"damp", "damping", "damp"}, {"grav", "gravity", "grav"}, {"angle", "maxangle", "angle"}, {"repeat", "repeat"}};
        for (String[] k : nums) {
            String v = m.arg(null, java.util.Arrays.copyOfRange(k, 1, k.length));
            if (v != null) cfg.append(k[0]).append('=').append(SkillRuntime.num(v, 0)).append(';');
        }
        String em = m.arg(null, "exactmatch", "em", "exact", "match");
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("ik", part(m), cfg.toString(), em == null || SkillRuntime.bool(em) ? "1" : "0");
        }
    }

    /** iktarget{p;y;follow;clear;em} @objetivo: la cadena llega a ese objetivo (o sitio). La lanza el propio mob. */
    static void ikTarget(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null) return;
        boolean clear = SkillRuntime.bool(m.arg("false", "clear", "c")) || targets.isEmpty();
        String flags = (clear ? "1" : "0") + (exact(m) ? "1" : "0");
        if (clear) {
            a.prop("iktgt", part(m), "", flags);
            return;
        }
        Tgt t = targets.get(0);
        boolean follow = SkillRuntime.bool(m.arg("true", "follow", "f"));
        Entity e = t.entity();
        Vec3 p = t.pos();
        a.prop("iktgt", part(m), (follow && e != null ? e.getId() : -1) + "|" + p.x + "|" + p.y + "|" + p.z + "|"
                + SkillRuntime.num(m.arg("0", "yoffset", "y", "yo"), 0), flags);
    }

    /** ikchain{root;tip;pole;remove}: una cadena IK nueva (o la quita). */
    static void ikChain(Mech m, Ctx ctx, List<Tgt> targets) {
        String root = m.arg("", "root", "r"), tip = m.arg(null, "tip", "t");
        if (tip == null) return;
        boolean remove = SkillRuntime.bool(m.arg("false", "remove", "rm"));
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("ikchain", root, tip + "|" + m.arg("", "pole"), remove ? "1" : "0");
        }
    }

    // ------------------------------------------------------------------------------------------- huesos

    /** setitemmodel{m;p;material;data;color;enchant}: el objeto que se ve en un hueso de objeto (ih_, ir_, il_). */
    static void setItemModel(Mech m, Ctx ctx, List<Tgt> targets) {
        String mat = m.arg("", "material", "i", "item", "mat");
        String item = itemId(ctx, mat);
        String v = item + "|" + (int) SkillRuntime.num(m.arg("0", "data"), 0) + "|" + m.arg("", "color") + "|"
                + (SkillRuntime.bool(m.arg("false", "enchant")) ? 1 : 0);
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("bitem", part(m), v, "1");
        }
    }

    /** linkitembone{p;slot}: el hueso de objeto enseña lo que lleva el mob en esa ranura (ahora). */
    static void linkItemBone(Mech m, Ctx ctx, List<Tgt> targets) {
        String slot = m.arg("HAND", "slot", "s").toUpperCase(Locale.ROOT);
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a == null || !(t.entity() instanceof LivingEntity le)) continue;
            EquipmentSlot es = switch (slot) {
                case "OFFHAND", "OFF_HAND" -> EquipmentSlot.OFFHAND;
                case "HEAD", "HELMET" -> EquipmentSlot.HEAD;
                case "CHEST", "CHESTPLATE" -> EquipmentSlot.CHEST;
                case "LEGS", "LEGGINGS" -> EquipmentSlot.LEGS;
                case "FEET", "BOOTS" -> EquipmentSlot.FEET;
                default -> EquipmentSlot.MAINHAND;
            };
            ItemStack st = le.getItemBySlot(es);
            String id = st.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
            a.prop("bitem", part(m), id + "|0||" + (st.isEnchanted() ? 1 : 0), "1");
        }
    }

    /** El objeto del pack (sus objetos de MythicMobs son tfclient:jefe_<jefe>_<objeto>) o el material de Minecraft. */
    static String itemId(Ctx ctx, String mat) {
        if (mat == null || mat.isBlank()) return "";
        String key = mat.trim().toLowerCase(Locale.ROOT);
        SkillDefs.BossInfo bi = ctx.cls.bossInfo;
        if (bi != null && bi.drops.containsKey(key)) {
            return "tfclient:" + ("jefe_" + ctx.cls.id + "_" + key).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        }
        return key.contains(":") ? key : "minecraft:" + key;
    }

    /**
     * setmodeltag{m;p;tag;visible;scale;shadow;seethrough;background;opacity;billboard}: el texto de un hueso de nombre
     * (tag_). Los marcadores del pack (&lt;caster.hp&gt;...) se resuelven aquí.
     */
    static void setModelTag(Mech m, Ctx ctx, List<Tgt> targets) {
        String raw = SkillRuntime.unquote(m.arg("", "tag", "t"));
        boolean visible = SkillRuntime.bool(m.arg("true", "visible", "v"));
        String scale = m.arg("1", "scale").replace("|", ",");
        // escala (x,y,z) | sombra | a través | fondo | opacidad | alineación | ancho de línea | billboard
        String style = scale + "|" + (SkillRuntime.bool(m.arg("false", "shadow")) ? 1 : 0) + "|"
                + (SkillRuntime.bool(m.arg("true", "seethrough")) ? 1 : 0) + "|"
                + (SkillRuntime.bool(m.arg("true", "usedefaultbackground", "defaultbackground", "db")) ? -1
                : (int) SkillRuntime.num(m.arg("0", "backgroundcolor", "background"), 0)) + "|"
                + (int) SkillRuntime.num(m.arg("-1", "textopacity", "opacity"), -1) + "|"
                + m.arg("CENTER", "alignment", "align").toUpperCase(Locale.ROOT) + "|"
                + (int) SkillRuntime.num(m.arg("200", "linewidth"), 200) + "|" + m.arg("CENTER", "billboard").toUpperCase(Locale.ROOT);
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a == null) continue;
            String text = raw.indexOf('<') >= 0 ? SkillVars.text(raw, ctx, t) : raw;
            a.prop("btag", part(m), style + "\u0001" + text, visible ? "1" : "0");
        }
    }

    /** setmodeltagvisible{m;p;visible}. */
    static void setModelTagVisible(Mech m, Ctx ctx, List<Tgt> targets) {
        boolean visible = SkillRuntime.bool(m.arg("true", "visible", "v"));
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("btagv", part(m), visible ? "1" : "0", null);
        }
    }

    /** enchant{m;p;enchant;em;child}: brillo de encantado en el modelo o en un hueso. */
    static void enchant(Mech m, Ctx ctx, List<Tgt> targets) {
        boolean on = SkillRuntime.bool(m.arg("true", "enchant", "en"));
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("ench", part(m), on ? "1" : "0", flags(m));
        }
    }

    /** billboard{m;p;billboard=FIXED|VERTICAL|HORIZONTAL|CENTER;em;child}: el hueso mira siempre a la cámara. */
    static void billboard(Mech m, Ctx ctx, List<Tgt> targets) {
        String mode = m.arg("FIXED", "billboard", "b", "bb", "bill").toUpperCase(Locale.ROOT);
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("bill", part(m), mode, flags(m));
        }
    }

    /** glowbone (meg:glow){m;p;glow;color;em;child}: el contorno brillante de Minecraft en el modelo o en un hueso. */
    static void glowBone(Mech m, Ctx ctx, List<Tgt> targets) {
        boolean on = SkillRuntime.bool(m.arg("true", "glow", "g"));
        String color = m.arg("FFFFFF", "color", "c").replace("#", "");
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("bglow", part(m), (on ? "1" : "0") + "|" + color, flags(m));
        }
    }

    /** cycleparts{m;p;nm;np=a,b,c;interval;duration}: el hueso va cambiando de pieza cada «interval» ticks. */
    static void cycleParts(Mech m, Ctx ctx, List<Tgt> targets) {
        String p = part(m);
        String nmRaw = m.arg(null, "newmodelid", "nm", "nmid", "newmodel");
        String parts = m.arg(null, "cycledparts", "np", "npid", "newpart", "newpartid", "newparts");
        if (p.isEmpty() || parts == null) return;
        String nm = nmRaw == null ? null : nmRaw.contains(".") ? nmRaw : ctx.cls.id + "." + nmRaw.toLowerCase(Locale.ROOT);
        String[] list = parts.split(",");
        int every = (int) Math.max(1, SkillRuntime.num(m.arg("1", "interval", "i"), 1));
        int times = (int) SkillRuntime.num(m.arg(String.valueOf(list.length - 1), "duration", "d"), list.length - 1);
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a == null) continue;
            String model = nm != null ? nm : a.me;
            for (int k = 0; k <= times; k++) {
                String np = list[k % list.length].trim();
                SkillRuntime.later(k * every, ctx.cast, () -> {
                    if (a.alive) a.prop("part", p, model, np);
                });
            }
        }
    }

    /**
     * modelplayerskin{m;limbs;uuid;username;skin;slim} @jugador: la skin de los miembros de jugador del modelo («limbs»,
     * separados por comas; sin ellos, todos): la del jugador objetivo, la de ese uuid o nombre (aunque no esté conectado:
     * se baja una vez, como ModelEngine) o la de unos datos de textura en base64 (skin=). slim: fuerza los brazos finos;
     * sin él, los de la skin.
     */
    static void playerSkin(Mech m, Ctx ctx, List<Tgt> targets) {
        String limbs = m.arg("", "limbs", "p", "pbone");
        String uuid = m.arg(null, "uuid"), name = m.arg(null, "username", "user", "name");
        String data = m.arg(null, "skin", "s");
        String slim = m.arg("", "slim");
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null) return;
        if (data != null && !data.isBlank()) {
            a.prop("skin", limbs, "tex:" + SkillRuntime.unquote(data).trim(), slim);
            return;
        }
        String who = uuid != null ? uuid : name != null ? name : "";
        if (who.isEmpty() && !targets.isEmpty() && targets.get(0).entity() instanceof net.minecraft.world.entity.player.Player pl) {
            who = pl.getUUID().toString();
        }
        if (who.isEmpty()) return;
        // Conectado: el cliente ya tiene su skin. Si no, sus datos de textura (Mojang), una vez por jugador
        net.minecraft.server.MinecraftServer srv = a.level.getServer();
        if (online(srv, who)) {
            a.prop("skin", limbs, who, slim);
            return;
        }
        String cached = SKIN_DATA.get(who.toLowerCase(Locale.ROOT));
        if (cached != null) {
            a.prop("skin", limbs, "tex:" + cached, slim);
            return;
        }
        String key = who;
        net.minecraft.Util.backgroundExecutor().execute(() -> {
            String tex = fetchTextures(srv, key);
            if (tex == null) return;
            srv.execute(() -> {
                SKIN_DATA.put(key.toLowerCase(Locale.ROOT), tex);
                if (a.alive) a.prop("skin", limbs, "tex:" + tex, slim);
            });
        });
    }

    /** Los datos de textura (base64) ya bajados de cada uuid o nombre. */
    private static final java.util.Map<String, String> SKIN_DATA = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.util.UUID uuidOf(String s) {
        String raw = s.replace("-", "");
        if (raw.length() != 32) return null;
        try {
            return new java.util.UUID(Long.parseUnsignedLong(raw.substring(0, 16), 16), Long.parseUnsignedLong(raw.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean online(net.minecraft.server.MinecraftServer srv, String who) {
        if (srv == null) return false;
        java.util.UUID u = uuidOf(who);
        return u != null ? srv.getPlayerList().getPlayer(u) != null : srv.getPlayerList().getPlayerByName(who) != null;
    }

    /** Pide a Mojang (por la caché de perfiles del servidor) los datos de textura de un jugador; null si no hay. */
    private static String fetchTextures(net.minecraft.server.MinecraftServer srv, String who) {
        try {
            java.util.UUID u = uuidOf(who);
            com.mojang.authlib.GameProfile gp;
            if (u != null) {
                gp = new com.mojang.authlib.GameProfile(u, null);
            } else {
                var cache = srv.getProfileCache();
                gp = cache == null ? null : cache.get(who).orElse(null);
            }
            if (gp == null) return null;
            com.mojang.authlib.GameProfile full = srv.getSessionService().fillProfileProperties(gp, false);
            for (com.mojang.authlib.properties.Property p : full.getProperties().get("textures")) return p.getValue();
        } catch (Exception e) {
            net.tierrasfantasticas.tfclient.TFClient.LOGGER.debug("TF Jefes: sin skin de {}", who, e);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------- correas

    /**
     * leash{m;p;leash} @objetivo: una correa como la de Minecraft desde el hueso de correa (l_) del modelo de quien lanza
     * hasta el objetivo; leash=false la quita. Como en ModelEngine solo se ve («visually leash»): no tira de nadie.
     */
    static void leash(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null) return;
        boolean on = SkillRuntime.bool(m.arg("true", "leash", "l"));
        String part = m.arg("", "partid", "p", "pid", "part");
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e != null && e != ctx.caster.entity) a.prop("leash", String.valueOf(e.getId()), part, on ? "1" : "0");
        }
    }

    /**
     * leashself{m;s;d}: una correa entre dos huesos de correa del mismo modelo (s la sujeta, d va atado). Sin d, quita
     * las que sujeta s.
     */
    static void leashSelf(Mech m, Ctx ctx, List<Tgt> targets) {
        String src = m.arg(null, "sourceid", "s", "sid", "src", "source");
        String dst = m.arg(null, "destid", "d", "did", "dest");
        if (src == null) return;
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("leashself", src, dst == null ? "" : dst, dst == null ? "0" : "1");
        }
    }

    // ------------------------------------------------------------------------------------------- huesos en vivo

    /**
     * animatemodel{m;p;em;position;rotation;scale;r;o;copy;rm}: mueve un hueso a mano, sin animación (posición en
     * píxeles y giro en grados de Blockbench, como los fotogramas). r: sumado a lo que ya tenía a mano; o: tapa a sus
     * animaciones; copy: parte de la forma del hueso en el modelo; rm=GLOBAL: el giro en los ejes del modelo.
     */
    static void animateModel(Mech m, Ctx ctx, List<Tgt> targets) {
        String part = part(m);
        if (part.isEmpty()) return;
        double[] pos = vec(m.arg("0,0,0", "position", "pos"), 0), rot = vec(m.arg("0,0,0", "rotation", "rot"), 0);
        double[] scl = vec(m.arg("1,1,1", "scale", "scl"), 1);
        // A los signos del modelo convertido (como sus animaciones: la x de la posición y la x e y del giro, al revés)
        String spec = (-pos[0]) + "," + pos[1] + "," + pos[2] + "|" + (-rot[0]) + "," + (-rot[1]) + "," + rot[2] + "|" + scl[0] + ","
                + scl[1] + "," + scl[2] + "|" + (SkillRuntime.bool(m.arg("false", "relative", "rel", "r")) ? 1 : 0)
                + (SkillRuntime.bool(m.arg("false", "override", "ov", "o")) ? 1 : 0) + (SkillRuntime.bool(m.arg("true", "copy")) ? 1 : 0)
                + (m.arg("LOCAL", "rotmode", "rm").equalsIgnoreCase("GLOBAL") ? 1 : 0);
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null && a.layers != null) a.prop("anim", part, spec, exact(m) ? "1" : "0");
        }
    }

    private static double[] vec(String s, double def) {
        String[] p = s.split(",");
        double x = SkillRuntime.num(p[0].trim(), def);
        return new double[] {x, p.length > 1 ? SkillRuntime.num(p[1].trim(), def) : x, p.length > 2 ? SkillRuntime.num(p[2].trim(), def) : x};
    }

    /**
     * rootmotion{w;bw;ov}: con ov=true la entidad anda lo que sus animaciones mueven el hueso raíz (model{rootbone}); con
     * ov=false deja de hacerlo. w: peso con sus ataques; bw: con el reposo y el andar.
     */
    static void rootMotion(Mech m, Ctx ctx, List<Tgt> targets) {
        String w = m.arg(null, "weight", "w"), bw = m.arg(null, "baseweight", "bw"), ov = m.arg(null, "override", "ov");
        String spec = (w == null ? "NaN" : String.valueOf(SkillRuntime.num(w, -1))) + "|" + (bw == null ? "NaN" : String.valueOf(SkillRuntime.num(bw, -1)))
                + "|" + (ov == null ? "" : SkillRuntime.bool(ov) ? "1" : "0");
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null && a.layers != null) a.prop("root", "", spec, null);
        }
    }

    /** changeparent{m;p;c}: el hueso «c» (con lo que cuelga de él) pasa a colgar de «p». */
    static void changeParent(Mech m, Ctx ctx, List<Tgt> targets) {
        String parent = m.arg(null, "parent", "p"), child = m.arg(null, "child", "c");
        if (parent == null || child == null) return;
        for (Tgt t : targets) edit(SkillModels.holder(t, false, ctx), "cp|" + child + "|" + parent);
    }

    /**
     * submodel{m;p;sp;pfx;r}: añade bajo «p» una copia de la pieza «sp» del modelo (con lo que cuelga de ella; con «pfx»
     * delante del nombre) o, con r=true, la quita (con su etiqueta: «ob_cola»): ni se ve, ni es caja de golpe, ni asiento.
     */
    static void subModel(Mech m, Ctx ctx, List<Tgt> targets) {
        String sub = m.arg(null, "subpartid", "sp", "spid", "subpart");
        if (sub == null) return;
        String e = SkillRuntime.bool(m.arg("false", "remove", "r")) ? "sm-|" + sub
                : "sm+|" + m.arg("", "partid", "p", "pid", "part") + "|" + sub + "|" + m.arg("", "prefix", "pfx");
        for (Tgt t : targets) edit(SkillModels.holder(t, false, ctx), e);
    }

    /** Un cambio en vivo del modelo: se apunta (en orden) y se manda entero (el cliente rehace el modelo igual). */
    private static void edit(SkillActor a, String e) {
        if (a == null || a.me == null || a.layers == null) return;
        a.edits.add(e);
        SkillModels.modelOf(a);
        a.prop("medit", "", String.join("\n", a.edits), null);
    }

    /** swapentity{m;hitbox;invisible;step} @objetivo: el modelo de quien lanza pasa a esa entidad. */
    static void swapEntity(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        Entity to = targets.isEmpty() ? null : targets.get(0).entity();
        if (a == null || to == null) return;
        String step = m.arg(null, "step", "s");
        SkillModels.swap(a, to, SkillRuntime.bool(m.arg("true", "invisible", "i", "invis")), step == null ? -1 : SkillRuntime.num(step, 0.5));
    }

    /** petrify{m}: una estatua del modelo como está ahora (quieta para siempre), donde está. */
    static void petrify(Mech m, Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null && a.me != null) SkillModels.petrify(a);
        }
    }

    /**
     * pairmodel{hidden;remove} @jugadores: a esos jugadores se les esconde (hidden=true) o se les enseña siempre
     * (hidden=false) el modelo de quien lanza; remove vuelve a lo normal.
     */
    static void pairModel(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.entity == null ? ctx.caster.actor : SkillModels.holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null) return;
        boolean hidden = SkillRuntime.bool(m.arg("false", "hidden", "h")), remove = SkillRuntime.bool(m.arg("false", "remove", "r"));
        for (Tgt t : targets) {
            if (!(t.entity() instanceof net.minecraft.server.level.ServerPlayer p)) continue;
            if (remove) a.pairs.remove(p.getUUID());
            else a.pairs.put(p.getUUID(), hidden);
            SkillNet.toPlayer(p, new SkillNet.Prop(a.id, "pair", remove ? "" : hidden ? "1" : "0", null, null));
        }
    }

    /** modeldisguise{m;seeself} @jugador: le pone ese modelo encima (el disfraz); seeself: si se ve él mismo. */
    static void modelDisguise(Mech m, Ctx ctx, List<Tgt> targets) {
        String mid = modelOf(ctx, m);
        if (mid == null) return;
        java.util.Map<String, String> args = new java.util.HashMap<>();
        args.put("mid", mid);
        SkillModels.model(m.copyWith(args, m.ta), ctx, targets);
        boolean see = SkillRuntime.bool(m.arg("true", "seeself", "s", "see"));
        for (Tgt t : targets) {
            SkillActor a = SkillModels.holder(t, false, ctx);
            if (a != null) a.prop("seeself", see ? "1" : "0", null, null);
        }
    }

    /** modelundisguise @jugador: le quita el disfraz. */
    static void modelUndisguise(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillModels.model(m.copyWith(java.util.Map.of("remove", "true"), m.ta), ctx, targets);
    }

    // ------------------------------------------------------------------------------------------- VFX de ModelEngine

    /** Los vfx{} de cada mob (por su UUID) o proyectil (el Mover). */
    private static final java.util.Map<Object, SkillActor> VFX = new java.util.HashMap<>();

    /** A qué va pegado el vfx de esta línea: el proyectil que la lanza o la entidad del objetivo. */
    private static Object vfxKey(Ctx ctx, Tgt t) {
        if (t != null && t.entity() != null) return t.entity().getUUID();
        return ctx.mover;
    }

    private static List<Object> vfxKeys(Ctx ctx, List<Tgt> targets) {
        List<Object> out = new java.util.ArrayList<>();
        for (Tgt t : targets) {
            Object k = vfxKey(ctx, t);
            if (k != null && !out.contains(k)) out.add(k);
        }
        if (out.isEmpty() && ctx.mover != null) out.add(ctx.mover);
        return out;
    }

    private static SkillActor vfxOf(Object key) {
        SkillActor a = VFX.get(key);
        if (a != null && !a.alive) {
            VFX.remove(key);
            return null;
        }
        return a;
    }

    private static String modelOf(Ctx ctx, Mech m) {
        String raw = m.arg(null, "modelid", "m", "mid", "model");
        return raw == null ? null : raw.contains(".") ? raw : ctx.cls.id + "." + raw.toLowerCase(Locale.ROOT);
    }

    /** vfx{m;p;remove;radius;color;visible;enchant;bvisible}: un modelo (o solo uno de sus huesos) pegado al objetivo. */
    static void vfx(Mech m, Ctx ctx, List<Tgt> targets) {
        boolean remove = SkillRuntime.bool(m.arg("false", "remove", "r"));
        String mid = modelOf(ctx, m);
        String part = m.arg("", "partid", "p", "pid", "part");
        VFX.values().removeIf(a -> !a.alive);
        for (Tgt t : targets.isEmpty() ? java.util.Collections.singletonList((Tgt) null) : targets) {
            Object key = vfxKey(ctx, t);
            if (key == null) continue;
            SkillActor old = vfxOf(key);
            if (old != null) {
                old.remove();
                VFX.remove(key);
            }
            if (remove || mid == null) continue;
            Entity e = t == null ? null : t.entity();
            Vec3 at = e != null ? e.position() : ctx.mover != null ? ctx.mover.pos : ctx.caster.pos();
            float yaw = e instanceof LivingEntity le ? le.yBodyRot : ctx.caster.yaw();
            SkillActor a = new SkillActor(ctx.cls, null, ctx.caster.level(), at, yaw, 0F, ctx.caster);
            a.host = ctx.host;
            a.me = mid;
            if (e != null) {
                a.follow = e;
                // bvisible=false (como ModelEngine) esconde el mob de debajo; a los jugadores nunca
                a.hideHost = !SkillRuntime.bool(m.arg("false", "bvisible", "bv")) && !(e instanceof net.minecraft.world.entity.player.Player);
            } else if (key instanceof SkillMovers.Mover mv) {
                a.carried = true;
                mv.vfx.add(a);
            }
            SkillRuntime.ACTORS.add(a);
            SkillNet.near(a.level, a.pos, a.spawnMessage());
            VFX.put(key, a);
            if (!part.isEmpty()) a.prop("only", part, null, null);
            String color = m.arg(null, "color", "c");
            if (color != null) a.prop("tint", color.startsWith("#") ? color : "#" + color, null, null);
            if (SkillRuntime.bool(m.arg("false", "enchant", "en"))) a.prop("ench", "", "1", "10");
            if (!SkillRuntime.bool(m.arg("true", "visible", "v"))) a.prop("vfxvis", "0", null, null);
        }
    }

    /** vfxpos / vfxrot / vfxscale {x,y,z o position/rotation/scale; relative} y sus «reset». */
    static void vfxTransform(String what, Mech m, Ctx ctx, List<Tgt> targets, boolean reset) {
        String vec = what.equals("pos") ? m.arg(null, "position", "p", "pos") : what.equals("rot") ? m.arg(null, "rotation", "r", "rot")
                : m.arg(null, "scale", "s", "scl", "sca");
        double x, y, z;
        if (vec != null) {
            String[] v = vec.split(",");
            x = SkillRuntime.num(v[0], 0);
            y = v.length > 1 ? SkillRuntime.num(v[1], 0) : x;
            z = v.length > 2 ? SkillRuntime.num(v[2], 0) : x;
        } else {
            x = SkillRuntime.num(m.arg("0", "x"), 0);
            y = SkillRuntime.num(m.arg("0", "y"), 0);
            z = SkillRuntime.num(m.arg("0", "z"), 0);
        }
        boolean rel = SkillRuntime.bool(m.arg("false", "relative", "rel", what.equals("rot") ? "rel" : "r"));
        boolean origin = SkillRuntime.bool(m.arg("false", "neworigin", "o", "origin"));
        for (Object key : vfxKeys(ctx, targets)) {
            SkillActor a = vfxOf(key);
            if (a == null) continue;
            a.prop("vfxtf", what, reset ? "reset" : x + "|" + y + "|" + z + "|" + (rel ? 1 : 0) + "|" + (origin ? 1 : 0), null);
        }
    }

    /** vfxchangemodel{m;p}: otro modelo (o hueso) para el vfx. */
    static void vfxChangeModel(Mech m, Ctx ctx, List<Tgt> targets) {
        String mid = modelOf(ctx, m);
        String part = m.arg("", "partid", "p", "pid", "part");
        for (Object key : vfxKeys(ctx, targets)) {
            SkillActor a = vfxOf(key);
            if (a == null || mid == null) continue;
            a.me = mid;
            a.prop("me", mid, null, null);
            if (!part.isEmpty()) a.prop("only", part, null, null);
        }
    }

    /** vfxsetvis{visible} / vfxenchant{enchant} / vfxtint{color} / vfxbrightness{block;sky} sobre el vfx. */
    static void vfxSet(String what, Mech m, Ctx ctx, List<Tgt> targets) {
        for (Object key : vfxKeys(ctx, targets)) {
            SkillActor a = vfxOf(key);
            if (a == null) continue;
            switch (what) {
                case "vis" -> a.prop("vfxvis", SkillRuntime.bool(m.arg("false", "visible", "v")) ? "1" : "0", null, null);
                case "ench" -> a.prop("ench", "", SkillRuntime.bool(m.arg("true", "enchant", "en")) ? "1" : "0", "10");
                case "tint" -> {
                    String c = m.arg("FFFFFF", "color", "c");
                    a.prop("tint", c.startsWith("#") ? c : "#" + c, null, null);
                }
                case "bright" -> {
                    int b = (int) SkillRuntime.num(m.arg("15", "block", "b"), 15);
                    int s2 = (int) SkillRuntime.num(m.arg(String.valueOf(b), "sky", "s"), b);
                    a.prop("bright", "", b + "|" + s2, "1");
                }
                default -> { }
            }
        }
    }

    /** ¿Algún hueso de un modelo se llama así (o lo lleva en el nombre con em=false)? Para el auditor y las pruebas. */
    static boolean hasBone(VfxModel vm, String part, boolean exact) {
        if (vm == null || part == null || part.isEmpty()) return false;
        if (exact) return vm.bone(part) >= 0;
        String want = part.toLowerCase(Locale.ROOT);
        for (VfxModel.Bone b : vm.bones) if (b.id.toLowerCase(Locale.ROOT).contains(want)) return true;
        return false;
    }
}
