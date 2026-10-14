package net.tierrasfantasticas.tfclient.skills;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * El temblor de cámara de los golpes de los jefes (recoil{r;pitch} de los packs: pisotones, rugidos, golpes contra el
 * suelo). Solo mueve lo que se ve, nunca hacia dónde mira el jugador, y se apaga solo en unos ticks. Las skills de las
 * clases no lo usan (el jugador ya se quejó de que le movieran la vista).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class CameraShake {
    /** Lo que tiembla ahora (grados) y lo de antes, para que sea suave entre ticks. */
    private static float pitch, yaw, roll, prevPitch, prevYaw, prevRoll;
    private static float targetPitch, targetYaw, targetRoll;

    private CameraShake() {}

    /** Un golpe: pitch y yaw de los packs (en «bloques» de MythicMobs) y su radio (más fuerte cuanto más grande). */
    static void add(float p, float y, float radius) {
        float k = Math.min(1.6F, 0.6F + radius / 30F);
        targetPitch += p * 6F * k;
        targetYaw += y * 6F * k;
        targetRoll += (p == 0 ? y : p) * 3F * k;
        targetPitch = clamp(targetPitch);
        targetYaw = clamp(targetYaw);
        targetRoll = clamp(targetRoll);
    }

    private static float clamp(float v) {
        return Math.max(-8F, Math.min(8F, v));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        prevPitch = pitch;
        prevYaw = yaw;
        prevRoll = roll;
        pitch += (targetPitch - pitch) * 0.6F;
        yaw += (targetYaw - yaw) * 0.6F;
        roll += (targetRoll - roll) * 0.6F;
        targetPitch *= 0.45F;
        targetYaw *= 0.45F;
        targetRoll *= 0.45F;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (Math.abs(pitch) + Math.abs(yaw) + Math.abs(roll) + Math.abs(prevPitch) < 0.01F) return;
        float t = (float) event.getPartialTick();
        event.setPitch(event.getPitch() + prevPitch + (pitch - prevPitch) * t);
        event.setYaw(event.getYaw() + prevYaw + (yaw - prevYaw) * t);
        event.setRoll(event.getRoll() + prevRoll + (roll - prevRoll) * t);
    }
}
