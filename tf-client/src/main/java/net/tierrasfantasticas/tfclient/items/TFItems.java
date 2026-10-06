package net.tierrasfantasticas.tfclient.items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Registra todos los objetos de los sets (tfclient:&lt;set&gt;_&lt;objeto&gt;) y su pestaña del modo creativo.
 * No tienen receta: se sacan del creativo o con /tf web sets give.
 */
public final class TFItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TFClient.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TFClient.MOD_ID);

    /** id del set → objetos del set, en orden. */
    public static final Map<String, List<RegistryObject<Item>>> BY_SET = new LinkedHashMap<>();
    /** Objetos por tipo, para darles en el cliente sus animaciones (tensar el arco, lanzar la caña...). */
    public static final Map<String, List<RegistryObject<Item>>> BY_TYPE = new LinkedHashMap<>();
    /** Iconos de los oficios (/tf jobs): tfclient:job_&lt;icono&gt;, solo para los menús (no salen en el creativo). */
    public static final List<String> JOB_ICONS = List.of("farmer", "miner", "wood_cutter", "digger", "fisherman", "hunter",
            "alchemist", "blacksmith", "builder", "enchanter");

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("sets", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tfclient.sets"))
            .icon(TFItems::icon)
            .displayItems((params, output) -> BY_SET.values().forEach(list -> list.forEach(o -> output.accept(o.get()))))
            .build());

    private TFItems() {}

    private static ItemStack icon() {
        for (List<RegistryObject<Item>> list : BY_SET.values()) {
            for (RegistryObject<Item> o : list) {
                if (o.getId().getPath().endsWith("_sword")) return new ItemStack(o.get());
            }
        }
        return BY_SET.isEmpty() ? ItemStack.EMPTY : new ItemStack(BY_SET.values().iterator().next().get(0).get());
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
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    // Los atributos de los objetos de los sets no se enseñan en su descripción (ver TFItemTypes.HIDE_ATTRIBUTES).
    private static Item create(TFSets.SetDef set, TFSets.ItemDef def) {
        Item.Properties p = new Item.Properties().fireResistant().rarity(Rarity.EPIC);
        // Nivel de atributos del set: el de las crates o el de su rango (TFTier)
        Tier tier = set.tier().tool();
        return switch (def.type()) {
            case "heavy" -> new TFItemTypes.Sword(tier, 6, -3.1F, p);
            case "axe" -> new TFItemTypes.Axe(tier, 5.0F, -3.0F, p);
            case "pickaxe" -> new TFItemTypes.Pickaxe(tier, 1, -2.8F, p);
            case "shovel" -> new TFItemTypes.Shovel(tier, 1.5F, -3.0F, p);
            case "hoe" -> new TFItemTypes.Hoe(tier, -4, 0.0F, p);
            case "bow" -> new TFItemTypes.Bow(p.durability(768));
            case "crossbow" -> new TFItemTypes.Crossbow(p.durability(930));
            case "fishing_rod" -> new TFItemTypes.FishingRod(p.durability(128));
            case "shield" -> new TFItemTypes.Shield(p.durability(672));
            case "trident" -> new TFItemTypes.Trident(p.durability(500));
            case "head" -> new TFItemTypes.Cosmetic(EquipmentSlot.HEAD, null, p.stacksTo(1));
            case "back" -> new TFItemTypes.Cosmetic(EquipmentSlot.CHEST,
                    def.worn() != null ? new ResourceLocation(def.worn()) : null, p.stacksTo(1));
            case "armor" -> new TFItemTypes.Armor(set, armorType(def.slot()), p);
            default -> new TFItemTypes.Sword(tier, 3, -2.4F, p);
        };
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
