package net.tierrasfantasticas.tfclient.items;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/** Los tipos de objeto de los sets: se comportan como los de Minecraft y llevan el nombre en el color del set. */
public final class TFItemTypes {
    private static final Map<Item, TFSets.SetDef> SET_OF = new HashMap<>();

    private TFItemTypes() {}

    static <T extends Item> T tag(T item, TFSets.SetDef set) {
        SET_OF.put(item, set);
        return item;
    }

    @Nullable
    public static TFSets.SetDef setOf(Item item) {
        return SET_OF.get(item);
    }

    static Component name(Item item, Component base) {
        TFSets.SetDef set = SET_OF.get(item);
        if (set == null) return base;
        MutableComponent c = base.copy();
        return c.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(set.color())).withItalic(false));
    }

    /** Los atributos (daño, velocidad, armadura…) de los objetos de los sets no salen en su descripción. */
    static final int HIDE_ATTRIBUTES = ItemStack.TooltipPart.MODIFIERS.getMask();

    static void tooltip(Item item, List<Component> tooltip) {
        TFSets.SetDef set = SET_OF.get(item);
        if (set == null) return;
        tooltip.add(Component.literal("Set " + set.name()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(set.color()))));
        tooltip.add(Component.literal("Tierras Fantásticas").withStyle(ChatFormatting.DARK_GRAY));
    }

    // --- Armas y herramientas ---

    public static class Sword extends SwordItem {
        public Sword(Tier tier, int damage, float speed, Properties p) {
            super(tier, damage, speed, p);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Axe extends AxeItem {
        public Axe(Tier tier, float damage, float speed, Properties p) {
            super(tier, damage, speed, p);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Pickaxe extends PickaxeItem {
        public Pickaxe(Tier tier, int damage, float speed, Properties p) {
            super(tier, damage, speed, p);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Shovel extends ShovelItem {
        public Shovel(Tier tier, float damage, float speed, Properties p) {
            super(tier, damage, speed, p);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Hoe extends HoeItem {
        public Hoe(Tier tier, int damage, float speed, Properties p) {
            super(tier, damage, speed, p);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Bow extends BowItem {
        public Bow(Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Crossbow extends CrossbowItem {
        public Crossbow(Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip(this, tooltip);
        }
    }

    public static class FishingRod extends FishingRodItem {
        public FishingRod(Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Shield extends ShieldItem {
        public Shield(Properties p) {
            super(p);
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, Component.translatable(getDescriptionId()));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    public static class Trident extends TridentItem {
        public Trident(Properties p) {
            super(p);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }
    }

    // --- Cosméticos: cascos y sombreros en la cabeza; alas, mochilas, capas y colas en la espalda ---

    public static class Cosmetic extends Item implements Equipable {
        private final EquipmentSlot slot;
        @Nullable
        private final ResourceLocation worn;

        public Cosmetic(EquipmentSlot slot, @Nullable ResourceLocation worn, Properties p) {
            super(p);
            this.slot = slot;
            this.worn = worn;
        }

        /** Modelo que se dibuja en la espalda (null para los de la cabeza, que dibuja Minecraft). */
        @Nullable
        public ResourceLocation wornModel() {
            return worn;
        }

        @Override
        public EquipmentSlot getEquipmentSlot() {
            return slot;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_GENERIC;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return swapWithEquipmentSlot(this, level, player, hand);
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.literal(slot == EquipmentSlot.HEAD ? "Cosmético · cabeza" : "Cosmético · espalda")
                    .withStyle(ChatFormatting.GRAY));
            tooltip(this, tooltip);
        }
    }

    // --- Armaduras ---

    /** Material de armadura de un set, según su nivel (TFTier). El nombre da la textura: tfclient:<set>_layer_N. */
    public record Material(String setId, TFTier tier) implements ArmorMaterial {
        private static final int[] DURABILITY = {13, 15, 16, 11};

        @Override
        public int getDurabilityForType(ArmorItem.Type type) {
            return DURABILITY[TFTier.index(type)] * tier.durabilityMultiplier();
        }

        @Override
        public int getDefenseForType(ArmorItem.Type type) {
            return (int) tier.defense(type);
        }

        @Override
        public int getEnchantmentValue() {
            return tier.armorEnchantment();
        }

        @Override
        public SoundEvent getEquipSound() {
            return tier.equipSound();
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @Override
        public String getName() {
            return "tfclient:" + setId;
        }

        @Override
        public float getToughness() {
            return tier.toughness();
        }

        @Override
        public float getKnockbackResistance() {
            return tier.knockbackResistance();
        }
    }

    // Los mismos identificadores que usa Minecraft para la armadura de cada pieza
    private static final Map<ArmorItem.Type, UUID> ARMOR_UUID = Map.of(
            ArmorItem.Type.BOOTS, UUID.fromString("845DB27C-C624-495F-8C9F-6020A9A58B6B"),
            ArmorItem.Type.LEGGINGS, UUID.fromString("D8499B04-0E66-4726-AB29-64469D734E0D"),
            ArmorItem.Type.CHESTPLATE, UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E"),
            ArmorItem.Type.HELMET, UUID.fromString("2AD3F246-FEE1-4E67-B886-69FD380BB150"));

    public static class Armor extends ArmorItem {
        private final TFSets.SetDef set;
        private final Multimap<Attribute, AttributeModifier> modifiers;

        public Armor(TFSets.SetDef set, ArmorItem.Type type, Properties p) {
            super(new Material(set.id(), set.tier()), type, p);
            this.set = set;
            // Los atributos se calculan aquí (y no con los enteros de ArmorMaterial) para admitir medios puntos.
            UUID uuid = ARMOR_UUID.get(type);
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.put(Attributes.ARMOR, new AttributeModifier(uuid, "Armor modifier", set.tier().defense(type), AttributeModifier.Operation.ADDITION));
            b.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(uuid, "Armor toughness", set.tier().toughness(), AttributeModifier.Operation.ADDITION));
            float kb = set.tier().knockbackResistance();
            if (kb > 0) {
                b.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(uuid, "Armor knockback resistance", kb, AttributeModifier.Operation.ADDITION));
            }
            this.modifiers = b.build();
        }

        @Override
        public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
            return slot == this.type.getSlot() ? modifiers : super.getDefaultAttributeModifiers(slot);
        }

        @Override
        public int getDefaultTooltipHideFlags(ItemStack stack) {
            return HIDE_ATTRIBUTES;
        }

        @Override
        public Component getName(ItemStack stack) {
            return name(this, super.getName(stack));
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip(this, tooltip);
        }

        /** Armaduras animadas (varios fotogramas): cambia de textura con el tiempo. */
        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            if (set.armorFrames() <= 1 || type != null) return null;
            int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
            int tick = entity != null ? entity.tickCount : 0;
            int frame = (tick / set.armorFrametime()) % set.armorFrames();
            return "tfclient:textures/models/armor/" + set.id() + "_layer_" + layer + "_f" + frame + ".png";
        }
    }
}
