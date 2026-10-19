package net.tierrasfantasticas.tfclient.vacio;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * La isla de The Void: la del mapa del dueño (EndCarageKitMap), con el pozo del centro relleno y la plaza de la grieta
 * del Void encima, y el santuario del caballero en la punta norte (1.3.89; tools/void/isla.py la convierte a
 * {@code tfclient-void-isla.bin}). Se carga una vez (en el
 * primer chunk que se genere) y {@link VoidIslandGenerator} copia en cada chunk lo que le toca: así no se pega nada de
 * golpe en el servidor y cada chunk nace ya con su trozo de isla.
 *
 * <p>Formato (gzip, big-endian): «TFVI», versión, tamaño (x, y, z), dónde va su esquina en el mundo, dónde está el
 * caballero, dónde se llega (x, y, z, yaw), dónde va la grieta del Void (desde la versión 2), el Velo del Alba (centro y
 * radio de la burbuja del santuario), el Corazón del Alba y los sitios de los fragmentos de luz (desde la versión 3,
 * 1.3.96: tools/void/isla_alba.py), la paleta (estados como en /setblock) y, por columna (z por fuera, x por dentro), sus
 * tramos: (y, largo, bloque).
 */
public final class VoidIsland {
    private static volatile VoidIsland loaded;
    private static volatile boolean failed;

    final int sx, sy, sz, ox, oy, oz;
    final BlockPos knight;
    final Vec3 arrive;
    final float arriveYaw;
    final BlockPos rift;
    /** (v3) El Velo del Alba: centro y radio de la burbuja del santuario; el Corazón del Alba; los fragmentos de luz. */
    final Vec3 bubble;
    final float bubbleR;
    final Vec3 heart;
    final java.util.List<BlockPos> shards;
    final BlockState[] palette;
    /** Por columna: dónde empiezan sus tramos en {@link #runs} (y el final, en la siguiente). */
    final int[] colStart;
    /** Tramos seguidos: y, largo, bloque. */
    final short[] runs;

    private VoidIsland(DataInputStream in) throws IOException {
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'T' || magic[1] != 'F' || magic[2] != 'V' || magic[3] != 'I') throw new IOException("no es una isla");
        int version = in.readInt();
        if (version < 1 || version > 3) throw new IOException("versión " + version);
        sx = in.readInt();
        sy = in.readInt();
        sz = in.readInt();
        ox = in.readInt();
        oy = in.readInt();
        oz = in.readInt();
        knight = new BlockPos(in.readInt(), in.readInt(), in.readInt());
        arrive = new Vec3(in.readInt() + 0.5, in.readInt(), in.readInt() + 0.5);
        arriveYaw = in.readFloat();
        rift = version >= 2 ? new BlockPos(in.readInt(), in.readInt(), in.readInt()) : new BlockPos(0, 72, 0);
        if (version >= 3) {
            bubble = new Vec3(in.readInt() + 0.5, in.readInt(), in.readInt() + 0.5);
            bubbleR = in.readInt();
            heart = new Vec3(in.readInt() + 0.5, in.readInt() + 0.5, in.readInt() + 0.5);
            int nf = in.readInt();
            java.util.List<BlockPos> list = new java.util.ArrayList<>();
            for (int i = 0; i < nf; i++) list.add(new BlockPos(in.readInt(), in.readInt(), in.readInt()));
            shards = java.util.List.copyOf(list);
        } else {
            bubble = new Vec3(knight.getX() + 0.5, knight.getY(), knight.getZ() + 0.5);
            bubbleR = 0F;
            heart = bubble.add(0, 12, 0);
            shards = java.util.List.of();
        }
        int n = in.readInt();
        palette = new BlockState[n];
        for (int i = 0; i < n; i++) {
            String s = in.readUTF();
            BlockState st;
            try {
                st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), s, false).blockState();
            } catch (Exception e) {
                TFClient.LOGGER.warn("TF Void: bloque desconocido en la isla: {}", s);
                st = Blocks.STONE.defaultBlockState();
            }
            palette[i] = st;
        }
        colStart = new int[sx * sz + 1];
        short[] buf = new short[1 << 16];
        int used = 0;
        for (int c = 0; c < sx * sz; c++) {
            colStart[c] = used;
            int k = in.readUnsignedShort();
            for (int r = 0; r < k; r++) {
                if (used + 3 > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
                buf[used++] = (short) in.readUnsignedShort();
                buf[used++] = (short) in.readUnsignedShort();
                buf[used++] = (short) in.readUnsignedShort();
            }
        }
        colStart[sx * sz] = used;
        runs = java.util.Arrays.copyOf(buf, used);
    }

    /** La isla (se lee la primera vez); null si no se pudo (entonces la dimensión queda vacía, sin romper nada). */
    public static VoidIsland get() {
        VoidIsland i = loaded;
        if (i != null || failed) return i;
        synchronized (VoidIsland.class) {
            if (loaded != null || failed) return loaded;
            try (InputStream raw = VoidIsland.class.getResourceAsStream("/tfclient-void-isla.bin")) {
                if (raw == null) throw new IOException("no está en el jar");
                loaded = new VoidIsland(new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(raw))));
                TFClient.LOGGER.info("TF Void: isla cargada ({}x{}x{}, {} bloques distintos)", loaded.sx, loaded.sy, loaded.sz,
                        loaded.palette.length);
            } catch (Exception e) {
                failed = true;
                TFClient.LOGGER.error("TF Void: no se pudo leer la isla", e);
            }
            return loaded;
        }
    }

    /** Dónde está el caballero del santuario. */
    public static BlockPos knightPos() {
        VoidIsland i = get();
        return i == null ? new BlockPos(0, 80, 0) : i.knight;
    }

    /** (1.3.96) El Velo del Alba: el centro de la burbuja del santuario (radio 0 si la isla no la tiene). */
    public static Vec3 bubbleCenter() {
        VoidIsland i = get();
        return i == null ? Vec3.ZERO : i.bubble;
    }

    public static float bubbleRadius() {
        VoidIsland i = get();
        return i == null ? 0F : i.bubbleR;
    }

    /** ¿Está ese punto dentro del Velo del Alba? */
    public static boolean inBubble(double x, double y, double z) {
        VoidIsland i = get();
        return i != null && i.bubbleR > 0 && i.bubble.distanceToSqr(x, y, z) < i.bubbleR * i.bubbleR;
    }

    /** (1.3.96) Donde flota el Corazón del Alba, sobre el templo. */
    public static Vec3 heartPos() {
        VoidIsland i = get();
        return i == null ? new Vec3(0, 90, 0) : i.heart;
    }

    /** (1.3.96) Los sitios de los fragmentos de luz (donde flota cada uno). */
    public static java.util.List<BlockPos> shards() {
        VoidIsland i = get();
        return i == null ? java.util.List.of() : i.shards;
    }

    /** Dónde va la grieta del Void (el centro de la plaza; se pisa en esa y). */
    public static BlockPos riftPos() {
        VoidIsland i = get();
        return i == null ? new BlockPos(0, 72, 0) : i.rift;
    }

    /** Dónde se llega al vacío (a la puerta del este del santuario, mirando hacia la grieta del centro). */
    public static Vec3 arrivePos() {
        VoidIsland i = get();
        return i == null ? new Vec3(0.5, 80, 6.5) : i.arrive;
    }

    public static float arriveYaw() {
        VoidIsland i = get();
        return i == null ? 180F : i.arriveYaw;
    }

    // ------------------------------------------------------------------ por dónde andan los jefes (1.3.92)

    /**
     * Lo cerca del borde de la isla que puede andar un jefe (el dueño: «que me persiga por toda la isla, pero que no se
     * salga y caiga al vacío: unos 5 o 6 bloques antes del borde, que no me siga»).
     */
    public static final float EDGE = 6F;
    /** Por columna: lo lejos que está del borde de la isla, en bloques (0 donde no hay suelo). */
    private float[] edge;
    /** Por columna: la columna más cercana por donde puede andar un jefe (−1 si no hay ninguna). */
    private int[] nearest;
    /** Por columna: ¿puede andar un jefe? (a EDGE o más del borde y en la parte de la isla de la grieta) */
    private boolean[] walk;

    /** Lo de arriba, hecho la primera vez (la distancia al borde con un chaflán de 8 vecinos: ~4 % de error). */
    private synchronized void edgeField() {
        if (edge != null) return;
        int n = sx * sz;
        float[] d = new float[n];
        net.minecraft.world.level.EmptyBlockGetter none = net.minecraft.world.level.EmptyBlockGetter.INSTANCE;
        boolean[] solid = new boolean[palette.length];
        for (int i = 0; i < palette.length; i++) {
            BlockState st = palette[i];
            solid[i] = !st.isAir() && !st.getCollisionShape(none, BlockPos.ZERO).isEmpty();
        }
        final float big = 1e6F, diag = 1.4142135F;
        for (int c = 0; c < n; c++) {
            boolean ground = false;
            for (int r = colStart[c]; r < colStart[c + 1] && !ground; r += 3) {
                int b = runs[r + 2] & 0xFFFF;
                ground = b < palette.length && solid[b];
            }
            d[c] = ground ? big : 0F;
        }
        // (fuera del dibujo de la isla es vacío: cuenta como 0)
        for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
                int c = z * sx + x;
                if (d[c] == 0F) continue;
                float v = d[c];
                v = Math.min(v, (x > 0 ? d[c - 1] : 0F) + 1F);
                v = Math.min(v, (z > 0 ? d[c - sx] : 0F) + 1F);
                v = Math.min(v, (x > 0 && z > 0 ? d[c - sx - 1] : 0F) + diag);
                v = Math.min(v, (x < sx - 1 && z > 0 ? d[c - sx + 1] : 0F) + diag);
                d[c] = v;
            }
        }
        for (int z = sz - 1; z >= 0; z--) {
            for (int x = sx - 1; x >= 0; x--) {
                int c = z * sx + x;
                if (d[c] == 0F) continue;
                float v = d[c];
                v = Math.min(v, (x < sx - 1 ? d[c + 1] : 0F) + 1F);
                v = Math.min(v, (z < sz - 1 ? d[c + sx] : 0F) + 1F);
                v = Math.min(v, (x < sx - 1 && z < sz - 1 ? d[c + sx + 1] : 0F) + diag);
                v = Math.min(v, (x > 0 && z < sz - 1 ? d[c + sx - 1] : 0F) + diag);
                d[c] = v;
            }
        }
        // Solo la parte de la isla donde está la grieta: el islote del este se une por un puente estrecho (por él se
        // caería; mirado con la máscara en tools/void/mascara_jefes.py). Y nunca dentro del Velo del Alba (1.3.96: el
        // santuario; lo une a la isla un puente estrecho, pero por si acaso)
        boolean[] ok = new boolean[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        int rc = (rift.getZ() - oz) * sx + (rift.getX() - ox);
        if (rift.getX() - ox >= 0 && rift.getX() - ox < sx && rift.getZ() - oz >= 0 && rift.getZ() - oz < sz && d[rc] >= EDGE) {
            ok[rc] = true;
            queue[tail++] = rc;
            while (head < tail) {
                int c = queue[head++];
                int x = c % sx, z = c / sx;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || nx >= sx || nz < 0 || nz >= sz) continue;
                    int o = nz * sx + nx;
                    if (ok[o] || d[o] < EDGE || veilColumn(nx, nz)) continue;
                    ok[o] = true;
                    queue[tail++] = o;
                }
            }
        } else {
            for (int c = 0; c < n; c++) ok[c] = d[c] >= EDGE && !veilColumn(c % sx, c / sx);
        }
        // La columna buena más cercana de cada una (anchura desde todas las buenas a la vez)
        int[] near = new int[n];
        java.util.Arrays.fill(near, -1);
        head = 0;
        tail = 0;
        for (int c = 0; c < n; c++) {
            if (ok[c]) {
                near[c] = c;
                queue[tail++] = c;
            }
        }
        while (head < tail) {
            int c = queue[head++];
            int x = c % sx, z = c / sx;
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || nx >= sx || nz < 0 || nz >= sz) continue;
                int o = nz * sx + nx;
                if (near[o] >= 0) continue;
                near[o] = near[c];
                queue[tail++] = o;
            }
        }
        nearest = near;
        walk = ok;
        edge = d;
    }

    /** (1.3.96) ¿Cae esa columna (de la malla de la isla) dentro del Velo del Alba? Ahí no entra ningún jefe. */
    private boolean veilColumn(int x, int z) {
        if (bubbleR <= 0) return false;
        double dx = x + ox + 0.5 - bubble.x, dz = z + oz + 0.5 - bubble.z;
        return dx * dx + dz * dz < (bubbleR + 2) * (bubbleR + 2);
    }

    /** Lo lejos que está del borde de la isla (en bloques; 0 sobre el vacío o sin isla). */
    public static float edgeDistance(double x, double z) {
        VoidIsland i = get();
        if (i == null) return 0F;
        i.edgeField();
        int ix = (int) Math.floor(x) - i.ox, iz = (int) Math.floor(z) - i.oz;
        if (ix < 0 || ix >= i.sx || iz < 0 || iz >= i.sz) return 0F;
        return i.edge[iz * i.sx + ix];
    }

    /** ¿Puede andar un jefe por aquí? (sobre la isla, a {@link #EDGE} bloques o más del borde, en la parte de la grieta) */
    public static boolean walkable(double x, double z) {
        VoidIsland i = get();
        if (i == null) return false;
        i.edgeField();
        int ix = (int) Math.floor(x) - i.ox, iz = (int) Math.floor(z) - i.oz;
        return ix >= 0 && ix < i.sx && iz >= 0 && iz < i.sz && i.walk[iz * i.sx + ix];
    }

    /**
     * El sitio por donde puede andar un jefe más cercano a (x, z), en el centro de su bloque y a la altura y; null si
     * no hay isla. (Fuera del dibujo de la isla, el de su borde más cercano.)
     */
    public static Vec3 nearestWalkable(double x, double y, double z) {
        VoidIsland i = get();
        if (i == null) return null;
        i.edgeField();
        int ix = Math.max(0, Math.min(i.sx - 1, (int) Math.floor(x) - i.ox));
        int iz = Math.max(0, Math.min(i.sz - 1, (int) Math.floor(z) - i.oz));
        int c = i.nearest[iz * i.sx + ix];
        if (c < 0) return null;
        return new Vec3(c % i.sx + i.ox + 0.5, y, c / i.sx + i.oz + 0.5);
    }

    /** Copia en el chunk lo que le toca de la isla (y pone sus alturas de generación al día). */
    void fill(ChunkAccess chunk) {
        int cx = chunk.getPos().getMinBlockX(), cz = chunk.getPos().getMinBlockZ();
        // ¿Toca la isla?
        if (cx + 15 < ox || cx > ox + sx - 1 || cz + 15 < oz || cz > oz + sz - 1) return;
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight();
        for (int dz = 0; dz < 16; dz++) {
            int iz = cz + dz - oz;
            if (iz < 0 || iz >= sz) continue;
            for (int dx = 0; dx < 16; dx++) {
                int ix = cx + dx - ox;
                if (ix < 0 || ix >= sx) continue;
                int c = iz * sx + ix;
                for (int r = colStart[c]; r < colStart[c + 1]; r += 3) {
                    int y0 = runs[r] & 0xFFFF, len = runs[r + 1] & 0xFFFF, b = runs[r + 2] & 0xFFFF;
                    if (b >= palette.length) continue;
                    BlockState st = palette[b];
                    for (int y = y0; y < y0 + len; y++) {
                        int wy = y + oy;
                        if (wy < minY || wy >= maxY) continue;
                        pos.set(cx + dx, wy, cz + dz);
                        chunk.setBlockState(pos, st, false);
                        floor.update(dx, wy, dz, st);
                        surface.update(dx, wy, dz, st);
                    }
                }
            }
        }
    }

    /** La altura de la isla en una columna (lo de encima del bloque más alto que cumple la condición), o minY. */
    int height(int x, int z, int minY, java.util.function.Predicate<BlockState> counts) {
        int ix = x - ox, iz = z - oz;
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return minY;
        int c = iz * sx + ix;
        int best = minY;
        for (int r = colStart[c]; r < colStart[c + 1]; r += 3) {
            int y0 = runs[r] & 0xFFFF, len = runs[r + 1] & 0xFFFF, b = runs[r + 2] & 0xFFFF;
            if (b < palette.length && counts.test(palette[b])) best = Math.max(best, y0 + len + oy);
        }
        return best;
    }

    /** El bloque de la isla en un sitio del mundo (aire fuera de ella). */
    BlockState stateAt(int x, int y, int z) {
        int ix = x - ox, iz = z - oz, iy = y - oy;
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz || iy < 0 || iy >= sy) return Blocks.AIR.defaultBlockState();
        int c = iz * sx + ix;
        for (int r = colStart[c]; r < colStart[c + 1]; r += 3) {
            int y0 = runs[r] & 0xFFFF, len = runs[r + 1] & 0xFFFF, b = runs[r + 2] & 0xFFFF;
            if (iy >= y0 && iy < y0 + len && b < palette.length) return palette[b];
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** La columna entera (para getBaseColumn). */
    BlockState[] column(int x, int z, int minY, int height) {
        BlockState[] out = new BlockState[height];
        java.util.Arrays.fill(out, Blocks.AIR.defaultBlockState());
        int ix = x - ox, iz = z - oz;
        if (ix < 0 || ix >= sx || iz < 0 || iz >= sz) return out;
        int c = iz * sx + ix;
        for (int r = colStart[c]; r < colStart[c + 1]; r += 3) {
            int y0 = runs[r] & 0xFFFF, len = runs[r + 1] & 0xFFFF, b = runs[r + 2] & 0xFFFF;
            if (b >= palette.length) continue;
            for (int y = y0; y < y0 + len; y++) {
                int i = y + oy - minY;
                if (i >= 0 && i < height) out[i] = palette[b];
            }
        }
        return out;
    }
}
