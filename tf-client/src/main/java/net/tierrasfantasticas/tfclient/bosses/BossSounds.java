package net.tierrasfantasticas.tfclient.bosses;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Los sonidos propios de las grietas y de la muerte de los jefes (1.3.86; antes eran los de Minecraft: amatista, faro,
 * cristal y la explosión de la TNT). Se hacen con tools/bosses/sonidos_grieta.py y están en sounds.json; no hace falta
 * registrarlos (el paquete de sonido lleva su nombre).
 */
public final class BossSounds {
    private BossSounds() {}

    private static SoundEvent of(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(TFClient.MOD_ID, name));
    }

    /** El zumbido del vacío cerca de una grieta (en bucle, lo pone el cliente: RiftSky). */
    public static final SoundEvent RIFT_AMBIENT = of("grieta.ambiente");
    /** Golpean el cristal: empieza a despertar. */
    public static final SoundEvent RIFT_HIT = of("grieta.golpe");
    /** Los 3,5 s del despertar. */
    public static final SoundEvent RIFT_WAKE = of("grieta.despertar");
    /** Sale el jefe. */
    public static final SoundEvent RIFT_BURST = of("grieta.estallido");
    /** La grieta vuelve a estar lista. */
    public static final SoundEvent RIFT_READY = of("grieta.lista");
    /** La muerte épica, desde que empieza: el jefe se deshace (sin golpes; antes chasquidos que «sonaban a tambor»). */
    public static final SoundEvent DEATH_DISSOLVE = of("jefes.muerte_disolucion");
    /** La música siniestra cerca de una grieta, fuera de la pelea (la pone RiftSky, en bucle). */
    public static final SoundEvent RIFT_MUSIC = of("grieta.musica");
    /** El estallido final de la muerte épica. */
    public static final SoundEvent DEATH_BURST = of("jefes.muerte_estallido");
}
