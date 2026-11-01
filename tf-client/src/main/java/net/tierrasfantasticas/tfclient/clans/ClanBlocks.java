/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.156) Los bloques de los clanes: el cofre del Banco del clan (1.3.158: el modelo de la crate de madera crate_lvl1
 * de TF Crates, tools/pad/banco_modelo.py; 1.3.159: a la escala de las crates y dibujado a plena luz por
 * ClanBankRenderer, como las crates) y (1.3.159) el estandarte del clan, con la insignia del clan (la de ahora: si la
 * cambian, cambia en todos sus estandartes) y su etiqueta.
 */
public final class ClanBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TFClient.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TFClient.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TFClient.MOD_ID);

    public static final RegistryObject<Block> BANK = BLOCKS.register("banco_clan", ClanBankBlock::new);
    public static final RegistryObject<Item> BANK_ITEM = ITEMS.register("banco_clan",
            () -> new BlockItem(BANK.get(), new Item.Properties().rarity(Rarity.RARE).stacksTo(1)));
    public static final RegistryObject<Block> BANNER = BLOCKS.register("estandarte_clan", ClanBannerBlock::new);
    public static final RegistryObject<Item> BANNER_ITEM = ITEMS.register("estandarte_clan",
            () -> new BlockItem(BANNER.get(), new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ClanBankBlockEntity>> BANK_BE = BLOCK_ENTITIES.register("banco_clan",
            () -> BlockEntityType.Builder.of(ClanBankBlockEntity::new, BANK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ClanBannerBlockEntity>> BANNER_BE = BLOCK_ENTITIES.register("estandarte_clan",
            () -> BlockEntityType.Builder.of(ClanBannerBlockEntity::new, BANNER.get()).build(null));

    private ClanBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
