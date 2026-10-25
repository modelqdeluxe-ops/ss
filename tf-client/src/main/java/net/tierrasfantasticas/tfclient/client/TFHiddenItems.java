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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.139, el dueño: «no quiero que salgan los objetos del client en el JEI: ven todos los sets; solo OP y creativo
 * pueden ver nuestros objetos en el inventario creativo») Quién puede ver los objetos de tfclient en las listas de
 * objetos: el OP (permiso 2 o más, el que manda el servidor) o quien está en creativo. A los demás no les salen en JEI
 * (compat/jei/TFJeiPlugin) ni en la pestaña del creativo (TFItems.TAB). Se mira cada segundo y, si cambia, se rehace.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFHiddenItems {
    /** Los visores de objetos que nos dejan esconderlos (JEI…): nombre → (enseñar) → cuántos nuestros hay en su lista. */
    public interface Viewer {
        int apply(boolean show);
    }

    private static final Map<String, Viewer> VIEWERS = new LinkedHashMap<>();
    private static final Map<String, Boolean> APPLIED = new LinkedHashMap<>();
    /** Para la prueba en el juego: cuántos de los nuestros quedaron en cada visor tras el último cambio. */
    public static final Map<String, Integer> COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static Boolean tabState;
    /** Para la prueba en el juego (el jugador de la prueba es OP): null = lo de verdad. */
    public static volatile Boolean testForce;

    private TFHiddenItems() {}

    /** ¿Puede ver nuestros objetos? Sin jugador (menú), no. */
    public static boolean allowed() {
        if (testForce != null) return testForce;
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && (p.isCreative() || p.hasPermissions(2));
    }

    public static synchronized void viewer(String name, Viewer viewer) {
        if (viewer == null) {
            VIEWERS.remove(name);
        } else {
            VIEWERS.put(name, viewer);
        }
        APPLIED.remove(name);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.player.tickCount % 20 != 0) return;
        update();
    }

    public static synchronized void update() {
        boolean show = allowed();
        for (Map.Entry<String, Viewer> e : VIEWERS.entrySet()) {
            if (Boolean.valueOf(show).equals(APPLIED.get(e.getKey()))) continue;
            APPLIED.put(e.getKey(), show);
            int n = e.getValue().apply(show);
            COUNTS.put(e.getKey(), n);
            TFClient.LOGGER.info("TF Client: objetos de tfclient en {}: {} ({} en la lista)", e.getKey(), show ? "se ven" : "ocultos", n);
        }
        if (tabState != null && tabState != show) rebuildTabs();
        tabState = show;
    }

    /** La pestaña del creativo se guarda hecha: se borra lo guardado para que se rehaga al abrir el inventario. */
    private static void rebuildTabs() {
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
