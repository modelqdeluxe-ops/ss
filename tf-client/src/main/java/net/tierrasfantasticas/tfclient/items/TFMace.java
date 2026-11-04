/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.171, el dueño: «el martillo de todos los sets, haz que sean una maza, es decir que sea lo mismo que una maza; no
 * tiene sentido que sea martillo y sea como espada; revisa cómo funciona la maza y lo pones para todos los martillos que
 * tengan los sets, y que sean compatibles sus encantamientos») La maza de Minecraft 1.21 en la 1.20.1, para los martillos
 * y las mazas de los sets ({@link TFItemTypes.Mace}):
 * <ul>
 *   <li>Ataque lento (velocidad 0,6), sin barrido y sin romper bloques en creativo.</li>
 *   <li><b>Golpe aplastante</b>: si el que pega lleva cayendo más de 1,5 bloques (y no planea con élitros), suma +4 de
 *   daño por cada uno de los 3 primeros bloques, +2 por cada uno de los 5 siguientes y +1 por cada bloque después (sin
 *   tope); si es crítico, también ×1,5. Se lo come aunque el ataque no esté cargado. Al acertar: no hay daño de caída (la
 *   caída vuelve a contar desde ahí), se queda casi quieto en el aire un instante, empuja a los de alrededor (3,5
 *   bloques; el doble si caía más de 5) y, en cada TF Client cercano, la explosión de polvo y el sonido de la maza de la
 *   1.21 (TFMaceClient: los sonidos se bajan de Mojang). No empuja a quien no se puede pegar (sin PvP en el servidor o en esa zona, salvo en un duelo),
 *   ni a sus mascotas, ni a los de su equipo.</li>
 *   <li>Encantamientos de la maza: <b>Densidad</b> (I-V, +0,5 de daño por bloque caído y nivel, solo en el golpe
 *   aplastante), <b>Brecha</b> (I-IV, la armadura del blanco protege un 15 % menos por nivel; mixin
 *   LivingEntityBreachMixin) y <b>Ráfaga de viento</b> (I-III, tras un golpe aplastante lo lanza 8, 16 o 24 bloques hacia
 *   arriba; de tesoro: libros del botín y de los aldeanos, no de la mesa). Densidad, Brecha, Castigo y Perdición de los
 *   artrópodos no van juntos. En la mesa: Densidad y Brecha; con libro en el yunque, además, Ráfaga de viento, Castigo,
 *   Perdición de los artrópodos, Aspecto ígneo y la maldición de desaparición (como en Java). Ni Filo ni Saqueo ni
 *   Empuje ni Barrido. (Irrompibilidad y Reparación, como en todas las piezas de los sets, no: no se gastan.)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFMace {
    private TFMace() {}

    public static final EnchantmentCategory CATEGORY = EnchantmentCategory.create("TFCLIENT_MAZA", item -> item instanceof TFItemTypes.Mace);
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, TFClient.MOD_ID);
    public static final RegistryObject<Enchantment> DENSITY = ENCHANTMENTS.register("densidad", Density::new);
    public static final RegistryObject<Enchantment> BREACH = ENCHANTMENTS.register("brecha", Breach::new);
    public static final RegistryObject<Enchantment> WIND_BURST = ENCHANTMENTS.register("rafaga_viento", WindBurst::new);
    /** La velocidad hacia arriba de Ráfaga de viento I, II y III (8, 16 y 24 bloques con la gravedad de Minecraft). */
    static final double[] BURST = {1.185, 1.757, 2.223};

    // -----------------------------------------------------------------------------------------------------------------
    // Los encantamientos
    // -----------------------------------------------------------------------------------------------------------------

    private static final EquipmentSlot[] HAND = {EquipmentSlot.MAINHAND};

    /** Densidad, Brecha, Castigo y Perdición de los artrópodos: uno solo de ellos. */
    static boolean exclusiveGroup(Enchantment e) {
        return e instanceof DamageEnchantment || e instanceof Density || e instanceof Breach;
    }

    public static final class Density extends Enchantment {
        Density() {
            super(Rarity.UNCOMMON, CATEGORY, HAND);
        }

        @Override
        public int getMaxLevel() {
            return 5;
        }

        @Override
        public int getMinCost(int level) {
            return 5 + (level - 1) * 8;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 20;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return super.checkCompatibility(other) && !exclusiveGroup(other);
        }
    }

    public static final class Breach extends Enchantment {
        Breach() {
            super(Rarity.UNCOMMON, CATEGORY, HAND);
        }

        @Override
        public int getMaxLevel() {
            return 4;
        }

        @Override
        public int getMinCost(int level) {
            return 15 + (level - 1) * 9;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 50;
        }

        @Override
        protected boolean checkCompatibility(Enchantment other) {
            return super.checkCompatibility(other) && !exclusiveGroup(other);
        }
    }

    public static final class WindBurst extends Enchantment {
        WindBurst() {
            super(Rarity.RARE, CATEGORY, HAND);
        }

        @Override
        public int getMaxLevel() {
            return 3;
        }

        @Override
        public int getMinCost(int level) {
            return 15 + (level - 1) * 9;
        }

        @Override
        public int getMaxCost(int level) {
            return getMinCost(level) + 50;
        }

        @Override
        public boolean isTreasureOnly() {
            return true;
        }
    }

    /** ¿Lo ofrece la mesa a una maza? Densidad y Brecha (Irrompibilidad no: las piezas de los sets no se gastan). */
    static boolean tableAccepts(Enchantment e) {
        return e == DENSITY.get() || e == BREACH.get();
    }

    /** ¿Se le pone con un libro en el yunque? Los de la mesa, Ráfaga de viento y los que Java deja en la maza. */
    public static boolean anvilAccepts(Enchantment e) {
        return e != null && (e.category == CATEGORY || e == Enchantments.SMITE || e == Enchantments.BANE_OF_ARTHROPODS
                || e == Enchantments.FIRE_ASPECT || e == Enchantments.VANISHING_CURSE);
    }

    public static boolean isMace(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TFItemTypes.Mace;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // El golpe aplastante
    // -----------------------------------------------------------------------------------------------------------------

    /** ¿Puede dar un golpe aplastante ahora? (cayendo más de 1,5 bloques y sin planear) */
    public static boolean canSmash(LivingEntity attacker) {
        return attacker.fallDistance > 1.5F && !attacker.isFallFlying();
    }

    /** El daño de más del golpe aplastante (sin el crítico), con Densidad. */
    public static float smashBonus(float fall, int density) {
        float bonus;
        if (fall <= 3.0F) bonus = 4.0F * fall;
        else if (fall <= 8.0F) bonus = 12.0F + 2.0F * (fall - 3.0F);
        else bonus = 22.0F + fall - 8.0F;
        return bonus + 0.5F * density * fall;
    }

    /** Si el último golpe de cada jugador fue crítico (CriticalHitEvent llega justo antes del daño). */
    private static final Map<UUID, Boolean> CRIT = new ConcurrentHashMap<>();
    /** Brecha del golpe que se está aplicando: a quién y cuánto quita de la armadura (lo usa el mixin, mismo hilo). */
    private static LivingEntity breachTarget;
    private static float breachFactor;
    /** Para la prueba en el juego: lo último que hizo el golpe aplastante. */
    public static volatile String testLast = "";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCrit(CriticalHitEvent event) {
        if (!isMace(event.getEntity().getMainHandItem())) return;
        Event.Result r = event.getResult();
        CRIT.put(event.getEntity().getUUID(), r == Event.Result.ALLOW || r == Event.Result.DEFAULT && event.isVanillaCritical());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Player p) || !event.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        ItemStack weapon = p.getMainHandItem();
        if (!isMace(weapon)) return;
        Boolean crit = CRIT.remove(p.getUUID());
        if (canSmash(p)) {
            float bonus = smashBonus(p.fallDistance, EnchantmentHelper.getItemEnchantmentLevel(DENSITY.get(), weapon));
            if (Boolean.TRUE.equals(crit)) bonus *= 1.5F;
            event.setAmount(event.getAmount() + bonus);
            testLast = "aplastante: caída " + String.format(java.util.Locale.ROOT, "%.1f", p.fallDistance) + ", +" + String.format(java.util.Locale.ROOT, "%.1f", bonus)
                    + (Boolean.TRUE.equals(crit) ? " (crítico)" : "") + ", daño " + String.format(java.util.Locale.ROOT, "%.1f", event.getAmount());
        }
        int breach = EnchantmentHelper.getItemEnchantmentLevel(BREACH.get(), weapon);
        if (breach > 0) {
            breachTarget = event.getEntity();
            breachFactor = Math.min(1.0F, 0.15F * breach);
        }
    }

    /** Lo que el mixin de la armadura debe quitar a este golpe (0 si nada); lo consume. */
    public static float takeBreach(LivingEntity target) {
        if (breachTarget != target) return 0.0F;
        breachTarget = null;
        return breachFactor;
    }

    /** Tras acertar (Item.hurtEnemy, en el servidor): el empujón, el polvo y el golpe, sin daño de caída, y la ráfaga. */
    static void afterHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player p) || !(p.level() instanceof ServerLevel level) || !canSmash(p)) return;
        float fall = p.fallDistance;
        boolean heavy = fall > 5.0F;
        // el empujón a los de alrededor
        int pushed = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.5), e -> pushable(p, target, e))) {
            Vec3 d = e.position().subtract(target.position());
            double power = (3.5 - d.length()) * 0.7F * (heavy ? 2 : 1) * (1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if (power <= 0.0) continue;
            Vec3 v = d.lengthSqr() < 1.0E-6 ? Vec3.ZERO : d.normalize().scale(power);
            e.push(v.x, 0.7F, v.z);
            e.hurtMarked = true;
            if (e instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
            pushed++;
        }
        // sin daño de caída: la caída vuelve a contar desde aquí, y se queda casi quieto un instante
        p.resetFallDistance();
        Vec3 v = p.getDeltaMovement();
        double up = 0.01;
        int burst = EnchantmentHelper.getItemEnchantmentLevel(WIND_BURST.get(), stack);
        if (burst > 0) up = BURST[Math.min(BURST.length, burst) - 1];
        // (1.3.173) lo que se ve y se oye, como la 1.21: el polvo del bloque que pisa el que recibe y el sonido de la maza
        // desde quien pega (TFMaceClient, en cada TF Client cercano)
        TFMaceNet.send(level, new TFMaceNet.Smash(target.getOnPos(), target.onGround(), heavy, p.getX(), p.getY(), p.getZ(), burst));
        p.setDeltaMovement(v.x, up, v.z);
        p.hurtMarked = true;
        if (p instanceof ServerPlayer sp) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
        testLast += " · empujó " + pushed + (burst > 0 ? " · ráfaga " + burst : "") + " · caída tras el golpe " + p.fallDistance;
    }

    /** ¿Le empuja el golpe aplastante? Como en Java, y además respetando el PvP del servidor y de las zonas. */
    private static boolean pushable(Player p, LivingEntity target, LivingEntity e) {
        if (e.isSpectator() || e == p || e == target || p.isAlliedTo(e)) return false;
        if (e instanceof TamableAnimal t && t.isTame() && p.getUUID().equals(t.getOwnerUUID())) return false;
        if (e instanceof ArmorStand as && as.isMarker()) return false;
        if (target.distanceToSqr(e) > 3.5 * 3.5) return false;
        if (e instanceof Player other) {
            if (other.isCreative() || !p.canHarmPlayer(other)) return false;
            if (net.tierrasfantasticas.tfclient.pad.server.PadClanDuels.fighting(p.getUUID(), other.getUUID())) return true;
            if (!net.tierrasfantasticas.tfclient.claims.data.GlobalFlags.getInstance().globalPVP) return false;
            var claim = net.tierrasfantasticas.tfclient.claims.data.ClaimManager.getInstance().getClaimAt(other.level(), other.blockPosition());
            return claim == null || claim.getFlags().pvpAll || !claim.getFlags().blockPVP;
        }
        return true;
    }
}
