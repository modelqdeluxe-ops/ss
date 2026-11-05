package com.lukeolafp.relativeblocks.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lukeolafp.relativeblocks.RelativeBlocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Una losa vertical («ortostato») por cada losa de cualquier mod. En Fabric se creaban al registrarse cada losa
 * (RegistryEntryAddedCallback); en Forge, al acabar el registro de bloques de TODOS los mods, justo antes de que se
 * congele (mixin/GameDataMixin): así salen también las de los mods que se registran después de este. Sus objetos, en
 * el RegisterEvent de los objetos.
 */
public final class VerticalSlabs {
   private static final Map<SlabBlock, VerticalSlabBlock> BY_SOURCE = new LinkedHashMap<>();
   private static final Map<VerticalSlabBlock, ResourceLocation> IDS = new LinkedHashMap<>();
   private static boolean ready;
   private static boolean registered;

   private VerticalSlabs() {
   }

   public static Collection<VerticalSlabBlock> all() {
      return Collections.unmodifiableCollection(BY_SOURCE.values());
   }

   @org.jetbrains.annotations.Nullable
   public static ResourceLocation idOf(VerticalSlabBlock block) {
      return IDS.get(block);
   }

   /** El mod ya está cargado: a partir de aquí se pueden crear las losas verticales. */
   public static void ready() {
      ready = true;
   }

   /** GameDataMixin: todos los mods registraron sus bloques y el registro aún está abierto. */
   public static void registerBlocks() {
      if (!ready || registered) {
         return;
      }
      registered = true;
      ModContainer self = ModList.get().getModContainerById(RelativeBlocks.MOD_ID).orElse(null);
      ModLoadingContext context = ModLoadingContext.get();
      context.setActiveContainer(self);
      try {
         List<Entry<ResourceKey<Block>, Block>> entries = new ArrayList<>(ForgeRegistries.BLOCKS.getEntries());
         for (Entry<ResourceKey<Block>, Block> entry : entries) {
            consider(entry.getKey().location(), entry.getValue());
         }
      } finally {
         context.setActiveContainer(null);
      }
      RelativeBlocks.LOGGER.info("Registered {} vertical slabs", BY_SOURCE.size());
   }

   private static void consider(ResourceLocation id, Block block) {
      if (block instanceof SlabBlock slab && !BY_SOURCE.containsKey(slab) && slab.defaultBlockState().hasProperty(SlabBlock.TYPE)) {
         ResourceLocation verticalId = idFor(id);
         VerticalSlabBlock vertical = new VerticalSlabBlock(slab);
         ForgeRegistries.BLOCKS.register(verticalId, vertical);
         BY_SOURCE.put(slab, vertical);
         IDS.put(vertical, verticalId);
      }
   }

   public static void registerItems(RegisterEvent.RegisterHelper<Item> helper) {
      for (Entry<VerticalSlabBlock, ResourceLocation> entry : IDS.entrySet()) {
         final VerticalSlabBlock vertical = entry.getKey();
         helper.register(entry.getValue(), new BlockItem(vertical, new Item.Properties()) {
            @Override
            public Component getDescription() {
               return vertical.getName();
            }

            @Override
            public Component getName(ItemStack stack) {
               return vertical.getName();
            }
         });
      }
   }

   /** Como en Fabric: en «Bloques de construcción», cada una detrás de su losa (o al final si su losa no está). */
   public static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() != CreativeModeTabs.BUILDING_BLOCKS) {
         return;
      }
      for (VerticalSlabBlock vertical : BY_SOURCE.values()) {
         ItemStack mine = new ItemStack(vertical);
         if (mine.isEmpty()) {
            continue;
         }
         ItemStack source = new ItemStack(vertical.getSource());
         if (!source.isEmpty() && event.getEntries().contains(source)) {
            event.getEntries().putAfter(source, mine, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
         } else {
            event.accept(mine);
         }
      }
   }

   private static ResourceLocation idFor(ResourceLocation slab) {
      return RelativeBlocks.id("vertical_slab/" + slab.getNamespace() + "/" + slab.getPath());
   }

   /** Las recetas de las losas verticales, sacadas de las de sus losas (tres en fila → tres en columna). */
   public static void addRecipes(Map<ResourceLocation, JsonElement> recipes) {
      Map<ResourceLocation, JsonElement> sorted = new TreeMap<>(recipes);
      List<SlabBlock> done = new ArrayList<>();
      int added = 0;

      for (Entry<ResourceLocation, JsonElement> entry : sorted.entrySet()) {
         if (!(entry.getValue() instanceof JsonObject recipe)) {
            continue;
         }
         if (!"minecraft:crafting_shaped".equals(GsonHelper.getAsString(recipe, "type", ""))) {
            continue;
         }
         if (!(recipe.get("result") instanceof JsonObject result)) {
            continue;
         }
         ResourceLocation output = ResourceLocation.tryParse(GsonHelper.getAsString(result, "item", ""));
         if (output == null || !BuiltInRegistries.ITEM.containsKey(output)) {
            continue;
         }
         if (!(Block.byItem(BuiltInRegistries.ITEM.get(output)) instanceof SlabBlock slab)) {
            continue;
         }
         if (done.contains(slab) || !BY_SOURCE.containsKey(slab)) {
            continue;
         }
         JsonElement ingredient = rowOfThree(recipe);
         if (ingredient == null) {
            continue;
         }
         ResourceLocation verticalId = IDS.get(BY_SOURCE.get(slab));
         if (verticalId == null || recipes.containsKey(verticalId)) {
            continue;
         }
         recipes.put(verticalId, stacked(recipe, ingredient, verticalId, GsonHelper.getAsInt(result, "count", 1)));
         done.add(slab);
         added++;
      }

      RelativeBlocks.LOGGER.info("Worked out {} vertical slab recipes", added);
   }

   private static JsonElement rowOfThree(JsonObject recipe) {
      if (recipe.get("pattern") instanceof JsonArray pattern && pattern.size() == 1 && recipe.get("key") instanceof JsonObject key) {
         String row = pattern.get(0).getAsString();
         if (row.length() == 3 && row.charAt(0) != ' ' && row.chars().distinct().count() == 1L) {
            return key.get(String.valueOf(row.charAt(0)));
         }
      }
      return null;
   }

   private static JsonObject stacked(JsonObject slabRecipe, JsonElement ingredient, ResourceLocation vertical, int count) {
      JsonObject recipe = new JsonObject();
      recipe.addProperty("type", "minecraft:crafting_shaped");
      recipe.addProperty("category", "building");
      // las condiciones de la receta de la losa (Forge: «conditions»), para que solo exista si existe la de la losa
      if (slabRecipe.has("conditions")) {
         recipe.add("conditions", slabRecipe.get("conditions").deepCopy());
      }
      JsonArray pattern = new JsonArray();
      pattern.add("#");
      pattern.add("#");
      pattern.add("#");
      recipe.add("pattern", pattern);
      JsonObject key = new JsonObject();
      key.add("#", ingredient.deepCopy());
      recipe.add("key", key);
      JsonObject result = new JsonObject();
      result.addProperty("item", vertical.toString());
      result.addProperty("count", count);
      recipe.add("result", result);
      return recipe;
   }
}
