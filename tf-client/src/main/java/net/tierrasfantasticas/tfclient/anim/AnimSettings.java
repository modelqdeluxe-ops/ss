package net.tierrasfantasticas.tfclient.anim;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFConfigDir;
import org.joml.Vector3f;

/**
 * Las animaciones de Punchy por tipo de objeto (1.3.69), de cada jugador: config/tfclient/animaciones.properties. Se
 * cambian en el pad, Efectos › ANIMACIONES. Un tipo apagado se le dice a Punchy como si ese objeto estuviera en su
 * lista negra (mixin en PunchyConfig.isItemBlacklisted): esa mano se ve y se mueve como en Minecraft normal, por el
 * mismo camino que Punchy usa para su lista negra. La mano vacía también (Punchy por sí solo nunca la bloquea).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class AnimSettings {
    /** Los tipos, en el orden del pad: clave del archivo, nombre y explicación. */
    public enum Kind {
        // 1.3.72: las armas y objetos del servidor con modelo 3D propio. Punchy cuelga el arma de su brazo y, en las
        // herramientas, usa la posición de primera persona del modelo (forceVanillaDisplay), pensada para la espada de
        // Minecraft (se mueve 1,13/3,2/1,13 px). Las nuestras traen la suya (oni_right_sword: 60° y 5,5/4/−10,25 px):
        // sobre el brazo de Punchy hace de palanca y cada espadazo la manda al suelo. De fábrica, como antes de Punchy.
        MODEL("modelos", "Armas y objetos del servidor", "Tienen su modelo 3D y Punchy los descoloca. NO: se ven como siempre.", false),
        SWORD("espadas", "Espadas", ""),
        AXE("hachas", "Hachas", ""),
        PICKAXE("picos", "Picos", ""),
        SHOVEL("palas", "Palas", ""),
        HOE("azadas", "Azadas", ""),
        BOW("arco", "Arco", "Tensar y disparar."),
        CROSSBOW("ballesta", "Ballesta", "Cargar y disparar."),
        SHIELD("escudo", "Escudo", "Cubrirse."),
        TRIDENT("tridente", "Tridente", "Golpear y lanzar."),
        FISHING("pesca", "Caña de pescar", "Lanzar y recoger."),
        FOOD("comida", "Comida y bebidas", "Comer, beber pociones, leche..."),
        BLOCK("bloques", "Bloques", "Llevarlos y colocarlos."),
        HAND("mano", "Mano vacía", "Puñetazos y gestos sin nada en la mano."),
        OTHER("otros", "Otros objetos", "Todo lo demás: tijeras, mechero, mapas...");

        public final String key, title, help;
        /** Encendido de fábrica. */
        public final boolean def;

        Kind(String key, String title, String help) {
            this(key, title, help, true);
        }

        Kind(String key, String title, String help, boolean def) {
            this.key = key;
            this.title = title;
            this.help = help;
            this.def = def;
        }
    }

    private static final boolean[] ON = new boolean[Kind.values().length];
    private static boolean allOn = true;
    private static boolean loaded;
    private static final Map<Item, Kind> CACHE = new IdentityHashMap<>();

    static {
        defaults();
    }

    private static void defaults() {
        for (Kind k : Kind.values()) ON[k.ordinal()] = k.def;
    }

    private AnimSettings() {}

    public static boolean on(Kind k) {
        load();
        return ON[k.ordinal()];
    }

    public static void toggle(Kind k) {
        load();
        ON[k.ordinal()] = !ON[k.ordinal()];
        update();
        save();
    }

    /** Como vienen de fábrica (el botón RESTABLECER de la página). */
    public static void reset() {
        defaults();
        update();
        save();
    }

    /** Lo pregunta Punchy (mixin) muchas veces por fotograma: si todo está encendido, no mira nada. */
    public static boolean blocked(ItemStack stack) {
        if (!loaded) load();
        if (allOn || stack == null) return false;
        return !ON[kind(stack).ordinal()];
    }

    public static Kind kind(ItemStack s) {
        if (s.isEmpty()) return Kind.HAND;
        Kind k = CACHE.get(s.getItem());
        if (k == null) {
            k = classify(s);
            CACHE.put(s.getItem(), k);
        }
        return k;
    }

    private static Kind classify(ItemStack s) {
        Item i = s.getItem();
        try {
            if (ownModel(s)) return Kind.MODEL;
            if (i instanceof TridentItem) return Kind.TRIDENT;
            if (i instanceof SwordItem || s.is(ItemTags.SWORDS)) return Kind.SWORD;
            if (i instanceof AxeItem || s.is(ItemTags.AXES)) return Kind.AXE;
            if (i instanceof PickaxeItem || s.is(ItemTags.PICKAXES)) return Kind.PICKAXE;
            if (i instanceof ShovelItem || s.is(ItemTags.SHOVELS)) return Kind.SHOVEL;
            if (i instanceof HoeItem || s.is(ItemTags.HOES)) return Kind.HOE;
            if (i instanceof CrossbowItem) return Kind.CROSSBOW;
            if (i instanceof BowItem) return Kind.BOW;
            if (i instanceof ShieldItem || s.canPerformAction(ToolActions.SHIELD_BLOCK)) return Kind.SHIELD;
            if (i instanceof FishingRodItem) return Kind.FISHING;
            UseAnim use = s.getUseAnimation();
            if (s.isEdible() || use == UseAnim.EAT || use == UseAnim.DRINK) return Kind.FOOD;
            if (i instanceof BlockItem) return Kind.BLOCK;
        } catch (Exception ignored) {
            // un objeto raro de otro mod: «otros»
        }
        return Kind.OTHER;
    }

    /** Acaba el apagado de Punchy cuando toca (PunchyBridge.turnOff). */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) PunchyBridge.tick(Minecraft.getInstance());
    }

    /**
     * ¿Es un objeto del servidor con su propio modelo? Los de TF Client cuya posición en primera persona no es la de
     * Minecraft (item/generated e item/handheld: giro 0, −90, 25; desplazamiento 1,13, 3,2, 1,13 px; escala 0,68).
     */
    private static boolean ownModel(ItemStack s) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (id == null || !TFClient.MOD_ID.equals(id.getNamespace())) return false;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(s, null, null, 0);
        ItemTransform t = model.getTransforms().getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
        return !(near(t.rotation, 0F, -90F, 25F) && near(t.translation, 1.13F / 16F, 3.2F / 16F, 1.13F / 16F)
                && near(t.scale, 0.68F, 0.68F, 0.68F));
    }

    private static boolean near(Vector3f v, float x, float y, float z) {
        return Math.abs(v.x() - x) < 0.01F && Math.abs(v.y() - y) < 0.01F && Math.abs(v.z() - z) < 0.01F;
    }

    // las etiquetas (espadas, hachas...) llegan al entrar en un mundo: lo apuntado antes puede estar mal
    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        CACHE.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) CACHE.clear();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static Path file() {
        return TFConfigDir.file("animaciones.properties", null);
    }

    private static void update() {
        boolean all = true;
        for (boolean b : ON) all &= b;
        allOn = all;
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (Files.exists(f)) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                p.load(r);
                for (Kind k : Kind.values()) {
                    String v = p.getProperty(k.key);
                    if (v != null) ON[k.ordinal()] = Boolean.parseBoolean(v.trim());
                }
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Client: no se pudieron leer los ajustes de las animaciones", e);
            }
        }
        update();
    }

    private static void save() {
        Properties p = new Properties();
        for (Kind k : Kind.values()) p.setProperty(k.key, Boolean.toString(ON[k.ordinal()]));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            p.store(w, "TF Client - animaciones de Punchy por tipo de objeto");
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudieron guardar los ajustes de las animaciones", e);
        }
    }
}
