/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.items;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * (1.3.173, el dueño: «la maza no hace ni tiene el sonido ni la animación de la maza vanilla») Lo que se ve y se oye del
 * golpe aplastante, como en la 1.21:
 * <ul>
 *   <li><b>El sonido</b>: el de la maza de la 1.21 (golpe en el suelo, golpe fuerte si se caía más de 5 bloques, golpe
 *   en el aire) y el de la Ráfaga de viento. La 1.20.1 no los trae y los archivos son de Mojang (no se meten en el mod):
 *   cada TF Client los baja de los servidores de Mojang (resources.download.minecraft.net, como el lanzador, con su
 *   huella SHA-1 comprobada) la primera vez que arranca, los guarda en &lt;juego&gt;/tfclient/sonidos_minecraft y los sirve
 *   como un paquete de recursos fijo («TF Client: sonidos de la maza»). Si no se pudieron bajar, suena lo de antes
 *   (yunque y explosión).</li>
 *   <li><b>La animación</b>: la explosión de polvo del bloque del suelo de la 1.21 (ParticleUtils.spawnSmashAttackParticles
 *   con 750: 250 partículas que saltan en el centro y 500 en un anillo de 3,5 bloques; cada una, como DUST_PILLAR: sube
 *   con fuerza, casi sin moverse de lado, y dura 20-40 ticks). Respeta el ajuste de partículas.</li>
 * </ul>
 */
public final class TFMaceClient {
    private TFMaceClient() {}

    /** Los sonidos de la 1.21 que se bajan: ruta dentro de assets/minecraft/sounds y su huella (índice de assets 17). */
    private record Asset(String path, String sha1) {}

    private static final Asset[] ASSETS = {
            new Asset("item/mace/smash_air1.ogg", "dd201cdf56f5e3360380c2cdc3a21cc5c12ecfa6"),
            new Asset("item/mace/smash_air2.ogg", "a898059fce9481ea07fffd8dcab80b6087d684e7"),
            new Asset("item/mace/smash_air3.ogg", "54dcde12a230e188c268653a1def01aa2f63a124"),
            new Asset("item/mace/smash_ground1.ogg", "88efd3b53d45f8cc6ae2ab39a414aa20d9760c46"),
            new Asset("item/mace/smash_ground2.ogg", "df4382620752d04c27e3f33be08bacc2fc89078c"),
            new Asset("item/mace/smash_ground3.ogg", "d761b39e2ae68753d68a3611c3d8efbf213e669a"),
            new Asset("item/mace/smash_ground4.ogg", "42409a52c41c8dee98159236a4fd98813f29bef6"),
            new Asset("item/mace/smash_ground_heavy.ogg", "6ffcfff22efc6ba726ed80d7891b1c6e707bed60"),
            new Asset("entity/wind_charge/wind_burst1.ogg", "b64e99d3d09d12c97242f1d6336a4aea4c3233ec"),
            new Asset("entity/wind_charge/wind_burst2.ogg", "c07f8dcd5ec017c90a863a48a52ca2e7b3f0544e"),
            new Asset("entity/wind_charge/wind_burst3.ogg", "eb0abf982987a1b0f79a06fb45afbe880e275d17")};

    /** ¿Están los sonidos de la maza (bajados y comprobados)? */
    public static volatile boolean available;
    /** Para la prueba en el juego: cuántos golpes aplastantes llegaron y cuántas partículas salieron con el último. */
    public static volatile int testSmashes, testParticles;
    public static volatile String testSound = "";

    // -----------------------------------------------------------------------------------------------------------------
    // Los sonidos: bajarlos (una vez) y el paquete de recursos
    // -----------------------------------------------------------------------------------------------------------------

    private static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("tfclient").resolve("sonidos_minecraft");
    }

    /** Los que faltan, de Mojang (como mucho 15 s en total la primera vez); true si están todos. */
    static boolean ensure(Path root) {
        long deadline = System.currentTimeMillis() + 15_000;
        boolean all = true;
        for (Asset a : ASSETS) {
            Path file = root.resolve("assets/minecraft/sounds").resolve(a.path());
            try {
                if (Files.isRegularFile(file) && a.sha1().equals(sha1(Files.readAllBytes(file)))) continue;
                if (System.currentTimeMillis() > deadline) {
                    all = false;
                    continue;
                }
                byte[] data = download("https://resources.download.minecraft.net/" + a.sha1().substring(0, 2) + "/" + a.sha1());
                if (!a.sha1().equals(sha1(data))) throw new java.io.IOException("la huella no coincide");
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(tmp, data);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                all = false;
                TFClient.LOGGER.warn("TF Client: no se pudo bajar el sonido de la maza {} de Mojang: {}", a.path(), e.toString());
            }
        }
        try {
            Path meta = root.resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.createDirectories(root);
                Files.writeString(meta, "{\"pack\":{\"pack_format\":15,\"description\":\"TF Client: sonidos de la maza (de Minecraft 1.21)\"}}",
                        StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            TFClient.LOGGER.warn("TF Client: no se pudo preparar el paquete de los sonidos de la maza: {}", e.toString());
        }
        return all;
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(6000);
        c.setRequestProperty("User-Agent", "TF-Client/" + TFClient.VERSION);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            return in.readNBytes(512 * 1024);
        } finally {
            c.disconnect();
        }
    }

    private static String sha1(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(data);
        StringBuilder b = new StringBuilder();
        for (byte x : d) b.append(String.format("%02x", x));
        return b.toString();
    }

    @Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        /** Antes de cargar los recursos: los sonidos (si faltan) y su paquete, siempre puesto y arriba del todo. */
        @SubscribeEvent
        public static void onPacks(AddPackFindersEvent event) {
            if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
            Path root = root();
            available = ensure(root);
            TFClient.LOGGER.info("TF Client: sonidos de la maza de la 1.21 {}", available ? "listos" : "sin bajar (suenan los de antes)");
            event.addRepositorySource(consumer -> {
                Pack pack = Pack.readMetaAndCreate("tfclient_sonidos_maza", Component.literal("TF Client: sonidos de la maza"), true,
                        id -> new PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
                if (pack != null) consumer.accept(pack);
            });
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // El golpe aplastante
    // -----------------------------------------------------------------------------------------------------------------

    private static SoundEvent ours(String name) {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(TFClient.MOD_ID, name));
    }

    static void smash(TFMaceNet.Smash m) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        testSmashes++;
        // el sonido, como la 1.21: desde quien pega, volumen 1 y tono 1
        if (available) {
            String ev = !m.onGround() ? "maza.golpe_aire" : m.heavy() ? "maza.golpe_suelo_fuerte" : "maza.golpe_suelo";
            level.playLocalSound(m.x(), m.y(), m.z(), ours(ev), SoundSource.PLAYERS, 1.0F, 1.0F, false);
            testSound = ev;
        } else if (m.onGround()) {
            level.playLocalSound(m.x(), m.y(), m.z(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, m.heavy() ? 0.8F : 0.6F, m.heavy() ? 0.75F : 1.05F, false);
            if (m.heavy()) level.playLocalSound(m.x(), m.y(), m.z(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.55F, 1.35F, false);
            testSound = "yunque (sin los sonidos de la 1.21)";
        } else {
            level.playLocalSound(m.x(), m.y(), m.z(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F, false);
            testSound = "empuje (sin los sonidos de la 1.21)";
        }
        // la explosión de polvo del suelo
        BlockState state = level.getBlockState(m.ground());
        testParticles = state.isAir() ? 0 : dust(mc, level, m.ground(), state, 750);
        // la Ráfaga de viento
        if (m.windBurst() > 0) {
            if (available) level.playLocalSound(m.x(), m.y(), m.z(), ours("maza.rafaga"), SoundSource.PLAYERS, 1.0F, 1.0F, false);
            else level.playLocalSound(m.x(), m.y(), m.z(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0F, 0.6F, false);
            RandomSource r = level.random;
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                level.addParticle(ParticleTypes.CLOUD, m.x() + Math.cos(a) * 0.6, m.y() + 0.1, m.z() + Math.sin(a) * 0.6,
                        Math.cos(a) * 0.25, 0.05 + r.nextDouble() * 0.05, Math.sin(a) * 0.25);
            }
        }
    }

    /**
     * ParticleUtils.spawnSmashAttackParticles de la 1.21 con su DUST_PILLAR (TerrainParticle.DustPillarProvider): power/3
     * en el centro y power/1,5 en un anillo de 3,5 bloques; cada una sube con fuerza (su velocidad + gauss/2), casi sin
     * moverse de lado (gauss/30) y dura 20-40 ticks. Con partículas «reducidas», una de cada tres; con «mínimas», una de
     * cada diez. Devuelve cuántas salieron.
     */
    static int dust(Minecraft mc, ClientLevel level, BlockPos pos, BlockState state, int power) {
        Vec3 c = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(c) > 64 * 64) return 0;
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, state);
        RandomSource r = level.random;
        int keep = switch (mc.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 3;
            case MINIMAL -> 10;
        };
        int made = 0;
        for (int i = 0; i < power / 3.0F; i++) {
            double x = c.x + r.nextGaussian() / 2.0, z = c.z + r.nextGaussian() / 2.0, ys = r.nextGaussian() * 0.2F;
            if (i % keep == 0 && pillar(mc, r, opt, x, c.y, z, ys)) made++;
        }
        for (int j = 0; j < power / 1.5F; j++) {
            double x = c.x + 3.5 * Math.cos(j) + r.nextGaussian() / 2.0, z = c.z + 3.5 * Math.sin(j) + r.nextGaussian() / 2.0;
            double ys = r.nextGaussian() * 0.05F;
            if (j % keep == 0 && pillar(mc, r, opt, x, c.y, z, ys)) made++;
        }
        return made;
    }

    private static boolean pillar(Minecraft mc, RandomSource r, BlockParticleOption opt, double x, double y, double z, double ySpeed) {
        Particle p = mc.particleEngine.createParticle(opt, x, y, z, 0.0, ySpeed, 0.0);
        if (p == null) return false;
        p.setParticleSpeed(r.nextGaussian() / 30.0, ySpeed + r.nextGaussian() / 2.0, r.nextGaussian() / 30.0);
        p.setLifetime(r.nextInt(20) + 20);
        return true;
    }
}
