package net.tierrasfantasticas.tfclient.items;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Cosméticos puestos en los huecos de accesorios (mod Accessories o Curios, el que usa Artifacts). Así las alas y
 * mochilas van en la espalda y el pecho queda libre para la pechera.
 *
 * <p>Que acepten los objetos lo deciden las etiquetas de datos (accessories:back, accessories:cape, curios:back). Para
 * dibujarlos hay dos caminos, los dos por reflexión (ninguno de los dos mods es obligatorio y si algo cambia por dentro
 * no se rompe el juego):
 * <ol>
 *   <li>El oficial: se registra un dibujante para cada objeto en el mod de accesorios, que lo llama cuando el hueco
 *   está visible. Es lo que hacen los mods de accesorios normales.</li>
 *   <li>Si eso no se puede, BackLayer busca los objetos puestos en los huecos y los dibuja él.</li>
 * </ol>
 * En el registro del juego (latest.log) queda una línea «TF Client: accesorios ...» con el camino que se usa.
 */
public final class TFAccessorySlots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Predicate<ItemStack> IS_BACK = stack ->
            stack.getItem() instanceof TFItemTypes.Cosmetic cosmetic && cosmetic.wornModel() != null;

    /** Dibuja un cosmético en la espalda de un modelo humanoide (lo implementa BackLayer). */
    public interface BackRenderer {
        void render(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, HumanoidModel<?> model,
                    boolean armored);
    }

    /** Ya dibuja Accessories / Curios con nuestro dibujante: BackLayer no tiene que buscar en sus huecos. */
    private static boolean accessoriesDraws;
    private static boolean curiosDraws;
    private static boolean accessoriesFailed;
    private static boolean curiosFailed;
    private static boolean loggedAccessories;
    private static boolean loggedCurios;

    private TFAccessorySlots() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Camino oficial: dibujantes registrados en el mod de accesorios (solo cliente, en FMLClientSetupEvent)
    // ---------------------------------------------------------------------------------------------------------------

    public static void registerRenderers(List<Item> items, BackRenderer renderer) {
        ModList mods = ModList.get();
        if (mods.isLoaded("accessories")) {
            try {
                accessoriesDraws = registerAccessories(items, renderer);
            } catch (Throwable t) {
                LOGGER.warn("TF Client: accesorios: no se pudo registrar el dibujante en Accessories", t);
            }
            LOGGER.info("TF Client: accesorios: Accessories {}", accessoriesDraws
                    ? "dibuja las alas con nuestro dibujante" : "sin dibujante; se buscan en sus huecos");
        }
        // Con Accessories, Curios suele ser su capa de compatibilidad: registrar en los dos los dibujaría dos veces.
        if (!accessoriesDraws && mods.isLoaded("curios")) {
            try {
                curiosDraws = registerCurios(items, renderer);
            } catch (Throwable t) {
                LOGGER.warn("TF Client: accesorios: no se pudo registrar el dibujante en Curios", t);
            }
            LOGGER.info("TF Client: accesorios: Curios {}", curiosDraws
                    ? "dibuja las alas con nuestro dibujante" : "sin dibujante; se buscan en sus huecos");
        }
    }

    /**
     * AccessoriesRendererRegistry.registerRenderer(Item, Supplier&lt;AccessoryRenderer&gt;); AccessoryRenderer.render(
     * ItemStack, SlotReference, PoseStack, EntityModel, MultiBufferSource, int luz, 6 × float).
     */
    private static boolean registerAccessories(List<Item> items, BackRenderer renderer) throws Exception {
        Class<?> registry = Class.forName("io.wispforest.accessories.api.client.AccessoriesRendererRegistry");
        Class<?> api = Class.forName("io.wispforest.accessories.api.client.AccessoryRenderer");
        Method register = TFAccessoryLookup.find(registry, "registerRenderer", Item.class, Supplier.class);
        if (register == null) return false;
        Object proxy = proxy(api, (method, args) -> {
            if (!"render".equals(method.getName()) || args == null) return null;
            ItemStack stack = arg(args, ItemStack.class);
            PoseStack pose = arg(args, PoseStack.class);
            MultiBufferSource buffers = arg(args, MultiBufferSource.class);
            HumanoidModel<?> model = arg(args, HumanoidModel.class);
            Integer light = arg(args, Integer.class);
            LivingEntity entity = entityOf(arg(args, 1));
            if (stack == null || pose == null || buffers == null || model == null || light == null) return null;
            if (entity != null && entity.isInvisible()) return null;
            renderer.render(pose, buffers, light, stack, model, entity != null && armored(entity));
            return null;
        });
        Supplier<Object> supplier = () -> proxy;
        for (Item item : items) register.invoke(null, item, supplier);
        return true;
    }

    /**
     * CuriosRendererRegistry.register(Item, Supplier&lt;ICurioRenderer&gt;); ICurioRenderer.render(ItemStack,
     * SlotContext, PoseStack, RenderLayerParent, MultiBufferSource, int luz, 6 × float). Curios ya no lo llama si el
     * jugador ocultó el hueco.
     */
    private static boolean registerCurios(List<Item> items, BackRenderer renderer) throws Exception {
        Class<?> registry = Class.forName("top.theillusivec4.curios.api.client.CuriosRendererRegistry");
        Class<?> api = Class.forName("top.theillusivec4.curios.api.client.ICurioRenderer");
        Method register = TFAccessoryLookup.find(registry, "register", Item.class, Supplier.class);
        if (register == null) return false;
        Object proxy = proxy(api, (method, args) -> {
            if (!"render".equals(method.getName()) || args == null) return null;
            ItemStack stack = arg(args, ItemStack.class);
            PoseStack pose = arg(args, PoseStack.class);
            MultiBufferSource buffers = arg(args, MultiBufferSource.class);
            RenderLayerParent<?, ?> parent = arg(args, RenderLayerParent.class);
            Integer light = arg(args, Integer.class);
            LivingEntity entity = entityOf(arg(args, 1));
            if (stack == null || pose == null || buffers == null || parent == null || light == null) return null;
            if (!(parent.getModel() instanceof HumanoidModel<?> model)) return null;
            if (entity != null && entity.isInvisible()) return null;
            renderer.render(pose, buffers, light, stack, model, entity != null && armored(entity));
            return null;
        });
        Supplier<Object> supplier = () -> proxy;
        for (Item item : items) register.invoke(null, item, supplier);
        return true;
    }

    private interface Handler {
        @Nullable
        Object call(Method method, @Nullable Object[] args) throws Throwable;
    }

    /** Implementa una interfaz del otro mod: los métodos por defecto siguen igual, «render» llama a nuestro código. */
    private static Object proxy(Class<?> api, Handler handler) {
        InvocationHandler invocation = new InvocationHandler() {
            @Override
            public Object invoke(Object self, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "hashCode":
                        if (method.getParameterCount() == 0) return System.identityHashCode(self);
                        break;
                    case "equals":
                        if (method.getParameterCount() == 1) return self == args[0];
                        break;
                    case "toString":
                        if (method.getParameterCount() == 0) return "TFClient" + api.getSimpleName();
                        break;
                    default:
                        break;
                }
                if (method.isDefault() && !"render".equals(method.getName())) {
                    try {
                        return InvocationHandler.invokeDefault(self, method, args);
                    } catch (Throwable t) {
                        // p. ej. shouldRender(boolean): sin el método por defecto, se dibuja si el mod iba a dibujar
                        if (method.getReturnType() == boolean.class) {
                            Boolean given = args == null ? null : arg(args, Boolean.class);
                            return given == null || given;
                        }
                        return defaultValue(method.getReturnType());
                    }
                }
                try {
                    Object result = handler.call(method, args);
                    return result != null ? result : defaultValue(method.getReturnType());
                } catch (Throwable t) {
                    LOGGER.warn("TF Client: accesorios: error al dibujar", t);
                    return defaultValue(method.getReturnType());
                }
            }
        };
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, invocation);
    }

    @Nullable
    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == long.class) return 0L;
        return null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> T arg(Object[] args, Class<T> type) {
        for (Object a : args) {
            if (type.isInstance(a)) return (T) a;
        }
        return null;
    }

    @Nullable
    private static Object arg(Object[] args, int index) {
        return index < args.length ? args[index] : null;
    }

    /** SlotReference.entity() / SlotContext.entity(): el que lleva puesto el objeto. */
    @Nullable
    private static LivingEntity entityOf(@Nullable Object slot) {
        if (slot == null) return null;
        try {
            Method entity = TFAccessoryLookup.find(slot.getClass(), "entity");
            Object e = entity == null ? null : entity.invoke(slot);
            return e instanceof LivingEntity living ? living : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean armored(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ArmorItem;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Camino de respaldo: buscar los objetos puestos en los huecos (lo usa BackLayer)
    // ---------------------------------------------------------------------------------------------------------------

    /** Cosméticos de espalda en huecos de accesorios que no dibuja ya el propio mod (vacío si no hay ninguno). */
    public static List<ItemStack> backCosmetics(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>();
        ModList mods = ModList.get();
        if (!accessoriesDraws && !accessoriesFailed && mods.isLoaded("accessories")) {
            try {
                TFAccessoryLookup.fromAccessories(entity, IS_BACK, out);
                if (!loggedAccessories && !out.isEmpty()) {
                    loggedAccessories = true;
                    LOGGER.info("TF Client: accesorios: encontrado en los huecos de Accessories {}", out);
                }
            } catch (Throwable t) {
                accessoriesFailed = true;
                LOGGER.warn("TF Client: accesorios: no se pudieron leer los huecos de Accessories", t);
            }
        }
        if (out.isEmpty() && !accessoriesDraws && !curiosDraws && !curiosFailed && mods.isLoaded("curios")) {
            try {
                TFAccessoryLookup.fromCurios(entity, IS_BACK, out, true);
                if (!loggedCurios && !out.isEmpty()) {
                    loggedCurios = true;
                    LOGGER.info("TF Client: accesorios: encontrado en los huecos de Curios {}", out);
                }
            } catch (Throwable t) {
                curiosFailed = true;
                LOGGER.warn("TF Client: accesorios: no se pudieron leer los huecos de Curios", t);
            }
        }
        return out;
    }
}
