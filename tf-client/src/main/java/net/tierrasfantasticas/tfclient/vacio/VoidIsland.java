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
 * La isla de The Void: la del mapa del dueño (EndCarageKitMap), con el pozo del centro relleno y el santuario del
 * caballero de luz encima (tools/void/isla.py la convierte a {@code tfclient-void-isla.bin}). Se carga una vez (en el
 * primer chunk que se genere) y {@link VoidIslandGenerator} copia en cada chunk lo que le toca: así no se pega nada de
 * golpe en el servidor y cada chunk nace ya con su trozo de isla.
 *
 * <p>Formato (gzip, big-endian): «TFVI», versión, tamaño (x, y, z), dónde va su esquina en el mundo, dónde está el
 * caballero, dónde se llega (x, y, z, yaw), la paleta (estados como en /setblock) y, por columna (z por fuera, x por
 * dentro), sus tramos: (y, largo, bloque).
 */
public final class VoidIsland {
    private static volatile VoidIsland loaded;
    private static volatile boolean failed;

    final int sx, sy, sz, ox, oy, oz;
    final BlockPos knight;
    final Vec3 arrive;
    final float arriveYaw;
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
        if (version != 1) throw new IOException("versión " + version);
        sx = in.readInt();
        sy = in.readInt();
        sz = in.readInt();
        ox = in.readInt();
        oy = in.readInt();
        oz = in.readInt();
        knight = new BlockPos(in.readInt(), in.readInt(), in.readInt());
        arrive = new Vec3(in.readInt() + 0.5, in.readInt(), in.readInt() + 0.5);
        arriveYaw = in.readFloat();
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

    /** Dónde se llega al vacío (delante del caballero, mirándole). */
    public static Vec3 arrivePos() {
        VoidIsland i = get();
        return i == null ? new Vec3(0.5, 80, 6.5) : i.arrive;
    }

    public static float arriveYaw() {
        VoidIsland i = get();
        return i == null ? 180F : i.arriveYaw;
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
