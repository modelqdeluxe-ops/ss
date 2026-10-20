package net.tierrasfantasticas.tfclient.vacio;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La corrosión del Vacío (1.3.96). El dueño, primero: «que el Void robe tu luz»; y luego: «no quiero un contador en
 * pantalla, más bien un efecto de corrosión que en su máximo punto no sea molesto pero sí me haga daño, medio corazón;
 * y que parándome en un sitio de luz me cure del efecto, como cuando te ahogas y un bloque de magma te salva la
 * respiración; el set del Void es el único inmune».
 *
 * <ul>
 *   <li>Cada jugador lleva su luz (0..{@link #MAX}; la corrosión es lo que le falta). Fuera de un sitio de luz el Vacío
 *   se la va comiendo: de llena a nada en {@link #CORRODE_MIN} minutos. Lo ve en el borde de la pantalla
 *   ({@code VoidLightClient}: grietas violetas que entran desde los bordes, sin números).</li>
 *   <li>Sitios de luz: el Velo del Alba ({@link VoidIsland#inBubble}) y los Pozos de Luz ({@link LightWells#wellAt}); ahí
 *   la corrosión se va en unos segundos ({@link #REGEN}).</li>
 *   <li>Sin luz, el Vacío hace daño: tres cuartos de corazón ({@link #HURT}; 1.3.97, el dueño: «medio corazón y un cuarto
 *   más») cada {@link #HURT_EVERY} ticks (tipo de daño
 *   {@link #CORROSION}, que atraviesa la armadura; su muerte: «fue consumido por el Vacío»). Nada más: ni oscuridad ni
 *   lentitud.</li>
 *   <li>La armadura del Vacío ({@link VoidArmor}): cada pieza puesta, un 25 % más despacio; con las cuatro, nada.</li>
 *   <li>La primera vez que se pisa cada pozo se oye un Eco del Alba ({@link #ECHOES}): lo que dijo el caballero que se
 *   hizo luz ahí (se guardan en el mundo; sin cuenta ni nada que juntar).</li>
 *   <li>Al entrar en el Void (o conectarse en él), luz llena. El staff en creativo y los espectadores no la pierden.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VoidLight {
    public static final float MAX = 100F;
    /** Minutos que tarda el Vacío en corroer del todo fuera de la luz. */
    static final float CORRODE_MIN = 4F;
    private static final float DRAIN = MAX / (CORRODE_MIN * 60F * 20F);
    /** En un sitio de luz, de nada a llena en 3 s. */
    private static final float REGEN = MAX / (3F * 20F);
    /** El daño de la corrosión al máximo (tres cuartos de corazón) y cada cuánto. */
    static final float HURT = 1.5F;
    static final int HURT_EVERY = 60;
    public static final ResourceKey<DamageType> CORROSION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(TFClient.MOD_ID, "corrosion_vacio"));

    /** Lo que se le manda al cliente con la luz (lo que acaba de pasar). */
    public static final int EV_NONE = 0, EV_WELL = 1, EV_ENTER = 2, EV_LEAVE = 3, EV_EMPTY = 4, EV_HURT = 5, EV_PORTAL = 6;

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Para la prueba en el juego: golpes de corrosión dados. */
    static int hurts;

    private static final class State {
        float light = MAX;
        /** En un sitio de luz (el Velo o un pozo) y en cuál (−1 el Velo o ninguno). */
        boolean safe, wasSafe, inVeil;
        int well = -1;
        int sent = -1;
        boolean empty;
        long nextHurt;
        int event;
        boolean hinted;
    }

    /**
     * Los Ecos del Alba: lo que recuerda cada pozo (un caballero de la orden y lo que dijo). En orden: cada jugador oye
     * primero los que no ha oído.
     */
    static final String[][] ECHOES = {
            // (1.3.97: uno por Ancla del Alba, seis; cada uno, el caballero que se hizo luz para sostener ese fragmento)
            {"Ser Aldric, el Primero", "Cuando el ojo del cielo se tragó nuestra tierra, me quedé aquí. Si mi luz se apaga, este suelo cae. No se apagará."},
            {"Dama Ysolde", "Lo que pisas no es un mundo: son los restos de uno. Los sostenemos once. Descansa en mi luz, viajero, y sigue."},
            {"Ser Corvan", "El Vacío no muerde la carne: la corroe despacio. Mientras haya un Ancla cerca, no te alcanzará."},
            {"Hermano Teodric", "Del Coloso hicimos una armadura. El Vacío no se come lo que es suyo: quien la vista caminará donde nosotros caímos."},
            {"Dama Elowen", "Caelthar era el más joven. Le dejamos el Corazón del Alba porque era el único que aún creía. Que no esté solo."},
            {"Ser Mathis", "Las criaturas de la grieta no nacieron malas: el hambre del Vacío las vació. Cada una que cae devuelve un poco de luz al mundo."},
    };

    private VoidLight() {}

    /** Lo que ha oído y dónde ha estado cada jugador (un bit por eco y por pozo), guardado en el mundo principal. */
    static final class Echoes extends SavedData {
        final Map<UUID, Long> heard = new HashMap<>();
        final Map<UUID, Long> wells = new HashMap<>();

        static Echoes load(CompoundTag tag) {
            Echoes e = new Echoes();
            for (String key : new String[] {"ecos", "pozos"}) {
                CompoundTag h = tag.getCompound(key);
                for (String k : h.getAllKeys()) {
                    try {
                        (key.equals("ecos") ? e.heard : e.wells).put(UUID.fromString(k), h.getLong(k));
                    } catch (IllegalArgumentException ignored) {
                        // (una entrada rota no tumba el resto)
                    }
                }
            }
            return e;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag h = new CompoundTag();
            heard.forEach((k, v) -> h.putLong(k.toString(), v));
            tag.put("ecos", h);
            CompoundTag w = new CompoundTag();
            wells.forEach((k, v) -> w.putLong(k.toString(), v));
            tag.put("pozos", w);
            return tag;
        }
    }

    private static Echoes echoes(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Echoes::load, Echoes::new, "tfclient_ecos");
    }

    /** Cuántos ecos ha oído. */
    static int heard(ServerPlayer p) {
        return Long.bitCount(echoes(p.server).heard.getOrDefault(p.getUUID(), 0L));
    }

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    /** ¿Le afecta el Vacío? (no al staff en creativo ni a los espectadores) */
    private static boolean exposed(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator();
    }

    /** La primera vez en ese pozo: el eco del caballero que se hizo luz ahí (el siguiente que no ha oído). */
    private static void visitWell(ServerPlayer p, int well) {
        Echoes e = echoes(p.server);
        long seen = e.wells.getOrDefault(p.getUUID(), 0L);
        if (well < 0 || well >= 64 || (seen & 1L << well) != 0) return;
        e.wells.put(p.getUUID(), seen | 1L << well);
        long bits = e.heard.getOrDefault(p.getUUID(), 0L);
        int i = 0;
        while (i < ECHOES.length && (bits & 1L << i) != 0) i++;
        if (i < ECHOES.length) {
            e.heard.put(p.getUUID(), bits | 1L << i);
            String[] echo = ECHOES[i];
            p.sendSystemMessage(Component.literal("✦ Eco del Alba · ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("«" + echo[1] + "»").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC))
                    .append(Component.literal(" — " + echo[0]).withStyle(ChatFormatting.GRAY)));
        }
        e.setDirty();
    }

    /** La luz llena (al entrar en el Void). */
    static void refill(ServerPlayer p) {
        State s = state(p);
        s.light = MAX;
        s.empty = false;
        s.event = EV_NONE;
        if (!s.hinted) {
            s.hinted = true;
            p.sendSystemMessage(Component.literal("✦ Fuera del Velo del Alba, el Vacío corroe. Los Pozos de Luz y el Velo te limpian; "
                    + "la armadura del Vacío te protege.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        sync(p, s, true);
    }

    /** Un evento para el cliente (el portal, por ejemplo). */
    static void event(ServerPlayer p, int ev) {
        State s = state(p);
        s.event = ev;
        sync(p, s, true);
    }

    private static void sync(ServerPlayer p, State s, boolean force) {
        int v = Math.round(s.light);
        if (!force && v == s.sent && s.safe == s.wasSafe && s.event == EV_NONE) return;
        s.sent = v;
        s.wasSafe = s.safe;
        VoidNet.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new VoidNet.Light(v, s.safe, s.event));
        s.event = EV_NONE;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (!TFVoid.isVoid(p.level())) {
            STATES.remove(p.getUUID());
            return;
        }
        State s = state(p);
        boolean veil = VoidIsland.inBubble(p.getX(), p.getEyeY(), p.getZ());
        int well = veil ? -1 : LightWells.wellAt(p.getX(), p.getY(), p.getZ());
        boolean safe = veil || well >= 0;
        if (safe && !s.safe) s.event = veil ? EV_ENTER : EV_WELL;
        else if (!safe && s.safe) s.event = EV_LEAVE;
        if (well >= 0 && well != s.well) visitWell(p, well);
        s.safe = safe;
        s.inVeil = veil;
        s.well = well;
        int pieces = VoidArmor.pieces(p);
        if (!exposed(p) || !VoidTravel.idle(p) || pieces >= 4) {
            s.light = MAX;
        } else if (safe) {
            s.light = Math.min(MAX, s.light + REGEN);
        } else {
            s.light = Math.max(0F, s.light - DRAIN * (1F - 0.25F * pieces));
        }
        if (s.light > 0F) s.empty = false;
        // al máximo de corrosión: tres cuartos de corazón de vez en cuando
        if (s.light <= 0F && exposed(p) && !safe) {
            long now = p.level().getGameTime();
            if (!s.empty) {
                s.empty = true;
                s.event = EV_EMPTY;
                s.nextHurt = now + HURT_EVERY / 2;
                p.displayClientMessage(Component.literal("El Vacío te corroe... busca un Pozo de Luz o vuelve al Velo")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            } else if (now >= s.nextHurt) {
                s.nextHurt = now + HURT_EVERY;
                p.hurt(corrosion((ServerLevel) p.level()), HURT);
                hurts++;
                if (s.event == EV_NONE) s.event = EV_HURT;
            }
        }
        if (p.tickCount % 10 == 0 || s.event != EV_NONE) sync(p, s, false);
    }

    private static DamageSource corrosion(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CORROSION));
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTo() == TFVoid.VOID) refill(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && TFVoid.isVoid(p.level())) refill(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    /** Para la prueba en el juego: la luz que tiene ahora (−1 si no está en el Void). */
    public static float testLight(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? -1F : s.light;
    }

    /** Para la prueba: le deja con esa luz. */
    public static void testSet(ServerPlayer p, float light) {
        state(p).light = Math.max(0F, Math.min(MAX, light));
    }

    /** Para la prueba: ¿está en un sitio de luz? («velo», «pozo N» o «no») */
    public static String testWhere(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null || !s.safe ? "fuera de la luz" : s.inVeil ? "en el Velo del Alba" : "en el Pozo de Luz " + s.well;
    }

    public static boolean testInside(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.safe;
    }
}
