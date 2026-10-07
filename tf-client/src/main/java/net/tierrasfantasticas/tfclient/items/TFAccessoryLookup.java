package net.tierrasfantasticas.tfclient.items;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Lee lo que lleva puesto un jugador en los huecos de accesorios (mod Accessories o Curios), por reflexión: ninguno de
 * los dos mods es obligatorio. Sirve en el cliente y en el servidor (lo usan el dibujo de la espalda y las alas que
 * planean, {@link TFWings}).
 */
public final class TFAccessoryLookup {
    /** Métodos ya buscados (se usan en cada fotograma). */
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private TFAccessoryLookup() {}

    /**
     * AccessoriesCapability (get / getOptionally, o AccessoriesAPI.getCapability en versiones viejas) → getEquipped /
     * getAllEquipped / getFirstEquipped, o si no, sus contenedores (getContainers → getAccessories, un Container).
     */
    public static void fromAccessories(LivingEntity entity, Predicate<ItemStack> wanted, List<ItemStack> out) throws Exception {
        Object capability = null;
        Class<?> api = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
        for (String name : new String[] {"get", "getOptionally"}) {
            Method m = find(api, name, LivingEntity.class);
            if (m != null) capability = unwrap(m.invoke(null, entity));
            if (capability != null) break;
        }
        if (capability == null) {
            try {
                Class<?> old = Class.forName("io.wispforest.accessories.api.AccessoriesAPI");
                Method m = find(old, "getCapability", LivingEntity.class);
                if (m != null) capability = unwrap(m.invoke(null, entity));
            } catch (ClassNotFoundException ignored) {
                // versión sin AccessoriesAPI
            }
        }
        if (capability == null) return;
        Method equipped = find(capability.getClass(), "getEquipped", Predicate.class);
        if (equipped == null) equipped = find(capability.getClass(), "getAllEquipped");
        if (equipped == null) equipped = find(capability.getClass(), "getFirstEquipped", Predicate.class);
        if (equipped != null) {
            Object result = equipped.getParameterCount() == 1 ? equipped.invoke(capability, wanted) : equipped.invoke(capability);
            collect(result, wanted, out, false);
            if (!out.isEmpty()) return;
        }
        Method containers = find(capability.getClass(), "getContainers");
        Object map = containers == null ? null : containers.invoke(capability);
        if (map instanceof Map<?, ?> byName) {
            for (Object container : byName.values()) {
                Method accessories = container == null ? null : find(container.getClass(), "getAccessories");
                collect(accessories == null ? null : accessories.invoke(container), wanted, out, false);
            }
        }
    }

    /**
     * CuriosApi.getCuriosInventory(entity) (o el helper antiguo) → findCurios(predicado) → [SlotResult].
     * onlyVisible: sin los huecos en los que el jugador apagó el dibujo.
     */
    public static void fromCurios(LivingEntity entity, Predicate<ItemStack> wanted, List<ItemStack> out, boolean onlyVisible)
            throws Exception {
        Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
        Object handler = null;
        Method inventory = find(api, "getCuriosInventory", LivingEntity.class);
        if (inventory != null) {
            handler = unwrap(inventory.invoke(null, entity));
        } else {
            Method helper = find(api, "getCuriosHelper");
            Object h = helper == null ? null : helper.invoke(null);
            Method handlerOf = h == null ? null : find(h.getClass(), "getCuriosHandler", LivingEntity.class);
            if (handlerOf != null) handler = unwrap(handlerOf.invoke(h, entity));
        }
        if (handler == null) return;
        Method all = find(handler.getClass(), "findCurios", Predicate.class);
        if (all == null) all = find(handler.getClass(), "findFirstCurio", Predicate.class);
        if (all != null) collect(all.invoke(handler, wanted), wanted, out, onlyVisible);
    }

    /** Recoge los objetos de una lista, un Optional, un Container o un resultado (SlotEntryReference / SlotResult). */
    private static void collect(@Nullable Object result, Predicate<ItemStack> wanted, List<ItemStack> out, boolean onlyVisible)
            throws Exception {
        result = unwrap(result);
        if (result == null) return;
        if (result instanceof Collection<?> list) {
            for (Object entry : list) collect(entry, wanted, out, onlyVisible);
            return;
        }
        if (result instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) collect(container.getItem(i), wanted, out, onlyVisible);
            return;
        }
        if (result instanceof ItemStack stack) {
            if (wanted.test(stack) && !out.contains(stack)) out.add(stack);
            return;
        }
        if (onlyVisible && !visible(result)) return; // el jugador apagó el dibujo de ese hueco
        Method stackOf = find(result.getClass(), "stack");
        if (stackOf != null) collect(stackOf.invoke(result), wanted, out, onlyVisible);
    }

    /** SlotResult.slotContext().visible(): el botón de Curios para ocultar lo que llevas puesto. */
    private static boolean visible(Object slotResult) {
        try {
            Method context = find(slotResult.getClass(), "slotContext");
            Object ctx = context == null ? null : context.invoke(slotResult);
            Method visible = ctx == null ? null : find(ctx.getClass(), "visible");
            return visible == null || !(visible.invoke(ctx) instanceof Boolean b) || b;
        } catch (Exception e) {
            return true;
        }
    }

    /** Optional / LazyOptional de Forge → su valor (o null). */
    @Nullable
    static Object unwrap(@Nullable Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        if (value instanceof LazyOptional<?> lazy) return lazy.resolve().orElse(null);
        return value;
    }

    /**
     * Método por nombre y tipos de parámetros, buscado en una clase o interfaz pública (la implementación suele ser
     * interna del otro mod y no se puede llamar desde fuera), o null.
     */
    @Nullable
    static Method find(Class<?> type, String name, Class<?>... params) {
        String key = type.getName() + '#' + name + Arrays.toString(params);
        return METHODS.computeIfAbsent(key, k -> {
            for (Class<?> c : supertypes(type)) {
                if (!Modifier.isPublic(c.getModifiers())) continue;
                try {
                    return Optional.of(c.getMethod(name, params));
                } catch (NoSuchMethodException | SecurityException ignored) {
                    // se prueba el siguiente
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** La clase, sus padres y todas sus interfaces. */
    private static List<Class<?>> supertypes(Class<?> type) {
        List<Class<?>> out = new ArrayList<>();
        Deque<Class<?>> queue = new ArrayDeque<>();
        queue.add(type);
        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (out.contains(c)) continue;
            out.add(c);
            if (c.getSuperclass() != null) queue.add(c.getSuperclass());
            queue.addAll(List.of(c.getInterfaces()));
        }
        return out;
    }
}
