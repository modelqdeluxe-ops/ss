package net.tierrasfantasticas.tfclient.items;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Nivel de atributos de un set (tf_sets.json → "tier"): "iron", "diamond", "netherite" o "netherite+N", que es la
 * netherita con N puntos más de daño en las armas y herramientas, de armadura en cada pieza y de dureza.
 * Las crates usan netherite+1; cada rango tiene el suyo (ver tierras-fantasticas/tools/crates.py).
 */
public record TFTier(String base, float bonus) {
    public static final TFTier DEFAULT = new TFTier("netherite", 1.0F);

    // Armadura de Minecraft por pieza, en el orden botas, grebas, peto, casco
    private static final int[] IRON_DEFENSE = {2, 5, 6, 2};
    private static final int[] DIAMOND_DEFENSE = {3, 6, 8, 3};
    private static final int[] NETHERITE_DEFENSE = {3, 6, 8, 3};

    public static TFTier parse(String s) {
        if (s == null || s.isBlank()) return DEFAULT;
        String[] parts = s.trim().split("\\+", 2);
        String base = switch (parts[0]) {
            case "iron", "diamond", "netherite" -> parts[0];
            default -> "netherite";
        };
        float bonus = 0.0F;
        if (parts.length > 1) {
            try {
                bonus = Math.max(0.0F, Float.parseFloat(parts[1]));
            } catch (NumberFormatException e) {
                bonus = 0.0F;
            }
        }
        return new TFTier(base, bonus);
    }

    private Tiers vanilla() {
        return switch (base) {
            case "iron" -> Tiers.IRON;
            case "diamond" -> Tiers.DIAMOND;
            default -> Tiers.NETHERITE;
        };
    }

    /** Nivel de las armas y herramientas: el de Minecraft con el daño extra del set. */
    public Tier tool() {
        Tiers v = vanilla();
        if (bonus <= 0.0F) return v;
        float extra = bonus;
        return new Tier() {
            @Override public int getUses() { return v.getUses(); }
            @Override public float getSpeed() { return v.getSpeed(); }
            @Override public float getAttackDamageBonus() { return v.getAttackDamageBonus() + extra; }
            @Override public int getLevel() { return v.getLevel(); }
            @Override public int getEnchantmentValue() { return v.getEnchantmentValue(); }
            @Override public Ingredient getRepairIngredient() { return v.getRepairIngredient(); }
        };
    }

    static int index(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
        };
    }

    public double defense(ArmorItem.Type type) {
        int[] d = switch (base) {
            case "iron" -> IRON_DEFENSE;
            case "diamond" -> DIAMOND_DEFENSE;
            default -> NETHERITE_DEFENSE;
        };
        return d[index(type)] + bonus;
    }

    public float toughness() {
        float t = switch (base) {
            case "iron" -> 0.0F;
            case "diamond" -> 2.0F;
            default -> 3.0F;
        };
        return t + bonus;
    }

    public float knockbackResistance() {
        if (!"netherite".equals(base)) return 0.0F;
        return bonus > 0.0F ? 0.2F : 0.1F;
    }

    /** Multiplicador de durabilidad de la armadura (como en Minecraft). */
    public int durabilityMultiplier() {
        return switch (base) {
            case "iron" -> 15;
            case "diamond" -> 33;
            default -> 37;
        };
    }

    public int armorEnchantment() {
        return switch (base) {
            case "iron" -> 9;
            case "diamond" -> 10;
            default -> 15;
        };
    }

    public SoundEvent equipSound() {
        return switch (base) {
            case "iron" -> SoundEvents.ARMOR_EQUIP_IRON;
            case "diamond" -> SoundEvents.ARMOR_EQUIP_DIAMOND;
            default -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
    }
}
