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
 *   junta con otra ni se sacrifica en el yunque. En la piedra de afilar (1.3.168) solo se desencantan las de las crates
 *   ({@link #onGrindstone}); las de los rangos, no (quitaría los de su rango).</li>
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

    /** ¿Puede llevar este encantamiento ahora (lo admite, no lo tiene y no choca con los que tiene)? En la mesa. */
    private static boolean fits(ItemStack stack, Map<Enchantment, Integer> have, Enchantment e) {
        if (e == null || useless(e) || have.containsKey(e) || !stack.canApplyAtEnchantingTable(e)) return false;
        return compatible(have, e);
    }

    private static boolean compatible(Map<Enchantment, Integer> have, Enchantment e) {
        for (Enchantment o : have.keySet()) if (o != e && !e.isCompatibleWith(o)) return false;
        return true;
    }

    /**
     * (1.3.168, el dueño: «los encantamientos de algunas armaduras de los sets no son compatibles, por ejemplo el de espinas
     * solo me dejó ponerlo al peto») ¿Lo admite con un libro en el yunque? Con las reglas del YUNQUE de Minecraft
     * (Enchantment.canEnchant), no las de la mesa: Espinas en cualquier pieza de armadura (en la mesa solo sale para el
     * peto), Filo, Castigo y Perdición de los artrópodos en las hachas, Pies de escarcha y demás de libro… Y Espinas también
     * en los cascos 3D que dan armadura (Cosmetic con armadura: el yelmo del Void, el casco de Eterno…).
     */
    static boolean anvilAccepts(ItemStack stack, Enchantment e) {
        if (e == null || useless(e)) return false;
        if (e.canEnchant(stack)) return true;
        return e == Enchantments.THORNS && stack.canApplyAtEnchantingTable(Enchantments.ALL_DAMAGE_PROTECTION);
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
            if (!anvilAccepts(left, e)) continue;
            int book = Math.min(en.getValue(), e.getMaxLevel());
            Integer cur = out.get(e);
            int level;
            if (cur == null) {
                if (!compatible(out, e)) continue;
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

    /**
     * (1.3.168, el dueño: «haz que se puedan desencantar sin perder los atributos; ojo, esta feature es solo para las
     * crates, para los rangos no») La piedra de afilar con una pieza de una crate, sola: sale la MISMA pieza (sus
     * atributos —que son del objeto, no de los encantamientos—, su dueño, su pedido, su nombre: todo lo que lleva) sin los
     * encantamientos, menos las maldiciones (como en Minecraft), y da la experiencia de Minecraft. Nunca:
     * <ul>
     *   <li>las de los rangos (perderían los encantamientos de su rango; set con {@code "rank": true});</li>
     *   <li>dos piezas a la vez (Minecraft las funde en un objeto NUEVO: se perdería el dueño, el pedido y todo);</li>
     *   <li>una pieza sin nada que quitar.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem(), bottom = event.getBottomItem();
        if (!ours(top) && !ours(bottom)) return;
        ItemStack piece = ours(top) ? top : bottom;
        ItemStack other = piece == top ? bottom : top;
        if (!other.isEmpty() || !grindable(piece)) {
            event.setCanceled(true);
            return;
        }
        Map<Enchantment, Integer> keep = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> en : EnchantmentHelper.getEnchantments(piece).entrySet()) {
            if (en.getKey() != null && en.getKey().isCurse()) keep.put(en.getKey(), en.getValue());
        }
        ItemStack result = piece.copy();
        result.setCount(1);
        EnchantmentHelper.setEnchantments(keep, result);
        result.setRepairCost(0);
        event.setOutput(result);
        // -1: la experiencia la calcula Minecraft al sacarla (la de los encantamientos quitados)
        event.setXp(-1);
    }

    /** ¿Se puede desencantar en la piedra de afilar? Una pieza de una crate (no de un rango) con algo que no sea maldición. */
    public static boolean grindable(ItemStack stack) {
        if (!ours(stack) || stack.getCount() != 1) return false;
        TFSets.SetDef set = TFItemTypes.setOf(stack.getItem());
        if (set == null || set.rank()) return false;
        for (Enchantment e : EnchantmentHelper.getEnchantments(stack).keySet()) if (e != null && !e.isCurse()) return true;
        return false;
    }
}
