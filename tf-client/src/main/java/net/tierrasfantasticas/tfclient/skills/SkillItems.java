package net.tierrasfantasticas.tfclient.skills;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import net.tierrasfantasticas.tfclient.items.TFItemTypes;
import net.tierrasfantasticas.tfclient.items.TFItems;
import net.tierrasfantasticas.tfclient.items.TFSets;

/**
 * Las armas y armaduras de cada clase: se entregan al tener la clase (vinculadas, como las de los sets) y se quitan
 * si el jugador cambia de clase. Algunas clases miran o cambian lo que llevas en la mano (itemissimilar / equip HAND):
 * el guantelete del Dragón Rojo pasa de nivel 1 a 2 y al modo Balance Breaker.
 */
final class SkillItems {
    private SkillItems() {}

    /** El objeto del mod para un objeto de MythicMobs de la clase (o null). */
    private static Item item(SkillDefs.ClassDef cls, String mmItem) {
        if (cls == null || mmItem == null) return null;
        String id = cls.handItems.get(mmItem.toLowerCase(Locale.ROOT));
        return id == null ? null : ForgeRegistries.ITEMS.getValue(new ResourceLocation(TFClient.MOD_ID, id));
    }

    /** ¿Lleva en la mano ese objeto de la clase? */
    static boolean holds(net.minecraft.world.entity.player.Player p, SkillDefs.ClassDef cls, String mmItem) {
        Item it = item(cls, mmItem);
        return it != null && p.getMainHandItem().is(it);
    }

    /** equip{item=X:HAND} @self: si lleva en la mano un objeto de mano de su clase, pasa a ser X (mismo dueño). */
    static void swapHand(ServerPlayer p, SkillDefs.ClassDef cls, String mmItem) {
        Item to = item(cls, mmItem);
        ItemStack now = p.getMainHandItem();
        if (to == null || now.isEmpty() || now.is(to)) return;
        boolean ours = false;
        for (String id : cls.handItems.values()) {
            Item other = ForgeRegistries.ITEMS.getValue(new ResourceLocation(TFClient.MOD_ID, id));
            if (other != null && now.is(other)) ours = true;
        }
        if (!ours) return;
        ItemStack swapped = new ItemStack(to, now.getCount());
        if (now.getTag() != null) swapped.setTag(now.getTag().copy());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, swapped);
    }

    /** Lo que se entrega de la clase: todo su set menos las variantes de mano (solo la básica). */
    static void give(ServerPlayer p, SkillDefs.ClassDef cls) {
        if (cls == null || cls.set == null) return;
        List<RegistryObject<Item>> items = TFItems.BY_SET.get(cls.set);
        if (items == null) return;
        String base = cls.handItems.values().stream().min(java.util.Comparator.comparingInt(String::length)).orElse(null);
        int n = 0;
        for (RegistryObject<Item> o : items) {
            String id = o.getId().getPath();
            if (cls.handItems.containsValue(id) && !id.equals(base)) continue;
            ItemStack stack = new ItemStack(o.get());
            TFBinding.bind(stack, p);
            TFBinding.giveOrDrop(p, stack);
            n++;
        }
        if (n > 0) {
            p.containerMenu.broadcastChanges();
            p.sendSystemMessage(Component.literal("✦ Tienes el equipo de tu clase (" + n + " objetos, vinculados a ti).")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** Quita del inventario los objetos de otras clases (al cambiar de clase o al entrar). */
    static void purge(ServerPlayer p, String keepClass) {
        Inventory inv = p.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            TFSets.SetDef set = TFItemTypes.setOf(s.getItem());
            if (set == null || set.classId() == null || set.classId().equals(keepClass)) continue;
            inv.setItem(i, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) p.containerMenu.broadcastChanges();
    }
}
