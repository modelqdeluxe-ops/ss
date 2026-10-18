package net.tierrasfantasticas.tfclient.vacio;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * a generar, así que en el servidor del dueño seguían el santuario de la 1.3.88 en el centro y el templete de la punta
 * norte. 1.3.89: si al tener cargado el centro (o la punta norte) se ve lo de antes (lo que hay no es lo que debería
 * haber en un par de bloques que distinguen las dos islas), esa zona (un círculo de 47 bloques alrededor de la grieta y
 * uno de 14 alrededor del santuario) se reescribe columna a columna como en {@link VoidIsland}, 48 columnas por tick y
 * solo los bloques que cambian (sin vecinos ni objetos que caigan). Hecho, se apunta en los datos del mundo del Void
 * ({@code data/tfclient_void.dat}: islaV) y no se vuelve a mirar. En un mundo nuevo, los chunks ya nacen bien y solo se
 * apunta.
 */
final class IslandUpdate {
    static final int VERSION = 2;
    private static final int PER_TICK = 48;
    private static boolean done;
    private static Region center, shrine;

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

    /** Una zona: dónde mirar si está como antes y sus columnas por reescribir. */
    private static final class Region {
        final String name;
        final BlockPos mark;
        final int cx, cz, r;
        boolean checked;
        Deque<long[]> todo;
        int changed;

        Region(String name, BlockPos mark, int cx, int cz, int r) {
            this.name = name;
            this.mark = mark;
            this.cx = cx;
            this.cz = cz;
            this.r = r;
        }
    }

    /** Al arrancar el servidor (o al cambiar de mundo en un jugador sin servidor dedicado), se vuelve a mirar. */
    static void reset() {
        done = false;
        center = null;
        shrine = null;
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
        if (center == null) {
            BlockPos rift = VoidIsland.riftPos();
            BlockPos knight = VoidIsland.knightPos();
            // centro: encima de la plaza no hay nada (en la 1.3.88, el pedestal del santuario); santuario: el pedestal
            // de oro bajo el caballero (en el mapa original, el aire dentro del templete)
            center = new Region("centro", rift.above(2), rift.getX(), rift.getZ(), 47);
            shrine = new Region("santuario", knight.below(), knight.getX(), knight.getZ(), 14);
        }
        boolean all = true;
        for (Region r : new Region[] {center, shrine}) all &= step(level, island, r);
        if (all) {
            data.version = VERSION;
            data.setDirty();
            done = true;
        }
    }

    /** Un paso de una zona; true si ya está (bien desde el principio o reescrita). */
    private static boolean step(ServerLevel level, VoidIsland island, Region r) {
        if (!r.checked) {
            if (!level.hasChunkAt(r.mark)) return false;
            r.checked = true;
            if (level.getBlockState(r.mark) == island.stateAt(r.mark.getX(), r.mark.getY(), r.mark.getZ())) return true;
            r.todo = new ArrayDeque<>();
            for (int z = r.cz - r.r; z <= r.cz + r.r; z++) {
                for (int x = r.cx - r.r; x <= r.cx + r.r; x++) {
                    int dx = x - r.cx, dz = z - r.cz;
                    if (dx * dx + dz * dz <= r.r * r.r) r.todo.add(new long[] {x, z});
                }
            }
            TFClient.LOGGER.info("TF Void: la isla de este mundo es de antes; se pone al día ({}: {} columnas)", r.name, r.todo.size());
        }
        if (r.todo == null) return true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 0;
        int loads = 0;
        while (!r.todo.isEmpty() && n < PER_TICK) {
            long[] c = r.todo.peek();
            int x = (int) c[0], z = (int) c[1];
            if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                // (cargarlo aquí: el centro puede quedar fuera de la vista de quien llega al santuario)
                if (loads++ > 0) break;
                level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            }
            r.todo.poll();
            n++;
            BlockState[] col = island.column(x, z, island.oy, island.sy);
            for (int i = 0; i < col.length; i++) {
                pos.set(x, island.oy + i, z);
                if (level.getBlockState(pos) != col[i]) {
                    level.setBlock(pos, col[i], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
                    r.changed++;
                }
            }
        }
        if (!r.todo.isEmpty()) return false;
        TFClient.LOGGER.info("TF Void: {} de la isla al día ({} bloques cambiados)", r.name, r.changed);
        r.todo = null;
        return true;
    }
}
