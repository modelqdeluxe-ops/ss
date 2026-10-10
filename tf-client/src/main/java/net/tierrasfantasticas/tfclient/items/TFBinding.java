package net.tierrasfantasticas.tfclient.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los objetos de los sets de los rangos, las crates y la ruleta son permanentes y van vinculados al jugador (su UUID):
 * <ul>
 *   <li>No se rompen ni se gastan (sin durabilidad, {@link TFItem}); tirados al suelo no desaparecen, no los rompe
 *   nada (lava, cactus, explosiones) y si caen al vacío vuelven a su dueño.</li>
 *   <li>Solo su dueño puede cogerlos del suelo, llevarlos en el inventario, ponérselos o usarlos. Se pueden guardar en
 *   cofres; si otro jugador los saca, vuelven a su dueño (a su inventario, o al entrar si no está conectado).</li>
 *   <li>Se vinculan al entregarlos (/tf web sets give) y, los que no tienen dueño (los de antes de esta versión o los
 *   del creativo), al primer jugador que los lleve fuera del modo creativo.</li>
 * </ul>
 * Los cosméticos (sombreros, alas, mochilas, objetos de mano y los sets de cosméticos) no se vinculan: se pueden
 * regalar e intercambiar.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFBinding {
    private static final String OWNER = "TFOwner";
    private static final String OWNER_NAME = "TFOwnerName";

    private TFBinding() {}

    // ---------------------------------------------------------------------------------------------------------------
    // El dueño, guardado en el propio objeto
    // ---------------------------------------------------------------------------------------------------------------

    /** Se vincula: objeto de un set que no es un cosmético. */
    public static boolean bindable(ItemStack stack) {
        Item item = stack.getItem();
        if (!(item instanceof TFItem) || item instanceof TFItemTypes.Cosmetic || item instanceof TFItemTypes.Held) return false;
        TFSets.SetDef set = TFItemTypes.setOf(item);
        return set != null && !set.cosmetic();
    }

    @Nullable
    public static UUID owner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
    }

    @Nullable
    public static String ownerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(OWNER_NAME, Tag.TAG_STRING) ? tag.getString(OWNER_NAME) : null;
    }

    public static void bind(ItemStack stack, Player player) {
        if (!bindable(stack)) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(OWNER, player.getUUID());
        tag.putString(OWNER_NAME, player.getGameProfile().getName());
    }

    /** El jugador puede llevarlo y usarlo: es suyo, no tiene dueño o no se vincula. */
    public static boolean usableBy(ItemStack stack, Player player) {
        UUID owner = owner(stack);
        return owner == null || owner.equals(player.getUUID()) || !bindable(stack);
    }

    private static boolean exempt(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // En el suelo: no desaparecen ni se rompen (lo llama TFItem.onEntityItemUpdate en cada tick)
    // ---------------------------------------------------------------------------------------------------------------

    static void protect(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        ItemStack stack = entity.getItem();
        if (!entity.isInvulnerable()) {
            entity.setInvulnerable(true);
            entity.setUnlimitedLifetime();
            UUID owner = bindable(stack) ? owner(stack) : null;
            if (owner != null) entity.setTarget(owner); // Minecraft tampoco deja cogerlo a otros
        }
        if (entity.getY() < level.getMinBuildHeight() - 16) {
            ItemStack rescued = stack.copy();
            entity.discard();
            UUID owner = bindable(rescued) ? owner(rescued) : null;
            if (owner != null) {
                returnTo(level.getServer(), owner, rescued);
            } else {
                dropAtSpawn(level.getServer(), rescued);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eventos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPickup(EntityItemPickupEvent event) {
        if (!usableBy(event.getItem().getItem(), event.getEntity())) event.setCanceled(true);
    }

    /** Cada tick: vincula lo que no tiene dueño y devuelve lo que es de otro. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || exempt(player)) return;
        Inventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !bindable(stack)) continue;
            UUID owner = owner(stack);
            if (owner == null) {
                bind(stack, player);
                changed = true;
            } else if (owner.equals(player.getUUID())) {
                String name = player.getGameProfile().getName();
                if (!name.equals(ownerName(stack))) {
                    stack.getOrCreateTag().putString(OWNER_NAME, name);
                    changed = true;
                }
            } else {
                inv.setItem(i, ItemStack.EMPTY);
                String ownerName = ownerName(stack);
                player.sendSystemMessage(Component.literal(stack.getHoverName().getString() + " está vinculado a "
                        + (ownerName != null ? ownerName : "otro jugador") + ": solo su dueño puede tenerlo. Se le ha devuelto.")
                        .withStyle(ChatFormatting.RED));
                returnTo(player.server, owner, stack);
                changed = true;
            }
        }
        if (changed) player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<ItemStack> pending = Returns.get(player.server).take(player.getUUID());
        if (pending.isEmpty()) return;
        for (ItemStack stack : pending) giveOrDrop(player, stack);
        player.sendSystemMessage(Component.literal("Te hemos devuelto " + pending.size()
                + (pending.size() == 1 ? " objeto vinculado a tu cuenta." : " objetos vinculados a tu cuenta."))
                .withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!exempt(player) && !usableBy(player.getMainHandItem(), player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!exempt(player) && !usableBy(event.getItemStack(), player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player != null && !exempt(player) && !usableBy(player.getMainHandItem(), player)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Devolver a su dueño
    // ---------------------------------------------------------------------------------------------------------------

    private static void returnTo(MinecraftServer server, UUID owner, ItemStack stack) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            giveOrDrop(player, stack);
            player.sendSystemMessage(Component.literal(stack.getHoverName().getString() + " ha vuelto a tu inventario.")
                    .withStyle(ChatFormatting.GOLD));
        } else {
            Returns.get(server).add(owner, stack);
        }
    }

    public static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(player.getUUID());
            }
        }
        player.containerMenu.broadcastChanges();
    }

    private static void dropAtSpawn(MinecraftServer server, ItemStack stack) {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        BlockPos top = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, spawn);
        ItemEntity entity = new ItemEntity(overworld, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, stack);
        entity.setDeltaMovement(0, 0, 0);
        overworld.addFreshEntity(entity);
    }

    /** Objetos que esperan a que su dueño se conecte (se guardan con el mundo, en data/tfclient_returns.dat). */
    private static final class Returns extends SavedData {
        private static final String NAME = "tfclient_returns";
        private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

        static Returns get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Returns::load, Returns::new, NAME);
        }

        void add(UUID owner, ItemStack stack) {
            pending.computeIfAbsent(owner, k -> new ArrayList<>()).add(stack.copy());
            setDirty();
        }

        List<ItemStack> take(UUID owner) {
            List<ItemStack> out = pending.remove(owner);
            if (out == null) return List.of();
            setDirty();
            return out;
        }

        static Returns load(CompoundTag tag) {
            Returns r = new Returns();
            for (String key : tag.getAllKeys()) {
                UUID owner;
                try {
                    owner = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                List<ItemStack> list = new ArrayList<>();
                ListTag items = tag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    ItemStack stack = ItemStack.of(items.getCompound(i));
                    if (!stack.isEmpty()) list.add(stack);
                }
                if (!list.isEmpty()) r.pending.put(owner, list);
            }
            return r;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            pending.forEach((owner, list) -> {
                ListTag items = new ListTag();
                for (ItemStack stack : list) items.add(stack.save(new CompoundTag()));
                tag.put(owner.toString(), items);
            });
            return tag;
        }
    }
}
