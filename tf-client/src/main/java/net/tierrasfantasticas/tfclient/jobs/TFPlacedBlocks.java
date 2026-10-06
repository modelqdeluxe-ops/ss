package net.tierrasfantasticas.tfclient.jobs;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.util.TFJson;

/**
 * Bloques puestos por jugadores, para que romper lo que uno mismo coloca (o volver a colocar en el mismo sitio) no pague.
 * Guarda los últimos 200.000 sitios de cada dimensión en &lt;mundo&gt;/tfclient/colocados.bin.
 */
final class TFPlacedBlocks {
    private static final int MAX_PER_LEVEL = 200_000;

    private static final class Positions {
        final LongOpenHashSet set = new LongOpenHashSet();
        final LongArrayFIFOQueue order = new LongArrayFIFOQueue();

        void add(long pos) {
            if (!set.add(pos)) return;
            order.enqueue(pos);
            while (order.size() > MAX_PER_LEVEL) set.remove(order.dequeueLong());
        }
    }

    private static final Map<String, Positions> LEVELS = new HashMap<>();
    private static Path file;
    private static boolean dirty;

    private TFPlacedBlocks() {}

    /** Apunta el sitio. Devuelve false si ya estaba apuntado (no es un sitio nuevo). */
    static boolean place(String level, BlockPos pos) {
        Positions p = LEVELS.computeIfAbsent(level, k -> new Positions());
        long key = pos.asLong();
        if (p.set.contains(key)) return false;
        p.add(key);
        dirty = true;
        return true;
    }

    static boolean placed(String level, BlockPos pos) {
        Positions p = LEVELS.get(level);
        return p != null && p.set.contains(pos.asLong());
    }

    static void load(MinecraftServer server) {
        LEVELS.clear();
        file = TFJson.worldFile(server, "colocados.bin");
        if (!Files.exists(file)) return;
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int levels = in.readInt();
            for (int i = 0; i < levels; i++) {
                String level = in.readUTF();
                int n = in.readInt();
                Positions p = LEVELS.computeIfAbsent(level, k -> new Positions());
                for (int j = 0; j < n; j++) p.add(in.readLong());
            }
        } catch (IOException e) {
            TFClient.LOGGER.warn("TF Oficios: no se pudo leer {}: {}", file, e.getMessage());
        }
    }

    static void save() {
        if (!dirty || file == null) return;
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("colocados.bin.tmp");
            try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(LEVELS.size());
                for (Map.Entry<String, Positions> e : LEVELS.entrySet()) {
                    out.writeUTF(e.getKey());
                    LongArrayFIFOQueue order = e.getValue().order;
                    int n = order.size();
                    out.writeInt(n);
                    // Se recorre la cola sin perderla: se saca y se vuelve a meter.
                    for (int i = 0; i < n; i++) {
                        long v = order.dequeueLong();
                        out.writeLong(v);
                        order.enqueue(v);
                    }
                }
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            TFClient.LOGGER.warn("TF Oficios: no se pudo guardar {}: {}", file, e.getMessage());
        }
    }
}
