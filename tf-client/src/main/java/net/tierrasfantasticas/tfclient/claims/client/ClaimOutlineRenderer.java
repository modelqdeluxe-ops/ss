package net.tierrasfantasticas.tfclient.claims.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import org.joml.Vector3f;

/**
 * El contorno de las protecciones, hecho de polvo mágico del color de la zona (antes eran líneas finas formando una
 * caja con un cuadrado arriba; ya no hay tapa).
 *
 *  - Lejos del límite: líneas suaves de polvo que titila, en las esquinas (columnas verticales) y a ras de suelo a lo
 *    largo de cada lado. Solo cerca del jugador.
 *  - Pegado al límite (a 5 bloques o menos de un lado): ese trozo de pared se llena de una cortina de polvillo que
 *    parpadea y sube en diagonal, para ver bien dónde acaba la zona.
 *
 * Se ve en los mismos casos que antes: las zonas con «Ver contorno» que manda el servidor y la vista previa al llevar
 * una piedra de protección en la mano. Rendimiento: un tope fijo de motas por tick (ver LINE_BUDGET y CURTAIN_BUDGET),
 * solo a menos de 32 bloques, nada detrás de la cámara, y menos aún con «Partículas: reducidas/mínimas».
 */
@Mod.EventBusSubscriber(modid=TFClient.MOD_ID, value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimOutlineRenderer {
    // Hasta dónde se ven las líneas (bloques, en planta) y cuánto suben/bajan las columnas alrededor del jugador
    private static final double VIEW_RANGE = 32.0;
    private static final double COLUMN_BELOW = 16.0;
    private static final double COLUMN_ABOVE = 24.0;
    // Si el suelo queda más lejos que esto en vertical (cuevas), la línea de suelo va a la altura del jugador
    private static final double GROUND_SNAP = 12.0;
    // Cortina: distancia al lado para que salga, ancho a cada lado del jugador y alto (desde los pies)
    private static final double NEAR_RANGE = 5.0;
    private static final double CURTAIN_HALF_WIDTH = 6.0;
    private static final double CURTAIN_BELOW = 1.5;
    private static final double CURTAIN_ABOVE = 4.5;
    // Separación entre las franjas diagonales y lo rápido que se deslizan (bloques por tick)
    private static final double BAND_SPACING = 1.6;
    private static final double BAND_DRIFT = 0.04;
    private static final double CURTAIN_SPEED = 0.045;
    // Tope de motas por tick: líneas (todas las zonas juntas) y cortina (por pared cercana, máx. 2 paredes)
    private static final int LINE_BUDGET = 14;
    private static final int CURTAIN_BUDGET = 10;
    private static final int MAX_NEAR_WALLS = 2;

    // Listas reutilizadas cada tick (sin basura)
    private static final List<double[]> PREVIEW_BOXES = new ArrayList<double[]>();
    private static final List<double[]> PREVIEW_WALLS = new ArrayList<double[]>();
    private static final List<double[]> PREVIEW_CORNERS = new ArrayList<double[]>();
    private static final List<double[]> EMITTERS = new ArrayList<double[]>();
    private static final List<double[]> NEAR = new ArrayList<double[]>();

    // Tipos de emisor de línea
    private static final int GROUND = 0;
    private static final int COLUMN = 1;

    private ClaimOutlineRenderer() {
    }

    // Al salir del servidor se olvidan los contornos (que no aparezcan en el siguiente)
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBorderStore.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localplayer = minecraft.player;
        ClientLevel clientlevel = minecraft.level;
        if (localplayer == null || clientlevel == null || minecraft.isPaused()) {
            return;
        }
        List<double[]> walls = ClientBorderStore.walls();
        List<double[]> corners = ClientBorderStore.corners();
        // Vista previa de la piedra de protección en la mano (misma zona que antes, ahora sin techo)
        PREVIEW_WALLS.clear();
        PREVIEW_CORNERS.clear();
        ClaimTier claimtier = ClaimBlocks.readTier(localplayer.getMainHandItem());
        if (claimtier == null) {
            claimtier = ClaimBlocks.readTier(localplayer.getOffhandItem());
        }
        if (claimtier != null) {
            BlockPos blockpos = localplayer.blockPosition();
            PREVIEW_BOXES.clear();
            PREVIEW_BOXES.add(new double[]{blockpos.getX() - claimtier.radius, blockpos.getY() - claimtier.height, blockpos.getZ() - claimtier.radius,
                    blockpos.getX() + claimtier.radius + 1, clientlevel.getMaxBuildHeight(), blockpos.getZ() + claimtier.radius + 1,
                    claimtier.r, claimtier.g, claimtier.b});
            ClientBorderStore.toWalls(PREVIEW_BOXES, PREVIEW_WALLS, PREVIEW_CORNERS);
        }
        if (walls.isEmpty() && PREVIEW_WALLS.isEmpty()) {
            return;
        }
        // Respeta el ajuste de partículas del jugador
        ParticleStatus particlestatus = minecraft.options.particles().get();
        float f = particlestatus == ParticleStatus.MINIMAL ? 0.3f : (particlestatus == ParticleStatus.DECREASED ? 0.6f : 1.0f);
        if (!MagicDustParticle.spritesReady() || clientlevel.getGameTime() % 20L == 0L) {
            MagicDustParticle.refreshSprites(minecraft);
        }
        Vec3 vec3 = minecraft.gameRenderer.getMainCamera().getPosition();
        Vector3f look = minecraft.gameRenderer.getMainCamera().getLookVector();
        double px = localplayer.getX();
        double py = localplayer.getY();
        double pz = localplayer.getZ();
        RandomSource randomsource = clientlevel.getRandom();

        // 1) Líneas de polvo (esquinas y suelo), repartidas por longitud visible
        EMITTERS.clear();
        ClaimOutlineRenderer.collectLines(walls, corners, px, py, pz, false);
        ClaimOutlineRenderer.collectLines(PREVIEW_WALLS, PREVIEW_CORNERS, px, py, pz, true);
        int i = Math.max(1, Math.round(LINE_BUDGET * f));
        double d0 = 0.0;
        for (double[] adouble : EMITTERS) {
            d0 += adouble[0];
        }
        if (d0 > 0.0) {
            for (int j = 0; j < i; ++j) {
                double d1 = randomsource.nextDouble() * d0;
                double[] adouble1 = EMITTERS.get(EMITTERS.size() - 1);
                for (double[] adouble2 : EMITTERS) {
                    d1 -= adouble2[0];
                    if (d1 > 0.0) continue;
                    adouble1 = adouble2;
                    break;
                }
                ClaimOutlineRenderer.spawnLine(minecraft, clientlevel, randomsource, adouble1, py, vec3, look);
            }
        }

        // 2) Cortina diagonal que parpadea en las paredes que el jugador tiene al lado (solo las de verdad, no la vista previa)
        NEAR.clear();
        for (double[] adouble3 : walls) {
            boolean flag = adouble3[0] == 0.0;
            double d2 = flag ? px : pz;
            double d3 = Math.abs((flag ? pz : px) - adouble3[1]);
            if (d3 > NEAR_RANGE || d2 < adouble3[2] - 1.0 || d2 > adouble3[3] + 1.0 || py < adouble3[4] - 2.0 || py > adouble3[5]) continue;
            NEAR.add(new double[]{d3, d2, adouble3[0], adouble3[1], adouble3[2], adouble3[3], adouble3[4], adouble3[5], adouble3[6], adouble3[7], adouble3[8]});
        }
        if (!NEAR.isEmpty()) {
            NEAR.sort((a, b) -> Double.compare(a[0], b[0]));
            double phase = (clientlevel.getGameTime() * BAND_DRIFT) % BAND_SPACING;
            for (int k = 0; k < Math.min(MAX_NEAR_WALLS, NEAR.size()); ++k) {
                ClaimOutlineRenderer.spawnCurtain(minecraft, clientlevel, randomsource, NEAR.get(k), py, phase, f);
            }
        }
    }

    /** Mete en EMITTERS los trozos de línea a la vista: {peso, tipo, ...}. */
    private static void collectLines(List<double[]> walls, List<double[]> corners, double px, double py, double pz, boolean preview) {
        double p = preview ? 1.0 : 0.0;
        for (double[] adouble : walls) {
            boolean flag = adouble[0] == 0.0;
            double d0 = Math.abs((flag ? pz : px) - adouble[1]);
            if (d0 > VIEW_RANGE) continue;
            double d1 = Math.sqrt(VIEW_RANGE * VIEW_RANGE - d0 * d0);
            double d2 = flag ? px : pz;
            double d3 = Math.max(adouble[2], d2 - d1);
            double d4 = Math.min(adouble[3], d2 + d1);
            if (d4 <= d3) continue;
            // {peso, tipo, eje, fija, u0, u1, abajo, arriba, r, g, b, vista previa}
            EMITTERS.add(new double[]{d4 - d3, GROUND, adouble[0], adouble[1], d3, d4, adouble[4], adouble[5], adouble[6], adouble[7], adouble[8], p});
        }
        for (double[] adouble1 : corners) {
            double d5 = adouble1[0] - px;
            double d6 = adouble1[1] - pz;
            if (d5 * d5 + d6 * d6 > VIEW_RANGE * VIEW_RANGE) continue;
            double d7 = Math.max(adouble1[2], py - COLUMN_BELOW);
            double d8 = Math.min(adouble1[3], py + COLUMN_ABOVE);
            if (d8 <= d7) continue;
            // Las columnas pesan algo menos que el suelo para no comerse todo el presupuesto
            // {peso, tipo, x, z, y0, y1, -, -, r, g, b, vista previa}
            EMITTERS.add(new double[]{(d8 - d7) * 0.6, COLUMN, adouble1[0], adouble1[1], d7, d8, 0.0, 0.0, adouble1[4], adouble1[5], adouble1[6], p});
        }
    }

    private static void spawnLine(Minecraft minecraft, ClientLevel clientlevel, RandomSource randomsource, double[] adouble, double py, Vec3 cam, Vector3f look) {
        double x;
        double y;
        double z;
        double vx = 0.0;
        double vz = 0.0;
        if (adouble[1] == GROUND) {
            boolean flag = adouble[2] == 0.0;
            double u = adouble[4] + randomsource.nextDouble() * (adouble[5] - adouble[4]);
            x = flag ? u : adouble[3];
            z = flag ? adouble[3] : u;
            // A ras de suelo (el más alto de los dos bloques que tocan el límite; MOTION_BLOCKING es el mapa que tiene el cliente)
            int i = Mth.floor(x);
            int j = Mth.floor(z);
            int k = Math.max(clientlevel.getHeight(Heightmap.Types.MOTION_BLOCKING, i, j),
                    flag ? clientlevel.getHeight(Heightmap.Types.MOTION_BLOCKING, i, j - 1) : clientlevel.getHeight(Heightmap.Types.MOTION_BLOCKING, i - 1, j));
            y = Math.abs(k - py) > GROUND_SNAP ? py : k;
            y = Mth.clamp(y, adouble[6], adouble[7]) + 0.08 + randomsource.nextDouble() * 0.18;
            // Deriva muy lenta a lo largo de la línea
            double d0 = (randomsource.nextDouble() - 0.5) * 0.012;
            vx = flag ? d0 : 0.0;
            vz = flag ? 0.0 : d0;
        } else {
            x = adouble[2];
            z = adouble[3];
            y = adouble[4] + randomsource.nextDouble() * (adouble[5] - adouble[4]);
        }
        // Nada detrás de la cámara (salvo lo que está pegado)
        double d1 = x - cam.x;
        double d2 = y - cam.y;
        double d3 = z - cam.z;
        if (d1 * look.x() + d2 * look.y() + d3 * look.z() < 0.0 && d1 * d1 + d2 * d2 + d3 * d3 > 9.0) {
            return;
        }
        boolean preview = adouble[11] != 0.0;
        // La vista previa se mueve con el jugador: vive menos para no dejar estela
        int life = preview ? 10 + randomsource.nextInt(5) : 22 + randomsource.nextInt(13);
        float[] col = ClaimOutlineRenderer.tint(randomsource, adouble[8], adouble[9], adouble[10], 0.25f);
        float size = 0.07f + randomsource.nextFloat() * 0.05f;
        minecraft.particleEngine.add(new MagicDustParticle(clientlevel, x, y, z, vx, 0.004 + randomsource.nextDouble() * 0.008, vz,
                col[0], col[1], col[2], false, randomsource.nextFloat() < 0.75f, life, size, 0.85f));
    }

    /**
     * Cortina de polvillo en el trozo de pared junto al jugador: las motas salen sobre franjas diagonales (45°) que se
     * deslizan poco a poco y cada mota sube en diagonal por su franja; el pulso común las hace parpadear. Cuanto más
     * cerca del límite, más densa y más viva.
     * near = {distancia, proyección, eje, fija, u0, u1, abajo, arriba, r, g, b}
     */
    private static void spawnCurtain(Minecraft minecraft, ClientLevel clientlevel, RandomSource randomsource, double[] near, double py, double phase, float f) {
        double closeness = 1.0 - near[0] / NEAR_RANGE;
        int i = Math.round((float)(CURTAIN_BUDGET * f * (0.35 + 0.65 * closeness)));
        double ua = Math.max(near[4], near[1] - CURTAIN_HALF_WIDTH);
        double ub = Math.min(near[5], near[1] + CURTAIN_HALF_WIDTH);
        double va = Math.max(near[6], py - CURTAIN_BELOW);
        double vb = Math.min(near[7], py + CURTAIN_ABOVE);
        if (ub <= ua || vb <= va || i <= 0) {
            return;
        }
        boolean flag = near[2] == 0.0;
        // Sentido de la diagonal alternando cada pocos segundos, para que no parezca siempre igual
        double dir = (clientlevel.getGameTime() / 200L) % 2L == 0L ? 1.0 : -1.0;
        float alpha = (float)(0.6 + 0.4 * closeness);
        for (int j = 0; j < i; ++j) {
            double v = va + randomsource.nextDouble() * (vb - va);
            double u = ua + randomsource.nextDouble() * (ub - ua);
            // Lleva la mota a la franja diagonal más cercana (u - dir·v = fase + n·separación)
            double d0 = Mth.positiveModulo(u - dir * v - phase, BAND_SPACING);
            if (d0 > BAND_SPACING / 2.0) {
                d0 -= BAND_SPACING;
            }
            u = u - d0 + randomsource.nextGaussian() * 0.06;
            if (u < ua || u > ub) continue;
            double x = flag ? u : near[3];
            double z = flag ? near[3] : u;
            double d1 = dir * CURTAIN_SPEED;
            float[] col = ClaimOutlineRenderer.tint(randomsource, near[8], near[9], near[10], 0.35f);
            float size = 0.06f + randomsource.nextFloat() * 0.06f;
            minecraft.particleEngine.add(new MagicDustParticle(clientlevel, x, v, z, flag ? d1 : 0.0, CURTAIN_SPEED, flag ? 0.0 : d1,
                    col[0], col[1], col[2], true, randomsource.nextFloat() < 0.6f, 18 + randomsource.nextInt(7), size, alpha));
        }
    }

    // El color de la zona con un toque de blanco al azar (destellos más claros, como polvo mágico)
    private static float[] tint(RandomSource randomsource, double r, double g, double b, float maxWhite) {
        float f = randomsource.nextFloat() * maxWhite;
        return new float[]{(float)(r + (1.0 - r) * f), (float)(g + (1.0 - g) * f), (float)(b + (1.0 - b) * f)};
    }
}
