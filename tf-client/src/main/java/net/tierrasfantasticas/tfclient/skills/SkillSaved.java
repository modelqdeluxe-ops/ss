package net.tierrasfantasticas.tfclient.skills;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Lo de MythicMobs que se guarda con el mundo (los NPC de escenas, 1.3.101): las variables globales puestas con
 * setvariable{save=true} (las pieles y cosméticos que eligió el menú del pack para cada NPC, la ruta de cada caminante,
 * las rutas del panel) y los «pins» (/mm pin: sitios con nombre a los que van con goto @pin{name} y que miran con
 * ?distanceFromPin). Va en el mundo principal (data/tfclient_mythic.dat).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class SkillSaved extends SavedData {
    private static final String NAME = "tfclient_mythic";

    /** Un pin: su mundo y su sitio. */
    public record Pin(ResourceKey<Level> dim, Vec3 at) {}

    private final Map<String, String> vars = new LinkedHashMap<>();
    private final Map<String, Pin> pins = new LinkedHashMap<>();

    private static SkillSaved get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ServerLevel over = server.overworld();
        return over.getDataStorage().computeIfAbsent(SkillSaved::read, SkillSaved::new, NAME);
    }

    /** Al arrancar el servidor: las variables guardadas vuelven a estar puestas (antes de que carguen los NPC). */
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        SkillSaved d = get();
        if (d != null) d.vars.forEach((k, v) -> SkillRuntime.putVar(SkillRuntime.globalVars(), k, v, 0));
    }

    /** setvariable{save=true} de una global: también al disco. */
    static void saveVar(String name, String value) {
        SkillSaved d = get();
        if (d == null) return;
        if (value.equals(d.vars.put(name, value))) return;
        d.setDirty();
    }

    static void forgetVar(String name) {
        SkillSaved d = get();
        if (d != null && d.vars.remove(name) != null) d.setDirty();
    }

    /** Pone una variable global y la guarda (lo que hace setvariable{var=global.X;save=true}). */
    public static void setGlobal(String name, String value) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (value == null) {
            SkillRuntime.globalVars().remove(n);
            forgetVar(n);
        } else {
            SkillRuntime.putVar(SkillRuntime.globalVars(), n, value, 0);
            saveVar(n, value);
        }
    }

    /** Quita todas las variables globales cuyo nombre acaba así (las de un NPC borrado: «hat<su uuid>»…). */
    public static void forgetSuffix(String suffix) {
        String sfx = suffix.toLowerCase(java.util.Locale.ROOT);
        SkillRuntime.globalVars().keySet().removeIf(k -> k.endsWith(sfx));
        SkillSaved d = get();
        if (d != null && d.vars.keySet().removeIf(k -> k.endsWith(sfx))) d.setDirty();
    }

    /** Lo que vale ahora una variable global (null si no está puesta). */
    public static String global(String name) {
        return SkillRuntime.globalVars().get(name.toLowerCase(java.util.Locale.ROOT));
    }

    // ------------------------------------------------------------------------------------------- pins

    /** El pin con ese nombre (sin mayúsculas), o null. */
    public static Pin pin(String name) {
        SkillSaved d = get();
        return d == null || name == null ? null : d.pins.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** El sitio del pin si está en ese mundo (MythicMobs: un pin de otro mundo no es objetivo). */
    static Vec3 pinIn(String name, Level level) {
        Pin p = pin(name);
        return p == null || level == null || !p.dim().equals(level.dimension()) ? null : p.at();
    }

    public static void setPin(String name, Level level, Vec3 at) {
        SkillSaved d = get();
        if (d == null || name == null || name.isBlank()) return;
        d.pins.put(name.toLowerCase(java.util.Locale.ROOT), new Pin(level.dimension(), at));
        d.setDirty();
    }

    public static void removePin(String name) {
        SkillSaved d = get();
        if (d != null && name != null && d.pins.remove(name.toLowerCase(java.util.Locale.ROOT)) != null) d.setDirty();
    }

    public static Map<String, Pin> pins() {
        SkillSaved d = get();
        return d == null ? Map.of() : Collections.unmodifiableMap(d.pins);
    }

    // ------------------------------------------------------------------------------------------- disco

    private static SkillSaved read(CompoundTag tag) {
        SkillSaved d = new SkillSaved();
        CompoundTag v = tag.getCompound("vars");
        for (String k : v.getAllKeys()) d.vars.put(k, v.getString(k));
        ListTag list = tag.getList("pins", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(p.getString("dim"));
            if (dim == null) continue;
            d.pins.put(p.getString("name"), new Pin(ResourceKey.create(Registries.DIMENSION, dim),
                    new Vec3(p.getDouble("x"), p.getDouble("y"), p.getDouble("z"))));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag v = new CompoundTag();
        vars.forEach(v::putString);
        tag.put("vars", v);
        ListTag list = new ListTag();
        pins.forEach((name, p) -> {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            t.putString("dim", p.dim().location().toString());
            t.putDouble("x", p.at().x);
            t.putDouble("y", p.at().y);
            t.putDouble("z", p.at().z);
            list.add(t);
        });
        tag.put("pins", list);
        return tag;
    }
}
