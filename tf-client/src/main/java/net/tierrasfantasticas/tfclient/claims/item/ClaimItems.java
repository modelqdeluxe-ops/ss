package net.tierrasfantasticas.tfclient.claims.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;

/**
 * Las piedras de protección: un bloque (tfclient:proteccion_&lt;tamaño&gt;, con la textura de TF del color de su
 * tamaño) y su objeto, que no es un BlockItem: al usarlo lo coloca {@code BlockProtectionEvents} y crea la zona.
 * Los objetos de Fantastic Claims (claimblocks:proteccion_*) que haya en los mundos pasan solos a estos.
 */
public final class ClaimItems {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TFClient.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TFClient.MOD_ID);
    private static final Map<String, RegistryObject<Item>> BY_TIER = new HashMap<>();
    private static final Map<String, RegistryObject<Block>> BLOCK_BY_TIER = new HashMap<>();
    /** proteccion_10x10 → objeto (para pasar los de Fantastic Claims). */
    private static final Map<String, RegistryObject<Item>> BY_NAME = new HashMap<>();
    private static final List<RegistryObject<Item>> ALL = new ArrayList<>();

    private ClaimItems() {
    }

    public static void register(IEventBus ieventbus) {
        BLOCKS.register(ieventbus);
        ITEMS.register(ieventbus);
    }

    public static String name(ClaimTier claimtier) {
        return "proteccion_" + claimtier.label().toLowerCase(Locale.ROOT);
    }

    public static Item itemFor(ClaimTier claimtier) {
        if (claimtier == null) {
            return null;
        }
        RegistryObject<Item> registryobject = BY_TIER.get(claimtier.id);
        return registryobject != null && registryobject.isPresent() ? registryobject.get() : null;
    }

    public static Block blockFor(ClaimTier claimtier) {
        if (claimtier == null) {
            return null;
        }
        RegistryObject<Block> registryobject = BLOCK_BY_TIER.get(claimtier.id);
        return registryobject != null && registryobject.isPresent() ? registryobject.get() : null;
    }

    public static List<RegistryObject<Item>> all() {
        return ALL;
    }

    public static String registryName(ClaimTier claimtier) {
        return claimtier == null ? "" : TFClient.MOD_ID + ":" + ClaimItems.name(claimtier);
    }

    /** Fantastic Claims ya no está: sus piedras (claimblocks:proteccion_*) pasan a ser las de TF Claims. */
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, "claimblocks")) {
            RegistryObject<Item> registryobject = BY_NAME.get(mapping.getKey().getPath());
            if (registryobject != null && registryobject.isPresent()) {
                mapping.remap(registryobject.get());
            }
        }
    }

    static {
        for (ClaimTier claimtier : ClaimTier.VALUES) {
            String s = ClaimItems.name(claimtier);
            BLOCK_BY_TIER.put(claimtier.id, BLOCKS.register(s, () -> new ProtectionBlock(claimtier)));
            RegistryObject<Item> registryobject = ITEMS.register(s, () -> new ProtectionItem(claimtier));
            BY_TIER.put(claimtier.id, registryobject);
            BY_NAME.put(s, registryobject);
            ALL.add(registryobject);
        }
    }
}
