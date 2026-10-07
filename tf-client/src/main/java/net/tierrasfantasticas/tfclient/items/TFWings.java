package net.tierrasfantasticas.tfclient.items;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Las alas de los sets planean como unas élitros, sin gastarse nunca (las mochilas, capas, colas y demás no).
 *
 * <p>Puestas en el pecho lo hace el propio objeto ({@link TFItemTypes.Cosmetic#canElytraFly}). Puestas en el hueco de
 * la espalda de Accessories o Curios (con la pechera en el pecho), Minecraft no las mira: ItemStackMixin pregunta aquí
 * cuando el objeto del pecho no puede planear.
 */
public final class TFWings {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Predicate<ItemStack> IS_WINGS = TFWings::isWings;
    /** Última respuesta por jugador en este tick: Minecraft pregunta varias veces seguidas. */
    private static final Map<LivingEntity, long[]> CACHE = new WeakHashMap<>();
    private static boolean accessoriesFailed;
    private static boolean curiosFailed;

    private TFWings() {}

    public static boolean isWings(ItemStack stack) {
        return stack.getItem() instanceof TFItemTypes.Cosmetic cosmetic && cosmetic.glides();
    }

    /** El objeto del pecho de este jugador no planea, pero lleva unas alas en el hueco de la espalda. */
    public static boolean fromBackSlot(ItemStack stack, LivingEntity entity) {
        if (!(entity instanceof Player) || entity.getItemBySlot(EquipmentSlot.CHEST) != stack) return false;
        long tick = entity.level().getGameTime();
        synchronized (CACHE) {
            long[] last = CACHE.get(entity);
            if (last != null && last[0] == tick) return last[1] != 0;
            boolean worn = wornInSlots(entity);
            CACHE.put(entity, new long[] {tick, worn ? 1 : 0});
            return worn;
        }
    }

    private static boolean wornInSlots(LivingEntity entity) {
        ModList mods = ModList.get();
        List<ItemStack> out = new ArrayList<>(1);
        if (!accessoriesFailed && mods.isLoaded("accessories")) {
            try {
                TFAccessoryLookup.fromAccessories(entity, IS_WINGS, out);
            } catch (Throwable t) {
                accessoriesFailed = true;
                LOGGER.warn("TF Client: alas: no se pudieron leer los huecos de Accessories", t);
            }
        }
        if (out.isEmpty() && !curiosFailed && mods.isLoaded("curios")) {
            try {
                TFAccessoryLookup.fromCurios(entity, IS_WINGS, out, false);
            } catch (Throwable t) {
                curiosFailed = true;
                LOGGER.warn("TF Client: alas: no se pudieron leer los huecos de Curios", t);
            }
        }
        return !out.isEmpty();
    }
}
