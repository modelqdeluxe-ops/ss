package net.tierrasfantasticas.tfclient.bosses;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Las grietas de este fotograma, guardadas para dibujarlas después de la pasada final del paquete de shaders
 * ({@link net.tierrasfantasticas.tfclient.vacio.AfterShaders}: con Oculus, lo dibujado con nuestros shaders mientras
 * dibuja el mundo no deja nada). Los vértices ya van con la cámara de ese momento; primero la obsidiana (escribe la
 * profundidad) y luego el desgarro y el círculo (transparentes, ordenados por distancia).
 */
@OnlyIn(Dist.CLIENT)
public final class RiftLate {
    private static MultiBufferSource.BufferSource source;
    private static boolean pending;

    private RiftLate() {}

    static MultiBufferSource buffers() {
        if (source == null) {
            Map<RenderType, BufferBuilder> fixed = new LinkedHashMap<>();
            fixed.put(BossRenderTypes.RIFT_OBSIDIAN, new BufferBuilder(BossRenderTypes.RIFT_OBSIDIAN.bufferSize()));
            fixed.put(BossRenderTypes.RIFT_FX, new BufferBuilder(BossRenderTypes.RIFT_FX.bufferSize()));
            source = MultiBufferSource.immediateWithBuffers(fixed, new BufferBuilder(256));
        }
        pending = true;
        return source;
    }

    /** Dibuja las grietas guardadas (con la proyección y la vista de la escena ya puestas). */
    public static void flush() {
        if (!pending || source == null) return;
        pending = false;
        source.endBatch();
    }
}
