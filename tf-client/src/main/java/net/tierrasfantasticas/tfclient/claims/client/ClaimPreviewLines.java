/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.claims.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.tierrasfantasticas.tfclient.TFClient;
import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * La vista previa de una piedra de protección en la mano (1.3.120, el dueño: «el contorno, cuando la tengo en la mano
 * antes de ponerla, hazlo una línea sólida, no con partículas, porque no se ve; colocada, el contorno de partículas de
 * siempre»): el borde de la zona que haría si la pusiera donde está, como una valla de luz del color de la piedra: una
 * línea gruesa a ras de suelo (sigue el terreno), otra 2 bloques más arriba, un poste cada 4 bloques y las cuatro
 * esquinas altas. Se ve también a través del terreno, más tenue, para saber por dónde va detrás de una loma. Solo el
 * trozo a menos de {@link #RANGE} bloques del jugador (una zona de 500 tiene 2000 bloques de borde).
 */
@Mod.EventBusSubscriber(modid = TFClient.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimPreviewLines {
    private static final int RANGE = 56;
    private static final double LIFT = 0.06, FENCE = 2.0, CORNER = 14.0, SNAP = 12.0;
    private static final int POST_EVERY = 4;

    private ClaimPreviewLines() {}

    /** Las líneas que se ven a través de los bloques (tenues): las de Minecraft sin prueba de profundidad. */
    private static final class Types extends RenderType {
        private Types(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean so, Runnable a, Runnable b) {
            super(n, f, m, s, c, so, a, b);
        }

        static final RenderType THROUGH = create("tfclient_claim_preview_through", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 4096, false, false, CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(2.0))).setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setOutputState(ITEM_ENTITY_TARGET).setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(NO_DEPTH_TEST).setCullState(NO_CULL).createCompositeState(false));
        static final RenderType SOLID = create("tfclient_claim_preview", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 4096, false, false, CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(4.0))).setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setOutputState(ITEM_ENTITY_TARGET).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).createCompositeState(false));
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.options.hideGui) return;
        ClaimTier tier = ClaimBlocks.readTier(player.getMainHandItem());
        if (tier == null) tier = ClaimBlocks.readTier(player.getOffhandItem());
        if (tier == null) return;
        BlockPos p = player.blockPosition();
        int x0 = p.getX() - tier.radius, z0 = p.getZ() - tier.radius, x1 = p.getX() + tier.radius + 1, z1 = p.getZ() + tier.radius + 1;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int r = Math.round(tier.r * 255), g = Math.round(tier.g * 255), b = Math.round(tier.b * 255);
        // un poco más vivo que el color de la piedra
        int hr = Math.min(255, r + 40), hg = Math.min(255, g + 40), hb = Math.min(255, b + 40);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (int pass = 0; pass < 2; pass++) {
            boolean through = pass == 0;
            VertexConsumer vc = buffers.getBuffer(through ? Types.THROUGH : Types.SOLID);
            int a = through ? 70 : 235;
            double py = player.getY();
            // los cuatro lados, cada uno con su línea de suelo, la de arriba y los postes
            // (el borde va por las aristas de los bloques: x0..x1 y z0..z1 con x1 y z1 justo fuera de la zona; el suelo se
            // mira en la fila de bloques de dentro)
            side(vc, m, n, level, py, true, z0, z0, x0, x1, hr, hg, hb, a);
            side(vc, m, n, level, py, true, z1, z1 - 1, x0, x1, hr, hg, hb, a);
            side(vc, m, n, level, py, false, x0, x0, z0, z1, hr, hg, hb, a);
            side(vc, m, n, level, py, false, x1, x1 - 1, z0, z1, hr, hg, hb, a);
            for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
                if (Math.abs(c[0] - player.getX()) > RANGE || Math.abs(c[1] - player.getZ()) > RANGE) continue;
                double y = ground(level, c[0], c[1], py);
                line(vc, m, n, c[0], y, c[1], c[0], y + CORNER, c[1], hr, hg, hb, a);
            }
            buffers.endBatch(through ? Types.THROUGH : Types.SOLID);
        }
        ps.popPose();
        RenderSystem.lineWidth(1F);
    }

    /**
     * Un lado: a lo largo de x (alongX, en z = fixed) o de z (en x = fixed), de from a to; el suelo, en la fila de dentro
     * (inside). Solo el trozo cerca del jugador.
     */
    private static void side(VertexConsumer vc, Matrix4f m, Matrix3f n, ClientLevel level, double py, boolean alongX, int fixed, int inside,
                             int from, int to, int r, int g, int b, int a) {
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player.getX(), pz = mc.player.getZ();
        if (Math.abs(fixed - (alongX ? pz : px)) > RANGE) return;
        double mid = alongX ? px : pz;
        int s = (int) Math.max(from, Math.floor(mid - RANGE)), e = (int) Math.min(to, Math.ceil(mid + RANGE));
        if (s >= e) return;
        double prevY = Double.NaN;
        for (int k = s; k <= e; k++) {
            int along = Math.min(k, to - 1);
            double y = alongX ? ground(level, along, inside, py) : ground(level, inside, along, py);
            double x = alongX ? k : fixed, z = alongX ? fixed : k;
            if (!Double.isNaN(prevY)) {
                double lx = alongX ? k - 1 : fixed, lz = alongX ? fixed : k - 1;
                // a ras de suelo (con su escalón si cambia la altura) y la de arriba
                line(vc, m, n, lx, prevY, lz, x, prevY, z, r, g, b, a);
                if (y != prevY) line(vc, m, n, x, prevY, z, x, y, z, r, g, b, a);
                line(vc, m, n, lx, prevY + FENCE, lz, x, prevY + FENCE, z, r, g, b, a * 3 / 4);
                if (y != prevY) line(vc, m, n, x, prevY + FENCE, z, x, y + FENCE, z, r, g, b, a * 3 / 4);
            }
            if (Math.floorMod(k, POST_EVERY) == 0) line(vc, m, n, x, y, z, x, y + FENCE, z, r, g, b, a * 3 / 4);
            prevY = y;
        }
    }

    /** La altura del suelo en esa columna (un poco por encima); en una cueva, a la altura del jugador. */
    private static double ground(ClientLevel level, int x, int z, double py) {
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return (Math.abs(h - py) > SNAP ? Math.floor(py) : h) + LIFT;
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, double ax, double ay, double az, double bx, double by, double bz,
                             int r, int g, int b, int a) {
        float dx = (float) (bx - ax), dy = (float) (by - ay), dz = (float) (bz - az);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4F) return;
        dx /= len;
        dy /= len;
        dz /= len;
        vc.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, (float) bx, (float) by, (float) bz).color(r, g, b, a).normal(n, dx, dy, dz).endVertex();
    }
}
