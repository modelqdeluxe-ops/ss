package net.tierrasfantasticas.tfclient.crates.item;

import net.tierrasfantasticas.tfclient.crates.client.color.FSText;
import net.tierrasfantasticas.tfclient.crates.config.CrateConfig;
import net.tierrasfantasticas.tfclient.crates.config.Rarity;
import net.tierrasfantasticas.tfclient.crates.registry.ModRegistry;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;

public final class CrateItems {
   public static final String TAG_ROOT = "fscrates";
   public static final String TAG_IS_CRATE = "isCrate";
   public static final String TAG_CRATE_ID = "crateId";
   public static final String TAG_RARITY = "rarity";
   public static final String TAG_CONFIG = "config";
   public static final String BLOCK_ENTITY_TAG = "BlockEntityTag";

   private CrateItems() {
   }

   public static ItemStack buildCrate(CrateConfig crate) {
      ItemStack stack = new ItemStack((ItemLike)ModRegistry.CRATE_ITEM.get());
      CompoundTag root = new CompoundTag();
      root.putBoolean("isCrate", true);
      root.putString("crateId", crate.id);
      root.putString("rarity", crate.rarity.name());
      root.put("config", crate.save());
      stack.getOrCreateTag().put("fscrates", root);
      CompoundTag beTag = new CompoundTag();
      beTag.put("config", crate.save());
      stack.getOrCreateTag().put("BlockEntityTag", beTag);
      MutableComponent name = Component.literal(
            crate.displayName.isEmpty() ? "✦ Crate " + crate.rarity.displayName() + " ✦" : FSText.toLegacy(crate.displayName).replace('&', '§')
         )
         .withStyle(crate.rarity.color());
      stack.setHoverName(name);
      if (crate.glow) {
         EnchantmentHelper.setEnchantments(Map.of(Enchantments.UNBREAKING, 1), stack);
         stack.getOrCreateTag().putInt("HideFlags", 1);
      }

      applyLore(
         stack,
         "§7Rareza base: " + crate.rarity.color() + crate.rarity.displayName(),
         "§7Colócala y ábrela con la §d✦ Fantastic Key ✦§7.",
         crate.cooldownSeconds > 0 ? "§8Cooldown: " + crate.cooldownSeconds : null
      );
      return stack;
   }

   public static ItemStack buildKey() {
      return new ItemStack(ModRegistry.key());
   }

   private static KeyModels.Entry resolveUniqueEntry(CrateConfig crate) {
      KeyModels.Entry e = KeyModels.byId(crate.uniqueKeyModel);
      return e != null ? e : KeyModels.first();
   }

   public static String expectedUniqueKeyName(CrateConfig crate) {
      if (crate.uniqueKeyName != null && !crate.uniqueKeyName.isBlank()) {
         return crate.uniqueKeyName;
      } else {
         KeyModels.Entry e = resolveUniqueEntry(crate);
         return e != null ? e.defaultName : "✦ Llave de Crate ✦";
      }
   }

   public static ItemStack buildUniqueKey(CrateConfig crate) {
      KeyModels.Entry entry = resolveUniqueEntry(crate);
      ItemStack stack = new ItemStack(ModRegistry.uniqueKey());
      String name = expectedUniqueKeyName(crate);
      CompoundTag root = new CompoundTag();
      root.putString("keyModel", entry != null ? entry.id : "");
      root.putString("crateId", crate.id == null ? "" : crate.id);
      root.putString("crateName", crate.displayName == null ? "" : crate.displayName);
      root.putString("keyName", name);
      stack.getOrCreateTag().put("fscrates", root);
      if (entry != null) {
         stack.getOrCreateTag().putInt("CustomModelData", entry.cmd);
      }

      MutableComponent hover = Component.literal(name.replace('&', '§'));
      stack.setHoverName(hover);
      return stack;
   }

   public static boolean isUniqueKey(ItemStack stack) {
      return stack != null && stack.getItem() instanceof UniqueKeyItem;
   }

   public static String uniqueKeyCrateId(ItemStack stack) {
      return stack != null && stack.hasTag() ? stack.getTag().getCompound("fscrates").getString("crateId") : "";
   }

   public static boolean uniqueKeyMatches(CrateConfig crate, ItemStack key) {
      if (crate != null && isUniqueKey(key) && key != null && key.hasTag()) {
         CompoundTag root = key.getTag().getCompound("fscrates");
         if (!crate.id.equals(root.getString("crateId"))) {
            return false;
         } else {
            KeyModels.Entry entry = resolveUniqueEntry(crate);
            String expectModel = entry != null ? entry.id : "";
            return !expectModel.equals(root.getString("keyModel")) ? false : expectedUniqueKeyName(crate).equals(root.getString("keyName"));
         }
      } else {
         return false;
      }
   }

   public static ItemStack buildEditorWand() {
      return new ItemStack((ItemLike)ModRegistry.EDITOR_WAND.get());
   }

   public static boolean isEditorWand(ItemStack stack) {
      return stack != null && stack.getItem() instanceof EditorWandItem;
   }

   public static boolean isCrate(ItemStack stack) {
      return stack != null && stack.hasTag() && stack.getTag().getCompound("fscrates").getBoolean("isCrate");
   }

   public static boolean isKey(ItemStack stack) {
      return stack != null && stack.getItem() instanceof KeyItem;
   }

   public static String crateId(ItemStack stack) {
      return stack != null && stack.hasTag() ? stack.getTag().getCompound("fscrates").getString("crateId") : "";
   }

   public static Rarity rarity(ItemStack stack) {
      return stack != null && stack.hasTag() ? Rarity.byName(stack.getTag().getCompound("fscrates").getString("rarity")) : Rarity.COMMON;
   }

   public static ItemStack findUsableKey(Player player, CrateConfig crate) {
      if (player != null && crate != null) {
         ItemStack mainHand = player.getMainHandItem();
         if (keyOpens(crate, mainHand)) {
            return mainHand;
         } else {
            for (ItemStack stack : player.getInventory().items) {
               if (keyOpens(crate, stack)) {
                  return stack;
               }
            }

            ItemStack offhand = player.getOffhandItem();
            return keyOpens(crate, offhand) ? offhand : ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static boolean keyOpens(CrateConfig crate, ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      } else {
         return isKey(stack) ? true : crate != null && crate.uniqueKeyEnabled && uniqueKeyMatches(crate, stack);
      }
   }

   public static String requiredKeyName(CrateConfig crate) {
      return crate != null && crate.uniqueKeyEnabled ? expectedUniqueKeyName(crate).replace('&', '§') : "§d✦ Fantastic Key ✦";
   }

   public static CrateConfig readConfig(ItemStack stack) {
      if (!isCrate(stack)) {
         return null;
      } else {
         CompoundTag root = stack.getTag().getCompound("fscrates");
         return !root.contains("config") ? null : CrateConfig.load(root.getCompound("config"));
      }
   }

   private static void applyLore(ItemStack stack, String... lines) {
      ListTag lore = new ListTag();

      for (String line : lines) {
         if (line != null) {
            MutableComponent c = Component.literal(line);
            lore.add(StringTag.valueOf(Serializer.toJson(c)));
         }
      }

      CompoundTag display = stack.getOrCreateTagElement("display");
      display.put("Lore", lore);
   }
}
