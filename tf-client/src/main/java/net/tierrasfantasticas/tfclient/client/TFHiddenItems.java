/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los objetos de tfclient no salen en ninguna lista de objetos, para nadie. (1.3.139: solo se escondían a quien no era
 * OP ni estaba en creativo; 1.3.168, el dueño: «elimina por completo que los items del mod salgan en el inventario
 * creativo… ni operadores ni miembros con creativo lo podrán ver… solo por comando del tf».) El mod ya no tiene pestaña
 * en el creativo (TFItems) y aquí se quitan de los visores que los listan desde el registro (JEI: compat/jei). Lo que de
 * verdad lo impide es el servidor (items/TFCreativeGuard): aunque un cliente viejo o trucado los enseñe, no se pueden sacar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFHiddenItems {
    /** Los visores de objetos que nos dejan esconderlos (JEI…): (enseñar) → cuántos nuestros quedan en su lista. */
    public interface Viewer {
        int apply(boolean show);
    }

    private static final Map<String, Viewer> VIEWERS = new LinkedHashMap<>();
    /** Para la prueba en el juego: cuántos de los nuestros quedaron en cada visor tras esconderlos. */
    public static final Map<String, Integer> COUNTS = new java.util.concurrent.ConcurrentHashMap<>();

    private TFHiddenItems() {}

    public static synchronized void viewer(String name, Viewer viewer) {
        if (viewer == null) {
            VIEWERS.remove(name);
            COUNTS.remove(name);
        } else {
            VIEWERS.put(name, viewer);
            COUNTS.remove(name);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.player.tickCount % 20 != 0) return;
        update();
    }

    /** Esconde lo nuestro de cada visor que aún lo tenga (al registrarse o si le volvió a aparecer). */
    public static synchronized void update() {
        for (Map.Entry<String, Viewer> e : VIEWERS.entrySet()) {
            Integer before = COUNTS.get(e.getKey());
            if (before != null && before == 0) continue;
            int n = e.getValue().apply(false);
            COUNTS.put(e.getKey(), n);
            if (before == null || before != n) TFClient.LOGGER.info("TF Client: objetos de tfclient en {}: ocultos ({} en la lista)", e.getKey(), n);
        }
    }

    /** Para la prueba en el juego: cuántos objetos nuestros hay en TODAS las pestañas del creativo (-1: aún no). */
    public static volatile int testTabCount = -1;

    /**
     * Para la prueba: en el hilo del cliente, rehace las pestañas como al abrir el inventario creativo siendo OP (con las
     * pestañas de operador) y cuenta lo nuestro en todas, el buscador incluido.
     */
    public static void requestTabCount() {
        testTabCount = -1;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null || mc.level == null) return;
            update();
            forgetTabs();
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            int n = 0;
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                for (ItemStack s : tab.getDisplayItems()) if (ours(s)) n++;
                for (ItemStack s : tab.getSearchTabDisplayItems()) if (ours(s)) n++;
            }
            testTabCount = n;
        });
    }

    private static boolean ours(ItemStack s) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id != null && TFClient.MOD_ID.equals(id.getNamespace());
    }

    /** Las pestañas se guardan hechas: se borra lo guardado para que se rehagan. */
    private static void forgetTabs() {
        try {
            for (Field f : CreativeModeTabs.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && f.getType() == CreativeModeTab.ItemDisplayParameters.class) {
                    f.setAccessible(true);
                    f.set(null, null);
                }
            }
        } catch (Throwable t) {
            TFClient.LOGGER.warn("TF Client: no se pudo rehacer el inventario creativo: {}", t.toString());
        }
    }
}
