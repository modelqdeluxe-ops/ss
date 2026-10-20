package net.tierrasfantasticas.tfclient.vacio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.tierrasfantasticas.tfclient.TFClient;

/**
 * Pone al día la isla de un Void que ya se generó con una versión anterior del mod: los chunks guardados no se vuelven
 * a generar, así que en el servidor del dueño seguiría la isla de antes.
 *
 * <p>1.3.89 reescribía el centro y la punta norte. 1.3.90 (el dueño: «la grieta no está en el centro; hazla más
 * grande»): la isla es ×1,5 y está centrada en la grieta, así que cambia entera. Al arrancar el servidor (el Void hace
 * ticks aunque no haya nadie), si el pedestal del caballero nuevo no está (es otra isla), se reescribe todo lo que
 * ocupaban la isla vieja y la nueva, chunk a chunk y empezando por el santuario (donde se llega), 128 columnas por tick
 * y solo los bloques que cambian (sin vecinos ni objetos que caigan): unos 450 chunks, menos de un minuto. Hecho, se
 * apunta en los datos del mundo del Void ({@code data/tfclient_void.dat}: islaV) y no se vuelve a mirar. En un mundo
 * nuevo, los chunks ya nacen bien y solo se apunta. El caballero viejo se va solo ({@link KnightEntity}) y sale el del
 * santuario nuevo; la grieta sigue donde estaba (0 72 0).
 *
 * <p>1.3.96: el Santuario del Alba (una isla aparte al norte, con el Puente de Luz desde el santuario viejo) y las luces
 * de los fragmentos: la caja de la isla crece hacia el norte y el pedestal del caballero está en la isla nueva, así que
 * los mundos de antes se ponen al día igual (versión 4).
 *
 * <p>1.3.97: el Void entero en roca del Vacío (sin lana, tierra ni piedra del End), los Fragmentos del Alba Rota cuatro veces
 * más grandes, el santuario más al norte y la Llegada donde estaba el Umbral: la caja (617 × 958) contiene la de antes y
 * se rehace entera (versión 5; ~2300 chunks, unos 4 minutos la primera vez).
 */
final class IslandUpdate {
    static final int VERSION = 5;
    private static final int PER_TICK = 128;
    /** Lo que ocupaba la isla de la 1.3.88-1.3.89 (x, z del mundo; con margen). */
    private static final int OLD_X0 = -61, OLD_X1 = 140, OLD_Z0 = -163, OLD_Z1 = 97;
    private static boolean done, checked;
    private static Deque<long[]> chunks;
    private static int chunksTotal, changed;

    private IslandUpdate() {}

    /** Lo que se guarda en el mundo del Void. */
    static final class Data extends SavedData {
        int version;

        static Data load(CompoundTag tag) {
            Data d = new Data();
            d.version = tag.getInt("islaV");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("islaV", version);
            return tag;
        }
    }

    /**
     * Para la prueba en el juego: hace como si la isla de este mundo fuera la de antes (versión 2, el pedestal del
     * caballero cambiado y una columna de piedra en la plaza) para ver que se pone al día sola.
     */
    static void pretendOld(ServerLevel level) {
        Data data = level.getDataStorage().computeIfAbsent(Data::load, Data::new, "tfclient_void");
        data.version = 2;
        data.setDirty();
        level.setBlock(VoidIsland.knightPos().below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int y = 72; y < 80; y++) {
            level.setBlock(new BlockPos(6, y, 6), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        reset();
    }

    /** Para la prueba: cómo va. */
    static String status() {
        if (done) return "al día (" + chunksTotal + " chunks, " + changed + " bloques cambiados)";
        return chunks == null ? "sin empezar" : "quedan " + chunks.size() + " de " + chunksTotal + " chunks";
    }

    static boolean finished() {
        return done;
    }

    /** Al arrancar el servidor (o al cambiar de mundo en un jugador sin servidor dedicado), se vuelve a mirar. */
    static void reset() {
        done = false;
        checked = false;
        chunks = null;
        chunksTotal = 0;
        changed = 0;
    }

    static void tick(ServerLevel level) {
        if (done) return;
        Data data = level.getDataStorage().computeIfAbsent(Data::load, Data::new, "tfclient_void");
        if (data.version >= VERSION) {
            done = true;
            return;
        }
        VoidIsland island = VoidIsland.get();
        if (island == null) return;
        if (!checked) {
            checked = true;
            // El pedestal del caballero nuevo (bajo sus pies): si está, la isla ya es la de ahora
            BlockPos mark = VoidIsland.knightPos().below();
            level.getChunk(SectionPos.blockToSectionCoord(mark.getX()), SectionPos.blockToSectionCoord(mark.getZ()));
            if (level.getBlockState(mark) == island.stateAt(mark.getX(), mark.getY(), mark.getZ())) {
                finish(level, data, "la isla ya es la de ahora");
                return;
            }
            chunks = plan(island);
            chunksTotal = chunks.size();
            TFClient.LOGGER.info("TF Void: la isla de este mundo es de antes; se pone al día ({} chunks, primero el santuario)", chunksTotal);
        }
        if (step(level, island)) {
            finish(level, data, String.format("isla al día (%d chunks, %d bloques cambiados)", chunksTotal, changed));
        }
    }

    private static void finish(ServerLevel level, Data data, String what) {
        data.version = VERSION;
        data.setDirty();
        done = true;
        chunks = null;
        TFClient.LOGGER.info("TF Void: {}", what);
    }

    /** Los chunks de la isla vieja y la nueva, del más cercano al santuario nuevo al más lejano; en cada uno, sus columnas. */
    private static Deque<long[]> plan(VoidIsland island) {
        int x0 = Math.min(OLD_X0, island.ox), x1 = Math.max(OLD_X1, island.ox + island.sx - 1);
        int z0 = Math.min(OLD_Z0, island.oz), z1 = Math.max(OLD_Z1, island.oz + island.sz - 1);
        BlockPos k = VoidIsland.knightPos();
        List<long[]> list = new ArrayList<>();
        for (int cz = SectionPos.blockToSectionCoord(z0); cz <= SectionPos.blockToSectionCoord(z1); cz++) {
            for (int cx = SectionPos.blockToSectionCoord(x0); cx <= SectionPos.blockToSectionCoord(x1); cx++) {
                long dx = cx * 16L + 8 - k.getX(), dz = cz * 16L + 8 - k.getZ();
                // {chunk x, chunk z, distancia², columna siguiente (0..256)}
                list.add(new long[] {cx, cz, dx * dx + dz * dz, 0});
            }
        }
        list.sort(Comparator.comparingLong(a -> a[2]));
        return new ArrayDeque<>(list);
    }

    /** Un tick: hasta PER_TICK columnas (cargando como mucho un chunk); true si ya no queda nada. */
    private static boolean step(ServerLevel level, VoidIsland island) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        boolean loaded = false;
        while (!chunks.isEmpty() && n < PER_TICK) {
            long[] c = chunks.peek();
            int cx = (int) c[0], cz = (int) c[1];
            if (!level.hasChunk(cx, cz)) {
                if (loaded) break;
                level.getChunk(cx, cz);
                loaded = true;
            }
            while (c[3] < 256 && n < PER_TICK) {
                int x = cx * 16 + (int) (c[3] & 15), z = cz * 16 + (int) (c[3] >> 4);
                c[3]++;
                n++;
                BlockState[] col = island.column(x, z, island.oy, island.sy);
                for (int i = 0; i < col.length; i++) {
                    pos.set(x, island.oy + i, z);
                    if (level.getBlockState(pos) != col[i]) {
                        level.setBlock(pos, col[i], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                        changed++;
                    }
                }
            }
            if (c[3] >= 256) {
                chunks.poll();
                int left = chunks.size();
                if (left % 50 == 0 && left > 0) {
                    TFClient.LOGGER.info("TF Void: isla, quedan {} de {} chunks", left, chunksTotal);
                }
            }
        }
        return chunks.isEmpty();
    }
}
