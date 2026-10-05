package net.tierrasfantasticas.tfclient.items;

import com.mojang.logging.LogUtils;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Cosméticos puestos en los huecos de accesorios (mod Accessories o Curios, el que usa Artifacts). Así las alas y
 * mochilas van en la espalda y el pecho queda libre para la pechera.
 *
 * <p>Que acepten los objetos lo deciden las etiquetas de datos (accessories:back, accessories:cape, curios:back). Aquí
 * solo se leen los objetos puestos para dibujarlos, por reflexión: ninguno de los dos mods es obligatorio y, si alguno
 * cambia por dentro, simplemente no se dibuja (nunca rompe el juego).
 */
public final class TFAccessorySlots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Predicate<ItemStack> IS_BACK = stack ->
            stack.getItem() instanceof TFItemTypes.Cosmetic cosmetic && cosmetic.wornModel() != null;

    /** Métodos ya buscados (se usan en cada fotograma). */
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private static boolean accessoriesFailed;
    private static boolean curiosFailed;

    private TFAccessorySlots() {}

    /** Cosméticos de espalda que el jugador lleva en huecos de accesorios (vacío si no hay ninguno o no hay mods). */
    public static List<ItemStack> backCosmetics(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>();
        ModList mods = ModList.get();
        if (!accessoriesFailed && mods.isLoaded("accessories")) {
            try {
                fromAccessories(entity, out);
            } catch (Throwable t) {
                accessoriesFailed = true;
                LOGGER.warn("TF Client: no se pudieron leer los huecos de Accessories", t);
            }
        }
        if (out.isEmpty() && !curiosFailed && mods.isLoaded("curios")) {
            try {
                fromCurios(entity, out);
            } catch (Throwable t) {
                curiosFailed = true;
                LOGGER.warn("TF Client: no se pudieron leer los huecos de Curios", t);
            }
        }
        return out;
    }

    /**
     * Solo cliente: Accessories dibuja por su cuenta los objetos sin dibujante propio; los nuestros ya los dibuja
     * BackLayer, así que se le dice que no dibuje nada (si no, saldrían dos veces).
     */
    public static void disableAccessoriesDefaultRender(Iterable<Item> items) {
        if (!ModList.get().isLoaded("accessories")) return;
        try {
            Class<?> registry = Class.forName("io.wispforest.accessories.api.client.AccessoriesRendererRegistry");
            Method none = find(registry, "registerNoRenderer", Item.class);
            if (none == null) return;
            for (Item item : items) none.invoke(null, item);
        } catch (Throwable t) {
            LOGGER.warn("TF Client: no se pudo quitar el dibujo por defecto de Accessories", t);
        }
    }

    /** io.wispforest.accessories.api.AccessoriesCapability.get(entity).getEquipped(predicado) → [SlotEntryReference]. */
    private static void fromAccessories(LivingEntity entity, List<ItemStack> out) throws Exception {
        Class<?> api = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
        Object capability = null;
        Method get = find(api, "get", LivingEntity.class);
        if (get != null) capability = get.invoke(null, entity);
        if (capability == null) {
            Method optionally = find(api, "getOptionally", LivingEntity.class);
            if (optionally != null) capability = unwrap(optionally.invoke(null, entity));
        }
        if (capability == null) return;
        Method equipped = find(capability.getClass(), "getEquipped", Predicate.class);
        if (equipped != null) {
            collect(equipped.invoke(capability, IS_BACK), out, false);
            return;
        }
        Method first = find(capability.getClass(), "getFirstEquipped", Predicate.class);
        if (first != null) collect(first.invoke(capability, IS_BACK), out, false);
    }

    /** CuriosApi.getCuriosInventory(entity) (o el helper antiguo) → findCurios(predicado) → [SlotResult]. */
    private static void fromCurios(LivingEntity entity, List<ItemStack> out) throws Exception {
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
        if (all != null) {
            collect(all.invoke(handler, IS_BACK), out, true);
            return;
        }
        Method first = find(handler.getClass(), "findFirstCurio", Predicate.class);
        if (first != null) collect(first.invoke(handler, IS_BACK), out, true);
    }

    /** Recoge los objetos de una lista, un Optional o un único resultado (SlotEntryReference / SlotResult). */
    private static void collect(@Nullable Object result, List<ItemStack> out, boolean curios) throws Exception {
        result = unwrap(result);
        if (result == null) return;
        if (result instanceof Collection<?> list) {
            for (Object entry : list) collect(entry, out, curios);
            return;
        }
        if (result instanceof ItemStack stack) {
            if (IS_BACK.test(stack)) out.add(stack);
            return;
        }
        if (curios && !visible(result)) return; // el jugador apagó el dibujo de ese hueco
        Method stackOf = find(result.getClass(), "stack");
        if (stackOf == null) return;
        Object stack = stackOf.invoke(result);
        if (stack instanceof ItemStack s && IS_BACK.test(s)) out.add(s);
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
    private static Object unwrap(@Nullable Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        if (value instanceof LazyOptional<?> lazy) return lazy.resolve().orElse(null);
        return value;
    }

    /**
     * Método por nombre y tipos de parámetros, buscado en una clase o interfaz pública (la implementación suele ser
     * interna del otro mod y no se puede llamar desde fuera), o null.
     */
    @Nullable
    private static Method find(Class<?> type, String name, Class<?>... params) {
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
            if (c == null || out.contains(c)) continue;
            out.add(c);
            if (c.getSuperclass() != null) queue.add(c.getSuperclass());
            queue.addAll(List.of(c.getInterfaces()));
        }
        return out;
    }
}
