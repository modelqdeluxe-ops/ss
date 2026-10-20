package net.tierrasfantasticas.tfclient.vacio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.items.TFBinding;
import net.tierrasfantasticas.tfclient.items.TFItems;

/**
 * La armadura del Vacío (1.3.96; el dueño: «habrá un set que obtendrán todos al derrotar 5 bosses, para que sean
 * inmunes al Void; estará disponible en el santuario; ese set es el único inmune al Void»): el set «void» de los sets de
 * Tierras Fantásticas (el Void Colossus del dueño, convertido como los demás por tools/build_mod_items.py).
 *
 * <ul>
 *   <li><b>Inmunidad</b> ({@link #pieces}): el yelmo (el modelo 3D del set, en la cabeza), el peto, las grebas y las
 *   botas. Con las cuatro, el Void no te corroe; con menos, más despacio (un 25 % menos por pieza: {@link VoidLight}).</li>
 *   <li><b>Cómo se gana</b>: derrotando {@link #NEED} jefes de las grietas (cualquier grieta: cuenta para quien le hizo daño
 *   y sigue cerca al caer, {@link #bossDefeated}). Se guarda por jugador en el mundo.</li>
 *   <li><b>Dónde</b>: en el Altar del Vacío del santuario ({@link VoidIsland#altarPos}) un soporte la lleva puesta, de
 *   pie en el suelo del altar, con un aura violeta ({@link SanctuaryFx}) y motas que suben; al tocarlo (o pidiéndosela a
 *   Caelthar) se recibe el set entero, vinculado a quien lo coge. <b>Una sola vez</b> por jugador (1.3.97, el dueño).</li>
 *   <li><b>Atributos</b>: los de los sets de las crates (netherita +1); su yelmo 3D da los de un casco (1.3.97).</li>
 * </ul>
 * El lore: la forjaron los caballeros del Alba con la piel del Coloso del Vacío, la primera criatura que cayó; el Void no
 * se come lo que es suyo, y quien la viste camina por él como uno de los suyos.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VoidArmor {
    public static final String SET = "void";
    /** Jefes que hay que derrotar para ganarla. */
    public static final int NEED = 5;
    static final String TAG = "tfclient_altar_vacio";
    private static final String[] WORN = {"void_helmet", "void_armor_chestplate", "void_armor_leggings", "void_armor_boots"};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final net.minecraft.core.particles.DustParticleOptions AURA =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.62F, 0.32F, 1F), 1.1F);

    private VoidArmor() {}

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(TFClient.MOD_ID, id));
    }

    /** Las piezas del Vacío que lleva puestas (0..4). */
    public static int pieces(Player p) {
        int n = 0;
        for (int i = 0; i < WORN.length; i++) {
            ItemStack s = p.getItemBySlot(SLOTS[i]);
            if (!s.isEmpty() && s.getItem() == item(WORN[i])) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------- jefes derrotados

    /** Los jefes derrotados por jugador, guardados en el mundo principal. */
    static final class Kills extends SavedData {
        final Map<UUID, Integer> kills = new HashMap<>();
        /** (1.3.97) Quienes ya la recogieron (una vez por jugador). */
        final Set<UUID> claimed = new HashSet<>();

        static Kills load(CompoundTag tag) {
            Kills k = new Kills();
            CompoundTag h = tag.getCompound("jefes");
            for (String key : h.getAllKeys()) {
                try {
                    k.kills.put(UUID.fromString(key), h.getInt(key));
                } catch (IllegalArgumentException ignored) {
                    // (una entrada rota no tumba el resto)
                }
            }
            CompoundTag c = tag.getCompound("reclamada");
            for (String key : c.getAllKeys()) {
                try {
                    k.claimed.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {
                    // (igual)
                }
            }
            return k;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag h = new CompoundTag();
            kills.forEach((k, v) -> h.putInt(k.toString(), v));
            tag.put("jefes", h);
            CompoundTag c = new CompoundTag();
            for (UUID u : claimed) c.putBoolean(u.toString(), true);
            tag.put("reclamada", c);
            return tag;
        }
    }

    private static Kills data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Kills::load, Kills::new, "tfclient_jefes_vacio");
    }

    public static int kills(ServerPlayer p) {
        return data(p.server).kills.getOrDefault(p.getUUID(), 0);
    }

    /** ¿Ya la recogió? (se da una sola vez) */
    public static boolean claimed(ServerPlayer p) {
        return data(p.server).claimed.contains(p.getUUID());
    }

    /** Ha caído el jefe de una grieta (Rifts.bossKilled): les cuenta a los que le hicieron daño y siguen cerca. */
    public static void bossDefeated(List<ServerPlayer> players) {
        if (players.isEmpty()) return;
        Kills k = data(players.get(0).server);
        for (ServerPlayer p : players) {
            int n = k.kills.merge(p.getUUID(), 1, Integer::sum);
            if (n < NEED) {
                p.sendSystemMessage(Component.literal("✦ Jefes derrotados para la armadura del Vacío: " + n + " de " + NEED)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (n == NEED) {
                p.sendSystemMessage(Component.literal("✦ Has derrotado a " + NEED + " jefes: la armadura del Vacío te espera en el "
                        + "Altar del santuario del Void. Tócala o pídesela a Caelthar.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            }
        }
        k.setDirty();
    }

    /** Para la prueba en el juego: esos jefes derrotados (y sin haberla recogido). */
    public static void testSetKills(ServerPlayer p, int n) {
        Kills k = data(p.server);
        k.kills.put(p.getUUID(), n);
        k.claimed.remove(p.getUUID());
        k.setDirty();
    }

    // ------------------------------------------------------------------------------------------- recogerla

    /**
     * Le da el set entero, vinculado (una sola vez por jugador). Devuelve cuántos objetos le dio, −1 si aún no la ha
     * ganado o −2 si ya la recogió.
     */
    public static int claim(ServerPlayer p) {
        int k = kills(p);
        if (k < NEED) {
            p.sendSystemMessage(Component.literal("✦ La armadura del Vacío es de quien ha derrotado a " + NEED + " jefes de las grietas. "
                    + "Llevas " + k + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
            return -1;
        }
        Kills data = data(p.server);
        if (data.claimed.contains(p.getUUID())) {
            p.sendSystemMessage(Component.literal("✦ La armadura del Vacío ya es tuya: el Coloso solo dio una piel, y la Orden solo "
                    + "forja una armadura por cada guerrero.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return -2;
        }
        List<RegistryObject<Item>> items = TFItems.BY_SET.get(SET);
        if (items == null) return 0;
        data.claimed.add(p.getUUID());
        data.setDirty();
        int given = 0;
        for (RegistryObject<Item> o : items) {
            Item it = o.get();
            ItemStack stack = new ItemStack(it);
            TFBinding.bind(stack, p);
            TFBinding.giveOrDrop(p, stack);
            given++;
        }
        ServerLevel level = p.serverLevel();
        if (given > 0) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE, SoundSource.PLAYERS, 1F, 0.8F);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 0.6F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 80, 0.5, 1, 0.5, 0.05);
            p.sendSystemMessage(Component.literal("✦ La armadura del Coloso del Vacío es tuya. Con el yelmo, el peto, las grebas y las "
                    + "botas puestos, el Vacío no te tocará.").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
        return given;
    }

    // ------------------------------------------------------------------------------------------- el altar

    /** El soporte del altar con la armadura puesta (lo pone y lo vigila TFVoid cada segundo). */
    static void tickAltar(ServerLevel level) {
        BlockPos a = VoidIsland.altarPos();
        if (a == null || !level.hasChunkAt(a) || !level.areEntitiesLoaded(ChunkPos.asLong(a))) return;
        List<ArmorStand> here = level.getEntitiesOfClass(ArmorStand.class, new AABB(a).inflate(3), e -> e.getTags().contains(TAG));
        for (int k = 1; k < here.size(); k++) here.get(k).discard();
        // motas violetas que suben alrededor (el aura la dibuja el cliente, SanctuaryFx)
        level.sendParticles(AURA, a.getX() + 0.5, a.getY() + 0.6, a.getZ() + 0.5, 4, 0.7, 0.6, 0.7, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5, 3, 0.5, 0.8, 0.5, 0.01);
        if (!here.isEmpty()) {
            // (1.3.97) de pie en el suelo del altar: el de la 1.3.96 estaba un bloque más arriba, sobre un pedestal
            ArmorStand s = here.get(0);
            if (s.position().distanceToSqr(a.getX() + 0.5, a.getY(), a.getZ() + 0.5) > 0.01) s.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0F, 0F);
            return;
        }
        ArmorStand s = EntityType.ARMOR_STAND.create(level);
        if (s == null) return;
        s.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0F, 0F);
        CompoundTag tag = new CompoundTag();
        s.addAdditionalSaveData(tag);
        tag.putBoolean("Invisible", false);
        tag.putBoolean("NoBasePlate", true);
        tag.putBoolean("ShowArms", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Invulnerable", true);
        tag.putInt("DisabledSlots", 4144959);
        s.readAdditionalSaveData(tag);
        s.setInvulnerable(true);
        s.setRightArmPose(new Rotations(-20F, 0F, 10F));
        s.setLeftArmPose(new Rotations(-10F, 0F, -10F));
        for (int i = 0; i < WORN.length; i++) {
            Item it = item(WORN[i]);
            if (it != null) s.setItemSlot(SLOTS[i], new ItemStack(it));
        }
        Item sword = item("void_longsword");
        if (sword != null) s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sword));
        s.addTag(TAG);
        s.setCustomName(Component.literal("Armadura del Coloso del Vacío").withStyle(ChatFormatting.LIGHT_PURPLE));
        s.setCustomNameVisible(true);
        level.addFreshEntity(s);
    }

    /** Tocar el soporte del altar: recoger la armadura (sin cambiarle nada al soporte). */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ArmorStand s) || !s.getTags().contains(TAG)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer p && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) claim(p);
    }

    @SubscribeEvent
    public static void onInteractPlain(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof ArmorStand s && s.getTags().contains(TAG)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
