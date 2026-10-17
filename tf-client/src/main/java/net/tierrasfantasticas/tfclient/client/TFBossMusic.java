package net.tierrasfantasticas.tfclient.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La música de las peleas de jefe (orden del dueño, 1.3.83): suena en bucle desde que empieza la pelea mientras el
 * jugador está en ella y se va con un fundido cuando el jefe muere (o se va, o el jugador se aleja). El servidor dice
 * qué jefes la piden (BossEntity, con el mensaje de pantalla «music»); suena mientras alguno la pida. Va con el volumen
 * de «Discos» (así suena aunque se tenga la música de Minecraft quitada) y, mientras suena, la música normal del juego
 * se calla (MusicManagerMixin).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFBossMusic {
    private static final ResourceLocation TRACK = new ResourceLocation(TFClient.MOD_ID, "jefes.musica");
    /** Volumen (la pista es fuerte: -13 dB de media) y fundidos de entrada y de salida, en ticks. */
    private static final float VOLUME = 0.7F;
    private static final int FADE_IN = 30, FADE_OUT = 60;

    /** Los jefes (su UUID) que piden la música ahora. */
    private static final Set<String> WANTED = new HashSet<>();
    private static Loop current;

    private TFBossMusic() {}

    /** Mensaje del servidor: el jefe «id» pide la música (on) o deja de pedirla. */
    public static void set(String id, boolean on) {
        if (on) WANTED.add(id);
        else WANTED.remove(id);
    }

    /** ¿Está sonando? (la música normal de Minecraft espera). */
    public static boolean isActive() {
        return current != null && !current.isStopped();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) WANTED.clear();
        boolean want = !WANTED.isEmpty();
        if (want && (current == null || current.isStopped() || current.leaving)) {
            if (current != null && !current.isStopped() && current.leaving) {
                // Volvía a irse y otro jefe la pide: sigue, subiendo otra vez
                current.leaving = false;
            } else {
                current = new Loop();
                mc.getSoundManager().play(current);
                mc.getMusicManager().stopPlaying();
            }
        } else if (!want && current != null && !current.isStopped()) {
            current.leaving = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        WANTED.clear();
        if (current != null) Minecraft.getInstance().getSoundManager().stop(current);
        current = null;
    }

    /** La pista en bucle, sin posición (se oye igual en todos lados), con sus fundidos. */
    private static final class Loop extends AbstractTickableSoundInstance {
        boolean leaving;
        private float fade;

        Loop() {
            super(SoundEvent.createVariableRangeEvent(TRACK), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            fade = leaving ? fade - 1F / FADE_OUT : Math.min(1F, fade + 1F / FADE_IN);
            if (fade <= 0F) {
                stop();
                return;
            }
            // Con curva, para que entre y se vaya suave
            this.volume = Math.max(0.001F, fade * fade * VOLUME);
        }
    }
}
