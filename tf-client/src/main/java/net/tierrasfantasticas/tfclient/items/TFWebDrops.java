/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.134, el dueño: «asegúrate de que los objetos que compran en la web, los que van marcados, no desaparezcan del
 * suelo incluso si pasa un clear lag») Lo comprado en la web (con la marca de su pedido, {@link TFRevocations#ORDER}, o
 * un objeto de set atado a su dueño) tirado en el suelo:
 * <ul>
 *   <li>no caduca (vida sin límite, también con el item-despawn-rate de Spigot) ni lo rompen la lava, el fuego, los
 *       cactus o las explosiones;</li>
 *   <li>no lo borra un clear lag: un plugin de Bukkit (ClearLagg, LaggRemover, el de Mohist…), un mod que limpia el
 *       suelo o /kill. Se reconoce porque lo borra quien no lo recoge: /kill (motivo KILLED) o una clase de fuera de
 *       Minecraft, Forge y CraftBukkit con «clear», «lag», «clean», «limpi», «remove», «purge», «kill» o «butcher» en el
 *       nombre entre las 8 llamadas de encima (ver {@link #keep}).</li>
 * </ul>
 * Lo demás sigue como siempre, para no duplicar nada: cogerlo (un jugador, una tolva, el imán de un mod), juntarse con
 * otra pila igual, caer al vacío, un reembolso (TFRevocations lo vacía antes) o el chunk que se descarga.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFWebDrops {
    /** Lo que, en el nombre de la clase que borra el objeto, delata un clear lag. */
    // (1.3.145) «trash» y demás: las papeleras (WorldListTrashCan recoge lo del suelo en una papelera que cualquiera abre y
    // luego se vacía: así se perdió parte del kit de HitGp con la 1.3.132)
    private static final String[] CLEANERS = {"clear", "lag", "clean", "limpi", "remove", "purge", "kill", "butcher", "trash", "garbage",
            "basura", "papelera", "sweep", "dustbin"};
    /** Lo de Minecraft, Forge, CraftBukkit, Java y el propio mod: borran por otras razones (recoger, juntar, el vacío). */
    private static final String[] SAFE = {"net.minecraft.", "net.minecraftforge.", "org.bukkit.", "org.spigotmc.", "java.",
            "jdk.", "sun.", "org.spongepowered.", "cpw.mods.", "net.tierrasfantasticas.", "com.mojang."};

    private TFWebDrops() {}

    /** ¿Es una compra de la web? (la marca del pedido o, los de antes, un objeto de set atado a su dueño) */
    public static boolean marked(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TFRevocations.ORDER, Tag.TAG_STRING)) return true;
        return stack.getItem() instanceof TFItem && TFBinding.owner(stack) != null;
    }

    /** Al caer al suelo: sin caducar y sin que lo rompa nada. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof ItemEntity item)) return;
        if (!marked(item.getItem())) return;
        item.setUnlimitedLifetime();
        item.setInvulnerable(true);
    }

    /** Por si algo le devuelve la edad (otro mod): nunca caduca. */
    @SubscribeEvent
    public static void onExpire(ItemExpireEvent event) {
        if (marked(event.getEntity().getItem())) {
            event.setExtraLife(6000);
            event.setCanceled(true);
        }
    }

    /**
     * Lo llama EntityRemoveMixin antes de quitar una entidad del mundo: true = se queda (es una compra de la web que
     * borra un clear lag). Solo mira la pila de llamadas cuando el objeto del suelo es de la web (pasa muy poco).
     */
    public static boolean keep(Entity entity, Entity.RemovalReason reason) {
        if (!(entity instanceof ItemEntity item) || entity.level().isClientSide) return false;
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) return false;
        ItemStack stack = item.getItem();
        if (stack.isEmpty() || !marked(stack)) return false;
        // al vacío se pierde, como siempre
        if (item.getY() < item.level().getMinBuildHeight() - 32) return false;
        if (reason == Entity.RemovalReason.KILLED) return log(item, "/kill");
        // quien lo borra está justo encima (Entity.discard, CraftEntity.remove y el plugin o el mod): solo las 14 primeras
        String by = StackWalker.getInstance().walk(frames -> frames.limit(14).map(StackWalker.StackFrame::getClassName)
                .filter(TFWebDrops::cleaner).findFirst().orElse(null));
        return by != null && log(item, by);
    }

    private static boolean cleaner(String cls) {
        for (String s : SAFE) if (cls.startsWith(s)) return false;
        String low = cls.toLowerCase(Locale.ROOT);
        for (String c : CLEANERS) if (low.contains(c)) return true;
        return false;
    }

    private static final java.util.Map<String, Long> LOGGED = new java.util.HashMap<>();

    /** Una línea en el log por quien lo intenta (como mucho una vez por minuto). */
    private static boolean log(ItemEntity item, String by) {
        long now = System.currentTimeMillis();
        Long last = LOGGED.get(by);
        if (last == null || now - last > 60_000) {
            LOGGED.put(by, now);
            TFClient.LOGGER.info("TF Web: {} quiso borrar del suelo una compra de la web ({} x{} en {} {} {}); se queda",
                    by, item.getItem().getItem(), item.getItem().getCount(), item.getBlockX(), item.getBlockY(), item.getBlockZ());
        }
        return true;
    }
}
