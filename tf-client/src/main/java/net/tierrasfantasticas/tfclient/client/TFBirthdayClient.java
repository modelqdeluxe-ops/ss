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
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La fiesta de cumpleaños en este cliente ({@code server.TFBirthday}). (1.3.151, el dueño: «es muy simple el evento de
 * cumpleaños») Minuto y medio en cuatro actos:
 * <ol>
 *   <li><b>La cuenta atrás</b> (4 s): el cielo se tiñe de violeta, redoble de tambor y 3… 2… 1… en la pantalla.</li>
 *   <li><b>¡Ahora!</b>: un anillo de cohetes que estallan a la vez alrededor y un corazón enorme de luz en el cielo.</li>
 *   <li><b>La fiesta</b>: el letrero (¡FELIZ CUMPLEAÑOS!, el nombre y «¡Que cumplas muchos más!») delante y por encima
 *   del horizonte, rodeado de brillos; el nombre escrito con estrellas que vuelan a su sitio; farolillos y globos que
 *   suben; cohetes al ritmo; confeti; «Cumpleaños feliz» dos veces con campanas, arpa, bajo y percusión; y, alrededor
 *   del cumpleañero, una corona de brillos y notas musicales que ven todos los que están cerca.</li>
 *   <li><b>La traca final</b>: cohetes sin parar, otro corazón, fanfarria y «¡Felicidades, NOMBRE!» a todos.</li>
 * </ol>
 * Todo son partículas ({@link TFPartyParticle}), sonidos y texto de este cliente: ni entidades ni daño, y se ve con
 * shaders (el tinte del cielo, solo sin ellos).
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFBirthdayClient {
    private static final Random RANDOM = new Random();
    /** A cuánto se dibuja el letrero (bloques) y a qué altura sobre el horizonte (grados). */
    private static final double DIST = 42;
    private static final float ELEVATION = 22F;
    /** Los actos (ticks desde que empieza). */
    private static final int BANG = 80, SONG_A = 110, SONG_B = 700, FINALE = 260;

    private static String name;
    private static int left, total, age;
    private static boolean sky, mine;
    private static float bannerYaw;
    private static boolean yawSet;
    private static final List<Rocket> ROCKETS = new ArrayList<>();
    /** Los que salen mientras se recorren los de ROCKETS (el segundo estallido de los dobles): entran después. */
    private static final List<Rocket> LATER = new ArrayList<>();
    /** Para la prueba en el juego: lo que ha salido. */
    public static int rocketsLaunched, bursts, notesPlayed, bannerFrames, lights;

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
            LATER.clear();
            return;
        }
        name = who;
        left = ticksLeft;
        sky = skyHere;
        mine = honoree;
        if (fresh) {
            // quien llega a mitad (entra o cambia de mundo) se salta lo que ya pasó
            total = net.tierrasfantasticas.tfclient.server.TFBirthday.LENGTH;
            age = Math.max(0, total - ticksLeft);
            yawSet = false;
            rocketsLaunched = bursts = notesPlayed = bannerFrames = lights = 0;
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

    private static void title(Minecraft mc, Component title, Component sub, int stay) {
        mc.gui.setTimes(4, stay, 12);
        mc.gui.setSubtitle(sub == null ? Component.empty() : sub);
        mc.gui.setTitle(title);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        name = null;
        left = 0;
        ROCKETS.clear();
        LATER.clear();
    }

    // ------------------------------------------------------------------------------------------------- cada tick

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        try {
            tick();
        } catch (Throwable t) {
            // la fiesta nunca cierra el juego: se acaba aquí y queda en el registro
            TFClient.LOGGER.error("TF Cumpleaños: fallo en la fiesta", t);
            name = null;
            ROCKETS.clear();
            LATER.clear();
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        left--;
        age++;
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        music(mc);
        screen(mc);
        Iterator<Rocket> it = ROCKETS.iterator();
        while (it.hasNext()) {
            Rocket r = it.next();
            if (r.tick(level)) it.remove();
        }
        ROCKETS.addAll(LATER);
        LATER.clear();
        honoree(level);
        if (!sky) return;
        Vec3 c = mc.player.position();
        boolean finale = left < FINALE;
        if (age == BANG) bang(level, c, mc);
        if (finale && left == FINALE - 40) heart(c, mc.gameRenderer.getMainCamera().getYRot() + 25F, 0xFF4FA0, 1.25F);
        if (finale && left == FINALE - 90) heart(c, mc.gameRenderer.getMainCamera().getYRot() - 25F, 0xFFD24A, 1.25F);
        if (age > BANG) {
            // cohetes: al ritmo de la canción mientras suena; en la traca, sin parar
            int every = finale ? 2 : age < BANG + 60 ? 5 : 8;
            if (left > 20 && age % every == 0) launch(level, c, finale, finale && RANDOM.nextInt(3) == 0);
            if (!finale && (age - BANG) % 500 == 60) nameInStars(c, mc.gameRenderer.getMainCamera().getYRot());
            lanterns(level, c, finale);
            confetti(level, c, finale ? 10 : 3);
            bannerSparkles(mc);
        }
    }

    /** La cuenta atrás, el «¡ahora!» y el título final. */
    private static void screen(Minecraft mc) {
        if (age == 1 && sky) title(mc, Component.literal("¡Atención!").withStyle(Style.EMPTY.withColor(0xFFD667).withBold(true)),
                Component.literal("Una sorpresa para " + name).withStyle(Style.EMPTY.withColor(0xF0E6FF)), 14);
        for (int i = 0; i < 3 && sky; i++) {
            if (age == 20 + i * 20) {
                title(mc, Component.literal(String.valueOf(3 - i)).withStyle(Style.EMPTY.withColor(new int[] {0xFF6B5E, 0xFFD667, 0x7CFF8A}[i]).withBold(true)), null, 14);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F, 1F));
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F + i * 0.26F, 0.7F));
            }
        }
        // redoble antes del «¡ahora!»
        if (sky && age > 6 && age < BANG && age % Math.max(1, 4 - age / 25) == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_SNARE.value(), 1F, 0.25F + 0.35F * age / BANG));
        }
        if (age == BANG && (sky || mine)) {
            title(mc, rainbow("¡Feliz cumpleaños!"), Component.literal(mine ? "Todo Tierras Fantásticas te felicita" : name)
                    .withStyle(Style.EMPTY.withColor(0xFFD667).withBold(!mine)), 70);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1F, 0.45F));
        }
        if (left == 70) {
            title(mc, rainbow("¡Felicidades, " + name + "!"), Component.literal("¡Que cumplas muchos más!").withStyle(Style.EMPTY.withColor(0xF0E6FF)), 50);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 0.6F));
        }
    }

    // ------------------------------------------------------------------------------------------------- cohetes

    private static final DyeColor[] BRIGHT = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE,
            DyeColor.CYAN, DyeColor.MAGENTA, DyeColor.PINK, DyeColor.PURPLE, DyeColor.WHITE};

    private static int[] colors(int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = BRIGHT[RANDOM.nextInt(BRIGHT.length)].getFireworkColor();
        return out;
    }

    private static void launch(ClientLevel level, Vec3 c, boolean big, boolean double_) {
        double a = RANDOM.nextDouble() * Math.PI * 2, d = 16 + RANDOM.nextDouble() * 38;
        rocket(level, c.x + Math.cos(a) * d, c.y - 4 + RANDOM.nextDouble() * 4, c.z + Math.sin(a) * d,
                c.y + 20 + RANDOM.nextDouble() * 26, 16 + RANDOM.nextInt(10), big, double_);
    }

    private static void rocket(ClientLevel level, double x, double fromY, double z, double toY, int life, boolean big, boolean double_) {
        toY = Math.min(level.getMaxBuildHeight() - 2, toY);
        ROCKETS.add(new Rocket(x, fromY, z, toY, life, big, double_));
        rocketsLaunched++;
        level.playLocalSound(x, fromY, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 2.5F, 0.9F + RANDOM.nextFloat() * 0.2F, false);
    }

    /** Un cohete: sube dejando chispas y estalla arriba (la explosión es la de los fuegos de Minecraft). */
    private static final class Rocket {
        final double x, z, fromY, toY;
        final int life;
        final boolean big, double_;
        int t;

        Rocket(double x, double fromY, double z, double toY, int life, boolean big, boolean double_) {
            this.x = x;
            this.fromY = fromY;
            this.z = z;
            this.toY = toY;
            this.life = life;
            this.big = big;
            this.double_ = double_;
        }

        boolean tick(ClientLevel level) {
            t++;
            double y = fromY + (toY - fromY) * Math.sin(Math.min(1.0, t / (double) life) * Math.PI / 2);
            level.addParticle(ParticleTypes.FIREWORK, x, y, z, RANDOM.nextGaussian() * 0.02, -0.15, RANDOM.nextGaussian() * 0.02);
            if (t < life) return false;
            level.createFireworks(x, toY, z, 0, 0, 0, explosion(big));
            bursts++;
            if (double_) {
                // el segundo estallido, un poco después y alrededor (un «crisantemo» doble)
                LATER.add(new Rocket(x + RANDOM.nextGaussian() * 3, toY, z + RANDOM.nextGaussian() * 3, toY + 4 + RANDOM.nextDouble() * 4, 6, false, false));
            }
            return true;
        }
    }

    private static CompoundTag explosion(boolean big) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        int n = big ? 1 + RANDOM.nextInt(2) : 1;
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

    /** ¡Ahora!: doce cohetes en anillo que estallan a la vez y el corazón grande delante. */
    private static void bang(ClientLevel level, Vec3 c, Minecraft mc) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            rocket(level, c.x + Math.cos(a) * 26, c.y - 2, c.z + Math.sin(a) * 26, c.y + 30, 22, true, i % 3 == 0);
        }
        heart(c, mc.gameRenderer.getMainCamera().getYRot(), 0xFF3D6E, 1.6F);
    }

    // ------------------------------------------------------------------------------------------------- luces

    /** Un punto del cielo: delante de esa dirección (grados), a esa altura sobre el horizonte y a esa distancia. */
    private static Vec3 skyPoint(Vec3 from, float yaw, float elevation, double dist) {
        double y = Math.toRadians(yaw), e = Math.toRadians(elevation);
        return from.add(-Math.sin(y) * Math.cos(e) * dist, Math.sin(e) * dist, Math.cos(y) * Math.cos(e) * dist);
    }

    /** Un corazón de luz en el cielo: el contorno que se abre desde el centro y corazoncitos dentro. */
    private static void heart(Vec3 c, float yaw, int rgb, float scale) {
        Vec3 center = skyPoint(c.add(0, 1.6, 0), yaw, 30F, 50);
        double y = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(y), 0, Math.sin(y));
        Vec3 up = new Vec3(0, 1, 0);
        for (int i = 0; i < 110; i++) {
            double t = i * Math.PI * 2 / 110;
            double hx = 16 * Math.pow(Math.sin(t), 3), hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            Vec3 at = center.add(right.scale(hx * 0.75 * scale)).add(up.scale(hy * 0.75 * scale));
            TFPartyParticle p = TFPartyParticle.spawn(TFPartyParticle.GLITTER, center.x, center.y, center.z, 0, 0, 0, rgb, 0.9F, 110, 0, 0.25F);
            if (p != null) p.flyTo(at, 14);
            lights++;
        }
        for (int i = 0; i < 26; i++) {
            Vec3 at = center.add(right.scale(RANDOM.nextGaussian() * 4 * scale)).add(up.scale(RANDOM.nextGaussian() * 3 * scale + 1));
            TFPartyParticle p = TFPartyParticle.spawn(TFPartyParticle.HEART, center.x, center.y, center.z, 0, 0, 0, 0xFFFFFF, 1.1F, 100, 0, 0.1F);
            if (p != null) p.flyTo(at, 20);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.9F));
    }

    /** El nombre escrito con estrellas doradas: salen de un punto y vuelan a su sitio, brillan unos segundos y se van. */
    private static void nameInStars(Vec3 c, float yaw) {
        String text = name.toUpperCase(Locale.ROOT);
        int cols = 0;
        for (char ch : text.toCharArray()) cols += (GLYPHS.containsKey(ch) ? 6 : 3);
        if (cols == 0) return;
        double step = Math.min(1.1, 56.0 / cols);
        // por encima del letrero y aún dentro de la pantalla
        Vec3 center = skyPoint(c.add(0, 1.6, 0), yaw, 37F, 70);
        double y = Math.toRadians(yaw);
        Vec3 right = new Vec3(Math.cos(y), 0, Math.sin(y));
        Vec3 up = new Vec3(0, 1, 0);
        double x0 = -cols * step / 2;
        int col = 0;
        for (char ch : text.toCharArray()) {
            String[] g = GLYPHS.get(ch);
            if (g == null) {
                col += 3;
                continue;
            }
            for (int row = 0; row < 7; row++) {
                for (int k = 0; k < 5; k++) {
                    if (g[row].charAt(k) != '#') continue;
                    Vec3 at = center.add(right.scale(x0 + (col + k) * step)).add(up.scale((3 - row) * step));
                    TFPartyParticle p = TFPartyParticle.spawn(TFPartyParticle.GLITTER, center.x, center.y - 8, center.z, 0, 0, 0,
                            Mth.hsvToRgb(0.11F + RANDOM.nextFloat() * 0.04F, 0.45F, 1F) & 0xFFFFFF, (float) (step * 0.6), 190, 0, 0.3F);
                    if (p != null) p.flyTo(at, 18 + RANDOM.nextInt(16));
                    lights++;
                }
            }
            col += 6;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4F, 0.7F));
    }

    /** Farolillos que suben despacio meciéndose y globos de colores (en la traca, más). */
    private static void lanterns(ClientLevel level, Vec3 c, boolean finale) {
        int n = finale ? 3 : 2;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 5 + RANDOM.nextDouble() * 40;
            TFPartyParticle.spawn(TFPartyParticle.FLASH, c.x + Math.cos(a) * d, c.y - 1 + RANDOM.nextDouble() * 3, c.z + Math.sin(a) * d,
                    0, 0.06 + RANDOM.nextDouble() * 0.04, 0, RANDOM.nextInt(4) == 0 ? 0xFFE08A : 0xFF9A3C, 0.32F, 280, 1F, 0.35F);
            lights++;
        }
        if (age % 3 == 0) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 9 + RANDOM.nextDouble() * 26;
            int rgb = Mth.hsvToRgb(RANDOM.nextFloat(), 0.7F, 1F) & 0xFFFFFF;
            TFPartyParticle.spawn(TFPartyParticle.FLASH, c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d,
                    0, 0.12 + RANDOM.nextDouble() * 0.05, 0, rgb, 0.55F, 220, 1.5F, 0F);
            lights++;
        }
    }

    private static final Block[] WOOL = {Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL,
            Blocks.MAGENTA_WOOL, Blocks.PINK_WOOL, Blocks.PURPLE_WOOL, Blocks.WHITE_WOOL};

    private static void confetti(ClientLevel level, Vec3 c, int n) {
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 2 + RANDOM.nextDouble() * 12;
            level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, WOOL[RANDOM.nextInt(WOOL.length)].defaultBlockState()),
                    c.x + Math.cos(a) * d, c.y + 8 + RANDOM.nextDouble() * 6, c.z + Math.sin(a) * d, 0, 0, 0);
        }
    }

    /** Brillos alrededor del letrero. */
    private static void bannerSparkles(Minecraft mc) {
        if (!yawSet || mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < 3; i++) {
            Vec3 at = skyPoint(eye, bannerYaw + (RANDOM.nextFloat() - 0.5F) * 46F, ELEVATION + (RANDOM.nextFloat() - 0.4F) * 16F, DIST + 2);
            TFPartyParticle.spawn(TFPartyParticle.GLITTER, at.x, at.y, at.z, 0, -0.01, 0, Mth.hsvToRgb(RANDOM.nextFloat(), 0.35F, 1F) & 0xFFFFFF,
                    0.5F, 26, 0, 0.5F);
        }
    }

    /** Alrededor del cumpleañero (lo ven todos los que lo tienen cerca): corona de brillos y notas musicales. */
    private static void honoree(ClientLevel level) {
        if (name == null || age < BANG) return;
        Minecraft mc = Minecraft.getInstance();
        for (Player p : level.players()) {
            if (!p.getGameProfile().getName().equalsIgnoreCase(name) || p.isInvisible()) continue;
            // en su propia vista en primera persona se le pondrían delante de los ojos
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 h = p.position().add(0, p.getBbHeight() + 0.35, 0);
            // corona: ocho puntos dorados que giran despacio (y una chispa que sube de vez en cuando)
            if (age % 2 == 0) {
                for (int i = 0; i < 8; i++) {
                    double a = age * 0.05 + i * Math.PI / 4;
                    TFPartyParticle.spawn(TFPartyParticle.GLITTER, h.x + Math.cos(a) * 0.38, h.y + (i % 2 == 0 ? 0.12 : 0), h.z + Math.sin(a) * 0.38,
                            0, 0, 0, i % 2 == 0 ? 0xFFE27A : 0xFFB23C, 0.1F, 3, 0, 0F);
                }
            }
            if (age % 9 == 0) {
                TFPartyParticle.spawn(TFPartyParticle.GLITTER, h.x + RANDOM.nextGaussian() * 0.2, h.y + 0.2, h.z + RANDOM.nextGaussian() * 0.2,
                        0, 0.03, 0, 0xFFF2B0, 0.12F, 24, 0, 0.4F);
            }
            if (age % 7 == 0) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                TFPartyParticle.spawn(TFPartyParticle.NOTE, p.getX() + Math.cos(a) * 0.9, p.getY() + 1.2 + RANDOM.nextDouble() * 0.8, p.getZ() + Math.sin(a) * 0.9,
                        0, 0.03, 0, Mth.hsvToRgb(RANDOM.nextFloat(), 0.7F, 1F) & 0xFFFFFF, 0.22F, 40, 0.5F, 0F);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- música

    /**
     * «Cumpleaños feliz»: {nota de la melodía, nota de la armonía (-1 nada), ticks}. Notas del bloque musical (0 = fa#3
     * … 24 = fa#5). Melodía en campanas, armonía en arpa, el bajo una octava por debajo de la armonía y percusión.
     */
    private static final int[][] SONG = {
            {1, -1, 6}, {1, -1, 2}, {3, 18, 8}, {1, 18, 8}, {6, 22, 8}, {5, 20, 16},
            {1, -1, 6}, {1, -1, 2}, {3, 20, 8}, {1, 20, 8}, {8, 23, 8}, {6, 22, 16},
            {1, -1, 6}, {1, -1, 2}, {13, 22, 8}, {10, 18, 8}, {6, 15, 8}, {5, 14, 8}, {3, 12, 16},
            {11, 15, 6}, {11, 15, 2}, {10, 18, 8}, {6, 15, 8}, {8, 20, 8}, {6, 18, 24}};

    private static void music(Minecraft mc) {
        for (int start : new int[] {SONG_A, SONG_B}) {
            int t = age - start;
            if (t < 0) continue;
            int at = 0;
            for (int i = 0; i < SONG.length; i++) {
                if (t == at) {
                    float v = start == SONG_B ? 0.65F : 0.55F;
                    note(mc, SoundEvents.NOTE_BLOCK_BELL.value(), SONG[i][0], v);
                    if (SONG[i][1] >= 0) note(mc, SoundEvents.NOTE_BLOCK_HARP.value(), SONG[i][1], v * 0.5F);
                    if (SONG[i][2] >= 8) {
                        note(mc, SoundEvents.NOTE_BLOCK_BASS.value(), SONG[i][1] >= 12 ? SONG[i][1] - 12 : SONG[i][0], v * 0.7F);
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F, 0.18F));
                    }
                    if (SONG[i][2] >= 16) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1F, 0.35F));
                    notesPlayed++;
                }
                at += SONG[i][2];
            }
            if (t == at) {
                // fanfarria: arpegio que sube
                for (int k = 0; k < 4; k++) note(mc, SoundEvents.NOTE_BLOCK_PLING.value(), 6 + k * 4, 0.45F);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F, 0.5F));
            }
        }
    }

    private static void note(Minecraft mc, SoundEvent sound, int note, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, (float) Math.pow(2.0, (Mth.clamp(note, 0, 24) - 12) / 12.0), volume));
    }

    // ------------------------------------------------------------------------------------------------- el cielo

    /** El cielo violeta (sin shaders): entra con la cuenta atrás y se va al final. */
    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!active() || !sky) return;
        float k = Mth.clamp(Math.min(age / 60F, left / 60F), 0F, 1F) * 0.45F;
        float pulse = 0.85F + 0.15F * Mth.sin(age * 0.05F);
        event.setRed(Mth.lerp(k, event.getRed(), 0.42F * pulse));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.12F * pulse));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.55F * pulse));
    }

    // ------------------------------------------------------------------------------------------------- el letrero

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active() || !sky || age < BANG) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Camera cam = event.getCamera();
        float partial = event.getPartialTick();
        float time = age + partial;
        float alpha = Mth.clamp(Math.min((time - BANG) / 30F, (left - partial) / 40F), 0F, 1F);
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
            // entra creciendo con un pequeño rebote
            float pop = Mth.clamp((time - BANG) / 14F, 0F, 1F);
            float bounce = pop < 1F ? 1F + 0.18F * Mth.sin(pop * Mth.PI) : 1F;
            float s = 0.30F * (0.4F + 0.6F * pop) * bounce;
            pose.scale(-s, -s, s);
            int a = (int) (alpha * 255) << 24;
            line(font, pose, buffers, "¡FELIZ CUMPLEAÑOS!", -24, 1.7F, time, a, 0);
            line(font, pose, buffers, "❤ " + name + " ❤", 0, 1.6F, time, a, 1);
            line(font, pose, buffers, "¡Que cumplas muchos más!", 22, 0.9F, time, a, 2);
            bannerFrames++;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Cumpleaños: no se pudo dibujar el letrero", t);
            name = null;
        } finally {
            pose.popPose();
            buffers.endBatch();
        }
    }

    /** Una línea centrada, letra a letra: 0 arcoíris que corre y ondea; 1 dorada con brillo que pasa; 2 blanca suave. */
    private static void line(Font font, PoseStack pose, MultiBufferSource buffers, String text, float y, float scale, float time, int alpha, int style) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        int[] cps = text.codePoints().toArray();
        float width = font.width(text);
        float x = -width / 2F;
        for (int i = 0; i < cps.length; i++) {
            String ch = new String(Character.toChars(cps[i]));
            float wave = style == 2 ? 0F : (float) Math.sin(time * 0.15 + i * 0.55) * 1.6F;
            int rgb = switch (style) {
                case 0 -> Mth.hsvToRgb(((time * 0.006F) + i * 0.06F) % 1F, 0.6F, 1F);
                case 1 -> ch.equals("❤") ? 0xFF5068 : Mth.hsvToRgb(0.12F, 0.55F - 0.4F * (float) Math.max(0, Math.sin(time * 0.1 - i * 0.4)), 1F);
                default -> 0xF0E6FF;
            };
            FormattedCharSequence seq = FormattedCharSequence.forward(ch, Style.EMPTY.withBold(style != 2));
            font.drawInBatch8xOutline(seq, x, y / scale + wave, alpha | (rgb & 0xFFFFFF), alpha | 0x2A0F1E, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
            x += font.width(seq);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------------------------------------------- letras de estrellas

    /** Letras de 5x7 para escribir el nombre con estrellas (los nombres de Minecraft: letras, números y _). */
    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            Map.entry('A', new String[] {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('B', new String[] {"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."}),
            Map.entry('C', new String[] {".####", "#....", "#....", "#....", "#....", "#....", ".####"}),
            Map.entry('D', new String[] {"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."}),
            Map.entry('E', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#####"}),
            Map.entry('F', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#...."}),
            Map.entry('G', new String[] {".####", "#....", "#....", "#.###", "#...#", "#...#", ".###."}),
            Map.entry('H', new String[] {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}),
            Map.entry('J', new String[] {"..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."}),
            Map.entry('K', new String[] {"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"}),
            Map.entry('L', new String[] {"#....", "#....", "#....", "#....", "#....", "#....", "#####"}),
            Map.entry('M', new String[] {"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"}),
            Map.entry('N', new String[] {"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}),
            Map.entry('O', new String[] {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('P', new String[] {"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}),
            Map.entry('Q', new String[] {".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"}),
            Map.entry('R', new String[] {"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}),
            Map.entry('S', new String[] {".####", "#....", "#....", ".###.", "....#", "....#", "####."}),
            Map.entry('T', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('U', new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('V', new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."}),
            Map.entry('W', new String[] {"#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"}),
            Map.entry('X', new String[] {"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"}),
            Map.entry('Y', new String[] {"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('Z', new String[] {"#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"}),
            Map.entry('0', new String[] {".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."}),
            Map.entry('1', new String[] {"..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."}),
            Map.entry('2', new String[] {".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"}),
            Map.entry('3', new String[] {"####.", "....#", "....#", ".###.", "....#", "....#", "####."}),
            Map.entry('4', new String[] {"...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."}),
            Map.entry('5', new String[] {"#####", "#....", "####.", "....#", "....#", "#...#", ".###."}),
            Map.entry('6', new String[] {".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."}),
            Map.entry('7', new String[] {"#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."}),
            Map.entry('8', new String[] {".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."}),
            Map.entry('9', new String[] {".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."}),
            Map.entry('_', new String[] {".....", ".....", ".....", ".....", ".....", ".....", "#####"}));
}
