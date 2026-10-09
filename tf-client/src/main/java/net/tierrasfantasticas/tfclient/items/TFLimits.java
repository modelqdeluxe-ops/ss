package net.tierrasfantasticas.tfclient.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.common.collect.Multimap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Techo de atributos: ninguna arma, herramienta ni armadura de ningún mod supera a la netherita. Los únicos objetos por
 * encima son los de los sets de Tierras Fantásticas ({@link TFItem}: rangos, crates y lo que se reparte en eventos),
 * vinculados a su dueño. Así hay un tope fijo y justo para todos.
 *
 * <p>Se aplica a todos los objetos de todos los mods al leer sus atributos (Forge: ItemAttributeModifierEvent, con la
 * prioridad más baja para ir después de los demás mods): el daño de ataque, la armadura de cada pieza, la dureza y la
 * resistencia al empuje se recortan al valor de la netherita (y se quitan los multiplicadores que suban). La velocidad
 * de minar se recorta al ritmo de un pico de netherita. Lo que ya está por debajo no cambia.
 *
 * <p>config/tfclient/limites.json (se crea solo): activado, los topes y «excepciones» (ids de objetos o «mod:*»).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class TFLimits {
    private static final UUID CAP_ID = UUID.fromString("6f6a8c33-2f0b-4d8b-9c1e-74661cafe001");
    // Mismos identificadores que usa Minecraft para el daño de las armas (así el tooltip sigue en verde)
    private static final UUID BASE_DAMAGE = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    private static boolean loaded;
    private static boolean enabled = true;
    /** Daño que se ve en el tooltip (incluye el 1 de la mano): espada de netherita 8, hacha de netherita 10. */
    private static double damage = 8;
    private static double heavyDamage = 10;
    /** Por debajo de esta velocidad de ataque, el arma cuenta como pesada (hacha de netherita: −3,0). */
    private static double heavySpeed = -2.9;
    private static double helmet = 3;
    private static double chest = 8;
    private static double legs = 6;
    private static double boots = 3;
    private static double toughness = 3;
    private static double knockback = 0.1;
    private static float mining = 9.0F;
    private static final Set<String> exceptions = new HashSet<>();

    private TFLimits() {}

    public static Path file() {
        return net.tierrasfantasticas.tfclient.util.TFConfigDir.file("limites.json", "tfclient-limits.json");
    }

    public static void load() {
        loaded = true;
        Path file = file();
        JsonObject json = Files.exists(file) ? TFJson.read(file) : null;
        if (json == null) {
            json = new JsonObject();
            if (Files.exists(file)) TFClient.LOGGER.warn("TF Límites: {} no es un JSON válido: uso los valores por defecto", file);
        }
        enabled = TFJson.bool(json, "activado", true);
        damage = TFJson.dec(json, "danoMaximo", 8);
        heavyDamage = TFJson.dec(json, "danoMaximoArmasPesadas", 10);
        heavySpeed = TFJson.dec(json, "velocidadArmaPesada", -2.9);
        JsonObject armor = TFJson.obj(json, "armadura");
        helmet = TFJson.dec(armor, "casco", 3);
        chest = TFJson.dec(armor, "peto", 8);
        legs = TFJson.dec(armor, "grebas", 6);
        boots = TFJson.dec(armor, "botas", 3);
        toughness = TFJson.dec(json, "durezaPorPieza", 3);
        knockback = TFJson.dec(json, "resistenciaEmpujePorPieza", 0.1);
        mining = (float) TFJson.dec(json, "velocidadMinado", 9);
        exceptions.clear();
        if (json.has("excepciones") && json.get("excepciones").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("excepciones")) if (el.isJsonPrimitive()) exceptions.add(el.getAsString().trim());
        }
        if (!Files.exists(file) || !json.has("activado")) save();
        TFClient.LOGGER.info("TF Límites: techo de netherita {} ({} excepciones)", enabled ? "activado" : "desactivado", exceptions.size());
    }

    private static void save() {
        JsonObject json = new JsonObject();
        json.addProperty("activado", enabled);
        json.addProperty("danoMaximo", damage);
        json.addProperty("danoMaximoArmasPesadas", heavyDamage);
        json.addProperty("velocidadArmaPesada", heavySpeed);
        JsonObject armor = new JsonObject();
        armor.addProperty("casco", helmet);
        armor.addProperty("peto", chest);
        armor.addProperty("grebas", legs);
        armor.addProperty("botas", boots);
        json.add("armadura", armor);
        json.addProperty("durezaPorPieza", toughness);
        json.addProperty("resistenciaEmpujePorPieza", knockback);
        json.addProperty("velocidadMinado", mining);
        com.google.gson.JsonArray ex = new com.google.gson.JsonArray();
        exceptions.forEach(ex::add);
        json.add("excepciones", ex);
        TFJson.write(file(), json);
    }

    /** Fuera del techo: los objetos de los sets (TFItem) y las excepciones de la configuración. */
    private static boolean exempt(ItemStack stack) {
        if (stack.getItem() instanceof TFItem) return true;
        if (exceptions.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && (exceptions.contains(id.toString()) || exceptions.contains(id.getNamespace() + ":*"));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (!loaded) load();
        if (!enabled) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || exempt(stack)) return;
        EquipmentSlot slot = event.getSlotType();
        Multimap<Attribute, AttributeModifier> mods = event.getModifiers();
        if (mods.isEmpty()) return;
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            if (!mods.containsKey(Attributes.ATTACK_DAMAGE)) return;
            double speed = sum(mods.get(Attributes.ATTACK_SPEED));
            double max = (speed <= heavySpeed ? heavyDamage : damage) - 1; // el 1 es el puño del jugador
            cap(event, Attributes.ATTACK_DAMAGE, max, slot == EquipmentSlot.MAINHAND ? BASE_DAMAGE : CAP_ID, "Weapon modifier");
            return;
        }
        double armorMax = switch (slot) {
            case HEAD -> helmet;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> boots;
            default -> -1;
        };
        if (armorMax < 0) return;
        if (mods.containsKey(Attributes.ARMOR)) cap(event, Attributes.ARMOR, armorMax, null, "Armor modifier");
        if (mods.containsKey(Attributes.ARMOR_TOUGHNESS)) cap(event, Attributes.ARMOR_TOUGHNESS, toughness, null, "Armor toughness");
        if (mods.containsKey(Attributes.KNOCKBACK_RESISTANCE)) cap(event, Attributes.KNOCKBACK_RESISTANCE, knockback, null, "Armor knockback resistance");
    }

    /** Suma lo que se añade; si pasa del tope o hay multiplicadores que suben, lo deja en un solo modificador con el tope. */
    private static void cap(ItemAttributeModifierEvent event, Attribute attribute, double max, UUID preferred, String name) {
        Collection<AttributeModifier> current = event.getModifiers().get(attribute);
        double add = 0;
        boolean boosted = false;
        UUID keep = null;
        for (AttributeModifier m : current) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) {
                add += m.getAmount();
                if (keep == null) keep = m.getId();
            } else if (m.getAmount() > 0) {
                boosted = true;
            }
        }
        if (add <= max + 1e-6 && !boosted) return;
        List<AttributeModifier> others = new ArrayList<>();
        for (AttributeModifier m : current) {
            if (m.getOperation() != AttributeModifier.Operation.ADDITION && m.getAmount() <= 0) others.add(m);
        }
        event.removeAttribute(attribute);
        UUID id = preferred != null ? preferred : keep != null ? keep : CAP_ID;
        event.addModifier(attribute, new AttributeModifier(id, name, Math.min(add, max), AttributeModifier.Operation.ADDITION));
        for (AttributeModifier m : others) event.addModifier(attribute, m);
    }

    private static double sum(Collection<AttributeModifier> mods) {
        double s = 0;
        for (AttributeModifier m : mods) if (m.getOperation() == AttributeModifier.Operation.ADDITION) s += m.getAmount();
        return s;
    }

    /** Minar: ninguna herramienta pasa del ritmo de la netherita. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!loaded) load();
        if (!enabled) return;
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack.isEmpty() || exempt(stack)) return;
        float base = stack.getDestroySpeed(event.getState());
        if (base > mining) event.setNewSpeed(event.getNewSpeed() * mining / base);
    }
}
