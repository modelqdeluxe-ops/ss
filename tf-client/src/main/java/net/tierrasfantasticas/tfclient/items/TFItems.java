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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Registra todos los objetos de los sets (tfclient:&lt;set&gt;_&lt;objeto&gt;). No tienen receta ni pestaña en el modo
 * creativo (1.3.168, el dueño: «elimina por completo el registro del inventario creativo»): solo se dan con los comandos
 * de TF (/tf web sets give…) y el servidor no deja sacarlos del creativo (TFCreativeGuard).
 */
public final class TFItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TFClient.MOD_ID);

    /** id del set → objetos del set, en orden. */
    public static final Map<String, List<RegistryObject<Item>>> BY_SET = new LinkedHashMap<>();
    /** Objetos por tipo, para darles en el cliente sus animaciones (tensar el arco, lanzar la caña...). */
    public static final Map<String, List<RegistryObject<Item>>> BY_TYPE = new LinkedHashMap<>();
    /** Iconos de los oficios (/tf jobs): tfclient:job_&lt;icono&gt;, solo para los menús (no salen en el creativo). */
    /** La Fantastic Coin: la moneda de oro de Tierras Fantásticas. Solo es el icono de las monedas en los menús (la
     *  tienda /tf shop, los oficios): el saldo es el de la economía del servidor (TFEconomy), no un objeto. */
    public static RegistryObject<Item> COIN;
    /** La moneda verde (1.3.38): la de abrir el Gachapón del pad. También es solo un icono: el saldo lo lleva el
     *  servidor (pad/server/PadGreen) y solo se gana con el TF Pass. */
    public static RegistryObject<Item> GREEN_COIN;
    public static RegistryObject<Item> ADMIN_PAD;
    /** (1.3.134) El disco de una canción compartida desde la app Música (pad/SharedDiscItem). */
    public static RegistryObject<Item> SHARED_DISC;
    public static final List<String> JOB_ICONS = List.of("farmer", "miner", "wood_cutter", "digger", "fisherman", "hunter",
            "alchemist", "blacksmith", "builder", "enchanter");

    private TFItems() {}

    /** Los objetos de los sets y las piedras de protección de TF Claims (los que se esconden de JEI y compañía). */
    public static List<Item> tabItems() {
        List<Item> out = new ArrayList<>();
        BY_SET.values().forEach(list -> list.forEach(o -> out.add(o.get())));
        net.tierrasfantasticas.tfclient.claims.item.ClaimItems.all().forEach(o -> out.add(o.get()));
        return out;
    }

    public static void register(IEventBus modBus) {
        TFSets.load();
        for (TFSets.SetDef set : TFSets.all().values()) {
            List<RegistryObject<Item>> list = new ArrayList<>();
            for (TFSets.ItemDef def : set.items()) {
                RegistryObject<Item> obj = ITEMS.register(def.id(), () -> TFItemTypes.tag(create(set, def), set));
                list.add(obj);
                BY_TYPE.computeIfAbsent(def.type(), k -> new ArrayList<>()).add(obj);
            }
            BY_SET.put(set.id(), list);
        }
        for (String icon : JOB_ICONS) ITEMS.register("job_" + icon, () -> new Item(new Item.Properties().stacksTo(1)));
        COIN = ITEMS.register("fantastic_coin", () -> new net.tierrasfantasticas.tfclient.economy.TFCoinItem(new Item.Properties().rarity(Rarity.UNCOMMON), false));
        GREEN_COIN = ITEMS.register("fantastic_coin_green", () -> new net.tierrasfantasticas.tfclient.economy.TFCoinItem(new Item.Properties().rarity(Rarity.RARE), true));
        ADMIN_PAD = ITEMS.register("pad_admin", net.tierrasfantasticas.tfclient.pad.AdminPadItem::new);
        SHARED_DISC = ITEMS.register("disco_compartido", net.tierrasfantasticas.tfclient.pad.SharedDiscItem::new);
        // (1.3.101) El panel de control de los NPC de escenas (/tf npc)
        net.tierrasfantasticas.tfclient.npcs.TFNpcs.PANEL = ITEMS.register("npc_panel", net.tierrasfantasticas.tfclient.npcs.NpcPanelItem::new);
        ITEMS.register(modBus);
        // (1.3.171) Densidad, Brecha y Ráfaga de viento (la maza) y las lanzas lanzadas con su modelo
        TFMace.ENCHANTMENTS.register(modBus);
        ENTITIES.register(modBus);
    }

    /** (1.3.171) El tridente de un set lanzado: se dibuja con su modelo, no con el tridente de Minecraft. */
    public static final net.minecraftforge.registries.DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES =
            net.minecraftforge.registries.DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TFClient.MOD_ID);
    public static final RegistryObject<net.minecraft.world.entity.EntityType<TFThrownTrident>> THROWN_TRIDENT = ENTITIES.register("lanza_lanzada",
            () -> net.minecraft.world.entity.EntityType.Builder.<TFThrownTrident>of(TFThrownTrident::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).build("lanza_lanzada"));

    // Los atributos de los objetos de los sets no se enseñan en su descripción (ver TFItemTypes.HIDE_ATTRIBUTES).
    private static Item create(TFSets.SetDef set, TFSets.ItemDef def) {
        Item.Properties p = new Item.Properties().fireResistant().rarity(Rarity.EPIC);
        // Nivel de atributos del set: el de las crates o el de su rango (TFTier)
        Tier tier = set.tier().tool();
        return switch (def.type()) {
            // (1.3.171) los martillos y las mazas, como la maza de Minecraft 1.21 (TFMace); los garrotes y guanteletes, igual
            case "heavy" -> isMace(def.id()) ? new TFItemTypes.Mace(tier, p.durability(500)) : new TFItemTypes.Sword(tier, 6, -3.1F, p);
            case "axe" -> new TFItemTypes.Axe(tier, 5.0F, -3.0F, p);
            case "pickaxe" -> new TFItemTypes.Pickaxe(tier, 1, -2.8F, p);
            case "shovel" -> new TFItemTypes.Shovel(tier, 1.5F, -3.0F, p);
            case "hoe" -> new TFItemTypes.Hoe(tier, -4, 0.0F, p);
            case "bow" -> new TFItemTypes.Bow(p.durability(768));
            case "crossbow" -> new TFItemTypes.Crossbow(p.durability(930));
            case "fishing_rod" -> new TFItemTypes.FishingRod(p.durability(128));
            case "shield" -> new TFItemTypes.Shield(p.durability(672));
            case "trident" -> new TFItemTypes.Trident(p.durability(500));
            case "head" -> new TFItemTypes.Cosmetic(EquipmentSlot.HEAD, null, false, p.stacksTo(1),
                    def.armor() ? TFItemTypes.armorModifiers(set.tier(), ArmorItem.Type.HELMET) : null);
            case "back" -> new TFItemTypes.Cosmetic(EquipmentSlot.CHEST,
                    def.worn() != null ? new ResourceLocation(def.worn()) : null, def.glide(), p.stacksTo(1), null);
            case "armor" -> new TFItemTypes.Armor(set, armorType(def.slot()), p);
            case "held" -> new TFItemTypes.Held(false, p.stacksTo(1));
            case "balloon" -> new TFItemTypes.Held(true, p.stacksTo(1));
            default -> new TFItemTypes.Sword(tier, 3, -2.4F, p);
        };
    }

    /** (1.3.171) ¿Es un martillo o una maza de un set? (valentine_hammer, nazgul_forge_hammer, nazgul_fire_mace…) */
    public static boolean isMace(String id) {
        return id.contains("hammer") || id.contains("mace");
    }

    private static ArmorItem.Type armorType(String slot) {
        return switch (slot == null ? "" : slot) {
            case "helmet" -> ArmorItem.Type.HELMET;
            case "leggings" -> ArmorItem.Type.LEGGINGS;
            case "boots" -> ArmorItem.Type.BOOTS;
            default -> ArmorItem.Type.CHESTPLATE;
        };
    }
}
