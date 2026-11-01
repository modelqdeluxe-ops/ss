/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.clans.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.clans.ClanEmote;
import net.tierrasfantasticas.tfclient.clans.ClanLooks;

/**
 * Los emotes del clan en cada TF Client que los ve: quién hace cuál y desde cuándo, su música (pegada al jugador), la
 * cámara de frente para el que lo hace (y luego la que tenía) y el enganche con el dibujo del jugador (la postura desde
 * LivingEntityRendererMixin, el cuerpo entero en RenderPlayerEvent.Pre y los efectos en .Post). Lo que hace cada uno
 * está en ClanEmotes (1.3.160: cinco). Todo sale del reloj: la prueba en el juego lo lleva fotograma a fotograma para
 * grabar el vídeo.
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class ClanEmoteClient {
    private record Run(double start, int emote) {}

    private static final Map<UUID, Run> ACTIVE = new HashMap<>();
    private static final Set<UUID> PUSHED = new HashSet<>();
    private static final long T0 = System.nanoTime();
    private static CameraType before;
    private static boolean cameraSet;

    /** Para grabar el vídeo en la prueba: el reloj avanza a mano (1/30 s por fotograma). */
    public static volatile boolean fixedClock;
    public static volatile double fixedTime;

    private ClanEmoteClient() {}

    public static double clock() {
        return fixedClock ? fixedTime : (System.nanoTime() - T0) / 1e9;
    }

    public static double duration(int emote) {
        return emote >= 0 && emote < ClanEmotes.DURATIONS.length ? ClanEmotes.DURATIONS[emote] : 5.0;
    }

    /** Llega del servidor: ese jugador hace ese emote. */
    public static void start(UUID uuid, int emote) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || emote < 0 || emote >= ClanEmote.IDS.length) return;
        ACTIVE.put(uuid, new Run(clock(), emote));
        Player p = mc.level.getPlayerByUUID(uuid);
        if (p != null) {
            ResourceLocation sound = new ResourceLocation(TFClient.MOD_ID, "clanes.emote_" + ClanEmote.IDS[emote]);
            mc.getSoundManager().play(new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(sound), SoundSource.PLAYERS, 1.0F, 1.0F, p,
                    RandomSource.create().nextLong()));
        }
        if (mc.player != null && uuid.equals(mc.player.getUUID()) && !fixedClock) {
            if (!cameraSet) before = mc.options.getCameraType();
            cameraSet = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
    }

    /** Cada tick: quita los que acabaron (y devuelve la cámara). */
    static void tick() {
        double now = clock();
        ACTIVE.values().removeIf(r -> now - r.start > duration(r.emote));
        Minecraft mc = Minecraft.getInstance();
        if (cameraSet && (mc.player == null || !ACTIVE.containsKey(mc.player.getUUID()))) {
            cameraSet = false;
            if (before != null) mc.options.setCameraType(before);
        }
    }

    /** Cuánto lleva ese jugador en su emote (s), o -1. */
    public static double elapsed(UUID uuid) {
        Run r = ACTIVE.get(uuid);
        if (r == null) return -1;
        double e = clock() - r.start;
        return e >= 0 && e <= duration(r.emote) ? e : -1;
    }

    /** El emote que hace ese jugador (0 a 4), o -1. */
    public static int emoteOf(UUID uuid) {
        Run r = ACTIVE.get(uuid);
        return r == null ? -1 : r.emote;
    }

    /** Lo que dura el emote que hace ese jugador (para la prueba: cuántos fotogramas grabar). */
    public static double durationOf(UUID uuid) {
        Run r = ACTIVE.get(uuid);
        return r == null ? 5.0 : duration(r.emote);
    }

    /** Cuánto manda el emote sobre la postura normal (entra y sale suave). */
    static double weight(double e, double dur) {
        return ClanEmotes.smooth(0, 0.18, e) * (1 - ClanEmotes.smooth(dur - 0.45, dur, e));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // La postura (desde LivingEntityRendererMixin, después de setupAnim)
    // ---------------------------------------------------------------------------------------------------------------

    public static void applyPose(HumanoidModel<?> m, LivingEntity entity) {
        if (!(entity instanceof Player p)) return;
        double e = elapsed(p.getUUID());
        int id = emoteOf(p.getUUID());
        if (e < 0 || id < 0) return;
        double w = weight(e, duration(id));
        ClanEmotes.P q = new ClanEmotes.P();
        ClanEmotes.pose(id, e, q);
        blend(m.rightArm, q.ra, q.ry, q.rz, w);
        blend(m.leftArm, q.la, q.ly, q.lz, w);
        blend(m.rightLeg, q.rl, 0, q.rlz, w);
        blend(m.leftLeg, q.ll, 0, q.llz, w);
        blend(m.head, q.hx, m.head.yRot + q.hy, 0, w);
        blend(m.body, q.bx, 0, 0, w);
        m.hat.copyFrom(m.head);
        if (m instanceof PlayerModel<?> pm) {
            pm.leftSleeve.copyFrom(pm.leftArm);
            pm.rightSleeve.copyFrom(pm.rightArm);
            pm.leftPants.copyFrom(pm.leftLeg);
            pm.rightPants.copyFrom(pm.rightLeg);
            pm.jacket.copyFrom(pm.body);
        }
    }

    private static void blend(ModelPart part, double x, double y, double z, double w) {
        part.xRot = (float) ClanEmotes.lerp(part.xRot, x, w);
        part.yRot = (float) ClanEmotes.lerp(part.yRot, y, w);
        part.zRot = (float) ClanEmotes.lerp(part.zRot, z, w);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // El cuerpo entero y los efectos
    // ---------------------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        double e = elapsed(p.getUUID());
        int id = emoteOf(p.getUUID());
        if (e < 0 || id < 0) return;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(p.getUUID());
        ClanEmotes.body(id, e, pose, Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPost(RenderPlayerEvent.Post event) {
        Player p = event.getEntity();
        if (PUSHED.remove(p.getUUID())) event.getPoseStack().popPose();
        double e = elapsed(p.getUUID());
        int id = emoteOf(p.getUUID());
        if (e < 0 || id < 0) return;
        ClanLooks.Look look = ClanLooks.ofPlayer(p.getUUID());
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            ClanEmotes.fx(id, e, pose, event.getMultiBufferSource(), p, look, event.getPartialTick());
        } finally {
            pose.popPose();
        }
    }
}
