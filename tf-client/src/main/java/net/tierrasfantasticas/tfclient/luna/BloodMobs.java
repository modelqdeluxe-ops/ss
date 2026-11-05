/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.luna;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Vector3f;

/**
 * Los mobs de la Luna de Sangre. Esa noche todos los mobs hostiles de Minecraft (zombis, esqueletos, creepers, arañas,
 * brujas…; los jefes y los de otros mods no) son más agresivos (ven más lejos, van más rápido y pegan más) y algunos
 * llevan modificadores, que se ven en su nombre (al mirarlos) y en sus partículas. Los fuertes —Élite (2 modificadores)
 * y Campeón (3)— tienen además <b>daño mágico</b>: su golpe (y sus flechas) ignoran la armadura (el dueño: «que aparezcan
 * mobs fuertes con daño mágico, que ignoren la armadura que traes y el daño te penetre»). El escudo sí los para.
 *
 * <p>Lo de cada mob va en sus datos («tf_luna»: rango, modificadores, si lo puso el evento), así que sobrevive a guardar
 * y cargar. Al amanecer, los que puso el evento se deshacen y a los demás se les quita todo ({@link #clean}).
 */
public final class BloodMobs {
    static final String TAG = "tf_luna";
    static final ResourceKey<DamageType> BLOOD_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(TFClient.MOD_ID, "luna_sangre"));

    private static final UUID FOLLOW_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000001");
    private static final UUID SPEED_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000002");
    private static final UUID DAMAGE_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000003");
    private static final UUID HEALTH_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000004");
    private static final UUID ARMOR_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000005");
    private static final UUID KNOCK_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000006");
    private static final UUID FAST_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000007");
    private static final UUID CHAMP_ARMOR_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000008");
    private static final UUID SMALL_ID = UUID.fromString("5a1c0a10-1175-4b1d-9e30-000000000009");

    /** Los modificadores. */
    public enum Affix {
        VAMPIRICO("Vampírico", 0xDC2626, "se cura con el daño que hace"),
        ARDIENTE("Ardiente", 0xF97316, "prende fuego al golpear"),
        GELIDO("Gélido", 0x7DD3FC, "ralentiza al golpear"),
        BLINDADO("Blindado", 0x94A3B8, "mucha armadura y no retrocede"),
        VELOZ("Veloz", 0xFDE047, "corre mucho más"),
        SOMBRA("Sombra", 0xA855F7, "aparece a tu espalda"),
        INVOCADOR("Invocador", 0x22D3EE, "a media vida llama a dos esbirros"),
        EXPLOSIVO("Explosivo", 0xEF4444, "explota al morir (sin romper bloques)"),
        ESPINOSO("Espinoso", 0x65A30D, "devuelve parte del daño cuerpo a cuerpo"),
        ESCUDO("Escudo arcano", 0xC4B5FD, "las flechas no le hacen nada"),
        LIDER("Líder de manada", 0xFBBF24, "hace más fuertes a los de alrededor"),
        FURIOSO("Furioso", 0xB91C1C, "pega más fuerte con poca vida"),
        DIVISOR("Divisor", 0x84CC16, "al morir se parte en dos");

        final String label;
        final int color;
        final String what;

        Affix(String label, int color, String what) {
            this.label = label;
            this.color = color;
            this.what = what;
        }

        static List<String> names() {
            List<String> out = new ArrayList<>();
            for (Affix a : values()) out.add(a.name());
            return out;
        }
    }

    public enum Rank {
        NORMAL, ELITE, CAMPEON
    }

    /** Los mobs de Minecraft que reciben la Luna de Sangre. */
    static final Set<EntityType<?>> TYPES = Set.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
            EntityType.SKELETON, EntityType.STRAY, EntityType.CREEPER, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.WITCH,
            EntityType.PILLAGER, EntityType.VINDICATOR);

    static final class Data {
        Rank rank = Rank.NORMAL;
        final EnumSet<Affix> affixes = EnumSet.noneOf(Affix.class);
        boolean extra, summoned, minion;
        int shadowIn = 12;

        boolean arcane() {
            return rank != Rank.NORMAL;
        }
    }

    static final Map<Mob, Data> LIVE = new WeakHashMap<>();
    /** Para la prueba: golpes convertidos en daño mágico. */
    public static int magicHits;

    private BloodMobs() {}

    public static boolean eligible(Entity e) {
        return e instanceof Mob m && e instanceof Enemy && TYPES.contains(m.getType());
    }

    static Data data(Entity e) {
        return e instanceof Mob m ? LIVE.get(m) : null;
    }

    /** ¿Lleva la marca de la luna (aunque aún no esté en la lista)? */
    static boolean marked(Mob mob) {
        return mob.getPersistentData().contains(TAG);
    }

    // ----------------------------------------------------------------------------------------------- poner y quitar

    /** Le da la luna (o recupera la que tenía) y lo apunta. */
    static Data empower(Mob mob, ServerLevel level, BloodMoonConfig cfg) {
        Data d = LIVE.get(mob);
        if (d != null) return d;
        CompoundTag tag = mob.getPersistentData().getCompound(TAG);
        boolean fresh = !mob.getPersistentData().contains(TAG) || !tag.getBoolean("hecho");
        d = new Data();
        if (mob.getPersistentData().contains(TAG)) {
            d.rank = Rank.values()[Mth.clamp(tag.getByte("rango"), 0, 2)];
            for (String s : tag.getString("afijos").split(",")) {
                try {
                    if (!s.isBlank()) d.affixes.add(Affix.valueOf(s));
                } catch (IllegalArgumentException ignored) {
                }
            }
            d.extra = tag.getBoolean("extra");
            d.summoned = tag.getBoolean("invocado");
            d.minion = tag.getBoolean("esbirro");
        }
        if (fresh && !d.minion) roll(mob, level, cfg, d);
        apply(mob, d, cfg, fresh);
        LIVE.put(mob, d);
        write(mob, d);
        return d;
    }

    /** Para la prueba en el juego: un mob de la luna con el rango y los modificadores que se digan. */
    public static void force(Mob mob, Rank rank, Affix... affixes) {
        Data before = LIVE.get(mob);
        strip(mob, before != null && before.affixes.contains(Affix.ARDIENTE));
        mob.setCustomName(null);
        mob.setCustomNameVisible(false);
        Data d = new Data();
        if (before != null) d.extra = before.extra;
        d.rank = rank;
        for (Affix a : affixes) d.affixes.add(a);
        apply(mob, d, BloodMoon.config(), true);
        LIVE.put(mob, d);
        write(mob, d);
    }

    /** Lo puso el evento (o un comando): al amanecer se deshace. */
    static void markExtra(Mob mob) {
        Data d = LIVE.get(mob);
        if (d == null) return;
        d.extra = true;
        write(mob, d);
    }

    /** Para la prueba: si lleva la marca de la luna. */
    public static boolean isMarked(Mob mob) {
        return marked(mob);
    }

    /** Rango y modificadores: lejos del spawn salen más fuertes; cerca (zona segura) solo alguno suelto. */
    private static void roll(Mob mob, ServerLevel level, BloodMoonConfig cfg, Data d) {
        RandomSource r = mob.getRandom();
        double dist = Math.sqrt(mob.blockPosition().distSqr(level.getSharedSpawnPos()));
        boolean safe = dist < cfg.zonaSegura;
        double f = safe ? 0 : Mth.clamp((dist - cfg.zonaSegura) / (double) (cfg.distanciaMaxima - cfg.zonaSegura), 0, 1);
        if (!safe) {
            double x = r.nextDouble();
            double champ = cfg.probCampeon * (1 + 2 * f), elite = cfg.probElite * (1 + 1.5 * f);
            if (x < champ) d.rank = Rank.CAMPEON;
            else if (x < champ + elite) d.rank = Rank.ELITE;
        }
        int n = switch (d.rank) {
            case CAMPEON -> 3;
            case ELITE -> 2;
            default -> r.nextDouble() < cfg.probModificador * (safe ? 0.5 : 1 + 0.6 * f) ? 1 : 0;
        };
        List<Affix> pool = new ArrayList<>();
        for (Affix a : Affix.values()) {
            if (cfg.modificadores.contains(a.name()) && fits(mob, a)) pool.add(a);
        }
        for (int i = 0; i < n && !pool.isEmpty(); i++) d.affixes.add(pool.remove(r.nextInt(pool.size())));
    }

    /** Lo que no tiene sentido en un mob (un creeper no pega: ni vampírico, ni ardiente, ni gélido, ni furioso). */
    private static boolean fits(Mob mob, Affix a) {
        boolean creeper = mob.getType() == EntityType.CREEPER;
        return switch (a) {
            case VAMPIRICO, ARDIENTE, GELIDO, FURIOSO -> !creeper;
            case EXPLOSIVO -> !creeper;
            default -> true;
        };
    }

    private static void apply(Mob mob, Data d, BloodMoonConfig cfg, boolean fresh) {
        add(mob, Attributes.FOLLOW_RANGE, FOLLOW_ID, "luna: ve más lejos", 24, AttributeModifier.Operation.ADDITION);
        add(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, "luna: más rápido", cfg.velocidadExtra, AttributeModifier.Operation.MULTIPLY_BASE);
        add(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "luna: más daño", cfg.danoExtra, AttributeModifier.Operation.MULTIPLY_BASE);
        if (d.rank != Rank.NORMAL) {
            double hp = d.rank == Rank.CAMPEON ? cfg.vidaCampeon : cfg.vidaElite;
            add(mob, Attributes.MAX_HEALTH, HEALTH_ID, "luna: vida", hp - 1, AttributeModifier.Operation.MULTIPLY_BASE);
        }
        if (d.rank == Rank.CAMPEON) add(mob, Attributes.ARMOR, CHAMP_ARMOR_ID, "luna: campeón", 4, AttributeModifier.Operation.ADDITION);
        if (d.affixes.contains(Affix.BLINDADO)) {
            add(mob, Attributes.ARMOR, ARMOR_ID, "luna: blindado", 8, AttributeModifier.Operation.ADDITION);
            add(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, "luna: blindado", 0.8, AttributeModifier.Operation.ADDITION);
        }
        if (d.affixes.contains(Affix.VELOZ)) add(mob, Attributes.MOVEMENT_SPEED, FAST_ID, "luna: veloz", 0.35, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (d.affixes.contains(Affix.ARDIENTE)) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        if (fresh) mob.setHealth(mob.getMaxHealth());
        if (d.rank != Rank.NORMAL || !d.affixes.isEmpty()) {
            mob.setCustomName(name(mob, d));
            mob.setCustomNameVisible(d.rank != Rank.NORMAL);
        }
    }

    private static void add(Mob mob, Attribute attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null || inst.getModifier(id) != null || amount == 0) return;
        inst.addPermanentModifier(new AttributeModifier(id, name, amount, op));
    }

    private static void remove(Mob mob, Attribute attr, UUID id) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    /** «☠ Zombi Campeón (Vampírico, Veloz, Sombra)»: el tipo con el color del rango y cada modificador con el suyo. */
    static Component name(Mob mob, Data d) {
        MutableComponent out = Component.empty();
        ChatFormatting color = switch (d.rank) {
            case CAMPEON -> ChatFormatting.DARK_RED;
            case ELITE -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
        if (d.rank == Rank.CAMPEON) out.append(Component.literal("☠ ").withStyle(ChatFormatting.DARK_RED));
        if (d.rank == Rank.ELITE) out.append(Component.literal("✦ ").withStyle(ChatFormatting.GOLD));
        out.append(mob.getType().getDescription().copy().withStyle(color, ChatFormatting.BOLD));
        if (d.rank == Rank.ELITE) out.append(Component.literal(" Élite").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (d.rank == Rank.CAMPEON) out.append(Component.literal(" Campeón").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        if (!d.affixes.isEmpty()) {
            out.append(Component.literal(" (").withStyle(ChatFormatting.GRAY));
            boolean first = true;
            for (Affix a : d.affixes) {
                if (!first) out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                out.append(Component.literal(a.label).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(a.color))));
                first = false;
            }
            out.append(Component.literal(")").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static void write(Mob mob, Data d) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("hecho", true);
        t.putByte("rango", (byte) d.rank.ordinal());
        StringBuilder s = new StringBuilder();
        for (Affix a : d.affixes) s.append(s.length() > 0 ? "," : "").append(a.name());
        t.putString("afijos", s.toString());
        t.putBoolean("extra", d.extra);
        t.putBoolean("invocado", d.summoned);
        t.putBoolean("esbirro", d.minion);
        t.putBoolean("nombre", d.rank != Rank.NORMAL || !d.affixes.isEmpty());
        mob.getPersistentData().put(TAG, t);
    }

    /** Al amanecer (o al cargarse ya de día): los del evento se deshacen; a los demás se les quita todo. */
    static void clean(Mob mob) {
        CompoundTag t = mob.getPersistentData().getCompound(TAG);
        LIVE.remove(mob);
        if (t.getBoolean("extra") || t.getBoolean("esbirro")) {
            if (mob.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 12,
                        mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.4, mob.getBbWidth() * 0.4, 0.02);
            }
            mob.discard();
            return;
        }
        strip(mob, t.getString("afijos").contains(Affix.ARDIENTE.name()));
        if (t.getBoolean("nombre")) {
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
        }
        if (mob.getHealth() > mob.getMaxHealth()) mob.setHealth(mob.getMaxHealth());
        mob.getPersistentData().remove(TAG);
    }

    /** Quita todo lo que pone la luna en los atributos (y la resistencia al fuego de «ardiente»). */
    private static void strip(Mob mob, boolean fire) {
        for (UUID id : new UUID[] {FOLLOW_ID, SPEED_ID, DAMAGE_ID}) {
            remove(mob, Attributes.FOLLOW_RANGE, id);
            remove(mob, Attributes.MOVEMENT_SPEED, id);
            remove(mob, Attributes.ATTACK_DAMAGE, id);
        }
        remove(mob, Attributes.MAX_HEALTH, HEALTH_ID);
        remove(mob, Attributes.ARMOR, ARMOR_ID);
        remove(mob, Attributes.ARMOR, CHAMP_ARMOR_ID);
        remove(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID);
        remove(mob, Attributes.MOVEMENT_SPEED, FAST_ID);
        if (fire) mob.removeEffect(MobEffects.FIRE_RESISTANCE);
    }

    // ----------------------------------------------------------------------------------------------- cada medio segundo

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.75F, 0.15F), 1.1F);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75F, 0.02F, 0.02F), 1.6F);

    static void tick(long gameTime) {
        List<Map.Entry<Mob, Data>> all = new ArrayList<>(LIVE.entrySet());
        for (Map.Entry<Mob, Data> e : all) {
            Mob mob = e.getKey();
            Data d = e.getValue();
            if (mob == null || mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) continue;
            double x = mob.getX(), y = mob.getY() + mob.getBbHeight() * 0.6, z = mob.getZ();
            double w = mob.getBbWidth() * 0.35, h = mob.getBbHeight() * 0.3;
            if (d.rank == Rank.CAMPEON) level.sendParticles(BLOOD, x, y, z, 3, w, h, w, 0.0);
            else if (d.rank == Rank.ELITE) level.sendParticles(GOLD, x, y, z, 2, w, h, w, 0.0);
            if (!d.affixes.isEmpty()) {
                Affix[] list = d.affixes.toArray(new Affix[0]);
                Affix a = list[(int) ((gameTime / 10) % list.length)];
                ParticleOptions p = particle(a, mob);
                if (p != null) level.sendParticles(p, x, y, z, 2, w, h, w, 0.01);
            }
            if (d.affixes.contains(Affix.SOMBRA) && --d.shadowIn <= 0) {
                d.shadowIn = 12 + mob.getRandom().nextInt(10);
                shadowStep(mob, level);
            }
            if (d.affixes.contains(Affix.LIDER) && gameTime % 40 == 0) {
                for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(8), o -> o != mob && o instanceof Enemy)) {
                    other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, true));
                    other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, true));
                }
            }
        }
    }

    private static ParticleOptions particle(Affix a, Mob mob) {
        return switch (a) {
            case VAMPIRICO -> ParticleTypes.DAMAGE_INDICATOR;
            case ARDIENTE -> ParticleTypes.FLAME;
            case GELIDO -> ParticleTypes.SNOWFLAKE;
            case BLINDADO -> ParticleTypes.CRIT;
            case VELOZ -> ParticleTypes.CLOUD;
            case SOMBRA -> ParticleTypes.PORTAL;
            case INVOCADOR -> ParticleTypes.SOUL;
            case EXPLOSIVO -> ParticleTypes.SMOKE;
            case ESPINOSO -> ParticleTypes.COMPOSTER;
            case ESCUDO -> ParticleTypes.ENCHANT;
            case LIDER -> ParticleTypes.ANGRY_VILLAGER;
            case FURIOSO -> mob.getHealth() < mob.getMaxHealth() * 0.5F ? ParticleTypes.LAVA : ParticleTypes.SMOKE;
            case DIVISOR -> ParticleTypes.ITEM_SLIME;
        };
    }

    /** Sombra: aparece dos bloques detrás de su objetivo (si hay sitio). */
    private static void shadowStep(Mob mob, ServerLevel level) {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return;
        double dist = mob.distanceTo(target);
        if (dist < 4 || dist > 24) return;
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) return;
        flat = flat.normalize();
        Vec3 dest = target.position().subtract(flat.scale(2.0));
        AABB box = mob.getBoundingBox().move(dest.subtract(mob.position()));
        if (!level.noCollision(mob, box) || !level.getBlockState(net.minecraft.core.BlockPos.containing(dest.x, dest.y - 0.5, dest.z)).isSolid()) return;
        level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
        mob.teleportTo(dest.x, dest.y, dest.z);
        mob.lookAt(target, 180F, 180F);
        level.sendParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 20, 0.3, 0.6, 0.3, 0.2);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.7F);
    }

    // ----------------------------------------------------------------------------------------------- en combate

    /** El daño mágico de los fuertes: el mismo daño, pero de un tipo que ignora la armadura (el escudo sí lo para). */
    static boolean convertToMagic(LivingEntity victim, DamageSource src, float amount) {
        if (!(victim instanceof Player) || src.is(BLOOD_MAGIC)) return false;
        Data d = data(src.getEntity());
        if (d == null || !d.arcane() || !(src.getEntity() instanceof Mob mob)) return false;
        var holder = victim.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(BLOOD_MAGIC);
        if (holder.isEmpty()) return false;
        magicHits++;
        victim.hurt(new DamageSource(holder.get(), src.getDirectEntity(), mob), amount);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(BLOOD, victim.getX(), victim.getY() + 1.0, victim.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
        }
        return true;
    }

    /** Lo que hace un mob de la luna al pegar: furioso, vampírico, ardiente, gélido. Devuelve el daño final. */
    static float onDealt(Mob mob, Data d, LivingEntity victim, float amount) {
        if (d.affixes.contains(Affix.FURIOSO) && mob.getHealth() < mob.getMaxHealth() * 0.5F) amount *= 1.5F;
        if (d.affixes.contains(Affix.VAMPIRICO)) {
            mob.heal(amount * 0.5F);
            if (mob.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
        }
        if (d.affixes.contains(Affix.ARDIENTE)) victim.setSecondsOnFire(4);
        if (d.affixes.contains(Affix.GELIDO)) victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        return amount;
    }

    /** Lo que pasa al pegarle: espinoso devuelve, invocador llama a sus esbirros a media vida. */
    static void onTaken(Mob mob, Data d, DamageSource src, float amount) {
        if (d.affixes.contains(Affix.ESPINOSO) && src.getDirectEntity() instanceof LivingEntity attacker && attacker == src.getEntity()
                && !src.is(DamageTypeTags.IS_PROJECTILE)) {
            attacker.hurt(mob.damageSources().thorns(mob), Math.max(1F, amount * 0.3F));
        }
        if (d.affixes.contains(Affix.INVOCADOR) && !d.summoned && mob.getHealth() - amount <= mob.getMaxHealth() * 0.5F
                && mob.getHealth() - amount > 0 && mob.level() instanceof ServerLevel level) {
            d.summoned = true;
            write(mob, d);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1F, 0.8F);
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.8, 0.5, 0.8, 0.05);
            for (int i = 0; i < 2; i++) spawnMinion(level, mob, 1.0, false);
        }
    }

    static void onDeath(Mob mob, Data d) {
        LIVE.remove(mob);
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (d.affixes.contains(Affix.EXPLOSIVO)) {
            level.explode(mob, mob.getX(), mob.getY() + 0.5, mob.getZ(), 2.4F, false, Level.ExplosionInteraction.NONE);
        }
        if (d.affixes.contains(Affix.DIVISOR)) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 15, 0.4, 0.4, 0.4, 0.02);
            for (int i = 0; i < 2; i++) spawnMinion(level, mob, 0.4, true);
        }
    }

    /** Un esbirro (invocador) o una mitad (divisor): del mismo tipo, sin modificadores; se deshace al amanecer. */
    private static void spawnMinion(ServerLevel level, Mob parent, double health, boolean small) {
        Entity made = parent.getType().create(level);
        if (!(made instanceof Mob m)) return;
        RandomSource r = parent.getRandom();
        m.moveTo(parent.getX() + r.nextGaussian() * 0.7, parent.getY(), parent.getZ() + r.nextGaussian() * 0.7, r.nextFloat() * 360F, 0F);
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(m.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
        CompoundTag t = new CompoundTag();
        t.putBoolean("esbirro", true);
        t.putBoolean("extra", true);
        m.getPersistentData().put(TAG, t);
        if (small && m instanceof Zombie z) z.setBaby(true);
        if (health < 1.0) {
            AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) hp.addPermanentModifier(new AttributeModifier(SMALL_ID, "luna: mitad", health - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
            m.setHealth(m.getMaxHealth());
        }
        if (parent.getTarget() != null) m.setTarget(parent.getTarget());
        level.addFreshEntity(m);
    }

    /** El escudo arcano: los proyectiles no le hacen nada. */
    static boolean blocksProjectile(Mob mob, Data d, DamageSource src) {
        if (!d.affixes.contains(Affix.ESCUDO) || !src.is(DamageTypeTags.IS_PROJECTILE)) return false;
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 20, 0.4, 0.5, 0.4, 0.5);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1F, 0.7F);
        }
        return true;
    }

    static int experience(Data d, int base, BloodMoonConfig cfg) {
        int k = switch (d.rank) {
            case CAMPEON -> cfg.experienciaCampeon;
            case ELITE -> cfg.experienciaElite;
            default -> cfg.experienciaNormal;
        };
        return base * Math.max(1, k);
    }
}
