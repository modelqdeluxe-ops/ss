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
 * 10 segundos seguidos: luego se pliegan, caes, y a los 3 segundos se pueden volver a abrir (sin barra en pantalla).
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
    /** Lo más que se puede planear seguido con unas alas (10 s): la «barra» del planeo. */
    public static final int GLIDE_TICKS = 200;
    /** Enfriamiento después de gastar el planeo (3 s); luego la barra vuelve a estar llena. */
    public static final int COOLDOWN_TICKS = 60;
    /**
     * Jugadores que gastaron el planeo → su {@code tickCount} cuando se les agotó. Caduca solo por tiempo (no hace
     * falta tocar el suelo ni que nadie lo borre), así nunca se queda bloqueado: al morir, cambiar de dimensión o
     * reconectar el jugador es otro objeto y empieza con la barra llena; en el mismo objeto su tickCount sigue
     * subiendo y a los 3 s se olvida. Cliente y servidor guardan cada uno el suyo (con su propia entidad) y aplican la
     * misma regla, así que coinciden.
     */
    private static final Map<LivingEntity, Integer> SPENT_AT = new WeakHashMap<>();

    private TFWings() {}

    /**
     * ¿Puede seguir planeando con unas alas? Mientras planeas se gasta la barra (10 s seguidos); al agotarse las alas
     * se pliegan, caes y empieza un enfriamiento de 3 s; después la barra vuelve a estar llena y se puede planear otra
     * vez (también en el aire). En el suelo, en el agua o montado nunca se planea (así no se queda «planeando» de pie),
     * pero eso no toca el enfriamiento. Lo mismo en el cliente y en el servidor, para que los dos vean lo mismo.
     *
     * <p>Antes el «gastado» solo se borraba aquí al estar en el suelo, pero Minecraft no pregunta si se puede planear
     * estando en el suelo (ni al saltar ni en el vuelo), así que nunca se borraba y ya no se volvía a planear.
     */
    public static boolean canGlide(LivingEntity entity) {
        synchronized (SPENT_AT) {
            if (cooling(entity)) return false;
            if (entity.onGround() || entity.isInWater() || entity.isPassenger()) return false;
            if (entity.isFallFlying() && entity.getFallFlyingTicks() >= GLIDE_TICKS) {
                SPENT_AT.put(entity, entity.tickCount);
                return false;
            }
            return true;
        }
    }

    /** Está en el enfriamiento de 3 s tras gastar el planeo (lo olvida en cuanto pasa). Llamar con SPENT_AT cogido. */
    private static boolean cooling(LivingEntity entity) {
        Integer at = SPENT_AT.get(entity);
        if (at == null) return false;
        int since = entity.tickCount - at;
        // since < 0: el contador volvió atrás (no debería); mejor dejar planear que bloquear
        if (since >= 0 && since < COOLDOWN_TICKS) return true;
        SPENT_AT.remove(entity);
        return false;
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
