/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.168, el dueño: «elimina por completo que los items del mod salgan en el inventario creativo… ni operadores ni
 * miembros con creativo lo podrán ver… solo por comando del tf me los podré dar… una rata fue a un mundo creativo de
 * construcción y podía sacar los items») El modo creativo de Minecraft deja que el CLIENTE diga qué objeto pone en cada
 * hueco (paquete ServerboundSetCreativeModeSlotPacket): con un cliente viejo (que aún tenía la pestaña TF) o trucado se
 * podía sacar cualquier objeto aunque la pestaña ya no se viera. Así que lo decide el SERVIDOR, en todos los mundos
 * (también los de Multiverse) y para todos (también los OP):
 * <ul>
 *   <li>En creativo, ningún objeto de tfclient puede aparecer en un hueco por ese paquete (sacarlo de una pestaña o del
 *   buscador, la rueda del ratón sobre un bloque nuestro, copiar con el botón central…), ni tirarse uno nuevo.</li>
 *   <li>Lo nuestro que ya se tiene tampoco se mueve con ese paquete (en creativo, mover un objeto son dos paquetes: quitar
 *   de un hueco y poner en otro; si el segundo se rechaza, el objeto se perdería): se queda donde está. Para ordenarlo,
 *   en supervivencia.</li>
 *   <li>El botón central en un cofre o inventario (copiar un montón entero en creativo) no copia lo nuestro
 *   ({@code mixin.items.ContainerCloneGuardMixin}).</li>
 * </ul>
 * Lo nuestro solo llega por los comandos de TF (/tf web sets give, la tienda, las protecciones…), la web y lo que dan los
 * propios sistemas del mod. Cuando se rechaza algo, el servidor le vuelve a mandar al cliente su inventario de verdad.
 */
public final class TFCreativeGuard {
    private TFCreativeGuard() {}

    /** Cuántas veces se ha rechazado algo (para la prueba en el juego y el registro). */
    public static volatile int blocked;
    private static final Map<UUID, Long> WARNED = new ConcurrentHashMap<>();

    /** ¿Es un objeto del mod (cualquiera: sets, protecciones, el pad, monedas, discos…)? */
    public static boolean ours(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && TFClient.MOD_ID.equals(id.getNamespace());
    }

    /**
     * El paquete de creativo «pon esto en el hueco slot» (1–45 del inventario; negativo = tirarlo): ¿se rechaza? Se
     * rechaza si pone o tira algo nuestro, o si cambia un hueco donde hay algo nuestro (salvo que lo deje igual).
     */
    public static boolean blocksCreativeSlot(ServerPlayer player, int slot, ItemStack incoming) {
        if (player == null || !player.isCreative()) return false;
        ItemStack current = slot >= 1 && slot <= 45 && slot < player.inventoryMenu.slots.size() ? player.inventoryMenu.getSlot(slot).getItem() : ItemStack.EMPTY;
        if (ours(incoming)) {
            if (slot >= 1 && ItemStack.matches(current, incoming)) return false;
            return reject(player, slot, incoming, true);
        }
        if (ours(current) && !ItemStack.matches(current, incoming)) return reject(player, slot, current, false);
        return false;
    }

    private static boolean reject(ServerPlayer player, int slot, ItemStack stack, boolean spawn) {
        blocked++;
        // el inventario de verdad, otra vez al cliente (lo que creía haber puesto o quitado desaparece / vuelve)
        try {
            player.inventoryMenu.sendAllDataToRemote();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.sendAllDataToRemote();
        } catch (Throwable ignored) {
            // nunca romper la conexión por esto
        }
        long now = System.currentTimeMillis();
        Long last = WARNED.get(player.getUUID());
        if (last == null || now - last > 10_000) {
            WARNED.put(player.getUUID(), now);
            TFClient.LOGGER.warn("TF Creativo: {} {} {} en creativo (hueco {}): rechazado; los objetos de TF solo se dan con los comandos de TF",
                    player.getGameProfile().getName(), spawn ? "intentó sacar" : "intentó mover", ForgeRegistries.ITEMS.getKey(stack.getItem()), slot);
            player.sendSystemMessage(Component.literal(spawn
                    ? "Los objetos de Tierras Fantásticas no se sacan del creativo: solo se dan con los comandos de TF."
                    : "Los objetos de Tierras Fantásticas no se mueven en creativo: cámbiate a supervivencia para ordenarlos.")
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    /** El botón central (copiar un montón entero en creativo) sobre algo nuestro: no se copia. */
    public static boolean blocksClone(ItemStack stack) {
        if (!ours(stack)) return false;
        blocked++;
        return true;
    }
}
