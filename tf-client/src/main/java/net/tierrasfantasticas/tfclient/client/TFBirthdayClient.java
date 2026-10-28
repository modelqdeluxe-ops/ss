/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * La fiesta de cumpleaños en este cliente ({@code server.TFBirthday}). (1.3.151, el dueño: «es muy simple») Minuto y
 * medio en cuatro actos:
 * <ol>
 *   <li><b>La cuenta atrás</b> (4 s): el cielo se tiñe de violeta, redoble de tambor y 3… 2… 1… en la pantalla.</li>
 *   <li><b>¡Ahora!</b>: un anillo de cohetes que estallan a la vez y un corazón enorme de luz en el cielo.</li>
 *   <li><b>La fiesta</b>: el letrero (¡FELIZ CUMPLEAÑOS!, el nombre entre dos corazones y «¡Que cumplas muchos más!»),
 *   el nombre escrito con estrellas, farolillos y globos que suben, cohetes, confeti, «Cumpleaños feliz» y, alrededor
 *   del cumpleañero, una corona de brillos y notas musicales que ven todos los que están cerca.</li>
 *   <li><b>La traca final</b>: cohetes sin parar, otro corazón, fanfarria y «¡Felicidades!» a todos.</li>
 * </ol>
 * (1.3.152, el dueño con capturas: «ese rótulo en el cielo es arriba, no enfrente; me sigue a donde veo y se ve
 * horrible… ponlo tipo a las 2 en punto», «con shaders no se ve el rótulo ni los fuegos», «el último mensaje cubre toda
 * la pantalla», «la música extiéndela, solo suena 3 segundos» y «el sonido del final, más bonito») El letrero, el
 * corazón y el nombre en estrellas están quietos en el cielo, en la dirección en la que miraba al empezar y a unos 58°
 * sobre el horizonte (las 2 en punto de un reloj con las 12 encima); los corazones del nombre son los de la vida de
 * Minecraft dibujados, no una letra (con otros paquetes de recursos ❤ salía raro); con paquete de shaders el letrero se
 * dibuja después de la pasada final del paquete ({@code vacio.AfterShaders}, como la grieta) y cada cohete suelta
 * además sus propias chispas de luz ({@link TFPartyParticle}, que los shaders sí dibujan); los títulos son nuestros y
 * se encogen para caber; «Cumpleaños feliz» suena seis veces seguidas (cada vez con otros instrumentos y, desde la
 * segunda, con bajo y vals de percusión) y acaba con una fanfarria de campanas y destellos.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT)
public final class TFBirthdayClient {
    private static final Random RANDOM = new Random();
    /** El letrero: a qué distancia (bloques) y a qué altura sobre el horizonte (grados; 58° ≈ las 2 en punto). */
    private static final double DIST = 58;
    private static final float ELEVATION = 58F;
    /** Los actos (ticks desde que empieza; FINALE = ticks que quedan cuando empieza la traca). */
    private static final int BANG = 80, FINALE = 260;
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");

    private static String name;
    private static int left, total, age;
    private static boolean sky, mine;
    /** Hacia dónde está todo lo del cielo (grados, como la cámara): hacia donde miraba al empezar. */
    private static float skyYaw;
    private static final List<Rocket> ROCKETS = new ArrayList<>();
    /** Los que salen mientras se recorren los de ROCKETS (el segundo estallido de los dobles): entran después. */
    private static final List<Rocket> LATER = new ArrayList<>();
    /** El texto grande de la pantalla: título, subtítulo, desde qué tick y cuánto dura. */
    private static Component bigTitle, bigSub;
    private static int bigStart, bigStay;
    /** Para la prueba en el juego: lo que ha salido. */
    public static int rocketsLaunched, bursts, notesPlayed, bannerFrames, bannerLate, lights;

    private TFBirthdayClient() {}

    public static boolean active() {
        return name != null && left > 0;
    }

    /** Lo que manda el servidor: empieza, sigue en otro mundo o se acaba (left = 0). */
    public static void party(String who, int ticksLeft, boolean skyHere, boolean honoree) {
        boolean fresh = !active() || !who.equals(name);
        if (ticksLeft <= 0 || who.isEmpty()) {
            stop();
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
            Minecraft mc = Minecraft.getInstance();
            skyYaw = mc.player != null ? mc.player.getYRot() : 0F;
            bigTitle = null;
            rocketsLaunched = bursts = notesPlayed = bannerFrames = bannerLate = lights = 0;
        }
    }

    private static void stop() {
        name = null;
        left = 0;
        bigTitle = null;
        ROCKETS.clear();
        LATER.clear();
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

    private static void big(Component title, Component sub, int stay) {
        bigTitle = title;
        bigSub = sub;
        bigStart = age;
        bigStay = stay;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
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
            stop();
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
        if (age == BANG) bang(level, c);
        if (finale && left == FINALE - 40) heart(c, skyYaw + 28F, 0xFF4FA0, 1.2F);
        if (finale && left == FINALE - 90) heart(c, skyYaw - 28F, 0xFFD24A, 1.2F);
        if (age > BANG) {
            int every = finale ? 2 : age < BANG + 60 ? 5 : 8;
            if (left > 20 && age % every == 0) launch(level, c, finale, finale && RANDOM.nextInt(3) == 0);
            if (!finale && (age - BANG) % 500 == 60) nameInStars(c);
            lanterns(c, finale);
            confetti(level, c, finale ? 10 : 3);
            bannerSparkles(mc);
        }
    }

    /** La cuenta atrás, el «¡ahora!» y el final (textos nuestros, que caben en cualquier pantalla). */
    private static void screen(Minecraft mc) {
        if (age == 1 && sky) big(Component.literal("¡Atención!").withStyle(Style.EMPTY.withColor(0xFFD667).withBold(true)),
                Component.literal("Una sorpresa para " + name).withStyle(Style.EMPTY.withColor(0xF0E6FF)), 16);
        for (int i = 0; i < 3 && sky; i++) {
            if (age == 20 + i * 20) {
                big(Component.literal(String.valueOf(3 - i)).withStyle(Style.EMPTY.withColor(new int[] {0xFF6B5E, 0xFFD667, 0x7CFF8A}[i]).withBold(true)), null, 16);
                ui(mc, SoundEvents.NOTE_BLOCK_BASEDRUM, 12, 1F);
                ui(mc, SoundEvents.NOTE_BLOCK_PLING, 12 + i * 4, 0.7F);
            }
        }
        // redoble antes del «¡ahora!»
        if (sky && age > 6 && age < BANG && age % Math.max(1, 4 - age / 25) == 0) ui(mc, SoundEvents.NOTE_BLOCK_SNARE, 12, 0.25F + 0.35F * age / BANG);
        if (age == BANG && (sky || mine)) {
            big(rainbow("¡Feliz cumpleaños!"), Component.literal(mine ? "Todo Tierras Fantásticas te felicita" : name)
                    .withStyle(Style.EMPTY.withColor(0xFFD667).withBold(!mine)), 70);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TOTEM_USE, 1F, 0.45F));
        }
        if (age == FANFARE) {
            big(rainbow("¡Felicidades!"), Component.literal(name + " · ¡Que cumplas muchos más!").withStyle(Style.EMPTY.withColor(0xF0E6FF)), 80);
        }
    }

    /** El texto grande: como mucho 4 veces la letra normal y nunca más ancho que el 80 % de la pantalla. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (!active() || bigTitle == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        float t = age - bigStart + event.getPartialTick();
        if (t > bigStay + 12) {
            bigTitle = null;
            return;
        }
        float a = Mth.clamp(Math.min(t / 4F, (bigStay + 12 - t) / 12F), 0F, 1F);
        if (a <= 0.02F) return;
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int alpha = Math.max(5, (int) (a * 255)) << 24;
        float pop = 1F + 0.12F * Mth.clamp(1F - t / 6F, 0F, 1F);
        float s = Math.min(4F, w * 0.8F / Math.max(1, font.width(bigTitle))) * pop;
        g.pose().pushPose();
        g.pose().translate(w / 2F, h * 0.27F, 0);
        g.pose().scale(s, s, 1F);
        g.drawString(font, bigTitle, -font.width(bigTitle) / 2, 0, 0xFFFFFF | alpha, true);
        g.pose().popPose();
        if (bigSub != null) {
            float s2 = Math.min(1.8F, w * 0.8F / Math.max(1, font.width(bigSub)));
            g.pose().pushPose();
            g.pose().translate(w / 2F, h * 0.27F + 10 * s + 6, 0);
            g.pose().scale(s2, s2, 1F);
            g.drawString(font, bigSub, -font.width(bigSub) / 2, 0, 0xFFFFFF | alpha, true);
            g.pose().popPose();
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

    /** Un cohete: sube dejando chispas y estalla arriba (la explosión de Minecraft y, encima, chispas de luz nuestras). */
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
            if (t % 2 == 0) TFPartyParticle.spawn(TFPartyParticle.GLITTER, x, y, z, 0, -0.05, 0, 0xFFE9B0, 0.22F, 12, 0, 0.3F);
            if (t < life) return false;
            int[] cols = colors(1 + RANDOM.nextInt(3));
            level.createFireworks(x, toY, z, 0, 0, 0, explosion(big, cols));
            sparks(x, toY, z, cols, big);
            bursts++;
            if (double_) {
                // el segundo estallido, un poco después y alrededor (un «crisantemo» doble)
                LATER.add(new Rocket(x + RANDOM.nextGaussian() * 3, toY, z + RANDOM.nextGaussian() * 3, toY + 4 + RANDOM.nextDouble() * 4, 6, false, false));
            }
            return true;
        }
    }

    private static CompoundTag explosion(boolean big, int[] cols) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag e = new CompoundTag();
        // 0 bola pequeña, 1 bola grande, 2 estrella, 4 estallido (sin la cara de creeper)
        int[] types = big ? new int[] {1, 1, 2, 4} : new int[] {0, 1, 2, 4};
        e.putByte("Type", (byte) types[RANDOM.nextInt(types.length)]);
        e.putIntArray("Colors", cols);
        e.putIntArray("FadeColors", colors(RANDOM.nextInt(2)));
        e.putBoolean("Flicker", RANDOM.nextBoolean());
        e.putBoolean("Trail", RANDOM.nextInt(3) > 0);
        list.add(e);
        tag.put("Explosions", list);
        return tag;
    }

    /** Chispas de luz que se abren en esfera, caen y se apagan (y un destello): se ven de día y con shaders. */
    private static void sparks(double x, double y, double z, int[] cols, boolean big) {
        TFPartyParticle.spawn(TFPartyParticle.FLASH, x, y, z, 0, 0, 0, 0xFFF4E0, big ? 5F : 3.5F, 4, 0, 0F);
        int n = big ? 70 : 46;
        double speed = big ? 0.55 : 0.42;
        for (int i = 0; i < n; i++) {
            double u = RANDOM.nextDouble() * 2 - 1, th = RANDOM.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - u * u);
            double v = speed * (0.85 + RANDOM.nextDouble() * 0.3);
            TFPartyParticle p = TFPartyParticle.spawn(i % 3 == 0 ? TFPartyParticle.GLITTER : TFPartyParticle.SPARK, x, y, z,
                    r * Math.cos(th) * v, u * v, r * Math.sin(th) * v, cols[i % cols.length] | 0x202020, big ? 0.42F : 0.34F,
                    28 + RANDOM.nextInt(16), 0, 0.35F);
            if (p != null) p.physics(0.012F, 0.91F);
        }
        lights += n;
    }

    /** ¡Ahora!: doce cohetes en anillo que estallan a la vez y el corazón grande en el cielo. */
    private static void bang(ClientLevel level, Vec3 c) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            rocket(level, c.x + Math.cos(a) * 26, c.y - 2, c.z + Math.sin(a) * 26, c.y + 30, 22, true, i % 3 == 0);
        }
        heart(c, skyYaw, 0xFF3D6E, 1.5F);
    }

    // ------------------------------------------------------------------------------------------------- luces

    /** Un punto del cielo: hacia esa dirección (grados), a esa altura sobre el horizonte y a esa distancia. */
    private static Vec3 skyPoint(Vec3 from, float yaw, float elevation, double dist) {
        double y = Math.toRadians(yaw), e = Math.toRadians(elevation);
        return from.add(-Math.sin(y) * Math.cos(e) * dist, Math.sin(e) * dist, Math.cos(y) * Math.cos(e) * dist);
    }

    /** La derecha de quien mira hacia «yaw». */
    private static Vec3 rightOf(float yaw) {
        double y = Math.toRadians(yaw);
        return new Vec3(-Math.cos(y), 0, -Math.sin(y));
    }

    /** Un corazón de luz en el cielo: el contorno que se abre desde el centro y corazoncitos dentro. */
    private static void heart(Vec3 c, float yaw, int rgb, float scale) {
        Vec3 center = skyPoint(c.add(0, 1.6, 0), yaw, 34F, 52);
        Vec3 right = rightOf(yaw);
        Vec3 up = new Vec3(0, 1, 0);
        for (int i = 0; i < 110; i++) {
            double t = i * Math.PI * 2 / 110;
            double hx = 16 * Math.pow(Math.sin(t), 3), hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            Vec3 at = center.add(right.scale(hx * 0.75 * scale)).add(up.scale(hy * 0.75 * scale));
            TFPartyParticle p = TFPartyParticle.spawn(TFPartyParticle.GLITTER, center.x, center.y, center.z, 0, 0, 0, rgb, 0.9F, 120, 0, 0.25F);
            if (p != null) p.flyTo(at, 14);
            lights++;
        }
        for (int i = 0; i < 26; i++) {
            Vec3 at = center.add(right.scale(RANDOM.nextGaussian() * 4 * scale)).add(up.scale(RANDOM.nextGaussian() * 3 * scale + 1));
            TFPartyParticle p = TFPartyParticle.spawn(TFPartyParticle.HEART, center.x, center.y, center.z, 0, 0, 0, 0xFFFFFF, 1.1F, 110, 0, 0.1F);
            if (p != null) p.flyTo(at, 20);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.9F));
    }

    /** El nombre escrito con estrellas doradas, debajo del letrero: salen de un punto y vuelan a su sitio. */
    private static void nameInStars(Vec3 c) {
        String text = name.toUpperCase(Locale.ROOT);
        int cols = 0;
        for (char ch : text.toCharArray()) cols += (GLYPHS.containsKey(ch) ? 6 : 3);
        if (cols == 0) return;
        double step = Math.min(1.1, 56.0 / cols);
        Vec3 center = skyPoint(c.add(0, 1.6, 0), skyYaw, 40F, 66);
        Vec3 right = rightOf(skyYaw);
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
    private static void lanterns(Vec3 c, boolean finale) {
        int n = finale ? 3 : 2;
        for (int i = 0; i < n; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 5 + RANDOM.nextDouble() * 40;
            TFPartyParticle.spawn(TFPartyParticle.FLASH, c.x + Math.cos(a) * d, c.y - 1 + RANDOM.nextDouble() * 3, c.z + Math.sin(a) * d,
                    0, 0.06 + RANDOM.nextDouble() * 0.04, 0, RANDOM.nextInt(4) == 0 ? 0xFFE08A : 0xFF9A3C, 0.32F, 280, 1F, 0.35F);
            lights++;
        }
        if (age % 3 == 0) {
            double a = RANDOM.nextDouble() * Math.PI * 2, d = 9 + RANDOM.nextDouble() * 26;
            TFPartyParticle.spawn(TFPartyParticle.FLASH, c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d,
                    0, 0.12 + RANDOM.nextDouble() * 0.05, 0, Mth.hsvToRgb(RANDOM.nextFloat(), 0.7F, 1F) & 0xFFFFFF, 0.55F, 220, 1.5F, 0F);
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

    /** Brillos alrededor del letrero (quieto en el cielo). */
    private static void bannerSparkles(Minecraft mc) {
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition();
        for (int i = 0; i < 3; i++) {
            Vec3 at = skyPoint(eye, skyYaw + (RANDOM.nextFloat() - 0.5F) * 60F, ELEVATION + (RANDOM.nextFloat() - 0.5F) * 18F, DIST + 3);
            TFPartyParticle.spawn(TFPartyParticle.GLITTER, at.x, at.y, at.z, 0, -0.01, 0, Mth.hsvToRgb(RANDOM.nextFloat(), 0.35F, 1F) & 0xFFFFFF,
                    0.6F, 26, 0, 0.5F);
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
     * «Cumpleaños feliz» en 3/4 (negra = 10 ticks): {melodía, armonía (-1 nada), ticks}. Notas del bloque musical (0 =
     * fa#3 … 24 = fa#5).
     */
    private static final int[][] SONG = {
            {1, -1, 8}, {1, -1, 2}, {3, 18, 10}, {1, 18, 10}, {6, 22, 10}, {5, 20, 20},
            {1, -1, 8}, {1, -1, 2}, {3, 20, 10}, {1, 20, 10}, {8, 23, 10}, {6, 22, 20},
            {1, -1, 8}, {1, -1, 2}, {13, 22, 10}, {10, 18, 10}, {6, 15, 10}, {5, 14, 10}, {3, 12, 20},
            {11, 15, 8}, {11, 15, 2}, {10, 18, 10}, {6, 15, 10}, {8, 20, 10}, {6, 18, 30}};
    private static final int VERSE = 260;

    /** Una vuelta: cuándo empieza, melodía, segunda voz de la melodía (o null), armonía, bajo, percusión (0 nada, 1 suave, 2 entera). */
    private record Verse(int start, Holder<SoundEvent> lead, Holder<SoundEvent> lead2, Holder<SoundEvent> harmony, boolean bass, int drums,
                         float volume) {}

    private static final Verse[] VERSES = {
            new Verse(100, SoundEvents.NOTE_BLOCK_BELL, null, SoundEvents.NOTE_BLOCK_HARP, false, 0, 0.55F),
            new Verse(100 + VERSE, SoundEvents.NOTE_BLOCK_FLUTE, null, SoundEvents.NOTE_BLOCK_BELL, true, 1, 0.6F),
            new Verse(100 + VERSE * 2, SoundEvents.NOTE_BLOCK_CHIME, SoundEvents.NOTE_BLOCK_BELL, SoundEvents.NOTE_BLOCK_HARP, true, 1, 0.55F),
            new Verse(100 + VERSE * 3, SoundEvents.NOTE_BLOCK_XYLOPHONE, null, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE, true, 2, 0.6F),
            new Verse(100 + VERSE * 4, SoundEvents.NOTE_BLOCK_BELL, SoundEvents.NOTE_BLOCK_PLING, SoundEvents.NOTE_BLOCK_HARP, true, 2, 0.6F),
            // la última, con la traca: todo a la vez
            new Verse(net.tierrasfantasticas.tfclient.server.TFBirthday.LENGTH - 110 - VERSE, SoundEvents.NOTE_BLOCK_BELL, SoundEvents.NOTE_BLOCK_FLUTE,
                    SoundEvents.NOTE_BLOCK_CHIME, true, 2, 0.7F)};
    /** La fanfarria del final, justo al acabar la última vuelta. */
    private static final int FANFARE = net.tierrasfantasticas.tfclient.server.TFBirthday.LENGTH - 110;

    private static void music(Minecraft mc) {
        for (Verse v : VERSES) {
            int t = age - v.start;
            if (t < 0 || t >= VERSE) continue;
            int at = 0;
            for (int[] n : SONG) {
                if (t == at) {
                    ui(mc, v.lead, n[0], v.volume);
                    if (v.lead2 != null) ui(mc, v.lead2, n[0] + 12 <= 24 ? n[0] + 12 : n[0], v.volume * 0.45F);
                    if (n[1] >= 0) ui(mc, v.harmony, n[1], v.volume * 0.45F);
                    if (v.bass && n[2] >= 10) ui(mc, SoundEvents.NOTE_BLOCK_BASS, n[1] >= 12 ? n[1] - 12 : n[0], v.volume * 0.75F);
                    notesPlayed++;
                }
                at += n[2];
            }
            // vals: bombo en el primer tiempo de cada compás y platillo en los otros dos (tras la anacrusa de 10 ticks)
            if (v.drums > 0 && t >= 10 && (t - 10) % 10 == 0) {
                boolean downbeat = (t - 10) % 30 == 0;
                if (downbeat) ui(mc, SoundEvents.NOTE_BLOCK_BASEDRUM, 12, v.drums == 2 ? 0.45F : 0.3F);
                else ui(mc, SoundEvents.NOTE_BLOCK_HAT, 14, v.drums == 2 ? 0.22F : 0.14F);
                if (v.drums == 2 && !downbeat && (t - 10) % 30 == 20) ui(mc, SoundEvents.NOTE_BLOCK_SNARE, 12, 0.18F);
            }
        }
        fanfare(mc, age - FANFARE);
    }

    /**
     * El final: arpegio que sube (do-mi-sol-do) con campanas y destellos, un acorde largo de campana, carillón, flauta y
     * arpa con el bajo, y una lluvia de carillón que baja hasta apagarse en el cristal de amatista.
     */
    private static void fanfare(Minecraft mc, int t) {
        if (t < 0 || t > 60) return;
        int[] up = {6, 10, 13, 18};
        if (t < 12 && t % 3 == 0) {
            int n = up[t / 3];
            ui(mc, SoundEvents.NOTE_BLOCK_BELL, n, 0.6F);
            ui(mc, SoundEvents.NOTE_BLOCK_PLING, n, 0.35F);
        }
        if (t == 13) {
            for (int n : new int[] {13, 18, 22}) {
                ui(mc, SoundEvents.NOTE_BLOCK_BELL, n, 0.5F);
                ui(mc, SoundEvents.NOTE_BLOCK_CHIME, n, 0.4F);
            }
            ui(mc, SoundEvents.NOTE_BLOCK_FLUTE, 18, 0.45F);
            ui(mc, SoundEvents.NOTE_BLOCK_HARP, 6, 0.5F);
            ui(mc, SoundEvents.NOTE_BLOCK_BASS, 6, 0.7F);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.6F));
        }
        if (t > 16 && t <= 40 && t % 2 == 0) ui(mc, SoundEvents.NOTE_BLOCK_CHIME, 24 - (t - 16) * 18 / 24, 0.28F);
        if (t == 44) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1F));
        if (t == 52) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.7F));
    }

    /** Una nota del bloque musical (0 = fa#3 … 24 = fa#5) que se oye igual estés donde estés. */
    private static void ui(Minecraft mc, Holder<SoundEvent> sound, int note, float volume) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), (float) Math.pow(2.0, (Mth.clamp(note, 0, 24) - 12) / 12.0), volume));
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

    /** Sin paquete de shaders: con el mundo, después de las partículas. */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || net.tierrasfantasticas.tfclient.vacio.AfterShaders.active()) return;
        if (banner(event.getPoseStack(), event.getPartialTick())) bannerFrames++;
    }

    /** Con paquete de shaders: después de su pasada final, con la profundidad de la escena (vacio.AfterShaders). */
    public static void renderAfterShaders(Matrix4f view, float partial) {
        PoseStack pose = new PoseStack();
        pose.mulPoseMatrix(view);
        if (banner(pose, partial)) {
            bannerFrames++;
            bannerLate++;
        }
    }

    /**
     * El letrero, quieto en el cielo: en la dirección en la que se miraba al empezar, a {@link #ELEVATION} grados y
     * mirando hacia el jugador. La niebla no lo borra (está lejos a propósito). Devuelve si se dibujó.
     */
    private static boolean banner(PoseStack pose, float partial) {
        if (!active() || !sky || age < BANG) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        float time = age + partial;
        float alpha = Mth.clamp(Math.min((time - BANG) / 30F, (left - partial) / 40F), 0F, 1F);
        if (alpha <= 0.02F) return false;
        double yaw = Math.toRadians(skyYaw), el = Math.toRadians(ELEVATION + Math.sin(time * 0.03) * 0.8);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float fogStart = RenderSystem.getShaderFogStart();
        pose.pushPose();
        try {
            pose.translate(-Math.sin(yaw) * Math.cos(el) * DIST, Math.sin(el) * DIST, Math.cos(yaw) * Math.cos(el) * DIST);
            // de cara al jugador, como si una cámara lo mirase desde él (sin seguir la vista)
            pose.mulPose(new Quaternionf().rotationYXZ((float) -yaw, (float) -el, 0F));
            float pop = Mth.clamp((time - BANG) / 14F, 0F, 1F);
            float bounce = pop < 1F ? 1F + 0.18F * Mth.sin(pop * Mth.PI) : 1F;
            float s = 0.42F * (0.4F + 0.6F * pop) * bounce;
            pose.scale(-s, -s, s);
            int a = (int) (alpha * 255) << 24;
            line(font, pose, buffers, "¡FELIZ CUMPLEAÑOS!", -26, 1.7F, time, a, 0);
            line(font, pose, buffers, name, 0, 1.6F, time, a, 1);
            line(font, pose, buffers, "¡Que cumplas muchos más!", 23, 0.9F, time, a, 2);
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            buffers.endBatch();
            return true;
        } catch (Throwable t) {
            TFClient.LOGGER.error("TF Cumpleaños: no se pudo dibujar el letrero", t);
            stop();
            return false;
        } finally {
            pose.popPose();
            buffers.endBatch();
            RenderSystem.setShaderFogStart(fogStart);
        }
    }

    /**
     * Una línea centrada, letra a letra: 0 arcoíris que corre y ondea; 1 el nombre, dorado con brillo que pasa y entre
     * dos corazones que laten; 2 blanca suave.
     */
    private static void line(Font font, PoseStack pose, MultiBufferSource buffers, String text, float y, float scale, float time, int alpha, int style) {
        pose.pushPose();
        pose.scale(scale, scale, scale);
        float top = y / scale;
        int[] cps = text.codePoints().toArray();
        Style st = Style.EMPTY.withBold(style != 2);
        float width = font.width(FormattedCharSequence.forward(text, st));
        float x = -width / 2F;
        for (int i = 0; i < cps.length; i++) {
            String ch = new String(Character.toChars(cps[i]));
            float wave = style == 2 ? 0F : (float) Math.sin(time * 0.15 + i * 0.55) * 1.6F;
            int rgb = switch (style) {
                case 0 -> Mth.hsvToRgb(((time * 0.006F) + i * 0.06F) % 1F, 0.6F, 1F);
                case 1 -> Mth.hsvToRgb(0.12F, 0.55F - 0.4F * (float) Math.max(0, Math.sin(time * 0.1 - i * 0.4)), 1F);
                default -> 0xF0E6FF;
            };
            FormattedCharSequence seq = FormattedCharSequence.forward(ch, st);
            font.drawInBatch8xOutline(seq, x, top + wave, alpha | (rgb & 0xFFFFFF), alpha | 0x2A0F1E, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
            x += font.width(seq);
        }
        if (style == 1) {
            float beat = 1F + 0.15F * Math.max(0F, Mth.sin(time * 0.35F));
            heartIcon(pose, buffers, -width / 2F - 14, top - 1, beat, alpha);
            heartIcon(pose, buffers, width / 2F + 5, top - 1, beat, alpha);
        }
        pose.popPose();
    }

    /** Un corazón de la vida de Minecraft (icons.png), de 9x9, con su centro en (x + 4,5, y + 4,5). */
    private static void heartIcon(PoseStack pose, MultiBufferSource buffers, float x, float y, float scale, int alpha) {
        VertexConsumer vc = buffers.getBuffer(RenderType.text(ICONS));
        Matrix4f m = pose.last().pose();
        float cx = x + 4.5F, cy = y + 4.5F, h = 4.5F * scale * 1.3F;
        float u0 = 52 / 256F, u1 = 61 / 256F, v0 = 0F, v1 = 9 / 256F;
        int a = (alpha >>> 24) & 0xFF;
        vc.vertex(m, cx - h, cy - h, 0).color(255, 255, 255, a).uv(u0, v0).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, cx - h, cy + h, 0).color(255, 255, 255, a).uv(u0, v1).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, cx + h, cy + h, 0).color(255, 255, 255, a).uv(u1, v1).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, cx + h, cy - h, 0).color(255, 255, 255, a).uv(u1, v0).uv2(LightTexture.FULL_BRIGHT).endVertex();
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
