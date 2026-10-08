package net.tierrasfantasticas.tfclient.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Armario: lo que cada jugador eligió en su perfil de la web para que se vea en cada hueco (cabeza, pecho, piernas,
 * pies y espalda), entre lo que compró. Solo es apariencia: se dibuja encima de lo que lleve puesto (lo tapa) y no da
 * defensa, ni planeo, ni nada. La web lo manda por el puente ({@link #applyWeb}), el servidor lo guarda en
 * &lt;mundo&gt;/tfclient/wardrobe.json y se lo pasa a todos los jugadores; cada cliente lo dibuja (mixins de
 * HumanoidArmorLayer y CustomHeadLayer, y la espalda en {@link TFItemsClient.BackLayer}).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFWardrobe {
    /** Huecos, en el orden de la web. */
    public static final String[] SLOTS = {"head", "chest", "legs", "feet", "back"};
    public static final int HEAD = 0, CHEST = 1, LEGS = 2, FEET = 3, BACK = 4;

    private static final String PROTOCOL = TFClient.VERSION;
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TFClient.MOD_ID, "wardrobe"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    /** Servidor: «set/pieza» por hueco ("" = nada). */
    private static final Map<UUID, String[]> WORN = new HashMap<>();
    /** Cliente: lo que se dibuja de cada jugador (null = nada en ese hueco). */
    private static final Map<UUID, ItemStack[]> SHOWN = new ConcurrentHashMap<>();
    private static MinecraftServer server;

    private TFWardrobe() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Sync.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::write).decoder(Sync::read).consumerMainThread(Sync::handle).add();
    }

    // ------------------------------------------------------------------------------------------- servidor

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        WORN.clear();
        load();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        server = null;
    }

    /** Lo que manda la web en cada consulta: [{uuid, items: {hueco: "set/pieza"}, at}] de los jugadores conectados. */
    public static void applyWeb(MinecraftServer srv, JsonArray list) {
        boolean changed = false;
        for (JsonElement el : list) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            UUID uuid;
            try {
                uuid = UUID.fromString(TFJson.str(o, "uuid", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            Long local = LOCAL.get(uuid);
            if (local != null && System.currentTimeMillis() - local < 20_000) continue;
            JsonObject items = TFJson.obj(o, "items");
            String[] worn = new String[SLOTS.length];
            for (int i = 0; i < SLOTS.length; i++) worn[i] = valid(TFJson.str(items, SLOTS[i], ""), i);
            String[] old = WORN.get(uuid);
            if (old != null ? Arrays.equals(old, worn) : isEmpty(worn)) continue;
            if (isEmpty(worn)) WORN.remove(uuid);
            else WORN.put(uuid, worn);
            changed = true;
            // A todos: cada uno dibuja a los demás con lo suyo
            CHANNEL.send(PacketDistributor.ALL.noArg(), new Sync(uuid, worn));
        }
        if (changed) save();
    }

    /** Cambios hechos desde el pad: durante un rato no se pisan con lo que mande la web (puede ser de antes). */
    private static final Map<UUID, Long> LOCAL = new HashMap<>();

    /** Lo que lleva puesto: «set/pieza» por hueco ("" = nada). */
    public static String[] worn(UUID uuid) {
        String[] w = WORN.get(uuid);
        return w == null ? new String[] {"", "", "", "", ""} : w.clone();
    }

    /** Pone (o quita, con "") una pieza desde el pad. La web se entera en la siguiente consulta del puente. */
    public static boolean setFromPad(UUID uuid, int slot, String piece) {
        String p = piece.isEmpty() ? "" : valid(piece, slot);
        if (!piece.isEmpty() && p.isEmpty()) return false;
        String[] worn = worn(uuid);
        worn[slot] = p;
        if (isEmpty(worn)) WORN.remove(uuid);
        else WORN.put(uuid, worn);
        LOCAL.put(uuid, System.currentTimeMillis());
        CHANNEL.send(PacketDistributor.ALL.noArg(), new Sync(uuid, worn));
        save();
        return true;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || server == null) return;
        // Al que entra, el armario de los conectados; a los demás, el suyo
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            String[] worn = WORN.get(other.getUUID());
            if (worn != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(other.getUUID(), worn));
        }
        String[] mine = WORN.get(player.getUUID());
        if (mine != null) CHANNEL.send(PacketDistributor.ALL.noArg(), new Sync(player.getUUID(), mine));
    }

    private static boolean isEmpty(String[] worn) {
        for (String s : worn) if (!s.isEmpty()) return false;
        return true;
    }

    /** La pieza si existe en el mod y va en ese hueco; si no, "". */
    private static String valid(String piece, int slot) {
        if (piece == null || !piece.matches("[a-z0-9_]+/[a-z0-9_]+")) return "";
        return fits(item(piece), slot) ? piece : "";
    }

    private static Item item(String piece) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(TFClient.MOD_ID, piece.replace('/', '_')));
    }

    private static boolean fits(@Nullable Item item, int slot) {
        return switch (slot) {
            case HEAD -> item instanceof TFItemTypes.Armor a && a.getType() == ArmorItem.Type.HELMET
                    || item instanceof TFItemTypes.Cosmetic c && c.getEquipmentSlot() == EquipmentSlot.HEAD;
            case CHEST -> item instanceof TFItemTypes.Armor a && a.getType() == ArmorItem.Type.CHESTPLATE;
            case LEGS -> item instanceof TFItemTypes.Armor a && a.getType() == ArmorItem.Type.LEGGINGS;
            case FEET -> item instanceof TFItemTypes.Armor a && a.getType() == ArmorItem.Type.BOOTS;
            case BACK -> item instanceof TFItemTypes.Cosmetic c && c.wornModel() != null;
            default -> false;
        };
    }

    private static Path dataFile() {
        return TFJson.worldFile(server, "wardrobe.json");
    }

    private static void load() {
        JsonObject json = TFJson.read(dataFile());
        if (json == null) return;
        JsonObject players = TFJson.obj(json, "jugadores");
        for (String key : players.keySet()) {
            try {
                JsonObject p = players.getAsJsonObject(key);
                String[] worn = new String[SLOTS.length];
                for (int i = 0; i < SLOTS.length; i++) worn[i] = valid(TFJson.str(p, SLOTS[i], ""), i);
                if (!isEmpty(worn)) WORN.put(UUID.fromString(key), worn);
            } catch (Exception ignored) {
                // entrada rota: se ignora
            }
        }
    }

    private static void save() {
        if (server == null) return;
        JsonObject players = new JsonObject();
        WORN.forEach((uuid, worn) -> {
            JsonObject p = new JsonObject();
            for (int i = 0; i < SLOTS.length; i++) if (!worn[i].isEmpty()) p.addProperty(SLOTS[i], worn[i]);
            players.add(uuid.toString(), p);
        });
        JsonObject json = new JsonObject();
        json.add("jugadores", players);
        TFJson.write(dataFile(), json);
    }

    // ------------------------------------------------------------------------------------------- red

    /** El armario de un jugador: «set/pieza» por hueco ("" = nada). */
    public record Sync(UUID player, String[] items) {
        static void write(Sync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            for (int i = 0; i < SLOTS.length; i++) buf.writeUtf(m.items[i], 96);
        }

        static Sync read(FriendlyByteBuf buf) {
            UUID player = buf.readUUID();
            String[] items = new String[SLOTS.length];
            for (int i = 0; i < SLOTS.length; i++) items[i] = buf.readUtf(96);
            return new Sync(player, items);
        }

        static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ItemStack[] shown = new ItemStack[SLOTS.length];
            boolean any = false;
            for (int i = 0; i < SLOTS.length; i++) {
                String piece = m.items[i];
                Item item = piece.isEmpty() ? null : item(piece);
                if (fits(item, i)) {
                    shown[i] = new ItemStack(item);
                    any = true;
                }
            }
            if (any) SHOWN.put(m.player, shown);
            else SHOWN.remove(m.player);
        }
    }

    // ------------------------------------------------------------------------------------------- cliente (dibujo)

    @Nullable
    private static ItemStack shown(LivingEntity entity, int slot) {
        if (!(entity instanceof Player) || SHOWN.isEmpty()) return null;
        ItemStack[] shown = SHOWN.get(entity.getUUID());
        return shown == null ? null : shown[slot];
    }

    private static int slotIndex(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HEAD;
            case CHEST -> CHEST;
            case LEGS -> LEGS;
            case FEET -> FEET;
            default -> -1;
        };
    }

    /**
     * Lo que dibuja la capa de armadura en un hueco: la armadura del armario; si en la cabeza hay un cosmético, nada
     * (lo dibuja la capa de la cabeza en lugar del casco).
     */
    public static ItemStack armorLayer(LivingEntity entity, EquipmentSlot slot, ItemStack real) {
        int i = slotIndex(slot);
        ItemStack o = i < 0 ? null : shown(entity, i);
        if (o == null) return real;
        return o.getItem() instanceof ArmorItem ? o : ItemStack.EMPTY;
    }

    /** Lo que dibuja la capa de la cabeza: el cosmético del armario; si es un casco de armadura, nada. */
    public static ItemStack headLayer(LivingEntity entity, ItemStack real) {
        ItemStack o = shown(entity, HEAD);
        if (o == null) return real;
        return o.getItem() instanceof ArmorItem ? ItemStack.EMPTY : o;
    }

    /** Lo de la espalda del armario (null si no hay): tapa a los de la espalda que lleve puestos. */
    @Nullable
    public static ItemStack back(LivingEntity entity) {
        return shown(entity, BACK);
    }

    /** true si se dibuja una pechera del armario (lo de la espalda va un píxel más atrás). */
    public static boolean chestArmor(LivingEntity entity) {
        ItemStack o = shown(entity, CHEST);
        return o != null && o.getItem() instanceof ArmorItem;
    }

    /** Al salir de un servidor se olvida el armario de todos. */
    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            SHOWN.clear();
        }
    }
}
