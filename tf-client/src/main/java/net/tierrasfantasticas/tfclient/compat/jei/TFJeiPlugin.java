/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.compat.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.client.TFHiddenItems;
import net.tierrasfantasticas.tfclient.items.TFItems;

/**
 * (1.3.139, el dueño: «el mod sale en el JEI: ven todos sus sets; solo OP y creativo pueden ver nuestros objetos»)
 * JEI carga esta clase solo si está instalado (por la anotación); el mod no depende de JEI. Quita de su lista los objetos
 * de tfclient mientras el jugador no pueda verlos ({@link TFHiddenItems#allowed}) y los devuelve cuando sí.
 */
@JeiPlugin
public final class TFJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;
    /** Los que se quitaron (para devolver exactamente esos). */
    private static final List<ItemStack> REMOVED = new ArrayList<>();

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TFClient.MOD_ID, "ocultar");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        REMOVED.clear();
        TFHiddenItems.viewer("JEI", TFJeiPlugin::apply);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        REMOVED.clear();
        TFHiddenItems.viewer("JEI", null);
    }

    /** show: que se vean (true) o no (false). Devuelve cuántos de los nuestros hay ahora en la lista de JEI. */
    private static int apply(boolean show) {
        IJeiRuntime rt = runtime;
        if (rt == null) return -1;
        var manager = rt.getIngredientManager();
        try {
            if (!show) {
                List<ItemStack> ours = new ArrayList<>();
                for (ItemStack s : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                    if (id != null && id.getNamespace().equals(TFClient.MOD_ID)) ours.add(s);
                }
                if (!ours.isEmpty()) {
                    manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, ours);
                    REMOVED.addAll(ours);
                }
            } else {
                // los que se quitaron y los de la pestaña del creativo que JEI no tenga (si su lista se hizo antes de
                // que llegara el permiso de OP, no están)
                java.util.Set<net.minecraft.world.item.Item> present = new java.util.HashSet<>();
                for (ItemStack s : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) present.add(s.getItem());
                List<ItemStack> add = new ArrayList<>();
                for (ItemStack s : REMOVED) if (present.add(s.getItem())) add.add(s);
                for (net.minecraft.world.item.Item i : TFItems.tabItems()) if (present.add(i)) add.add(new ItemStack(i));
                if (!add.isEmpty()) manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, add);
                REMOVED.clear();
            }
            int n = 0;
            for (ItemStack s : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                if (id != null && id.getNamespace().equals(TFClient.MOD_ID)) n++;
            }
            return n;
        } catch (Throwable t) {
            TFClient.LOGGER.warn("TF Client: no se pudo cambiar la lista de JEI: {}", t.toString());
            return -1;
        }
    }
}
