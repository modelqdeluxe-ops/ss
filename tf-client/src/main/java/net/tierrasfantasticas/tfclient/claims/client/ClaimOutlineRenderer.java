package net.tierrasfantasticas.tfclient.claims.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
 * El contorno de las protecciones, hecho de polvo mágico del color de la zona (sin tapa arriba). Todo ordenado, nada
 * al azar:
 *
 *  - LÍNEAS (esquinas = columnas verticales, y a ras de suelo a lo largo de cada lado): un punto cada medio bloque,
 *    clavado a la rejilla del mundo (no tiembla al moverse), quieto, opaco y con el color de la zona avivado. Cada
 *    4 puntos (cada 2 bloques) un «nodo» más grande y más blanco. Una onda de brillo recorre las líneas. Se repone
 *    cada LINE_PERIOD ticks con motas que viven un poco más, así la línea se ve continua. De lejos se aclara la rejilla
 *    (1 bloque, y a lo lejos solo los nodos).
 *  - AVISO DE CERCA (a 4 bloques o menos de un lado): en ese trozo de pared, junto al jugador, saltan muchas chispitas
 *    pequeñas del color de la zona, que suben rápido unos 2 bloques y se apagan; cuanto más cerca, más y más vivas.
 *
 * Se ve en los mismos casos que antes: las zonas con «Ver contorno» que manda el servidor y la vista previa al llevar
 * una piedra de protección en la mano (solo líneas, con motas de vida corta para que no dejen estela). Rendimiento:
 * topes fijos de motas por tanda (LINE_CAP, PREVIEW_CAP, SPARKS), solo a menos de 32 bloques, líneas nada
 * detrás de la cámara, y menos aún con «Partículas: reducidas/mínimas».
 */
@Mod.EventBusSubscriber(modid=TFClient.MOD_ID, value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimOutlineRenderer {
    // Hasta dónde se ven las líneas (bloques) y cuánto suben/bajan las columnas alrededor del jugador
    private static final double VIEW_RANGE = 32.0;
    private static final double COLUMN_BELOW = 16.0;
    private static final double COLUMN_ABOVE = 24.0;
    // Si el suelo queda más lejos que esto en vertical (cuevas), la línea de suelo va a la altura del jugador
    private static final double GROUND_SNAP = 12.0;
    // Altura de los puntos de suelo sobre el bloque
    private static final double GROUND_LIFT = 0.12;

    // Líneas: rejilla de medio bloque (índice k = coordenada·2), nodo cada 4 puntos
    private static final double STEP = 0.5;
    private static final int NODE_EVERY = 4;
    // Detalle según la distancia: rejilla completa hasta NEAR_DETAIL, 1 bloque hasta FAR_DETAIL, solo nodos después
    private static final double NEAR_DETAIL = 12.0;
    private static final double FAR_DETAIL = 22.0;
    // Cadencia y vida (ticks): la vida pasa un poco de la cadencia para que la línea no parpadee al reponerse
    private static final int LINE_PERIOD = 8;
    private static final int LINE_LIFE = 11;
    private static final int PREVIEW_PERIOD = 4;
    private static final int PREVIEW_LIFE = 6;
    // Tope de puntos por tanda (con todas las partículas; se reduce con el ajuste)
    private static final int LINE_CAP = 400;
    private static final int PREVIEW_CAP = 200;

    // Aviso de cerca: distancia al lado para que salga, cada cuántos ticks, cuántas chispitas (con todas las partículas),
    // ancho a cada lado del jugador y alto (desde los pies)
    private static final double NEAR_RANGE = 4.0;
    private static final int SPARK_TICKS = 1;
    private static final int SPARKS = 7;
    private static final double SPARK_HALF_WIDTH = 1.6;
    private static final double SPARK_BELOW = 0.1;
    private static final double SPARK_ABOVE = 1.6;
    private static final int MAX_NEAR_WALLS = 2;

    // Listas reutilizadas cada tick
    private static final List<double[]> PREVIEW_BOXES = new ArrayList<double[]>();
    private static final List<double[]> PREVIEW_WALLS = new ArrayList<double[]>();
    private static final List<double[]> PREVIEW_CORNERS = new ArrayList<double[]>();
    private static final List<double[]> EMITTERS = new ArrayList<double[]>();
    private static final List<double[]> NEAR = new ArrayList<double[]>();

    // Tipos de emisor de línea
    private static final int GROUND = 0;
    private static final int COLUMN = 1;

    // Reloj propio (ticks sin pausa): marca las tandas, sin depender de los saltos de hora del servidor
    private static int ticks = 0;

    // Estado de la tanda en curso (solo se usa dentro del tick, en el hilo del cliente)
    private static Minecraft mc;
    private static ClientLevel level;
    private static Vec3 cam;
    private static Vector3f look;
    private static double px;
    private static double py;
    private static double pz;
    private static double nearDetail;
    private static int lineLeft;
    private static int lineLife;
    private static int lineFadeOut;

    private ClaimOutlineRenderer() {
    }

    // Al salir del servidor se olvidan los contornos (que no aparezcan en el siguiente)
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBorderStore.clear();
        mc = null;
        level = null;
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
        ++ticks;
        List<double[]> walls = ClientBorderStore.walls();
        List<double[]> corners = ClientBorderStore.corners();
        // Vista previa de la piedra de protección en la mano (misma zona que antes, sin techo)
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
        if (!MagicDustParticle.spritesReady() || clientlevel.getGameTime() % 20L == 0L) {
            MagicDustParticle.refreshSprites(minecraft);
        }
        if (!MagicDustParticle.spritesReady()) {
            return; // sin las texturas de partículas todavía: nada (una mota sin textura cerraría el juego al dibujarse)
        }
        // Respeta el ajuste de partículas del jugador
        ParticleStatus particlestatus = minecraft.options.particles().get();
        float f = particlestatus == ParticleStatus.MINIMAL ? 0.3f : (particlestatus == ParticleStatus.DECREASED ? 0.6f : 1.0f);
        mc = minecraft;
        level = clientlevel;
        cam = minecraft.gameRenderer.getMainCamera().getPosition();
        look = minecraft.gameRenderer.getMainCamera().getLookVector();
        px = localplayer.getX();
        py = localplayer.getY();
        pz = localplayer.getZ();
        // Con partículas mínimas no hay rejilla de medio bloque; con reducidas solo muy cerca
        nearDetail = particlestatus == ParticleStatus.MINIMAL ? 0.0 : (particlestatus == ParticleStatus.DECREASED ? NEAR_DETAIL * 0.5 : NEAR_DETAIL);

        // 1) Líneas de las zonas (esquinas y suelo)
        if (!walls.isEmpty() && ticks % LINE_PERIOD == 0) {
            EMITTERS.clear();
            ClaimOutlineRenderer.collectLines(walls, corners);
            ClaimOutlineRenderer.spawnLines(Math.round(LINE_CAP * f), LINE_LIFE, 2);
        }
        // 1b) Vista previa: misma línea, repuesta más a menudo y con vida corta (se mueve con el jugador)
        if (!PREVIEW_WALLS.isEmpty() && ticks % PREVIEW_PERIOD == 0) {
            EMITTERS.clear();
            ClaimOutlineRenderer.collectLines(PREVIEW_WALLS, PREVIEW_CORNERS);
            ClaimOutlineRenderer.spawnLines(Math.round(PREVIEW_CAP * f), PREVIEW_LIFE, 1);
        }

        // 2) Chispitas que saltan en la pared que el jugador tiene al lado (solo las de verdad): aviso de que está cerca
        if (!walls.isEmpty() && ticks % SPARK_TICKS == 0) {
            NEAR.clear();
            for (double[] wall : walls) {
                boolean alongX = wall[0] == 0.0;
                double proj = alongX ? px : pz;
                double dist = Math.abs((alongX ? pz : px) - wall[1]);
                if (dist > NEAR_RANGE || proj < wall[2] - 1.0 || proj > wall[3] + 1.0 || py < wall[4] - 2.0 || py > wall[5]) continue;
                NEAR.add(new double[]{dist, proj, wall[0], wall[1], wall[2], wall[3], wall[4], wall[5], wall[6], wall[7], wall[8]});
            }
            if (!NEAR.isEmpty()) {
                NEAR.sort((a, b) -> Double.compare(a[0], b[0]));
                for (int i = 0; i < Math.min(MAX_NEAR_WALLS, NEAR.size()); ++i) {
                    ClaimOutlineRenderer.spawnSparks(NEAR.get(i), particlestatus);
                }
            }
        }
    }

    /**
     * Mete en EMITTERS los trozos de línea a la vista, ordenados del más cercano al más lejano:
     * suelo {distancia, GROUND, eje, fija, u0, u1, abajo, arriba, r, g, b} y columna {distancia, COLUMN, x, z, y0, y1, -, -, r, g, b}.
     */
    private static void collectLines(List<double[]> walls, List<double[]> corners) {
        for (double[] wall : walls) {
            boolean alongX = wall[0] == 0.0;
            double d0 = Math.abs((alongX ? pz : px) - wall[1]);
            if (d0 > VIEW_RANGE) continue;
            double half = Math.sqrt(VIEW_RANGE * VIEW_RANGE - d0 * d0);
            double proj = alongX ? px : pz;
            double ua = Math.max(wall[2], proj - half);
            double ub = Math.min(wall[3], proj + half);
            if (ub < ua) continue;
            double du = proj < ua ? ua - proj : (proj > ub ? proj - ub : 0.0);
            EMITTERS.add(new double[]{Math.sqrt(d0 * d0 + du * du), GROUND, wall[0], wall[1], ua, ub, wall[4], wall[5], wall[6], wall[7], wall[8]});
        }
        for (double[] corner : corners) {
            double dx = corner[0] - px;
            double dz = corner[1] - pz;
            if (dx * dx + dz * dz > VIEW_RANGE * VIEW_RANGE) continue;
            double y0 = Math.max(corner[2], py - COLUMN_BELOW);
            double y1 = Math.min(corner[3], py + COLUMN_ABOVE);
            if (y1 < y0) continue;
            EMITTERS.add(new double[]{Math.sqrt(dx * dx + dz * dz), COLUMN, corner[0], corner[1], y0, y1, 0.0, 0.0, corner[4], corner[5], corner[6]});
        }
        EMITTERS.sort((a, b) -> Double.compare(a[0], b[0]));
    }

    /** Una tanda de puntos de línea: de cada trozo, desde el punto más cercano al jugador hacia fuera, hasta el tope. */
    private static void spawnLines(int cap, int life, int fadeOut) {
        lineLeft = Math.max(1, cap);
        lineLife = life;
        lineFadeOut = fadeOut;
        for (double[] em : EMITTERS) {
            float[] rgb = ClaimOutlineRenderer.vivid(em[8], em[9], em[10]);
            // Índices de la rejilla de medio bloque dentro del trozo, y el más cercano al jugador
            int ka = Mth.ceil(em[4] / STEP - 1.0E-6);
            int kb = Mth.floor(em[5] / STEP + 1.0E-6);
            if (kb < ka) continue;
            double center = em[1] == GROUND ? (em[2] == 0.0 ? px : pz) : py;
            int kc = Mth.clamp((int)Math.round(center / STEP), ka, kb);
            for (int s = 0; ; ++s) {
                boolean any = false;
                if (kc + s <= kb) {
                    any = true;
                    if (!ClaimOutlineRenderer.linePoint(em, kc + s, rgb)) return;
                }
                if (s > 0 && kc - s >= ka) {
                    any = true;
                    if (!ClaimOutlineRenderer.linePoint(em, kc - s, rgb)) return;
                }
                if (!any) break;
            }
        }
    }

    /** Pone (o no) el punto k de una línea. Devuelve false cuando se acaba el tope de la tanda. */
    private static boolean linePoint(double[] em, int k, float[] rgb) {
        double c = k * STEP;
        double x;
        double y;
        double z;
        if (em[1] == GROUND) {
            boolean alongX = em[2] == 0.0;
            x = alongX ? c : em[3];
            z = alongX ? em[3] : c;
            // A ras de suelo (el más alto de los dos bloques que tocan el límite; MOTION_BLOCKING es el mapa que tiene el cliente)
            int i = Mth.floor(x);
            int j = Mth.floor(z);
            int top = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING, i, j),
                    alongX ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, i, j - 1) : level.getHeight(Heightmap.Types.MOTION_BLOCKING, i - 1, j));
            double ground = Math.abs(top - py) > GROUND_SNAP ? Mth.floor(py) : top;
            y = Mth.clamp(ground, em[6], em[7]) + GROUND_LIFT;
        } else {
            x = em[2];
            z = em[3];
            y = c;
        }
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > VIEW_RANGE) {
            return true;
        }
        // De lejos la rejilla se aclara (siempre un subconjunto de la de cerca, así no salta)
        if (d > FAR_DETAIL ? Math.floorMod(k, NODE_EVERY) != 0 : (d > nearDetail && Math.floorMod(k, 2) != 0)) {
            return true;
        }
        // Nada detrás de la cámara (salvo lo que está pegado)
        double cx = x - cam.x;
        double cy = y - cam.y;
        double cz = z - cam.z;
        if (cx * look.x() + cy * look.y() + cz * look.z() < 0.0 && cx * cx + cy * cy + cz * cz > 9.0) {
            return true;
        }
        boolean node = Math.floorMod(k, NODE_EVERY) == 0;
        MagicDustParticle particle = MagicDustParticle.point(level, x, y, z, rgb, node, (float)c, lineLife, 1, lineFadeOut);
        if (particle == null) {
            return false; // sin textura: se corta la tanda
        }
        mc.particleEngine.add(particle);
        return --lineLeft > 0;
    }

    /**
     * Chispitas sueltas en el trozo de pared junto al jugador: aparecen en sitios al azar a su alrededor, saltan un poco
     * hacia arriba y se apagan rápido, parpadeando; cuantas más y más vivas, más cerca del límite.
     * near = {distancia, proyección, eje, fija, u0, u1, abajo, arriba, r, g, b}
     */
    private static void spawnSparks(double[] near, ParticleStatus particlestatus) {
        double closeness = 1.0 - near[0] / NEAR_RANGE;
        boolean alongX = near[2] == 0.0;
        float f = particlestatus == ParticleStatus.MINIMAL ? 0.35f : (particlestatus == ParticleStatus.DECREASED ? 0.65f : 1.0f);
        int n = Math.max(1, Math.round(SPARKS * f * (float)(0.4 + 0.6 * closeness)));
        double lo = Math.max(near[4], near[1] - SPARK_HALF_WIDTH);
        double hi = Math.min(near[5], near[1] + SPARK_HALF_WIDTH);
        double vb = Math.max(near[6], py - SPARK_BELOW);
        double vt = Math.min(near[7], py + SPARK_ABOVE);
        if (hi <= lo || vt <= vb) {
            return;
        }
        float[] rgb = ClaimOutlineRenderer.vivid(near[8], near[9], near[10]);
        float alpha = (float)(0.7 + 0.3 * closeness);
        int axis = alongX ? 0 : 1;
        for (int i = 0; i < n; ++i) {
            double u = lo + level.random.nextDouble() * (hi - lo);
            double v = vb + level.random.nextDouble() * (vt - vb);
            double x = alongX ? u : near[3];
            double z = alongX ? near[3] : u;
            // salta hacia arriba (y un pelín hacia fuera de la pared, hacia el jugador) y se apaga
            double out = Math.signum((alongX ? pz : px) - near[3]) * 0.01;
            boolean big = level.random.nextFloat() < 0.3f;
            // diminutas y tranquilas: salen despacio, frenan poco a poco y suben un bloque y algo en lo que viven
            MagicDustParticle particle = MagicDustParticle.spark(level, x, v, z, alongX ? 0.0 : out, 0.042 + level.random.nextDouble() * 0.018,
                    alongX ? out : 0.0, rgb, big, big ? 0.5f : 0.15f, alpha, big ? 0.032f : 0.022f, 28 + level.random.nextInt(10), axis, near[4], near[5]);
            if (particle != null) {
                mc.particleEngine.add(particle);
            }
        }
    }

    /**
     * El color de la zona avivado para que destaque: se sube al máximo brillo y se satura más (los colores pálidos
     * dejan de verse lavados). Los grises se quedan casi blancos.
     */
    static float[] vivid(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        if (max < 0.02) {
            return new float[]{0.9f, 0.9f, 0.9f};
        }
        double rr = r / max;
        double gg = g / max;
        double bb = b / max;
        if (max - min > 0.08 * max) {
            rr = Mth.clamp(1.0 - (1.0 - rr) * 1.5, 0.0, 1.0);
            gg = Mth.clamp(1.0 - (1.0 - gg) * 1.5, 0.0, 1.0);
            bb = Mth.clamp(1.0 - (1.0 - bb) * 1.5, 0.0, 1.0);
        }
        return new float[]{(float)rr, (float)gg, (float)bb};
    }
}
