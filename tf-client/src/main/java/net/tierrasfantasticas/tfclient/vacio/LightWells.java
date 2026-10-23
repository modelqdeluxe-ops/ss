/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.vacio;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Los Pozos de Luz del Void (1.3.96; el dueño: «no quiero andar juntando fragmentos: que parándome en un sitio de luz me
 * cure del efecto, como cuando te ahogas y un bloque de magma te salva la respiración»). Donde un caballero del Alba se
 * hizo luz para sostener el fragmento ({@link VoidIsland#shards}, pocos: la corrosión es lenta) brota una burbuja de luz
 * como el Velo del santuario, más pequeña ({@link SanctuaryFx}): quien se queda dentro ({@link #RADIUS}) se limpia de la
 * corrosión ({@link VoidLight}). No se recogen ni se gastan.
 *
 * <p>(1.3.97, el dueño: «quita ese bloque de rana luz, solo haz una especie de burbuja como la del santuario que brille»)
 * Ya no hay esquirla flotante (una entidad de exhibición de bloque): las de antes, guardadas con su chunk, se quitan. El
 * servidor solo echa unas chispas doradas que suben.
 */
public final class LightWells {
    /** El radio de la burbuja que cura (en horizontal; el suelo es el del pozo, 2 por debajo de su punto). */
    public static final double RADIUS = 3.6;
    /** La etiqueta de las esquirlas de la 1.3.96 (para quitarlas). */
    static final String TAG = "tfclient_fragmento";
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.85F, 0.5F), 1.0F);

    private LightWells() {}

    static void reset() {
    }

    static void tick(ServerLevel level) {
        List<BlockPos> list = VoidIsland.shards();
        long now = level.getGameTime();
        if (now % 200 == 0) cleanup(level);
        if (list.isEmpty()) return;
        if (now % 10 == 0) sparkle(level, list, now);
    }

    /** ¿En qué pozo está (dentro de su burbuja)? −1 si en ninguno. */
    public static int wellAt(double x, double y, double z) {
        List<BlockPos> list = VoidIsland.shards();
        for (int i = 0; i < list.size(); i++) {
            BlockPos b = list.get(i);
            double dx = x - (b.getX() + 0.5), dz = z - (b.getZ() + 0.5);
            // (los pies, entre el suelo del pozo, 2 por debajo de su punto, y un poco por encima)
            double dy = y - (b.getY() - 1);
            if (dx * dx + dz * dz <= RADIUS * RADIUS && dy > -1.5 && dy < 3.5) return i;
        }
        return -1;
    }

    /** El centro de la burbuja de un pozo (en su suelo). */
    public static Vec3 bubbleCenter(BlockPos b) {
        return new Vec3(b.getX() + 0.5, b.getY() - 1.5, b.getZ() + 0.5);
    }

    /** Chispas doradas que suben dentro de la burbuja (pocas). */
    private static void sparkle(ServerLevel level, List<BlockPos> list, long now) {
        for (int i = 0; i < list.size(); i++) {
            BlockPos b = list.get(i);
            if (!level.hasChunkAt(b) || (now / 10 + i) % 2 != 0) continue;
            Vec3 c = bubbleCenter(b);
            level.sendParticles(GOLD, c.x, c.y + 1.0, c.z, 2, 1.6, 0.8, 1.6, 0.0);
        }
    }

    /** Las esquirlas de la 1.3.96 (un sapoluz flotante), fuera. */
    private static void cleanup(ServerLevel level) {
        for (Display.BlockDisplay d : level.getEntities(EntityTypeTest.forClass(Display.BlockDisplay.class), d -> d.getTags().contains(TAG))) {
            d.discard();
        }
    }

    /** Para la prueba en el juego. */
    public static String describe(ServerLevel level) {
        int old = level.getEntities(EntityTypeTest.forClass(Display.BlockDisplay.class), d -> d.getTags().contains(TAG)).size();
        return VoidIsland.shards().size() + " pozos de luz" + (old > 0 ? ", " + old + " esquirlas viejas aún (MAL)" : ", sin esquirlas");
    }
}
