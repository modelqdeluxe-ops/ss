package net.tierrasfantasticas.tfclient.skills;

import com.mojang.math.Axis;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.skills.SkillDefs.Mech;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Ctx;
import net.tierrasfantasticas.tfclient.skills.SkillRuntime.Tgt;
import net.tierrasfantasticas.tfclient.vfx.VfxLayers;
import net.tierrasfantasticas.tfclient.vfx.VfxModel;
import net.tierrasfantasticas.tfclient.vfx.VfxPose;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Los modelos de ModelEngine: ponerlos en un efecto o en un jugador (model), sus animaciones (state), cambiar o
 * esconder piezas (changepart, partvisibility) y teñirlos (tint). En un jugador el modelo va en un actor pegado a él
 * que lo tapa (como el disfraz de ModelEngine). El servidor también sabe dónde está cada hueso (@modelpart).
 */
final class SkillModels {
    private static final Map<UUID, SkillActor> DISGUISES = new HashMap<>();
    private static final Map<String, Optional<VfxModel>> MODELS = new HashMap<>();

    private SkillModels() {}

    /** El actor que lleva el modelo de ese objetivo: el propio actor o el disfraz del jugador (null si no tiene). */
    static SkillActor holder(Tgt t, boolean create, Ctx ctx) {
        if (t.who == null) return null;
        if (t.who.actor != null) return t.who.actor;
        Entity e = t.who.entity;
        if (e == null) return null;
        SkillActor a = DISGUISES.get(e.getUUID());
        if (a != null && !a.alive) {
            DISGUISES.remove(e.getUUID());
            a = null;
        }
        if (a == null && create) {
            float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
            a = new SkillActor(ctx.cls, null, (net.minecraft.server.level.ServerLevel) e.level(), e.position(), yaw, 0F, ctx.caster);
            a.follow = e;
            a.hideHost = true;
            a.host = ctx.host;
            DISGUISES.put(e.getUUID(), a);
            SkillRuntime.ACTORS.add(a);
        }
        return a;
    }

    static String modelId(Mech m) {
        return m.arg(null, "mid", "m", "modelid", "model");
    }

    static void model(Mech m, Ctx ctx, List<Tgt> targets) {
        String mid = modelId(m);
        boolean remove = SkillRuntime.bool(m.arg("false", "remove", "r"));
        for (Tgt t : targets) {
            if (remove) {
                SkillActor a = holder(t, false, ctx);
                // killowner: la entidad de debajo muere también (nunca un jugador)
                if (SkillRuntime.bool(m.arg("false", "killowner", "ko")) && t.entity() instanceof LivingEntity owner
                        && !(owner instanceof net.minecraft.world.entity.player.Player)) {
                    SkillMobs.Host h = SkillMobs.host(owner);
                    if (h != null) h.suicide();
                    else owner.kill();
                }
                if (a == null) continue;
                if (a.follow != null) {
                    a.remove();
                    DISGUISES.remove(a.follow.getUUID());
                } else {
                    a.me = null;
                    a.prop("me", "", null, null);
                }
                continue;
            }
            if (mid == null) continue;
            boolean isNew = t.who != null && t.who.actor == null && t.who.entity != null && holder(t, false, ctx) == null;
            SkillActor a = holder(t, true, ctx);
            if (a == null) continue;
            a.me = mid;
            a.anim = null;
            // Como el cliente (y ModelEngine): si el modelo tiene animación de aparecer, empieza con ella (para que
            // @modelpart vea los huesos donde se ven)
            VfxModel vm = load(mid);
            if (a.layers != null) {
                a.layers.clear();
                if (vm != null) a.layers.playSpawn(vm, a.age);
            } else if (vm != null && vm.anims.containsKey("spawn")) {
                a.anim = "spawn";
                a.animStart = a.age;
                a.animSpeed = 1F;
            }
            // En ModelEngine «s» es step (escalón), no la escala: en los jefes solo «scale»; las clases, como siempre
            float sc = (float) SkillRuntime.num(ctx.host != null ? m.arg("1", "scale", "size") : m.arg("1", "scale", "s", "size"), 1);
            if (sc > 0 && sc != a.scale) {
                a.scale = sc;
                if (!isNew) a.prop("scale", String.valueOf(sc), null, null);
            }
            if (isNew) {
                SkillNet.near(a.level, a.pos, a.spawnMessage());
            } else {
                a.prop("me", mid, null, null);
            }
            if (ctx.host != null) modelOptions(m, a, t, vm);
        }
    }

    /**
     * Lo demás de model{} de ModelEngine en un mob de jefe: damagetint (parpadea en rojo al recibir daño, sí por
     * defecto), nametag (el hueso tag_ donde sale su nombre), lockpitch/lockyaw (la cabeza no sigue la mirada) y step
     * (cuánto sube andando).
     */
    private static void modelOptions(Mech m, SkillActor a, Tgt t, VfxModel vm) {
        a.prop("dtint", SkillRuntime.bool(m.arg("true", "damagetint", "d", "tint")) ? "1" : "0", null, null);
        // Lo que ve el cliente: hasta dónde (viewrange, radius), el fuego (renderfire, forcefire), la sombra (showshadow)
        // y si se ve al jugador disfrazado él mismo (seeself)
        double view = SkillRuntime.num(m.arg("0", "viewrange", "vr"), 0), radius = SkillRuntime.num(m.arg("0", "radius", "rad"), 0);
        double far = Math.min(view > 0 ? view : Double.MAX_VALUE, radius > 0 ? radius : Double.MAX_VALUE);
        a.prop("view", far == Double.MAX_VALUE ? "0" : String.valueOf(far), null, null);
        a.prop("fire", (SkillRuntime.bool(m.arg("true", "renderfire", "rf")) ? "1" : "0") + "|"
                + (SkillRuntime.bool(m.arg("false", "forcefire", "ff")) ? "1" : "0"), null, null);
        a.prop("shadow", SkillRuntime.bool(m.arg("true", "showshadow")) ? "1" : "0", null, null);
        a.prop("seeself", SkillRuntime.bool(m.arg("true", "seeself", "vs", "see")) ? "1" : "0", null, null);
        // model{rootbone}: el hueso raíz de rootmotion
        String root = m.arg(null, "rootbone", "root", "rt");
        if (root != null) a.prop("root", root, "NaN|NaN|", null);
        String tag = m.arg(null, "nametag", "n", "name");
        Entity e = t.entity();
        SkillMobs.Host h = e == null ? null : SkillMobs.host(e);
        if (tag != null && vm != null && vm.bone(tag) >= 0 && h != null) {
            String name = h.def().display != null ? h.def().display : h.def().name;
            a.prop("btag", tag, "1|0|1|-1|-1\u0001" + name.replace('&', '\u00a7'), "1");
        }
        String lp = m.arg(null, "lockpitch", "lp", "lpitch"), ly = m.arg(null, "lockyaw", "ly", "lyaw");
        if (lp != null || ly != null) {
            a.prop("headlock", lp == null ? "" : SkillRuntime.bool(lp) ? "1" : "0", ly == null ? "" : SkillRuntime.bool(ly) ? "1" : "0", null);
        }
        String step = m.arg(null, "step", "s");
        if (step != null && e instanceof LivingEntity le) le.setMaxUpStep((float) Math.max(0, SkillRuntime.num(step, 0.6)));
        // La caja de la entidad: la del hueso «hitbox» del modelo o la de su tipo (hitbox=false), a la escala de
        // hitboxscale (sin ella, la de scale), y si se le puede dar (showhitbox); syncbody: el cuerpo sigue a la cabeza
        if (h != null) {
            h.setModelHitbox(SkillRuntime.bool(m.arg("true", "hitbox", "h")));
            String hbs = m.arg(null, "hitboxscale", "hbs");
            h.setHitboxScale((float) Math.max(0.01, SkillRuntime.num(hbs != null ? hbs : m.arg("1", "scale", "size"), 1)));
            h.setShowHitbox(SkillRuntime.bool(m.arg("true", "showhitbox")));
            h.setSyncBody(SkillRuntime.bool(m.arg("true", "syncbody")));
        }
    }

    static void state(Mech m, Ctx ctx, List<Tgt> targets) {
        String s = m.arg(null, "state", "s", "animation", "a");
        if (s == null) return;
        // Con sus marcadores, como MythicMobs: «hit_<random.1to3>» es hit_1, hit_2 o hit_3 cada vez
        if (s.indexOf('<') >= 0) s = SkillVars.text(s, ctx, Tgt.of(ctx.caster));
        boolean remove = SkillRuntime.bool(m.arg("false", "remove", "r"));
        float speed = (float) SkillRuntime.num(m.arg("1", "speed", "sp"), 1);
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null || a.me == null) continue;
            if (a.layers != null) {
                // Como ModelEngine 4: una animación por prioridad (la nueva sustituye a la de esa prioridad), con entrada y salida suaves
                VfxModel vm = modelOf(a);
                if (vm == null) continue;
                // Con el tempo del jefe (sus retardos se dividen igual) y, si encadena un ataque, entrando suave
                double tempo = SkillRuntime.tempo(ctx);
                float sp = speed * (float) tempo;
                int li = (int) Math.round(SkillRuntime.num(m.arg("0", "lerpin", "li"), 0) / tempo);
                int lo = (int) Math.round(SkillRuntime.num(m.arg("0", "lerpout", "lo"), 0) / tempo);
                if (ctx.host != null) li = Math.max(li, ctx.host.minLerp());
                int prio = (int) SkillRuntime.num(m.arg("1", "priority", "p"), 1);
                boolean force = SkillRuntime.bool(m.arg("false", "force", "f"));
                boolean instant = SkillRuntime.bool(m.arg("false", "ignorelerp", "i"));
                if (remove) {
                    a.layers.stop(s, a.age, instant ? 0 : m.a.containsKey("lo") || m.a.containsKey("lerpout") ? lo : -1);
                    a.prop("lstop", s, instant ? "0" : m.a.containsKey("lo") || m.a.containsKey("lerpout") ? String.valueOf(lo) : "-1", null);
                } else if (a.layers.play(vm, s, a.age, sp, li, lo, prio, force)) {
                    a.prop("lstate", s, sp + "|" + li + "|" + lo + "|" + prio + "|" + (force ? 1 : 0), "0");
                }
                continue;
            }
            if (remove) {
                if (s.equalsIgnoreCase(a.anim)) a.anim = null;
                a.prop("stop", s, null, null);
            } else {
                a.anim = s;
                a.animStart = a.age;
                a.animSpeed = speed;
                a.prop("state", s, String.valueOf(speed), null);
            }
        }
    }

    /** defaultstate{t=idle|walk;s=...}: la animación de reposo o de andar de ahora en adelante (la estatua de la Gárgola). */
    static void defaultState(Mech m, Ctx ctx, List<Tgt> targets) {
        String type = m.arg("idle", "type", "t");
        String st = m.arg(null, "state", "s");
        if (st == null) return;
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null || a.layers == null) continue;
            a.layers.setDefault(type, st, a.age);
            a.prop("dstate", type.toLowerCase(Locale.ROOT), st, null);
        }
    }

    /**
     * brightness{b;s;p=pieza;em=false}: la luz con la que se ve el modelo (0-15 de bloque y de cielo) o una pieza (y las
     * que la llevan en el nombre si em=false); b=-1 vuelve a la luz del sitio. Los jefes parpadean así al recibir golpes y
     * sus estelas y runas brillan.
     */
    static void brightness(Mech m, Ctx ctx, List<Tgt> targets) {
        int b = (int) SkillRuntime.num(m.arg("15", "block", "b"), 15);
        int sky = (int) SkillRuntime.num(m.arg(String.valueOf(b), "sky", "s"), b);
        String part = m.arg("", "partid", "pid", "part", "p");
        boolean exact = SkillRuntime.bool(m.arg("true", "exactmatch", "em"));
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null) continue;
            a.prop("bright", part, b + "|" + sky, exact ? "1" : "0");
        }
    }

    /**
     * segment{p;bounded;anglelimit;x;y;z;minx..maxz;extendrate;elasticity;em} de ModelEngine: cómo se mueven sus colas y
     * segmentos (lo hace el cliente: VfxPhysics). Solo se manda lo que pone la línea (lo demás se queda como estaba).
     */
    static void segment(Mech m, Ctx ctx, List<Tgt> targets) {
        String part = m.arg("", "partid", "pid", "part", "p");
        boolean exact = SkillRuntime.bool(m.arg("true", "exactmatch", "em", "exact", "match"));
        StringBuilder cfg = new StringBuilder();
        String b = m.arg(null, "bounded", "b", "bd");
        if (b != null) cfg.append("bounded=").append(SkillRuntime.bool(b) ? 1 : 0).append(';');
        String[][] keys = {{"angle", "anglelimit", "a", "al", "angle"}, {"x", "x"}, {"y", "y"}, {"minx", "minx"}, {"maxx", "maxx"},
                {"miny", "miny"}, {"maxy", "maxy"}, {"extend", "extendrate", "e", "er", "extend"}, {"elastic", "elasticity", "el"}};
        for (String[] k : keys) {
            String v = m.arg(null, java.util.Arrays.copyOfRange(k, 1, k.length));
            if (v != null) cfg.append(k[0]).append('=').append(SkillRuntime.num(v, 0)).append(';');
        }
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("seg", part, cfg.toString(), exact ? "1" : "0");
        }
    }

    /** remapmodel{m;n}: el modelo se ve con las piezas del mismo nombre de otro (el parpadeo del escudo roto). */
    static void remap(Mech m, Ctx ctx, List<Tgt> targets) {
        String to = m.arg(null, "newmodelid", "n", "nm", "newmodel");
        if (to == null) return;
        String nm = to.contains(".") ? to : ctx.cls.id + "." + to.toLowerCase(Locale.ROOT);
        // Un modelo que no existe (una variable sin poner: «<global.var.skin<caster.uuid>>» de los NPC de escenas, una
        // errata): ModelEngine no hace nada
        if (load(nm) == null) return;
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("remap", nm, null, null);
        }
    }

    /**
     * displaytransformation{action=set|add;transformation=right_rotation|left_rotation|translation|scale;value|val}: el
     * display gira o se mueve (1.3.89: también «val», como lo escribe el pack de Ignevar).
     */
    static void displayTransform(Mech m, Ctx ctx, List<Tgt> targets) {
        String action = m.arg("set", "action", "a").toLowerCase(Locale.ROOT);
        String tr = m.arg("translation", "transformation", "t").toLowerCase(Locale.ROOT);
        String value = SkillRuntime.unquote(m.arg("0", "value", "val", "v"));
        for (Tgt t : targets) {
            SkillActor a = t.who == null ? null : t.who.actor;
            if (a != null) a.prop("dt", action + "|" + tr, value, null);
        }
    }

    /** setmodelscale{scale}: el modelo más grande o más pequeño. */
    static void scale(Mech m, Ctx ctx, List<Tgt> targets) {
        float sc = (float) SkillRuntime.num(m.arg("1", "scale", "s", "amount", "a"), 1);
        if (sc <= 0) return;
        // scale{hitbox=true} de ModelEngine: la caja de golpe de la entidad, no lo que se ve
        if (SkillRuntime.bool(m.arg("false", "hitbox", "h"))) {
            for (Tgt t : targets) {
                SkillMobs.Host h = t.entity() == null ? null : SkillMobs.host(t.entity());
                if (h != null) h.setHitboxScale(sc);
            }
            return;
        }
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null) continue;
            a.scale = sc;
            a.prop("scale", String.valueOf(sc), null, null);
        }
    }

    /**
     * mountmodel @owner (desde el efecto): el jugador se monta en el modelo (el caballo del Invocador). El modelo va
     * con él (anda el jugador) y al jugador se le dibuja subido encima.
     */
    static void mount(Mech m, Ctx ctx, List<Tgt> targets) {
        SkillActor a = ctx.caster.actor;
        if (a == null) return;
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null) continue;
            a.rider = e;
            a.follow = e;
            a.hideHost = false;
            a.prop("mount", String.valueOf(e.getId()), "1.0", null);
            break;
        }
    }

    static void dismount(Ctx ctx, List<Tgt> targets) {
        for (Tgt t : targets) {
            Entity e = t.entity();
            if (e == null) continue;
            SkillActor a = mountedBy(e);
            if (a == null) continue;
            a.rider = null;
            a.follow = null;
            a.prop("mount", "", null, null);
        }
    }

    /** El efecto en el que va montado alguien (null si no). */
    static SkillActor mountedBy(Entity e) {
        for (SkillActor a : SkillRuntime.ACTORS) if (a.alive && a.rider == e) return a;
        return null;
    }

    static void unmounted(SkillActor a) {
        a.rider = null;
    }

    static void changePart(Mech m, Ctx ctx, List<Tgt> targets) {
        String part = m.arg(null, "partid", "pid", "part", "p");
        String newModel = m.arg(null, "newmodelid", "nmid", "nm", "newmodel");
        String newPart = m.arg(part, "newpartid", "npid", "np", "newpart");
        if (part == null || newModel == null) return;
        // El otro modelo, con el mismo nombre que en el pack
        String nm = newModel.contains(".") ? newModel : ctx.cls.id + "." + newModel.toLowerCase(Locale.ROOT);
        // Sin ese modelo o sin ese hueso en él (los cosméticos guardados de un NPC de escena que no los lleva): ModelEngine
        // no hace nada
        VfxModel nmod = load(nm);
        if (nmod == null || nmod.bone(newPart) < 0) return;
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("part", part, nm, newPart);
        }
    }

    static void partVis(Mech m, Ctx ctx, List<Tgt> targets) {
        String part = m.arg(null, "partid", "pid", "part", "p");
        if (part == null) return;
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a == null) continue;
            if (a.layers == null) {
                // Las clases, como siempre (sus packs se probaron así)
                boolean visible = SkillRuntime.bool(m.arg("true", "visible", "v", "visibility"));
                boolean child = SkillRuntime.bool(m.arg("true", "child", "c", "children"));
                a.prop("vis", part, String.valueOf(visible), String.valueOf(child));
                continue;
            }
            // Los jefes, como ModelEngine: sin «v» ESCONDE (v=false), sin «child» solo esa pieza y con em=false todas las
            // que llevan ese nombre. Antes salía al revés: el Esqueleto de la Torre enseñaba la espada de su muerte junto
            // a la pierna, la espada de la espalda y las almas de la mano, que el pack esconde al aparecer
            boolean visible = SkillRuntime.bool(m.arg("false", "visibility", "visible", "v"));
            boolean child = SkillRuntime.bool(m.arg("false", "child", "c", "ch", "children"));
            boolean exact = SkillRuntime.bool(m.arg("true", "exactmatch", "em", "exact", "match"));
            VfxModel vm = exact ? null : modelOf(a);
            if (vm == null) {
                a.prop("vis", part, String.valueOf(visible), String.valueOf(child));
                continue;
            }
            String want = part.toLowerCase(Locale.ROOT);
            for (VfxModel.Bone b : vm.bones) {
                if (b.hitbox == null && b.id.toLowerCase(Locale.ROOT).contains(want)) {
                    a.prop("vis", b.id, String.valueOf(visible), String.valueOf(child));
                }
            }
        }
    }

    /** Las animaciones por defecto del modelo de un jefe ({reposo, andar}), o null. */
    static String[] defaults(java.util.UUID mob) {
        SkillActor a = disguise(mob);
        return a == null || a.layers == null ? null : new String[] {a.layers.idleName(), a.layers.walkName()};
    }

    /** El instante del modelo de un jefe (para saber luego qué animaciones puso un ataque); NaN si no tiene. */
    static float mark(java.util.UUID mob) {
        SkillActor a = disguise(mob);
        return a == null || a.layers == null ? Float.NaN : a.age;
    }

    /**
     * Quita, entrando la siguiente suave, las animaciones que puso un ataque desde «since» (al encadenar otro: si el
     * nuevo usa otra prioridad, la del cortado seguiría encima).
     */
    static void cutSince(java.util.UUID mob, float since, int lerp) {
        SkillActor a = disguise(mob);
        if (a == null || a.layers == null || Float.isNaN(since)) return;
        for (VfxLayers.Layer l : new java.util.ArrayList<>(a.layers.active())) {
            if (l.start < since || l.stopAt >= 0 || l.priority == Integer.MAX_VALUE) continue;
            a.layers.stop(l.name, a.age, lerp);
            a.prop("lstop", l.name, String.valueOf(lerp), null);
        }
    }

    /** Vuelve a poner sus animaciones por defecto (al cortar un ataque que las había cambiado: combos). */
    static void restoreDefaults(java.util.UUID mob, String[] d) {
        SkillActor a = disguise(mob);
        if (a == null || a.layers == null || d == null) return;
        if (!d[0].equals(a.layers.idleName())) {
            a.layers.setDefault("idle", d[0], a.age);
            a.prop("dstate", "idle", d[0], null);
        }
        if (!d[1].equals(a.layers.walkName())) {
            a.layers.setDefault("walk", d[1], a.age);
            a.prop("dstate", "walk", d[1], null);
        }
    }

    static void tint(Mech m, Ctx ctx, List<Tgt> targets) {
        String color = m.arg("#ffffff", "color", "c");
        for (Tgt t : targets) {
            SkillActor a = holder(t, false, ctx);
            if (a != null) a.prop("tint", color, null, null);
        }
    }

    // ------------------------------------------------------------------------------------------- huesos

    /**
     * El modelo de un actor tal como está ahora: el suyo con los changeparent/submodel que le hayan hecho (VfxModel.derive;
     * el mismo objeto mientras no cambien), o null.
     */
    static VfxModel modelOf(SkillActor a) {
        if (a == null || a.me == null) return null;
        VfxModel base = load(a.me);
        if (base == null || a.edits.isEmpty()) {
            a.edited = null;
            a.editedKey = null;
            return base;
        }
        String key = a.me + "\n" + String.join("\n", a.edits);
        if (!key.equals(a.editedKey)) {
            VfxModel old = a.edited != null ? a.edited : base;
            a.edited = base.derive(a.edits);
            a.editedKey = key;
            if (a.layers != null) a.layers.rebind(old, a.edited);
        }
        return a.edited;
    }

    /**
     * swapentity{hitbox;invisible;step} @objetivo: el modelo pasa de quien lo lleva a esa entidad (con sus animaciones y
     * todo lo demás). invisible: esconde la entidad de debajo (a un jugador, nunca).
     */
    static void swap(SkillActor a, Entity to, boolean invisible, double step) {
        if (a == null || to == null || a.follow == null || to == a.follow) return;
        DISGUISES.remove(a.follow.getUUID());
        SkillActor other = DISGUISES.remove(to.getUUID());
        if (other != null && other != a) other.remove();
        a.follow = to;
        a.hideHost = invisible && !(to instanceof net.minecraft.world.entity.player.Player);
        DISGUISES.put(to.getUUID(), a);
        if (to instanceof LivingEntity le && step >= 0) le.setMaxUpStep((float) step);
        a.prop("follow", String.valueOf(to.getId()), a.hideHost ? "1" : "0", null);
    }

    /**
     * petrify: una estatua del modelo tal como está ahora (su pose, sus piezas, colores y brillo), que ya no cambia ni se
     * mueve. Se queda donde estaba (como un efecto: se va al minuto, como los que nadie quita).
     */
    static void petrify(SkillActor a) {
        VfxModel vm = modelOf(a);
        if (vm == null || a.layers == null) return;
        SkillActor st = new SkillActor(a.cls, null, a.level, a.pos, a.yaw, 0F, a.owner);
        st.host = a.host;
        st.me = a.me;
        st.scale = a.scale;
        st.edits.addAll(a.edits);
        st.layers = a.layers.copy(a.age);
        st.layers.freeze(0F);
        SkillRuntime.ACTORS.add(st);
        SkillNet.near(st.level, st.pos, st.spawnMessage());
        st.prop("lclear", "", null, null);
        // Lo que se ve de él (sus piezas, colores, brillos...), pero no a quién sigue ni lo de moverse
        for (SkillNet.Prop p : new java.util.ArrayList<>(a.sticky.values())) {
            if (p.key().equals("me") || p.key().equals("follow") || p.key().equals("headtgt") || p.key().equals("iktgt")) continue;
            st.prop(p.key(), p.a(), p.b(), p.c());
        }
        boolean[] hidden = a.hiddenNow(vm);
        if (hidden != null) {
            for (int i = 0; i < hidden.length; i++) {
                if (hidden[i] != vm.bones[i].hidden) st.prop("vis", vm.bones[i].id, String.valueOf(!hidden[i]), "false");
            }
        }
        for (VfxLayers.Layer l : st.layers.active()) {
            if (l.stopAt >= 0) continue;
            st.prop("lstate", l.name, l.speed + "|" + l.lerpIn + "|" + l.lerpOut + "|" + l.priority + "|1", String.valueOf((int) (st.age - l.start)));
        }
        st.prop("dstate", "idle", st.layers.idleName(), null);
        st.prop("dstate", "walk", st.layers.walkName(), null);
        if (st.layers.dead()) st.prop("death", "", "", "");
        st.prop("freeze", "0", null, null);
    }

    /**
     * rootmotion (cada tick del mob, en el servidor): la entidad anda lo que la animación ha movido su hueso raíz desde el
     * tick anterior (en el suelo; con la misma animación: si cambia, vuelve a contar desde ahí), con su peso.
     */
    static void rootStep(SkillMobs.Host h) {
        SkillActor a = disguise(h.mob().getUUID());
        if (a == null || a.layers == null || !a.layers.rootActive()) {
            if (a != null) a.rootLastKey = null;
            return;
        }
        VfxModel vm = modelOf(a);
        if (vm == null) return;
        pose(a, vm);
        float[] now = a.layers.rootOffset();
        String key = a.layers.rootKey();
        if (key.equals(a.rootLastKey)) {
            float w = a.layers.rootWeight();
            org.joml.Vector3f d = new org.joml.Vector3f(now[0] - a.rootLast[0], 0, now[2] - a.rootLast[2]);
            if (w > 0 && d.lengthSquared() > 1e-8F) {
                // Del espacio del modelo (píxeles) al mundo: su giro y su escala
                new Matrix4f().rotate(Axis.YP.rotationDegrees(180F - a.yaw)).scale(a.scale / 16F).transformDirection(d);
                h.mob().move(net.minecraft.world.entity.MoverType.SELF, new Vec3(d.x * w, 0, d.z * w));
            }
        }
        System.arraycopy(now, 0, a.rootLast, 0, 3);
        a.rootLastKey = key;
    }

    static VfxModel load(String id) {
        if (id == null) return null;
        return MODELS.computeIfAbsent(id, k -> {
            try (InputStream in = SkillModels.class.getResourceAsStream("/assets/tfclient/" + SkillDefs.modelDir(k) + k + ".json")) {
                return in == null ? Optional.empty() : Optional.of(VfxModel.read(in));
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Skills: no se pudo leer el modelo {}", k, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * @modelpart{pid=hueso;offset=LOCAL|GLOBAL;x;y;z;em}: dónde está ahora ese hueso del modelo de quien lanza. Con
     * em=false, todos los que lo llevan en el nombre (la cola entera de la Hidra). offset LOCAL: el desplazamiento va con
     * el giro del hueso (dos bloques a lo largo de la hoja de la espada); GLOBAL: en los ejes del mundo.
     */
    static List<Tgt> modelPart(Mech m, Ctx ctx, double yOff) {
        String part = m.targetArg(null, "pid", "p", "partid", "part");
        SkillActor a = holder(Tgt.of(ctx.caster), false, ctx);
        if (a == null || a.me == null || part == null) return List.of(Tgt.at(ctx.caster.pos().add(0, yOff, 0)));
        VfxModel model = modelOf(a);
        if (model == null) return List.of(Tgt.at(ctx.caster.pos().add(0, yOff, 0)));
        boolean exact = SkillRuntime.bool(m.targetArg("true", "exactmatch", "em"));
        java.util.List<Integer> bones = new java.util.ArrayList<>();
        if (exact) {
            int b = model.bone(part);
            if (b >= 0) bones.add(b);
        } else {
            String want = part.toLowerCase(Locale.ROOT);
            for (int i = 0; i < model.bones.length; i++) {
                if (model.bones[i].hitbox == null && model.bones[i].id.toLowerCase(Locale.ROOT).contains(want)) bones.add(i);
            }
        }
        if (bones.isEmpty()) return List.of(); // como ModelEngine: sin ese hueso no hay sitio
        Matrix4f[] mats = pose(a, model);
        // Los desplazamientos x/z y LOCAL, solo en los modelos de los jefes (las clases siguen como estaban)
        boolean boss = a.layers != null;
        double x = boss ? SkillRuntime.num(m.targetArg("0", "x", "xoffset", "xo"), 0) : 0;
        double y = yOff;
        double z = boss ? SkillRuntime.num(m.targetArg("0", "z", "zoffset", "zo"), 0) : 0;
        boolean local = boss && m.targetArg("GLOBAL", "offset", "o").equalsIgnoreCase("LOCAL");
        Matrix4f w = world(a);
        List<Tgt> out = new java.util.ArrayList<>();
        for (int b : bones) {
            Matrix4f bw = new Matrix4f(w).mul(mats[b]);
            Vector4f p = bw.transform(new Vector4f(0, 0, 0, 1));
            Vec3 at = new Vec3(p.x, p.y, p.z);
            if (local && (x != 0 || y != 0 || z != 0)) {
                // En los ejes del hueso (sin su escala): x, y, z en bloques
                org.joml.Vector3f d = new org.joml.Vector3f((float) x, (float) y, (float) z);
                org.joml.Matrix3f rot = new org.joml.Matrix3f(bw).normal();
                rot.transform(d);
                if (d.lengthSquared() > 1e-9) d.normalize((float) Math.sqrt(x * x + y * y + z * z));
                at = at.add(d.x, d.y, d.z);
            } else {
                at = at.add(x, y, z);
            }
            out.add(Tgt.at(at));
        }
        return out;
    }

    /** Los huesos que ha escondido partvis (según lo último que se mandó de cada uno): para sus cajas de golpe. */
    static boolean[] hiddenBones(SkillActor a, VfxModel model) {
        boolean[] hidden = new boolean[model.bones.length];
        if (a.layers != null) {
            // Un jefe: lo que tiene escondido de verdad (como SkillClient.hiddenMask: las cajas ob_ cuentan por su padre)
            boolean[] now = a.hiddenNow(model);
            if (now != null) for (int i = 0; i < hidden.length; i++) hidden[i] = now[i] && model.bones[i].hitbox == null;
            return hidden;
        }
        for (SkillNet.Prop p : a.sticky.values()) {
            if (!p.key().equals("vis") || !"false".equals(p.b())) continue;
            int b = model.bone(p.a());
            if (b >= 0) hidden[b] = true;
        }
        return hidden;
    }

    /** Del espacio del modelo (píxeles) al mundo: donde está el actor, su giro y su escala. */
    static Matrix4f world(SkillActor a) {
        return new Matrix4f().translate((float) a.pos.x, (float) a.pos.y, (float) a.pos.z)
                .rotate(Axis.YP.rotationDegrees(180F - a.yaw)).scale((a.layers != null ? a.scale : 1F) / 16F);
    }

    /** La pose de ahora de todos los huesos del modelo de un actor (con sus capas y hacia dónde mira, si las tiene). */
    static Matrix4f[] pose(SkillActor a, VfxModel model) {
        Matrix4f[] mats = new Matrix4f[model.bones.length];
        for (int i = 0; i < mats.length; i++) mats[i] = new Matrix4f();
        if (a.layers != null) {
            // Como el cliente (SkillClient.Actor.pose y renderModel): hacia dónde miran sus cabezas (headtarget,
            // lockmodelhead, su velocidad), sus capas y luego la IK y la física (colas que se arrastran, jig_)
            net.tierrasfantasticas.tfclient.vfx.VfxRig rig = a.rig(model);
            float headYaw = 0F, headPitch = 0F, body = a.yaw;
            org.joml.Vector3f eye = null;
            if (a.follow instanceof LivingEntity le) {
                body = le.yBodyRot;
                headYaw = net.minecraft.util.Mth.wrapDegrees(le.yHeadRot - le.yBodyRot);
                headPitch = le.getXRot();
                eye = new org.joml.Vector3f((float) le.getX(), (float) le.getEyeY(), (float) le.getZ());
            }
            net.tierrasfantasticas.tfclient.vfx.VfxRig.Entities ents = id -> {
                Entity e = a.level.getEntity(id);
                return e == null ? null : new float[] {(float) e.getX(), (float) e.getY(), (float) e.getZ(), e.getBbHeight()};
            };
            float[] look = rig.look(headYaw, headPitch, body, eye, ents, a.age);
            a.layers.pose(model, a.age, look[0], look[1], mats, rig.limits());
            rig.bend(mats, world(a), a.age, b -> a.layers.moves(model, b, a.age), ents);
            return mats;
        }
        VfxModel.Anim anim = a.anim == null ? null : model.anims.get(a.anim);
        float secs = (a.age - a.animStart) / 20F * a.animSpeed;
        // Una animación de una vez que ya acabó vuelve a la de reposo (como en el cliente)
        if (anim != null && anim.loop == 0 && secs > anim.length + 0.05F) {
            secs = (secs - anim.length) / Math.max(0.01F, a.animSpeed);
            anim = null;
        }
        if (anim == null) anim = model.anims.get("idle");
        VfxPose.compute(model, anim, secs, 0F, mats);
        return mats;
    }

    static void forget(UUID player) {
        SkillActor a = DISGUISES.remove(player);
        if (a != null) a.remove();
    }

    static void clear() {
        DISGUISES.clear();
    }

    /** El disfraz de un jugador (para mandárselo a quien llega), o null. */
    static SkillActor disguise(UUID player) {
        SkillActor a = DISGUISES.get(player);
        return a != null && a.alive ? a : null;
    }
}
