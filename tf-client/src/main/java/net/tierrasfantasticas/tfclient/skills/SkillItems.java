package net.tierrasfantasticas.tfclient.skills;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.tierrasfantasticas.tfclient.items.TFItemTypes;
import net.tierrasfantasticas.tfclient.items.TFSets;

/**
 * Las clases solo dan skills: ni armas ni armadura. Los objetos de clase que se dieron en versiones anteriores se
 * quitan del inventario al entrar o al cambiar de clase.
 *
 * <p>Algunas clases miran o cambian el objeto de su pack que llevas en la mano (itemissimilar / equip HAND): el
 * guantelete del Dragón Rojo pasa de nivel 1 a 2 y al modo Balance Breaker. Ese objeto ya no existe en el inventario:
 * se lleva «de mentira» aquí (empieza en el básico) y las condiciones lo miran a él.
 */
final class SkillItems {
    /** El objeto de mano del pack que «lleva» cada jugador (nombre de MythicMobs en minúsculas). */
    private static final Map<UUID, String> HAND = new HashMap<>();

    private SkillItems() {}

    /** El objeto de mano con el que empieza la clase (el de nombre más corto: Sacred_Gear antes que Sacred_Gear_s2). */
    private static String base(SkillDefs.ClassDef cls) {
        return cls.handItems.keySet().stream().min(java.util.Comparator.comparingInt(String::length).thenComparing(s -> s)).orElse(null);
    }

    /** ¿«Lleva» en la mano ese objeto de su clase? */
    static boolean holds(net.minecraft.world.entity.player.Player p, SkillDefs.ClassDef cls, String mmItem) {
        if (cls == null || mmItem == null || cls.handItems.isEmpty()) return false;
        String now = HAND.get(p.getUUID());
        if (now == null || !cls.handItems.containsKey(now)) now = base(cls);
        return mmItem.toLowerCase(Locale.ROOT).equals(now);
    }

    /** equip{item=X:HAND} @self: si X es un objeto de mano de su clase, pasa a «llevar» ese. */
    static void swapHand(ServerPlayer p, SkillDefs.ClassDef cls, String mmItem) {
        if (cls == null || mmItem == null) return;
        String key = mmItem.toLowerCase(Locale.ROOT);
        if (cls.handItems.containsKey(key)) HAND.put(p.getUUID(), key);
    }

    /** Al cambiar de clase, salir o morir: vuelve al objeto básico. */
    static void forget(UUID uuid) {
        HAND.remove(uuid);
    }

    /** Quita del inventario todos los objetos de clase (armas y armaduras que daban las versiones anteriores). */
    static void purge(ServerPlayer p) {
        Inventory inv = p.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            TFSets.SetDef set = TFItemTypes.setOf(s.getItem());
            if (set == null || set.classId() == null) continue;
            inv.setItem(i, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) p.containerMenu.broadcastChanges();
    }
}
