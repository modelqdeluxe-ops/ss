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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La luz del jugador en el Void (1.3.96; el dueño: «hagamos que el Void robe vitalidad, o sea que robe tu luz; hay que poner
 * fragmentos de luz por la isla para que la gente los tome; una isla santuario encerrada en una burbuja brillante que
 * contiene la luz y nos mantiene a salvo de esa corrosión»).
 *
 * <ul>
 *   <li>Cada jugador lleva su luz (0..{@link #MAX}). Fuera del Velo del Alba (la burbuja del santuario,
 *   {@link VoidIsland#inBubble}) el Void se la bebe: de llena a nada en {@link #DRAIN_MIN} minutos. Dentro, el Corazón del
 *   Alba se la devuelve en unos segundos.</li>
 *   <li>Avisos a la mitad, a un cuarto y al 10 %. Sin luz, el Void te consume: oscuridad, lentitud y daño de corrosión
 *   ({@link #CORROSION}, su mensaje de muerte: «fue consumido por el Vacío») cada 2 s hasta que vuelvas a tener luz.</li>
 *   <li>Los fragmentos de luz ({@link LightShards}) devuelven {@link #SHARD} y guardan un Eco del Alba: un recuerdo de un
 *   caballero de la orden ({@link #ECHOES}); cada jugador los colecciona (se guardan en el mundo).</li>
 *   <li>Al entrar en el Void se llega con la luz llena. El staff en creativo y los espectadores no la pierden.</li>
 * </ul>
 * El cliente la recibe ({@link VoidNet.Light}) y la enseña ({@code VoidLightClient}).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID)
public final class VoidLight {
    public static final float MAX = 100F;
    /** Minutos que tarda el Void en beberse la luz entera fuera del Velo. */
    static final float DRAIN_MIN = 6F;
    private static final float DRAIN = MAX / (DRAIN_MIN * 60F * 20F);
    /** Dentro del Velo, de nada a llena en 4 s. */
    private static final float REGEN = MAX / (4F * 20F);
    /** Lo que da un fragmento. */
    public static final float SHARD = 30F;
    /** El daño de la corrosión (sin luz) cada 2 s. */
    private static final float HURT = 1.5F;
    public static final ResourceKey<DamageType> CORROSION = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(TFClient.MOD_ID, "corrosion_vacio"));

    /** Lo que se le manda al cliente con la luz (lo que acaba de pasar). */
    public static final int EV_NONE = 0, EV_SHARD = 1, EV_ENTER = 2, EV_LEAVE = 3, EV_EMPTY = 4;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        float light = MAX;
        boolean inside, was;
        int sent = -1, warned;
        long nextHurt;
        int event;
    }

    /**
     * Los Ecos del Alba: lo que recuerda cada fragmento (un caballero de la orden y lo que dijo). En orden: cada jugador oye
     * primero los que no ha oído.
     */
    static final String[][] ECHOES = {
            {"Ser Aldric, el Primero", "Cuando el Void abrió sus fauces, el Alba no huyó. Se quebró en mil pedazos para cerrarlas. Nosotros somos sus guardianes."},
            {"Dama Ysolde", "Cada esquirla es una cerradura. Mientras brillen, lo que duerme en la grieta no despertará del todo."},
            {"Ser Corvan", "El Void no muerde la carne: bebe la luz. Cuando ya no te queda, empieza por la sangre."},
            {"Hermano Teodric", "Juré no apagarme. Si lees esto, es que mi luz aún te sirve. Tómala."},
            {"Dama Elowen", "Caelthar era el más joven de nosotros. Le dejamos el Corazón del Alba porque era el único que aún creía."},
            {"Ser Mathis", "Escuchad: las criaturas de la grieta no nacieron malas. El hambre del Void las vació por dentro."},
            {"Ser Aldric, el Primero", "Levantamos el Velo con lo último del Alba. Dentro, ninguna sombra puede tocarte. Fuera... no te alejes demasiado."},
            {"Dama Ysolde", "El agujero negro del cielo es su ojo. No duerme. Nunca ha dormido."},
            {"Hermano Teodric", "La primera grieta la abrió nuestra arrogancia: quisimos ver qué había al otro lado del Alba."},
            {"Ser Corvan", "Si oyes susurros cuando tu luz se apaga, no les contestes. Son los que el Void ya se tragó."},
            {"Dama Elowen", "Cada criatura que cae devuelve un poco de luz al mundo. Por eso la herida del cielo se cierra cuando una muere."},
            {"Ser Mathis", "Éramos doce. Once nos quedamos aquí, hechos esquirlas. Uno sigue en pie. Que no esté solo."},
            {"Ser Aldric, el Primero", "Hay una criatura que no encerramos: la encerró el Alba misma, en el fondo de todo. Ruega no conocerla."},
            {"Dama Ysolde", "Las islas del Void son trozos de mundos que perdieron su luz. Esta fue la nuestra."},
            {"Hermano Teodric", "Cuando reúnas todos los ecos, Caelthar sabrá que no estuvimos solos en la oscuridad."},
            {"La Orden del Alba", "Que la luz te guíe, viajero. Nosotros ya somos parte de ella."},
    };

    private VoidLight() {}

    /** Los ecos que ha oído cada jugador (un bit por eco), guardados en el mundo principal. */
    static final class Echoes extends SavedData {
        final Map<UUID, Long> heard = new HashMap<>();

        static Echoes load(CompoundTag tag) {
            Echoes e = new Echoes();
            CompoundTag h = tag.getCompound("ecos");
            for (String k : h.getAllKeys()) {
                try {
                    e.heard.put(UUID.fromString(k), h.getLong(k));
                } catch (IllegalArgumentException ignored) {
                    // (una entrada rota no tumba el resto)
                }
            }
            return e;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag h = new CompoundTag();
            heard.forEach((k, v) -> h.putLong(k.toString(), v));
            tag.put("ecos", h);
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

    /** ¿Pierde luz? (no el staff en creativo ni los espectadores) */
    private static boolean exposed(ServerPlayer p) {
        return !p.isCreative() && !p.isSpectator();
    }

    /** Un fragmento de luz recogido (LightShards): la luz y su eco. */
    static void shard(ServerPlayer p) {
        State s = state(p);
        s.light = Math.min(MAX, s.light + SHARD);
        s.event = EV_SHARD;
        s.warned = 0;
        sync(p, s, true);
        Echoes e = echoes(p.server);
        long bits = e.heard.getOrDefault(p.getUUID(), 0L);
        int i = 0;
        while (i < ECHOES.length && (bits & 1L << i) != 0) i++;
        boolean fresh = i < ECHOES.length;
        if (!fresh) i = p.getRandom().nextInt(ECHOES.length);
        if (fresh) {
            e.heard.put(p.getUUID(), bits | 1L << i);
            e.setDirty();
        }
        int n = Long.bitCount(e.heard.getOrDefault(p.getUUID(), 0L));
        String[] echo = ECHOES[i];
        p.sendSystemMessage(Component.literal("✦ Eco del Alba" + (fresh ? " (" + n + "/" + ECHOES.length + ")" : "") + " · ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("«" + echo[1] + "»").withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC))
                .append(Component.literal(" — " + echo[0]).withStyle(ChatFormatting.GRAY)));
        if (fresh && n == ECHOES.length) {
            p.sendSystemMessage(Component.literal("✦ Has reunido todos los Ecos del Alba. Caelthar querrá saberlo.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        p.displayClientMessage(Component.literal("+" + (int) SHARD + " de luz").withStyle(ChatFormatting.YELLOW), true);
    }

    /** La luz llena (al entrar en el Void). */
    static void refill(ServerPlayer p) {
        State s = state(p);
        s.light = MAX;
        s.warned = 0;
        s.event = EV_NONE;
        sync(p, s, true);
    }

    private static void sync(ServerPlayer p, State s, boolean force) {
        int v = Math.round(s.light);
        if (!force && v == s.sent && s.inside == s.was && s.event == EV_NONE) return;
        s.sent = v;
        s.was = s.inside;
        VoidNet.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p),
                new VoidNet.Light(v, s.inside, heard(p), ECHOES.length, s.event));
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
        boolean inside = VoidIsland.inBubble(p.getX(), p.getEyeY(), p.getZ());
        if (inside != s.inside) s.event = inside ? EV_ENTER : EV_LEAVE;
        s.inside = inside;
        if (!exposed(p) || !VoidTravel.idle(p)) {
            s.light = MAX;
        } else if (inside) {
            s.light = Math.min(MAX, s.light + REGEN);
            if (s.light >= 50F) s.warned = 0;
        } else {
            s.light = Math.max(0F, s.light - DRAIN);
        }
        // avisos al bajar
        float l = s.light;
        if (!inside && exposed(p)) {
            if (l <= 50F && s.warned < 1) warn(p, s, 1, "Tu luz se debilita: el Void se la bebe. Busca fragmentos de luz o vuelve al santuario.");
            else if (l <= 25F && s.warned < 2) warn(p, s, 2, "Te queda poca luz. La oscuridad te acecha.");
            else if (l <= 10F && s.warned < 3) warn(p, s, 3, "¡Tu luz se apaga! El Void está a punto de consumirte.");
        }
        // sin luz: el Void te consume
        if (l <= 0F && exposed(p) && !inside) {
            long now = p.level().getGameTime();
            if (s.warned < 4) {
                s.warned = 4;
                s.event = EV_EMPTY;
            }
            if (now >= s.nextHurt) {
                s.nextHurt = now + 40;
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
                p.hurt(corrosion((ServerLevel) p.level()), HURT);
                p.displayClientMessage(Component.literal("El Vacío te consume... busca un fragmento de luz o vuelve al santuario")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
        if (p.tickCount % 10 == 0 || s.event != EV_NONE) sync(p, s, false);
    }

    private static void warn(ServerPlayer p, State s, int level, String text) {
        s.warned = level;
        p.sendSystemMessage(Component.literal("✦ " + text).withStyle(level >= 3 ? ChatFormatting.RED : ChatFormatting.LIGHT_PURPLE));
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

    /** Para la prueba: ¿está dentro del Velo? */
    public static boolean testInside(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.inside;
    }
}
