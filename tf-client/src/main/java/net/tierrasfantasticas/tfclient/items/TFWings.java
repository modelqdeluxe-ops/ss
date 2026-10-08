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
 * Las alas de los sets planean como unas élitros, sin gastarse nunca (las mochilas, capas, colas y demás no), hasta
 * 10 segundos seguidos: luego se pliegan hasta tocar el suelo (TFGlideHud enseña el tiempo que queda).
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
    /** Lo más que se puede planear seguido con unas alas (10 s). */
    public static final int GLIDE_TICKS = 200;
    /** Jugadores que ya gastaron su planeo y no pueden volver a planear hasta tocar el suelo (o el agua). */
    private static final Map<LivingEntity, Boolean> SPENT = new WeakHashMap<>();

    private TFWings() {}

    /**
     * ¿Puede seguir planeando con unas alas? Hasta 10 s seguidos; después las alas se pliegan y no vuelven a abrirse
     * hasta tocar el suelo o el agua. En el suelo nunca se planea (así no se queda «planeando» de pie). Lo mismo en el
     * cliente y en el servidor, para que los dos vean lo mismo.
     */
    public static boolean canGlide(LivingEntity entity) {
        synchronized (SPENT) {
            if (entity.onGround() || entity.isInWater() || entity.isPassenger()) {
                SPENT.remove(entity);
                return false;
            }
            if (SPENT.containsKey(entity)) return false;
            if (entity.isFallFlying() && entity.getFallFlyingTicks() >= GLIDE_TICKS) {
                SPENT.put(entity, Boolean.TRUE);
                return false;
            }
            return true;
        }
    }

    /** Ya gastó el planeo y aún no ha tocado el suelo. */
    public static boolean spent(LivingEntity entity) {
        synchronized (SPENT) {
            return SPENT.containsKey(entity);
        }
    }

    /** Está planeando con unas alas de los sets (no con unas élitros normales). */
    public static boolean glidingWithWings(LivingEntity entity) {
        if (!entity.isFallFlying()) return false;
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (isWings(chest)) return true;
        return !chest.getItem().canElytraFly(chest, entity) && wornInSlots(entity);
    }

    public static boolean isWings(ItemStack stack) {
        return stack.getItem() instanceof TFItemTypes.Cosmetic cosmetic && cosmetic.glides();
    }

    /** El objeto del pecho de este jugador no planea, pero lleva unas alas en el hueco de la espalda. */
    public static boolean fromBackSlot(ItemStack stack, LivingEntity entity) {
        if (!(entity instanceof Player) || entity.getItemBySlot(EquipmentSlot.CHEST) != stack) return false;
        long tick = entity.level().getGameTime();
        synchronized (CACHE) {
            long[] last = CACHE.get(entity);
            if (last != null && last[0] == tick) return last[1] != 0 && canGlide(entity);
            boolean worn = wornInSlots(entity);
            CACHE.put(entity, new long[] {tick, worn ? 1 : 0});
            return worn && canGlide(entity);
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
