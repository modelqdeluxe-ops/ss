package net.tierrasfantasticas.tfclient.npcs;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.bosses.BossEntity;
import net.tierrasfantasticas.tfclient.bosses.Difficulty;
import net.tierrasfantasticas.tfclient.bosses.TFBosses;
import net.tierrasfantasticas.tfclient.skills.SkillMobs;
import net.tierrasfantasticas.tfclient.skills.SkillSaved;

/**
 * (1.3.101) Los NPC de escenas en el servidor: el panel de control (el objeto que da /tf npc solo a quien lo ejecuta, del
 * staff) coloca NPC de los packs convertidos (tools/npcs/build_npcs.py), los mueve y gira en tiempo real, les pone los
 * cosméticos de los menús de su pack, los duplica o los borra, y pone los pins y las rutas por las que andan. Lo que
 * cambia un NPC se guarda como en el pack (variables globales con su UUID, SkillSaved), así su ~onLoad lo vuelve a
 * poner al cargar el mundo. Todo en tf-client/NPCS.md.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFNpcs {
    /** El panel de control (se registra en TFItems). */
    public static RegistryObject<Item> PANEL;
    /** Rutas de caminante que maneja el panel (las del menú «scene_paths» del pack: path1..path6). */
    public static final int ROUTES = 6;
    /** Nivel de permisos del staff que usa el panel (el de /tf boss). */
    static final int LEVEL = 2;

    /** El NPC elegido de cada jugador del staff. */
    private static final Map<UUID, UUID> SELECTED = new HashMap<>();

    /** Cosméticos que poner a un NPC recién duplicado cuando ya tenga su modelo (su ~onSpawn lo pone al primer tick). */
    private record Pending(UUID npc, Map<String, String> chosen, long at) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private TFNpcs() {}

    // ------------------------------------------------------------------------------------------- /tf npc

    /** /tf npc (staff): el panel de control de NPC, solo a quien lo ejecuta. */
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("npc").requires(s -> s.hasPermission(LEVEL)).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            ItemStack panel = new ItemStack(PANEL.get());
            if (!p.getInventory().add(panel)) p.drop(panel, false);
            int packs = NpcCatalog.packs().size();
            ctx.getSource().sendSuccess(() -> Component.literal(packs == 0
                    ? "Panel de NPC entregado, pero este TF Client no trae NPC de escenas."
                    : "Panel de NPC entregado (" + packs + " packs). Clic derecho para abrirlo; clic derecho a un NPC para elegirlo.")
                    .withStyle(ChatFormatting.GOLD), false);
            return 1;
        });
    }

    static boolean holding(Player p) {
        return PANEL != null && (p.getMainHandItem().is(PANEL.get()) || p.getOffhandItem().is(PANEL.get()));
    }

    /**
     * Clic derecho a un NPC con el panel en la mano: lo elige y abre el panel (no salta su ~onInteract). true = era eso.
     * Lo llama BossEntity.mobInteract en los dos lados.
     */
    public static boolean panelInteract(Player player, BossEntity npc) {
        if (!holding(player)) return false;
        if (player instanceof ServerPlayer sp && sp.hasPermissions(LEVEL)) {
            SELECTED.put(sp.getUUID(), npc.getUUID());
            send(sp, "Elegido: " + npcName(npc), true);
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------- lo que pulsa el staff

    public static void act(ServerPlayer p, NpcNet.Act m) {
        if (!p.hasPermissions(LEVEL)) return;
        ServerLevel level = p.serverLevel();
        BossEntity sel = selected(p);
        String notice = "";
        switch (m.op()) {
            case NpcNet.PLACE -> {
                NpcCatalog.Pack pk = NpcCatalog.pack(m.a());
                NpcCatalog.Npc n = pk == null ? null : pk.npc(m.b());
                if (n == null) {
                    notice = "Ese NPC no está en este TF Client";
                    break;
                }
                Vec3 at = m.x() == 0 ? looked(p) : null;
                if (at == null) at = p.position();
                BossEntity e = spawn(level, pk, n, at, p.getYRot() + 180F);
                if (e != null) {
                    SELECTED.put(p.getUUID(), e.getUUID());
                    // La plantilla «S» del pack, no: una piel de verdad (cuando ya tenga su modelo)
                    Map<String, String> skin = autoskin(e, n);
                    if (!skin.isEmpty()) PENDING.add(new Pending(e.getUUID(), skin, level.getGameTime() + 25));
                    notice = "Colocado: " + n.name();
                }
            }
            case NpcNet.SELECT -> {
                Entity e = level.getEntity(m.id());
                if (e instanceof BossEntity b && b.isNpc()) {
                    SELECTED.put(p.getUUID(), b.getUUID());
                    notice = "Elegido: " + npcName(b);
                }
            }
            case NpcNet.SELECT_NEAR -> {
                List<BossEntity> near = near(p, 32);
                if (near.isEmpty()) {
                    notice = "No hay NPC a 32 bloques";
                } else {
                    int i = 0;
                    if (m.x() != 0 && sel != null) i = (near.indexOf(sel) + 1) % near.size();
                    SELECTED.put(p.getUUID(), near.get(i).getUUID());
                    notice = "Elegido: " + npcName(near.get(i));
                }
            }
            case NpcNet.DESELECT -> SELECTED.remove(p.getUUID());
            case NpcNet.MOVE -> {
                if (sel == null) break;
                // Respecto a como lo ve el staff: x = a su derecha, y = arriba, z = alejándose (al eje más cercano)
                Vec3 fwd = axis(p);
                Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
                Vec3 d = right.scale(m.x()).add(0, m.y(), 0).add(fwd.scale(m.z()));
                // Subirlo o bajarlo: se queda a esa altura (sin caer), como un NPC sentado en su silla
                if (m.y() != 0) sel.setNoGravity(true);
                Vec3 to = sel.position().add(d);
                sel.teleportTo(to.x, to.y, to.z);
                sel.setDeltaMovement(Vec3.ZERO);
            }
            case NpcNet.MOVE_TO -> {
                if (sel == null) break;
                Vec3 to = m.x() == 0 ? looked(p) : p.position();
                if (to == null) {
                    notice = "No miras a ningún bloque";
                    break;
                }
                sel.setNoGravity(false);
                sel.teleportTo(to.x, to.y, to.z);
                sel.setDeltaMovement(Vec3.ZERO);
            }
            case NpcNet.ROTATE -> {
                if (sel != null) rotate(sel, m.y() != 0 ? (float) m.x() : sel.getYRot() + (float) m.x());
            }
            case NpcNet.FACE_ME -> {
                if (sel == null) break;
                Vec3 d = p.position().subtract(sel.position());
                if (d.x * d.x + d.z * d.z > 1e-6) rotate(sel, (float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            }
            case NpcNet.OPTION -> {
                if (sel == null) break;
                NpcCatalog.Pack pk = NpcCatalog.pack(sel.bossId());
                NpcCatalog.Npc n = pk == null ? null : pk.npc(sel.mobName());
                NpcCatalog.Option o = pk == null ? null : pk.option(m.a(), m.b());
                if (n == null || o == null) break;
                apply(sel, n, m.a(), o);
                // remapmodel deja cada hueso como en la piel (fuera los cosméticos): se vuelven a poner los demás
                if (m.a().startsWith("skin")) {
                    Map<String, String> ch = chosen(sel.getUUID());
                    for (String g : NpcCatalog.GROUP_ORDER) {
                        if (g.equals(m.a()) || !ch.containsKey(g) || g.equals("skin")) continue;
                        NpcCatalog.Option other = pk.option(g, ch.get(g));
                        if (other != null) apply(sel, n, g, other);
                    }
                }
                notice = NpcCatalog.GROUPS.getOrDefault(m.a(), m.a()) + ": " + o.name();
            }
            case NpcNet.RESET -> {
                if (sel == null) break;
                notice = reset(sel) ? "Cosméticos quitados" : "Este NPC no tiene cosméticos que quitar";
            }
            case NpcNet.DELETE -> {
                if (sel == null) break;
                notice = "Borrado: " + npcName(sel);
                forget(sel.getUUID());
                sel.discard();
                SELECTED.remove(p.getUUID());
            }
            case NpcNet.DUPLICATE -> {
                if (sel == null) break;
                NpcCatalog.Pack pk = NpcCatalog.pack(sel.bossId());
                NpcCatalog.Npc n = pk == null ? null : pk.npc(sel.mobName());
                if (n == null) break;
                Vec3 fwd = axis(p);
                Vec3 at = sel.position().add(-fwd.z, 0, fwd.x);
                BossEntity copy = spawn(level, pk, n, at, sel.getYRot());
                if (copy == null) break;
                copy.setNoGravity(sel.isNoGravity());
                Map<String, String> ch = chosen(sel.getUUID());
                PENDING.add(new Pending(copy.getUUID(), ch.isEmpty() ? autoskin(copy, n) : ch, level.getGameTime() + 25));
                SELECTED.put(p.getUUID(), copy.getUUID());
                notice = "Duplicado: " + n.name();
            }
            case NpcNet.PIN_SET -> {
                if (m.a().isBlank()) break;
                SkillSaved.setPin(m.a(), level, feet(p));
                notice = "Pin «" + m.a() + "» aquí";
            }
            case NpcNet.PIN_CLEAR -> {
                SkillSaved.removePin(m.a());
                notice = "Pin «" + m.a() + "» quitado";
            }
            case NpcNet.ROUTE_ADD, NpcNet.ROUTE_UNDO, NpcNet.ROUTE_CLEAR -> {
                int r = (int) m.x();
                if (r < 1 || r > ROUTES) break;
                List<Vec3> pts = route(r);
                if (m.op() == NpcNet.ROUTE_ADD) pts.add(feet(p));
                else if (m.op() == NpcNet.ROUTE_UNDO && !pts.isEmpty()) pts.remove(pts.size() - 1);
                else if (m.op() == NpcNet.ROUTE_CLEAR) pts.clear();
                setRoute(r, pts);
                notice = "Ruta " + r + ": " + pts.size() + (pts.size() == 1 ? " punto" : " puntos");
            }
            case NpcNet.ROUTE_ASSIGN -> {
                int r = (int) m.x();
                if (sel == null || r < 1 || r > ROUTES) break;
                if (sel.cls() == null || sel.cls().meta("path" + r) == null) {
                    notice = "Este NPC no anda por rutas";
                } else if (route(r).isEmpty()) {
                    notice = "La ruta " + r + " no tiene puntos";
                } else {
                    // Como la herramienta del pack: postura «stand» y su skill pathN (postura pathN, la variable de su
                    // ruta y al primer punto)
                    SkillMobs.mech(sel, "setstance", Map.of("stance", "stand"), null);
                    SkillMobs.cast(sel, "path" + r, List.of(sel));
                    notice = "Ruta " + r + " para " + npcName(sel);
                }
            }
            default -> { }
        }
        send(p, notice, false);
    }

    /** Un NPC nuevo del pack, mirando hacia «yaw». */
    private static BossEntity spawn(ServerLevel level, NpcCatalog.Pack pk, NpcCatalog.Npc n, Vec3 at, float yaw) {
        BossEntity e = TFBosses.NPC.get().create(level);
        if (e == null) return null;
        e.moveTo(at.x, at.y, at.z, yaw, 0F);
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
        e.configure(pk.id(), n.mob(), Difficulty.NORMAL, 1, null);
        level.addFreshEntity(e);
        return e;
    }

    private static void rotate(BossEntity e, float yaw) {
        float y = net.minecraft.util.Mth.wrapDegrees(yaw);
        e.setYRot(y);
        e.setYBodyRot(y);
        e.setYHeadRot(y);
        e.turn(y, false);
    }

    /** Lo que eligió el menú del pack para ese NPC: los cambios de sus huesos y las variables que lo guardan. */
    private static void apply(BossEntity e, NpcCatalog.Npc n, String group, NpcCatalog.Option o) {
        String own = n.bareModel();
        String id = e.getUUID().toString();
        for (String[] op : o.ops()) {
            if (op[0].equals("part") && op.length >= 4) {
                SkillMobs.mech(e, "changepart", Map.of("m", own, "pid", op[1], "nm", op[2].isEmpty() ? own : op[2], "npid", op[3]), null);
            } else if (op[0].equals("remap") && op.length >= 2) {
                SkillMobs.mech(e, "remapmodel", Map.of("m", own, "n", op[1].isEmpty() ? own : op[1]), null);
            }
        }
        // Como setvariable{var=global.hat<target.uuid>;save=true} del pack: su ~onLoad lo vuelve a poner
        o.vars().forEach((k, v) -> SkillSaved.setGlobal(k + id, v.isEmpty() ? own : v));
        if (!group.equals("reset")) SkillSaved.setGlobal("tfnpc_" + group + id, o.id());
    }

    /**
     * La piel de un NPC nuevo con la plantilla del pack: una de las suyas (catálogo: autoskin), según su UUID (cada uno
     * distinta); en los dúos, otra para el segundo personaje.
     */
    private static Map<String, String> autoskin(BossEntity e, NpcCatalog.Npc n) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> pool = n.autoskin();
        if (pool.isEmpty() || !n.options().containsKey("skin")) return out;
        int i = Math.floorMod(e.getUUID().hashCode(), pool.size());
        out.put("skin", pool.get(i));
        if (n.options().containsKey("skin2")) out.put("skin2", pool.get((i + 3) % pool.size()).replace("piel_", "piel2_"));
        return out;
    }

    /**
     * Un NPC que vuelve del disco, después de su ~onLoad: lo que se le eligió con el panel, en orden (la piel primero).
     * El ~onLoad del pack ya pone su piel y sus cosméticos, pero no la piel del segundo personaje de los dúos.
     */
    public static void restore(BossEntity e) {
        Map<String, String> ch = chosen(e.getUUID());
        if (ch.isEmpty()) return;
        NpcCatalog.Pack pk = NpcCatalog.pack(e.bossId());
        NpcCatalog.Npc n = pk == null ? null : pk.npc(e.mobName());
        if (n == null) return;
        applyAll(e, pk, n, ch);
    }

    private static void applyAll(BossEntity e, NpcCatalog.Pack pk, NpcCatalog.Npc n, Map<String, String> ch) {
        for (String g : NpcCatalog.GROUP_ORDER) {
            NpcCatalog.Option o = ch.containsKey(g) ? pk.option(g, ch.get(g)) : null;
            if (o != null) apply(e, n, g, o);
        }
    }

    /** reset_cosmetics del pack: cada pieza vuelve a la suya. */
    private static boolean reset(BossEntity e) {
        NpcCatalog.Pack pk = NpcCatalog.pack(e.bossId());
        NpcCatalog.Npc n = pk == null ? null : pk.npc(e.mobName());
        List<NpcCatalog.Option> r = pk == null ? List.of() : pk.options().getOrDefault("reset", List.of());
        if (n == null || r.isEmpty()) return false;
        apply(e, n, "reset", r.get(0));
        for (String g : NpcCatalog.GROUP_ORDER) if (!g.equals("skin")) SkillSaved.setGlobal("tfnpc_" + g + e.getUUID(), null);
        return true;
    }

    /** Qué opción de cada grupo lleva ese NPC (grupo → id). */
    static Map<String, String> chosen(UUID npc) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String g : NpcCatalog.GROUP_ORDER) {
            String v = SkillSaved.global("tfnpc_" + g + npc);
            if (v != null) out.put(g, v);
        }
        return out;
    }

    /** Un NPC que se va para siempre: fuera sus variables (las que llevan su UUID). */
    private static void forget(UUID npc) {
        SkillSaved.forgetSuffix(npc.toString());
    }

    // ------------------------------------------------------------------------------------------- rutas

    private static final Pattern POINT = Pattern.compile("\\(\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\)");

    /** Los puntos de la ruta N (la variable global scenes_route_N que lee path_N_follow). */
    static List<Vec3> route(int n) {
        List<Vec3> out = new ArrayList<>();
        String v = SkillSaved.global("scenes_route_" + n);
        if (v == null) return out;
        Matcher mt = POINT.matcher(v);
        while (mt.find()) out.add(new Vec3(Double.parseDouble(mt.group(1)), Double.parseDouble(mt.group(2)), Double.parseDouble(mt.group(3))));
        return out;
    }

    private static void setRoute(int n, List<Vec3> pts) {
        if (pts.isEmpty()) {
            SkillSaved.setGlobal("scenes_route_" + n, null);
            SkillSaved.setGlobal("scenes_route_" + n + "_start", null);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Vec3 v : pts) {
            if (sb.length() > 0) sb.append(',');
            sb.append('(').append(fmt(v.x)).append(',').append(fmt(v.y)).append(',').append(fmt(v.z)).append(')');
        }
        SkillSaved.setGlobal("scenes_route_" + n, sb.toString());
        Vec3 s = pts.get(0);
        SkillSaved.setGlobal("scenes_route_" + n + "_start", fmt(s.x) + "," + fmt(s.y) + "," + fmt(s.z));
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    // ------------------------------------------------------------------------------------------- ayudas

    public static BossEntity selected(ServerPlayer p) {
        UUID id = SELECTED.get(p.getUUID());
        if (id == null) return null;
        Entity e = p.serverLevel().getEntity(id);
        return e instanceof BossEntity b && b.isNpc() && !b.isRemoved() ? b : null;
    }

    private static List<BossEntity> near(ServerPlayer p, double r) {
        List<BossEntity> list = p.serverLevel().getEntitiesOfClass(BossEntity.class, p.getBoundingBox().inflate(r), BossEntity::isNpc);
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return list;
    }

    /** El bloque al que mira (a 32 bloques), encima de la cara que mira; null si no mira a ninguno. */
    private static Vec3 looked(ServerPlayer p) {
        HitResult hit = p.pick(32, 1F, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos at = bh.getBlockPos().relative(bh.getDirection());
        return Vec3.atBottomCenterOf(at);
    }

    /** Donde está de pie (el centro de su bloque, a su altura). */
    private static Vec3 feet(ServerPlayer p) {
        return new Vec3(Math.floor(p.getX()) + 0.5, Math.round(p.getY() * 100) / 100.0, Math.floor(p.getZ()) + 0.5);
    }

    /** Hacia dónde mira el staff, al eje más cercano (para mover con las flechas sin torcerse). */
    private static Vec3 axis(ServerPlayer p) {
        double r = Math.toRadians(p.getYRot());
        double x = -Math.sin(r), z = Math.cos(r);
        return Math.abs(x) > Math.abs(z) ? new Vec3(Math.signum(x), 0, 0) : new Vec3(0, 0, Math.signum(z));
    }

    static String npcName(BossEntity e) {
        NpcCatalog.Pack pk = NpcCatalog.pack(e.bossId());
        NpcCatalog.Npc n = pk == null ? null : pk.npc(e.mobName());
        return n == null ? e.mobName() : n.name();
    }

    /** Le manda cómo está todo (y, con open, que abra el panel). */
    public static void send(ServerPlayer p, String notice, boolean open) {
        BossEntity sel = selected(p);
        List<NpcNet.PinInfo> pins = new ArrayList<>();
        SkillSaved.pins().forEach((name, pin) -> pins.add(new NpcNet.PinInfo(name, pin.at().x, pin.at().y, pin.at().z,
                pin.dim().equals(p.level().dimension()))));
        int[] routes = new int[ROUTES];
        for (int i = 0; i < ROUTES; i++) routes[i] = route(i + 1).size();
        List<NpcNet.Near> near = new ArrayList<>();
        for (BossEntity e : near(p, 32)) {
            if (near.size() >= 40) break;
            near.add(new NpcNet.Near(e.getId(), e.bossId(), e.mobName(), Math.sqrt(e.distanceToSqr(p))));
        }
        NpcNet.State s = sel == null
                ? new NpcNet.State(-1, "", "", 0, 0, 0, 0, Map.of(), pins, routes, near, notice, open)
                : new NpcNet.State(sel.getId(), sel.bossId(), sel.mobName(), sel.getX(), sel.getY(), sel.getZ(), sel.getYRot(),
                        chosen(sel.getUUID()), pins, routes, near, notice, open);
        NpcNet.send(p, s);
    }

    // ------------------------------------------------------------------------------------------- cada tick

    /**
     * Al staff con el panel en la mano: dónde están los pins, las rutas y los NPC sin modelo (los generadores y puestos
     * del pack), con partículas que solo ve él; y el NPC elegido, con un anillo. Los cosméticos de los duplicados.
     */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long t = p.serverLevel().getGameTime();
        if (t % 10 != 0 || !holding(p) || !p.hasPermissions(LEVEL)) return;
        ServerLevel level = p.serverLevel();
        Vec3 me = p.position();
        BossEntity sel = selected(p);
        NpcCatalog.Npc selNpc = null;
        if (sel != null) {
            NpcCatalog.Pack pk = NpcCatalog.pack(sel.bossId());
            selNpc = pk == null ? null : pk.npc(sel.mobName());
        }
        // Pins: los del NPC elegido en verde
        for (Map.Entry<String, SkillSaved.Pin> en : SkillSaved.pins().entrySet()) {
            SkillSaved.Pin pin = en.getValue();
            if (!pin.dim().equals(level.dimension()) || pin.at().distanceToSqr(me) > 64 * 64) continue;
            boolean mine = selNpc != null && selNpc.pins().contains(en.getKey());
            show(level, p, mine ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD, pin.at().add(0, 0.3, 0), 4, 0.1, 0.4);
        }
        // Rutas: cada una de un color, punto a punto
        for (int r = 1; r <= ROUTES; r++) {
            List<Vec3> pts = route(r);
            DustParticleOptions dust = new DustParticleOptions(ROUTE_COLORS[(r - 1) % ROUTE_COLORS.length], 1.2F);
            int budget = 300;
            for (int i = 0; i < pts.size() && budget > 0; i++) {
                Vec3 a = pts.get(i).add(0, 0.25, 0);
                if (a.distanceToSqr(me) <= 64 * 64) show(level, p, ParticleTypes.ELECTRIC_SPARK, a.add(0, 0.3, 0), 3, 0.05, 0.3);
                if (i + 1 >= pts.size()) break;
                Vec3 b = pts.get(i + 1).add(0, 0.25, 0);
                double len = a.distanceTo(b);
                for (double s = 0; s < len && budget > 0; s += 0.7) {
                    Vec3 q = a.add(b.subtract(a).scale(s / len));
                    if (q.distanceToSqr(me) > 48 * 48) continue;
                    show(level, p, dust, q, 1, 0, 0);
                    budget--;
                }
            }
        }
        // Los NPC sin modelo (marcadores del pack) y el elegido
        for (BossEntity n : near(p, 48)) {
            NpcCatalog.Pack pk = NpcCatalog.pack(n.bossId());
            NpcCatalog.Npc c = pk == null ? null : pk.npc(n.mobName());
            if (c != null && c.marker()) show(level, p, ParticleTypes.SOUL_FIRE_FLAME, n.position().add(0, 0.6, 0), 6, 0.15, 0.5);
        }
        if (sel != null) {
            DustParticleOptions white = new DustParticleOptions(new org.joml.Vector3f(1F, 1F, 1F), 1F);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                show(level, p, white, sel.position().add(Math.cos(a) * 0.7, 0.05, Math.sin(a) * 0.7), 1, 0, 0);
            }
        }
    }

    private static final org.joml.Vector3f[] ROUTE_COLORS = {new org.joml.Vector3f(1F, 0.85F, 0.2F), new org.joml.Vector3f(0.3F, 0.8F, 1F),
            new org.joml.Vector3f(1F, 0.35F, 0.35F), new org.joml.Vector3f(0.5F, 1F, 0.4F), new org.joml.Vector3f(0.85F, 0.45F, 1F),
            new org.joml.Vector3f(1F, 0.6F, 0.2F)};

    private static void show(ServerLevel level, ServerPlayer p, ParticleOptions o, Vec3 at, int n, double dxz, double dy) {
        level.sendParticles(p, o, true, at.x, at.y, at.z, n, dxz, dy, dxz, 0);
    }

    /** Los cosméticos de los duplicados, cuando ya tienen su modelo. */
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) pending(server);
    }

    private static void pending(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pd = it.next();
            if (now < pd.at()) continue;
            it.remove();
            for (ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(pd.npc()) instanceof BossEntity e) || !e.isNpc()) continue;
                NpcCatalog.Pack pk = NpcCatalog.pack(e.bossId());
                NpcCatalog.Npc n = pk == null ? null : pk.npc(e.mobName());
                if (n == null) break;
                applyAll(e, pk, n, pd.chosen());
                break;
            }
        }
    }
}
