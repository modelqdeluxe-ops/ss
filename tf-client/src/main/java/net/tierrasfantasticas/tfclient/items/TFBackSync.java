/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.138, el dueño: «no arreglaste esto: las alas de Accessories se quedan dibujadas aunque las quite») Lo que lleva
 * cada jugador en la espalda lo dice el SERVIDOR, no la copia que tenga el cliente del mod de accesorios (que puede
 * quedarse vieja: el jugador se las quita y en el cliente siguen ahí). Cada medio segundo el servidor mira las alas,
 * mochilas, capas y colas de cada jugador (en el pecho y en los huecos de Accessories o Curios, también los cosméticos)
 * y, si cambian, se lo cuenta a todos. El cliente solo dibuja en la espalda lo que el servidor confirma
 * ({@link #allowed}): el dibujante que registramos en el mod de accesorios, el respaldo de BackLayer y el Armario.
 * Sin noticias del servidor sobre ese jugador (un servidor sin TF Client), se dibuja como antes.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFBackSync {
    private static final Predicate<ItemStack> IS_BACK = stack ->
            stack.getItem() instanceof TFItemTypes.Cosmetic cosmetic && cosmetic.wornModel() != null;
    private static SimpleChannel channel;

    /** Servidor: lo último que se contó de cada jugador (ids de los objetos de su espalda). */
    private static final Map<UUID, List<String>> SENT = new HashMap<>();
    /** Cliente: lo que lleva en la espalda cada jugador según el servidor. */
    private static final Map<UUID, Set<Item>> WORN = new ConcurrentHashMap<>();

    private TFBackSync() {}

    static void register(SimpleChannel ch, int id) {
        channel = ch;
        ch.messageBuilder(Msg.class, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Msg::write).decoder(Msg::read).consumerMainThread(Msg::handle).add();
    }

    // ------------------------------------------------------------------------------------------- cliente

    /**
     * ¿Se puede dibujar este objeto en la espalda de esta entidad? Sí si el servidor dice que lo lleva (o si no ha dicho
     * nada de ella); no si el servidor dice que no lo lleva.
     */
    public static boolean allowed(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof Player) || testOff) return true;
        Set<Item> worn = WORN.get(entity.getUUID());
        return worn == null || worn.contains(stack.getItem());
    }

    /**
     * Lo que dice el servidor de los huecos de accesorios de este jugador: true si lleva algo en la espalda fuera del
     * pecho, false si no, null si no ha dicho nada (entonces se mira el mod de accesorios).
     */
    public static Boolean wearsInSlots(LivingEntity entity) {
        if (!(entity instanceof Player) || testOff) return null;
        Set<Item> worn = WORN.get(entity.getUUID());
        if (worn == null) return null;
        Item chest = entity.getItemBySlot(EquipmentSlot.CHEST).getItem();
        for (Item item : worn) if (item != chest) return true;
        return false;
    }

    /** Para la prueba en el juego: sin el arreglo (como antes: se dibuja lo que diga el mod de accesorios del cliente). */
    public static volatile boolean testOff;

    /** Para la prueba en el juego: lo que dice el servidor de ese jugador. */
    public static String describe(LivingEntity entity) {
        Set<Item> worn = WORN.get(entity.getUUID());
        if (worn == null) return "sin noticias";
        List<String> out = new ArrayList<>();
        for (Item i : worn) out.add(String.valueOf(ForgeRegistries.ITEMS.getKey(i)));
        return out.isEmpty() ? "nada" : String.join(",", out);
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            WORN.clear();
        }
    }

    // ------------------------------------------------------------------------------------------- servidor

    /** Las alas, mochilas… de su espalda: en el pecho y en los huecos de Accessories / Curios (también los cosméticos). */
    static List<String> backOf(ServerPlayer player) {
        List<ItemStack> found = new ArrayList<>();
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (IS_BACK.test(chest)) found.add(chest);
        ModList mods = ModList.get();
        try {
            if (mods.isLoaded("accessories")) {
                TFAccessoryLookup.fromAccessories(player, IS_BACK, found);
                TFAccessoryLookup.cosmeticsFromAccessories(player, IS_BACK, found);
            }
        } catch (Throwable ignored) {
            // otra versión de Accessories: lo que se haya podido leer
        }
        try {
            if (mods.isLoaded("curios")) {
                TFAccessoryLookup.fromCurios(player, IS_BACK, found, false);
                TFAccessoryLookup.cosmeticsFromCurios(player, IS_BACK, found);
            }
        } catch (Throwable ignored) {
            // otra versión de Curios
        }
        Set<String> ids = new java.util.TreeSet<>();
        for (ItemStack s : found) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key != null) ids.add(key.toString());
        }
        return new ArrayList<>(ids);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || channel == null
                || player.tickCount % 10 != 0) return;
        List<String> now = backOf(player);
        List<String> before = SENT.get(player.getUUID());
        if (now.equals(before)) return;
        SENT.put(player.getUUID(), now);
        channel.send(PacketDistributor.ALL.noArg(), new Msg(player.getUUID(), now));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || channel == null) return;
        // al que entra, lo de los que ya están (lo suyo sale en su primer medio segundo)
        for (Map.Entry<UUID, List<String>> e : SENT.entrySet()) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), new Msg(e.getKey(), e.getValue()));
        }
        SENT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------------------------------- red

    /** Lo que lleva un jugador en la espalda (ids de objetos; vacía = nada). */
    public record Msg(UUID player, List<String> items) {
        static void write(Msg m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeVarInt(Math.min(32, m.items.size()));
            for (int i = 0; i < Math.min(32, m.items.size()); i++) buf.writeUtf(m.items.get(i), 128);
        }

        static Msg read(FriendlyByteBuf buf) {
            UUID player = buf.readUUID();
            int n = Math.min(32, buf.readVarInt());
            List<String> items = new ArrayList<>();
            for (int i = 0; i < n; i++) items.add(buf.readUtf(128));
            return new Msg(player, items);
        }

        static void handle(Msg m, Supplier<NetworkEvent.Context> ctx) {
            Set<Item> set = new HashSet<>();
            for (String id : m.items) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) set.add(item);
            }
            WORN.put(m.player, set);
            ctx.get().setPacketHandled(true);
        }
    }
}
