package net.tierrasfantasticas.tfclient.items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Compras reembolsadas: lo que se entregó con ese pedido se retira del servidor esté donde esté.
 * <ul>
 *   <li>Al entregar una compra, el puente marca cada objeto con su pedido (NBT {@code TFOrder}).</li>
 *   <li>Si la compra se reembolsa (o hay una disputa), la web manda {@code /tf web sets revoke <uuid> <pedido> <set>
 *   [objeto]} aunque el jugador no esté conectado. Se guarda en el mundo (data/tfclient_revocations.dat) y se quita al
 *   momento de los jugadores conectados, del suelo, de los marcos, soportes y cofres con ruedas, y de los cofres y
 *   demás contenedores de los chunks cargados.</li>
 *   <li>Lo que no está cargado se quita cuando se carga: al entrar el jugador (inventario, cofre de ender y huecos de
 *   accesorios), al cargarse un chunk (sus contenedores y entidades), al abrir cualquier contenedor y, cada segundo,
 *   del inventario de cada jugador. También dentro de cajas de shulker y sacos.</li>
 *   <li>Los objetos de antes de esta versión no llevan el pedido: se reconocen por su dueño (UUID) y su set.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFRevocations {
    public static final String ORDER = "TFOrder";
    /** Chunks que se revisan por tick (los contenedores de un chunk recién cargado o de todos al reembolsar). */
    private static final int CHUNKS_PER_TICK = 16;

    /** Pedido que el puente está entregando ahora: lo que se da mientras tanto lleva su marca. */
    @Nullable
    private static String currentOrder;
    /** Chunks cargados de cada mundo y los que faltan por revisar. */
    private static final Map<ResourceKey<Level>, Set<Long>> LOADED = new HashMap<>();
    private static final ArrayDeque<PendingChunk> PENDING = new ArrayDeque<>();
    private static final ArrayDeque<Entity> PENDING_ENTITIES = new ArrayDeque<>();

    private record PendingChunk(ResourceKey<Level> level, long pos) {}

    /** Una compra reembolsada: de quién, qué pedido y qué set (y pieza, si solo era una). */
    public record Entry(UUID owner, String order, String set, @Nullable String item) {}

    private TFRevocations() {}

    // ---------------------------------------------------------------------------------------------------------------
    // Marca del pedido al entregar
    // ---------------------------------------------------------------------------------------------------------------

    public static void setCurrentOrder(@Nullable String order) {
        currentOrder = order == null || order.isBlank() ? null : order;
    }

    public static void stamp(ItemStack stack) {
        if (currentOrder != null && stack.getItem() instanceof TFItem) stack.getOrCreateTag().putString(ORDER, currentOrder);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Reembolso: se apunta y se retira lo que ya está cargado
    // ---------------------------------------------------------------------------------------------------------------

    /** Apunta la retirada y quita al momento lo cargado. Devuelve cuántos objetos se han quitado ya. */
    public static int revoke(MinecraftServer server, Entry entry) {
        Data data = Data.get(server);
        data.add(entry);
        int removed = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) removed += purgePlayer(player, data);
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) removed += purgeEntity(entity, data);
            Set<Long> chunks = LOADED.get(level.dimension());
            if (chunks != null) for (long pos : chunks) PENDING.add(new PendingChunk(level.dimension(), pos));
        }
        TFClient.LOGGER.info("TF Reembolsos: retirada del pedido {} ({} de {}): {} objetos quitados ya; se revisan {} chunks cargados",
                entry.order(), entry.set() + (entry.item() != null ? "/" + entry.item() : ""), entry.owner(), removed, PENDING.size());
        return removed;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // ¿Es de una compra reembolsada?
    // ---------------------------------------------------------------------------------------------------------------

    static boolean revoked(Data data, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TFItem)) return false;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ORDER, Tag.TAG_STRING)) return data.orders.contains(tag.getString(ORDER));
        // Objetos de antes de esta versión (sin pedido): por su dueño y su set.
        UUID owner = TFBinding.owner(stack);
        List<Entry> entries = owner != null ? data.byOwner.get(owner) : null;
        if (entries == null) return false;
        Item item = stack.getItem();
        TFSets.SetDef set = TFItemTypes.setOf(item);
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        if (set == null || key == null) return false;
        for (Entry e : entries) {
            if (e.set().equals(set.id()) && (e.item() == null || key.getPath().equals(e.set() + "_" + e.item()))) return true;
        }
        return false;
    }

    /** Quita lo reembolsado que haya dentro de un objeto (cajas de shulker, sacos). Devuelve cuántos quitó. */
    private static int purgeNested(Data data, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        int removed = 0;
        if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) removed += purgeList(data, tag.getCompound("BlockEntityTag"), "Items");
        removed += purgeList(data, tag, "Items");
        return removed;
    }

    private static int purgeList(Data data, CompoundTag holder, String key) {
        if (!holder.contains(key, Tag.TAG_LIST)) return 0;
        ListTag list = holder.getList(key, Tag.TAG_COMPOUND);
        int removed = 0;
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag entry = list.getCompound(i);
            ItemStack inner = ItemStack.of(entry);
            if (revoked(data, inner)) {
                list.remove(i);
                removed += inner.getCount();
            } else {
                int nested = purgeNested(data, inner);
                if (nested > 0) {
                    byte slot = entry.getByte("Slot");
                    CompoundTag saved = inner.save(new CompoundTag());
                    if (entry.contains("Slot")) saved.putByte("Slot", slot);
                    list.set(i, saved);
                    removed += nested;
                }
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dónde se busca
    // ---------------------------------------------------------------------------------------------------------------

    private static int purgeContainer(Data data, Container container) {
        int removed = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (revoked(data, stack)) {
                removed += stack.getCount();
                container.setItem(i, ItemStack.EMPTY);
            } else {
                removed += purgeNested(data, stack);
            }
        }
        if (removed > 0) container.setChanged();
        return removed;
    }

    private static int purgeHandler(Data data, IItemHandler handler) {
        int removed = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (revoked(data, stack)) {
                int count = stack.getCount();
                if (handler instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(i, ItemStack.EMPTY);
                } else {
                    handler.extractItem(i, count, false);
                }
                removed += count;
            } else {
                removed += purgeNested(data, stack);
            }
        }
        return removed;
    }

    private static int purgePlayer(ServerPlayer player, Data data) {
        if (data.isEmpty()) return 0;
        int removed = purgeContainer(data, player.getInventory()) + purgeContainer(data, player.getEnderChestInventory());
        ItemStack carried = player.containerMenu.getCarried();
        if (revoked(data, carried)) {
            removed += carried.getCount();
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        // Huecos de accesorios (Accessories / Curios): las pilas que devuelven son las del propio hueco.
        List<ItemStack> worn = new ArrayList<>();
        try {
            TFAccessoryLookup.fromAccessories(player, s -> revoked(data, s), worn);
        } catch (Throwable ignored) {
            // Sin el mod Accessories.
        }
        try {
            TFAccessoryLookup.fromCurios(player, s -> revoked(data, s), worn, false);
        } catch (Throwable ignored) {
            // Sin Curios.
        }
        for (ItemStack stack : worn) {
            removed += stack.getCount();
            stack.setCount(0);
        }
        if (removed > 0) {
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            player.sendSystemMessage(Component.literal("Se han retirado " + removed + (removed == 1 ? " objeto" : " objetos")
                    + " de una compra reembolsada.").withStyle(ChatFormatting.RED));
        }
        return removed;
    }

    private static int purgeEntity(Entity entity, Data data) {
        if (data.isEmpty() || entity.isRemoved()) return 0;
        int removed = 0;
        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            if (revoked(data, stack)) {
                removed = stack.getCount();
                item.discard();
            } else if (purgeNested(data, stack) > 0) {
                item.setItem(stack.copy());
                removed = 1;
            }
        } else if (entity instanceof ItemFrame frame) {
            if (revoked(data, frame.getItem())) {
                removed = 1;
                frame.setItem(ItemStack.EMPTY);
            }
        } else if (entity instanceof ServerPlayer) {
            return 0; // Los jugadores van aparte (purgePlayer)
        } else if (entity instanceof LivingEntity living) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = living.getItemBySlot(slot);
                if (revoked(data, stack)) {
                    removed += stack.getCount();
                    living.setItemSlot(slot, ItemStack.EMPTY);
                }
            }
        }
        if (entity instanceof Container container) removed += purgeContainer(data, container);
        return removed;
    }

    private static int purgeChunk(ServerLevel level, LevelChunk chunk, Data data) {
        int removed = 0;
        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
            if (be instanceof Container container) {
                removed += purgeContainer(data, container);
            } else {
                IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (handler != null) removed += purgeHandler(data, handler);
            }
        }
        if (removed > 0) {
            BlockPos at = chunk.getPos().getWorldPosition();
            TFClient.LOGGER.info("TF Reembolsos: quitados {} objetos reembolsados de contenedores del chunk {} en {}", removed, at,
                    level.dimension().location());
        }
        return removed;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Eventos: lo que se va cargando
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long pos = event.getChunk().getPos().toLong();
        LOADED.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos);
        if (!Data.get(level.getServer()).isEmpty()) PENDING.add(new PendingChunk(level.dimension(), pos));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Set<Long> set = LOADED.get(level.dimension());
        if (set != null) set.remove(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity() instanceof ServerPlayer) return;
        Data data = Data.get(level.getServer());
        if (data.isEmpty()) return;
        if (event.getEntity() instanceof ItemEntity item && revoked(data, item.getItem())) {
            event.setCanceled(true);
            return;
        }
        // Marcos, soportes y cofres con ruedas: en el siguiente tick (ya están del todo en el mundo).
        PENDING_ENTITIES.add(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) purgePlayer(player, Data.get(player.server));
    }

    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Data data = Data.get(player.server);
        if (data.isEmpty()) return;
        int removed = 0;
        for (Slot slot : event.getContainer().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            if (revoked(data, stack)) {
                removed += stack.getCount();
                slot.set(ItemStack.EMPTY);
            } else if (purgeNested(data, stack) > 0) {
                slot.setChanged();
                removed++;
            }
        }
        if (removed > 0) {
            event.getContainer().broadcastChanges();
            player.sendSystemMessage(Component.literal("Se han retirado de este contenedor objetos de una compra reembolsada.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        purgePlayer(player, Data.get(player.server));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (PENDING.isEmpty() && PENDING_ENTITIES.isEmpty())) return;
        MinecraftServer server = event.getServer();
        Data data = Data.get(server);
        if (data.isEmpty()) {
            PENDING.clear();
            PENDING_ENTITIES.clear();
            return;
        }
        while (!PENDING_ENTITIES.isEmpty()) purgeEntity(PENDING_ENTITIES.poll(), data);
        for (int i = 0; i < CHUNKS_PER_TICK && !PENDING.isEmpty(); i++) {
            PendingChunk next = PENDING.poll();
            ServerLevel level = server.getLevel(next.level());
            if (level == null) continue;
            ChunkPos pos = new ChunkPos(next.pos());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk != null) purgeChunk(level, chunk, data);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
        PENDING.clear();
        PENDING_ENTITIES.clear();
        currentOrder = null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lo que se ha reembolsado, guardado con el mundo
    // ---------------------------------------------------------------------------------------------------------------

    static final class Data extends SavedData {
        private static final String NAME = "tfclient_revocations";
        private final List<Entry> entries = new ArrayList<>();
        final Set<String> orders = new HashSet<>();
        final Map<UUID, List<Entry>> byOwner = new HashMap<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }

        void add(Entry entry) {
            if (entries.contains(entry)) return;
            index(entry);
            setDirty();
        }

        private void index(Entry entry) {
            entries.add(entry);
            orders.add(entry.order());
            byOwner.computeIfAbsent(entry.owner(), k -> new ArrayList<>()).add(entry);
        }

        static Data load(CompoundTag tag) {
            Data data = new Data();
            ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (!e.hasUUID("owner")) continue;
                data.index(new Entry(e.getUUID("owner"), e.getString("order"), e.getString("set"),
                        e.contains("item", Tag.TAG_STRING) ? e.getString("item") : null));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (Entry entry : entries) {
                CompoundTag e = new CompoundTag();
                e.putUUID("owner", entry.owner());
                e.putString("order", entry.order());
                e.putString("set", entry.set());
                if (entry.item() != null) e.putString("item", entry.item());
                list.add(e);
            }
            tag.put("entries", list);
            return tag;
        }
    }
}
