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

/**
 * JEI carga esta clase solo si está instalado (por la anotación); el mod no depende de JEI. Quita de su lista todos los
 * objetos de tfclient, para todos (1.3.168: también OP y creativo; ver {@link TFHiddenItems}).
 */
@JeiPlugin
public final class TFJeiPlugin implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TFClient.MOD_ID, "ocultar");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        TFHiddenItems.viewer("JEI", TFJeiPlugin::apply);
        TFHiddenItems.update();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        TFHiddenItems.viewer("JEI", null);
    }

    /** Quita lo nuestro de la lista de JEI. Devuelve cuántos de los nuestros quedan (0 = ninguno; -1 = no se pudo). */
    private static int apply(boolean show) {
        IJeiRuntime rt = runtime;
        if (rt == null) return -1;
        var manager = rt.getIngredientManager();
        try {
            List<ItemStack> ours = new ArrayList<>();
            for (ItemStack s : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
                if (id != null && id.getNamespace().equals(TFClient.MOD_ID)) ours.add(s);
            }
            if (!ours.isEmpty()) manager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, ours);
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
