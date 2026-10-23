/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los sets de los rangos, encantados (1.3.116; el dueño: «en los rangos debes encantar la armadura a tu consideración del
 * rango; a los 2 más caros, full encantamientos para armaduras, armas y herramientas; los vanilla, nada de filo 10» y,
 * después, «¿qué tienen que ver esas armaduras en los rangos si ya tenemos los sets custom?»): lo que se encanta es el
 * set propio del rango, al entregarlo (/tf web sets give &lt;jugador&gt; &lt;set&gt; encantado &lt;nivel&gt;). Cada pieza
 * recibe los encantamientos de su nivel que el juego le deja tener (Enchantment.canEnchant: la armadura, protección;
 * las armas, filo; las herramientas, eficiencia; el arco, poder…) y que no chocan con los que ya lleva.
 * <ul>
 *   <li>1 Mortal · 2 Inmortal · 3 Mágico · 4 Eterno · 5 Cósmico: cada uno algo más.</li>
 *   <li>6 Celestial y Fantástico: todos los encantamientos vanilla que admita cada pieza, al máximo (sin maldiciones;
 *   cuando dos chocan, el primero de la lista: protección, filo, fortuna, reparación, agilidad acuática…).</li>
 * </ul>
 * (1.3.125, el dueño: «los encantamientos de las armaduras de los rangos no se aplican») con el inventario lleno, las
 * piezas que no caben caen al suelo y el encantado, que llega justo después de «give», no las veía: ahora también se
 * encantan las del set que están tiradas cerca y, durante {@link #WINDOW_MS}, las que recoja o le entren al inventario.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFSetEnchant {
    public static final int MAX = 6;

    private TFSetEnchant() {}

    private static Map<Enchantment, Integer> tier(Object... pairs) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    private static final List<Map<Enchantment, Integer>> TIERS = List.of(
            tier(Enchantments.ALL_DAMAGE_PROTECTION, 2, Enchantments.UNBREAKING, 2, Enchantments.SHARPNESS, 2,
                    Enchantments.BLOCK_EFFICIENCY, 2, Enchantments.POWER_ARROWS, 2, Enchantments.QUICK_CHARGE, 1),
            tier(Enchantments.ALL_DAMAGE_PROTECTION, 2, Enchantments.UNBREAKING, 2, Enchantments.SHARPNESS, 3,
                    Enchantments.BLOCK_EFFICIENCY, 3, Enchantments.POWER_ARROWS, 3, Enchantments.MOB_LOOTING, 1,
                    Enchantments.BLOCK_FORTUNE, 1, Enchantments.QUICK_CHARGE, 1),
            tier(Enchantments.ALL_DAMAGE_PROTECTION, 3, Enchantments.UNBREAKING, 3, Enchantments.SHARPNESS, 4,
                    Enchantments.BLOCK_EFFICIENCY, 4, Enchantments.POWER_ARROWS, 4, Enchantments.MOB_LOOTING, 2,
                    Enchantments.BLOCK_FORTUNE, 2, Enchantments.FALL_PROTECTION, 2, Enchantments.PUNCH_ARROWS, 1,
                    Enchantments.QUICK_CHARGE, 2),
            tier(Enchantments.ALL_DAMAGE_PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.SHARPNESS, 5,
                    Enchantments.BLOCK_EFFICIENCY, 5, Enchantments.POWER_ARROWS, 5, Enchantments.MOB_LOOTING, 2,
                    Enchantments.BLOCK_FORTUNE, 3, Enchantments.FALL_PROTECTION, 4, Enchantments.FIRE_ASPECT, 1,
                    Enchantments.PUNCH_ARROWS, 1, Enchantments.QUICK_CHARGE, 2),
            tier(Enchantments.ALL_DAMAGE_PROTECTION, 4, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1,
                    Enchantments.SHARPNESS, 5, Enchantments.BLOCK_EFFICIENCY, 5, Enchantments.POWER_ARROWS, 5,
                    Enchantments.MOB_LOOTING, 3, Enchantments.BLOCK_FORTUNE, 3, Enchantments.FALL_PROTECTION, 4,
                    Enchantments.FIRE_ASPECT, 2, Enchantments.PUNCH_ARROWS, 2, Enchantments.FLAMING_ARROWS, 1,
                    Enchantments.QUICK_CHARGE, 3, Enchantments.MULTISHOT, 1));

    /** Todos, en el orden en que se eligen cuando dos no pueden ir juntos (el nivel 6: cada uno a su máximo). */
    private static final List<Enchantment> FULL = List.of(
            Enchantments.ALL_DAMAGE_PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.RESPIRATION,
            Enchantments.AQUA_AFFINITY, Enchantments.THORNS, Enchantments.SWIFT_SNEAK, Enchantments.FALL_PROTECTION,
            Enchantments.DEPTH_STRIDER, Enchantments.SOUL_SPEED, Enchantments.SHARPNESS, Enchantments.MOB_LOOTING,
            Enchantments.FIRE_ASPECT, Enchantments.KNOCKBACK, Enchantments.SWEEPING_EDGE, Enchantments.BLOCK_EFFICIENCY,
            Enchantments.BLOCK_FORTUNE, Enchantments.POWER_ARROWS, Enchantments.PUNCH_ARROWS, Enchantments.FLAMING_ARROWS,
            Enchantments.QUICK_CHARGE, Enchantments.MULTISHOT, Enchantments.LOYALTY, Enchantments.IMPALING,
            Enchantments.CHANNELING, Enchantments.FISHING_LUCK, Enchantments.FISHING_SPEED);

    /** Encanta la pieza según el nivel (1 a 6; fuera de eso, nada). */
    public static void apply(ItemStack stack, int level) {
        if (level <= 0 || stack.isEmpty()) return;
        if (level >= MAX) {
            for (Enchantment e : FULL) add(stack, e, e.getMaxLevel());
            return;
        }
        for (Map.Entry<Enchantment, Integer> e : TIERS.get(Math.min(level, TIERS.size()) - 1).entrySet()) add(stack, e.getKey(), e.getValue());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que aún no se ha podido encantar (piezas en el suelo o que llegan después)
    // ---------------------------------------------------------------------------------------------------------------

    /** Cuánto tiempo se siguen encantando las piezas de un set recién entregado (10 minutos). */
    static final long WINDOW_MS = 600_000;

    private record Pending(Set<Item> items, int level, long until) {}

    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();

    /** ¿Es una pieza de ese set aún sin encantar? */
    private static boolean wants(ItemStack stack, Set<Item> items) {
        return !stack.isEmpty() && items.contains(stack.getItem()) && !stack.isEnchanted();
    }

    /**
     * Encanta lo del set que lleve (inventario, armadura y la otra mano) y lo que haya tirado cerca, y apunta el set para
     * encantar durante un rato lo que le llegue después. Devuelve cuántas piezas encantó ya.
     */
    public static int enchantNow(ServerPlayer player, Set<Item> items, int level) {
        int n = 0;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!wants(stack, items)) continue;
            apply(stack, level);
            if (stack.isEnchanted()) n++;
        }
        for (ItemEntity drop : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8))) {
            ItemStack stack = drop.getItem().copy();
            if (!wants(stack, items)) continue;
            apply(stack, level);
            if (!stack.isEnchanted()) continue;
            drop.setItem(stack);
            n++;
        }
        player.containerMenu.broadcastChanges();
        synchronized (PENDING) {
            PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Pending(Set.copyOf(items), level, System.currentTimeMillis() + WINDOW_MS));
        }
        return n;
    }

    /** Lo que coge del suelo (antes de entrar al inventario). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        Pending p = pending(player, event.getItem().getItem());
        if (p == null) return;
        ItemStack stack = event.getItem().getItem().copy();
        apply(stack, p.level());
        if (stack.isEnchanted()) event.getItem().setItem(stack);
    }

    /** Cada segundo, lo del set sin encantar que ya tenga en el inventario (le llegó por otro camino). */
    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        List<Pending> list;
        synchronized (PENDING) {
            list = PENDING.get(player.getUUID());
            if (list == null) return;
            long now = System.currentTimeMillis();
            list.removeIf(p -> p.until() < now);
            if (list.isEmpty()) {
                PENDING.remove(player.getUUID());
                return;
            }
            list = new ArrayList<>(list);
        }
        boolean changed = false;
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            for (Pending p : list) {
                if (!wants(stack, p.items())) continue;
                apply(stack, p.level());
                changed |= stack.isEnchanted();
            }
        }
        if (changed) player.containerMenu.broadcastChanges();
    }

    private static Pending pending(ServerPlayer player, ItemStack stack) {
        synchronized (PENDING) {
            List<Pending> list = PENDING.get(player.getUUID());
            if (list == null) return null;
            long now = System.currentTimeMillis();
            for (Pending p : list) if (p.until() >= now && wants(stack, p.items())) return p;
            return null;
        }
    }

    private static void add(ItemStack stack, Enchantment e, int level) {
        if (!e.canEnchant(stack)) return;
        Map<Enchantment, Integer> has = EnchantmentHelper.getEnchantments(stack);
        if (has.containsKey(e) || !EnchantmentHelper.isEnchantmentCompatible(has.keySet(), e)) return;
        stack.enchant(e, Math.min(level, e.getMaxLevel()));
    }
}
