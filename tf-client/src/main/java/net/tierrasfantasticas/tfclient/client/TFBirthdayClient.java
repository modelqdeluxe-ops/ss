/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La fiesta de cumpleaños en este cliente ({@code server.TFBirthday}): un minuto con el letrero «¡FELIZ CUMPLEAÑOS!» y
 * el nombre en el cielo (letras de colores que ondean y siempre delante, un poco por encima del horizonte), cohetes que
 * suben y estallan alrededor (al final, una traca), confeti que cae y «Cumpleaños feliz» con campanas. Todo son
 * partículas, sonidos y texto del cliente: se ven igual con shaders.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFBirthdayClient {
    private static final Random RANDOM = new Random();
    /** A cuánto se dibuja el letrero (bloques) y a qué altura sobre el horizonte (grados). */
    private static final double DIST = 42;
    private static final float ELEVATION = 24F;
    private static final int FADE = 40;

    private static String name;
    private static int left, total, age;
    private static boolean sky, mine;
    private static float bannerYaw;
    private static boolean yawSet;
    private static final List<Rocket> ROCKETS = new ArrayList<>();
    /** Para la prueba en el juego: lo que ha salido. */
    public static int rocketsLaunched, bursts, notesPlayed, bannerFrames;

    private TFBirthdayClient() {}

    public static boolean active() {
        return name != null && left > 0;
    }

    /** Lo que manda el servidor: empieza, sigue en otro mundo o se acaba (left = 0). */
    public static void party(String who, int ticksLeft, boolean skyHere, boolean honoree) {
        boolean fresh = !active() || !who.equals(name);
        if (ticksLeft <= 0 || who.isEmpty()) {
            name = null;
            left = 0;
            ROCKETS.clear();
            return;
        }
        name = who;
        left = ticksLeft;
        sky = skyHere;
        mine = honoree;
        if (fresh) {
            total = ticksLeft;
            age = 0;
            yawSet = false;
            rocketsLaunched = bursts = notesPlayed = bannerFrames = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mine) {
                mc.gui.setTimes(10, 90, 25);
                mc.gui.setSubtitle(Component.literal("Todo Tierras Fantásticas te felicita").withStyle(Style.EMPTY.withColor(0xFFD667)));
                mc.gui.setTitle(rainbow("¡Feliz cumpleaños!"));
            }
        }
    }

    private static Component rainbow(String text) {
        var out = Component.empty();
        int i = 0;
        for (int cp : text.codePoints().toArray()) {
            out.append(Component.literal(new String(Character.toChars(cp)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Mth.hsvToRgb((i++ * 0.07F) % 1F, 0.55F, 1F) & 0xFFFFFF)).withBold(true)));
        }
        return out;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        name = null;
        left = 0;
        ROCKETS.clear();
    }

    // ------------------------------------------------------------------------------------------------- cada tick

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        left--;
        age++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        music(mc);
        if (!sky) return;
        Vec3 c = mc.player.position();
        // cohetes: uno cada poco; los últimos 8 s, la traca
        boolean finale = left < 160;
        int every = finale ? 3 : age < 60 ? 6 : 9;
        if (left > 20 && age % every == 0) launch(level, c, finale);
        Iterator<Rocket> it = ROCKETS.iterator();
        while (it.hasNext()) {
            Rocket r = it.next();
            if (r.tick(level)) it.remove();
        }
        // confeti alrededor (de lana de colores, cae como papelitos)
        if (left > 40) {
            for (int i = 0; i < 4; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2, d = 2 + RANDOM.nextDouble() * 12;
                Block wool = WOOL[RANDOM.nextInt(WOOL.length)];
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, wool.defaultBlockState()),
                        c.x + Math.cos(a) * d, c.y + 8 + RANDOM.nextDouble() * 6, c.z + Math.sin(a) * d, 0, 0, 0);
            }
        }
    }

    private static final Block[] WOOL = {Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL,
            Blocks.MAGENTA_WOOL, Blocks.PINK_WOOL, Blocks.PURPLE_WOOL, Blocks.WHITE_WOOL};

    private static void launch(ClientLevel level, Vec3 c, boolean big) {
        double a = RANDOM.nextDouble() * Math.PI * 2, d = 18 + RANDOM.nextDouble() * 34;
        double x = c.x + Math.cos(a) * d, z = c.z + Math.sin(a) * d;
        double fromY = c.y - 4 + RANDOM.nextDouble() * 4;
        double toY = Math.min(level.getMaxBuildHeight() - 2, c.y + 22 + RANDOM.nextDouble() * 22);
        ROCKETS.add(new Rocket(x, fromY, z, toY, 16 + RANDOM.nextInt(10), big));
        rocketsLaunched++;
        level.playLocalSound(x, fromY, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 2.5F, 0.9F + RANDOM.nextFloat() * 0.2F, false);
    }

    /** Un cohete: sube dejando chispas y estalla arriba (la explosión es la de los fuegos de Minecraft). */
    private static final class Rocket {
        final double x, z, fromY, toY;
        final int life;
        final boolean big;
        int t;

        Rocket(double x, double fromY, double z, double toY, int life, boolean big) {
            this.x = x;
            this.fromY = fromY;
            this.z = z;
            this.toY = toY;
            this.life = life;
            this.big = big;
        }

        boolean tick(ClientLevel level) {
            t++;
            double y = fromY + (toY - fromY) * Math.sin(Math.min(1.0, t / (double) life) * Math.PI / 2);
            level.addParticle(ParticleTypes.FIREWORK, x, y, z, RANDOM.nextGaussian() * 0.02, -0.15, RANDOM.nextGaussian() * 0.02);
            if (t < life) return false;
            level.createFireworks(x, toY, z, 0, 0, 0, explosion(big));
            bursts++;
            return true;
        }
    }

    private static CompoundTag explosion(boolean big) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        int n = big && RANDOM.nextInt(3) == 0 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            CompoundTag e = new CompoundTag();
            // 0 bola pequeña, 1 bola grande, 2 estrella, 4 estallido (sin la cara de creeper)
            int[] types = big ? new int[] {1, 1, 2, 4} : new int[] {0, 1, 2, 4};
            e.putByte("Type", (byte) types[RANDOM.nextInt(types.length)]);
            e.putIntArray("Colors", colors(1 + RANDOM.nextInt(3)));
            e.putIntArray("FadeColors", colors(RANDOM.nextInt(2)));
            e.putBoolean("Flicker", RANDOM.nextBoolean());
            e.putBoolean("Trail", RANDOM.nextInt(3) > 0);
            list.add(e);
        }
        tag.put("Explosions", list);
        return tag;
    }

    private static final DyeColor[] BRIGHT = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE,
            DyeColor.CYAN, DyeColor.MAGENTA, DyeColor.PINK, DyeColor.PURPLE, DyeColor.WHITE};

    private static int[] colors(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = BRIGHT[RANDOM.nextInt(BRIGHT.length)].getFireworkColor();
        return out;
    }

    // ------------------------------------------------------------------------------------------------- música

    /**
     * «Cumpleaños feliz» con campanas (y un arpa debajo en los tiempos fuertes): {nota, ticks}. Notas del bloque musical
     * (0 = fa#3 … 24 = fa#5): sol3 1, la3 3, si3 5, do4 6, re4 8, mi4 10, fa4 11, sol4 13.
     */
    private static final int[][] SONG = {
            {1, 6}, {1, 2}, {3, 8}, {1, 8}, {6, 8}, {5, 16},
            {1, 6}, {1, 2}, {3, 8}, {1, 8}, {8, 8}, {6, 16},
            {1, 6}, {1, 2}, {13, 8}, {10, 8}, {6, 8}, {5, 8}, {3, 16},
            {11, 6}, {11, 2}, {10, 8}, {6, 8}, {8, 8}, {6, 24}};
    private static final int SONG_START = 30;

    private static void music(Minecraft mc) {
        int t = age - SONG_START;
        if (t < 0) return;
        int at = 0;
        for (int i = 0; i < SONG.length; i++) {
            if (t == at) {
                note(mc, SoundEvents.NOTE_BLOCK_BELL.value(), SONG[i][0], 0.55F);
                if (SONG[i][1] >= 8) note(mc, SoundEvents.NOTE_BLOCK_HARP.value(), SONG[i][0], 0.35F);
                notesPlayed++;
                return;
            }
            at += SONG[i][1];
        }
        if (t == at) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.5F));
    }

    private static void note(Minecraft mc, SoundEvent sound, int note, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, (float) Math.pow(2.0, (note - 12) / 12.0), volume));
    }

    // ------------------------------------------------------------------------------------------------- el letrero

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active() || !sky) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = event.getCamera();
        float partial = event.getPartialTick();
        float time = age + partial;
        float alpha = Mth.clamp(Math.min(time / FADE, (left - partial) / FADE), 0F, 1F);
        if (alpha <= 0.02F) return;
        // siempre delante: sigue despacio hacia donde mira la cámara
        if (!yawSet) {
            bannerYaw = cam.getYRot();
            yawSet = true;
        }
        bannerYaw += Mth.wrapDegrees(cam.getYRot() - bannerYaw) * 0.03F;
        double yaw = Math.toRadians(bannerYaw), el = Math.toRadians(ELEVATION + Math.sin(time * 0.03) * 1.5);
        Vec3 dir = new Vec3(-Math.sin(yaw) * Math.cos(el), Math.sin(el), Math.cos(yaw) * Math.cos(el));
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        pose.pushPose();
        try {
            pose.translate(dir.x * DIST, dir.y * DIST, dir.z * DIST);
            pose.mulPose(cam.rotation());
            float s = 0.16F;
            pose.scale(-s, -s, s);
            int a = (int) (alpha * 255) << 24;
            line(font, pose, buffers, "¡FELIZ CUMPLEAÑOS!", -22, 1.6F, time, a, true);
            line(font, pose, buffers, "❤ " + name + " ❤", 2, 1.25F, time, a, false);
            bannerFrames++;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Cumpleaños: no se pudo dibujar el letrero", t);
            name = null;
        } finally {
            pose.popPose();
            buffers.endBatch();
        }
    }

    /** Una línea centrada, letra a letra: arcoíris que corre y ondea (la del nombre, dorada con brillo). */
    private static void line(Font font, PoseStack pose, MultiBufferSource buffers, String text, float y, float scale, float time, int alpha, boolean rainbow) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        int[] cps = text.codePoints().toArray();
        float width = font.width(text);
        float x = -width / 2F;
        for (int i = 0; i < cps.length; i++) {
            String ch = new String(Character.toChars(cps[i]));
            float wave = (float) Math.sin(time * 0.15 + i * 0.55) * 1.6F;
            int rgb = rainbow ? Mth.hsvToRgb(((time * 0.006F) + i * 0.06F) % 1F, 0.6F, 1F)
                    : ch.equals("❤") ? 0xFF5068 : Mth.hsvToRgb(0.12F, 0.55F - 0.25F * (float) Math.max(0, Math.sin(time * 0.1 - i * 0.4)), 1F);
            FormattedCharSequence seq = FormattedCharSequence.forward(ch, Style.EMPTY.withBold(true));
            font.drawInBatch8xOutline(seq, x, y + wave, alpha | (rgb & 0xFFFFFF), alpha | 0x2A0F1E, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
            x += font.width(seq);
        }
        pose.popPose();
    }
}
