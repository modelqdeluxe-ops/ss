/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.150, el dueño: «los objetos de las crates y rangos no se pueden encantar… que se les puedan meter encantamientos
 * vanilla independientemente de los que tengan, menos irrompibilidad porque ya son irrompibles… sin errores,
 * vulnerabilidades o pérdidas de nuestros atributos») Las piezas de los sets ({@link TFItem}) se encantan como las de
 * Minecraft, aunque ya lleven encantamientos:
 * <ul>
 *   <li>Mesa de encantamientos: Minecraft no deja poner lo que ya está encantado; las nuestras sí, mientras quede algún
 *   encantamiento que puedan llevar ({@code mixin.EnchantTableMixin}). La mesa solo ofrece los que no tienen y no
 *   chocan con los suyos ({@link #filter}), así nunca hay dos del mismo ni se pisan.</li>
 *   <li>Yunque con libro ({@link #onAnvil}): suma los del libro que la pieza admite, sin bajar nunca un nivel (los de
 *   los rangos que pasan del máximo de Minecraft se quedan); nunca «demasiado caro». El resultado es la misma pieza
 *   copiada (sus atributos, su dueño y todo lo demás), solo con más encantamientos.</li>
 *   <li>Nunca Irrompibilidad ni Reparación (no se gastan: no sirven de nada). Una pieza nuestra no se repara, no se
 *   junta con otra ni se sacrifica en el yunque, y no va a la piedra de afilar (quitaría los de su rango y daría
 *   experiencia gratis).</li>
 * </ul>
 * Los sets de rango que ya llevan todos al máximo (Celestial y Fantástico) no tienen nada que añadir: ni la mesa ni el
 * yunque les hacen nada.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFEnchanting {
    private TFEnchanting() {}

    /** Los que no sirven en una pieza que no se gasta. */
    public static boolean useless(Enchantment e) {
        return e == Enchantments.UNBREAKING || e == Enchantments.MENDING;
    }

    private static boolean ours(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof TFItem;
    }

    /** ¿Puede llevar este encantamiento ahora (lo admite, no lo tiene y no choca con los que tiene)? */
    private static boolean fits(ItemStack stack, Map<Enchantment, Integer> have, Enchantment e) {
        if (e == null || useless(e) || have.containsKey(e) || !stack.canApplyAtEnchantingTable(e)) return false;
        for (Enchantment o : have.keySet()) if (o != e && !e.isCompatibleWith(o)) return false;
        return true;
    }

    /** Mesa de encantamientos: ¿la acepta? null = la decide Minecraft (no es nuestra). */
    public static Boolean tableAccepts(ItemStack stack) {
        if (!ours(stack)) return null;
        if (stack.getCount() != 1 || stack.getItem().getEnchantmentValue() <= 0) return false;
        Map<Enchantment, Integer> have = EnchantmentHelper.getEnchantments(stack);
        for (Enchantment e : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (!e.isTreasureOnly() && e.isDiscoverable() && fits(stack, have, e)) return true;
        }
        return false;
    }

    /** Lo que ofrece la mesa a una pieza nuestra: sin los inútiles ni los que ya tiene o chocan con los suyos. */
    public static void filter(ItemStack stack, List<EnchantmentInstance> offered) {
        if (!ours(stack)) return;
        Map<Enchantment, Integer> have = EnchantmentHelper.getEnchantments(stack);
        offered.removeIf(i -> !fits(stack, have, i.enchantment));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft(), right = event.getRight();
        if (!ours(left) && !ours(right)) return;
        // una pieza nuestra a la derecha se gastaría; a la izquierda, solo con un libro (nada de reparar ni juntar dos)
        if (!ours(left) || !right.is(Items.ENCHANTED_BOOK)) {
            event.setCanceled(true);
            return;
        }
        Map<Enchantment, Integer> have = EnchantmentHelper.getEnchantments(left);
        Map<Enchantment, Integer> out = new LinkedHashMap<>(have);
        int cost = 0;
        for (Map.Entry<Enchantment, Integer> en : EnchantmentHelper.getEnchantments(right).entrySet()) {
            Enchantment e = en.getKey();
            if (e == null || useless(e) || !left.canApplyAtEnchantingTable(e)) continue;
            int book = Math.min(en.getValue(), e.getMaxLevel());
            Integer cur = out.get(e);
            int level;
            if (cur == null) {
                if (!fits(left, out, e)) continue;
                level = book;
            } else {
                // como Minecraft (iguales: uno más; si no, el mayor), sin pasar del máximo y sin bajar nunca lo que lleva
                level = Math.max(cur, cur == book ? Math.min(cur + 1, e.getMaxLevel()) : book);
                if (level <= cur) continue;
            }
            out.put(e, level);
            int rarity = switch (e.getRarity()) {
                case COMMON -> 1;
                case UNCOMMON -> 2;
                case RARE -> 4;
                case VERY_RARE -> 8;
            };
            cost += Math.max(1, rarity / 2) * level;
        }
        if (out.equals(have)) {
            event.setCanceled(true);
            return;
        }
        ItemStack result = left.copy();
        EnchantmentHelper.setEnchantments(out, result);
        String name = event.getName();
        if (name != null && !name.isBlank() && !name.equals(left.getHoverName().getString())) {
            result.setHoverName(Component.literal(name));
            cost++;
        }
        int base = Math.max(left.getBaseRepairCost(), right.getBaseRepairCost());
        result.setRepairCost(AnvilMenu.calculateIncreasedRepairCost(base));
        event.setOutput(result);
        event.setCost(Math.max(1, Math.min(39, cost + left.getBaseRepairCost() + right.getBaseRepairCost())));
        event.setMaterialCost(1);
    }

    @SubscribeEvent
    public static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        if (ours(event.getTopItem()) || ours(event.getBottomItem())) event.setCanceled(true);
    }
}
