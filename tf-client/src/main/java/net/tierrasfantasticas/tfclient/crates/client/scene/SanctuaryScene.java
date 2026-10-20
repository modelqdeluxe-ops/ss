package net.tierrasfantasticas.tfclient.crates.client.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.tierrasfantasticas.tfclient.crates.FSCrates;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * El santuario de las crates (1.3.99): la escena en vivo de la cinemática de apertura. Los bloques vienen de
 * assets/fscrates/escena/santuario.bin (lo escribe tools/crates/santuario.py, con los puntos de la escena y la ruta de
 * la cámara) y se dibujan con el renderizador de bloques de Minecraft sobre un mundo propio (SceneLevel): oclusión
 * ambiental, caras tapadas, colores de bioma, como en el juego.
 *
 * <p>Lo quieto (suelo, ruinas, árboles, el agua del estanque) se construye una vez en búferes de la tarjeta gráfica,
 * con la luz de la escena en el color de cada vértice: más oscuro y frío a la sombra del bosque, cálido y entero en el
 * haz de sol que cae sobre la crate, con manchas de sol entre las hojas. La hierba, las flores y los helechos se dibujan
 * en cada fotograma, mecidos por el viento.
 */
public final class SanctuaryScene {
    private static final ResourceLocation DATA = new ResourceLocation("fscrates", "escena/santuario.bin");
    private static final int F_SWAY = 1, F_WATER = 2;
    private static SanctuaryScene instance;
    private static boolean failed;

    /** Puntos de la escena (x, y, z, w): crate, pool, sun, spout0..3, cherry0..3, flowers0..2. */
    public final Map<String, float[]> anchors = new HashMap<>();
    /** La ruta de la cámara: tick, ojo (3), mira (3), campo de visión. */
    public final List<float[]> camera = new ArrayList<>();
    final SceneLevel level;
    private final List<int[]> blocks;          // x, y, z, paleta
    private final BlockState[] palette;
    private final int[] flags;
    private final Vector3f sun = new Vector3f(), crate = new Vector3f();

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    private final List<Plant> plants = new ArrayList<>();
    private Object builtFor;
    private TextureAtlasSprite waterSprite;

    private SanctuaryScene(BlockState[] palette, int[] flags, List<int[]> blocks, SceneLevel level) {
        this.palette = palette;
        this.flags = flags;
        this.blocks = blocks;
        this.level = level;
    }

    /** La escena (se carga la primera vez); null si no se pudo (entonces la cinemática usa la de siempre). */
    public static SanctuaryScene get() {
        if (instance == null && !failed) {
            try {
                instance = load();
            } catch (Throwable t) {
                failed = true;
                FSCrates.LOGGER.error("[TF Crates] No se pudo cargar el santuario de las crates", t);
            }
        }
        return instance;
    }

    public float[] anchor(String name) {
        float[] a = anchors.get(name);
        return a != null ? a : new float[4];
    }

    // ------------------------------------------------------------------------------------------------------ carga
    private static SanctuaryScene load() throws IOException {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(DATA);
        if (res.isEmpty()) throw new IOException("falta " + DATA);
        try (InputStream in = res.get().open(); DataInputStream d = new DataInputStream(in)) {
            byte[] magic = new byte[4];
            d.readFully(magic);
            if (magic[0] != 'T' || magic[1] != 'F' || magic[2] != 'S' || magic[3] != 'C') throw new IOException("no es una escena");
            d.readInt();
            int x0 = d.readInt(), y0 = d.readInt(), z0 = d.readInt();
            int np = d.readInt();
            BlockState[] pal = new BlockState[np];
            int[] flags = new int[np];
            var lookup = BuiltInRegistries.BLOCK.asLookup();
            for (int i = 0; i < np; i++) {
                String s = d.readUTF();
                flags[i] = d.readUnsignedByte();
                try {
                    pal[i] = BlockStateParser.parseForBlock(lookup, s, false).blockState();
                } catch (Exception e) {
                    FSCrates.LOGGER.warn("[TF Crates] Santuario: estado de bloque desconocido {}", s);
                    pal[i] = Blocks.AIR.defaultBlockState();
                }
            }
            int nb = d.readInt();
            List<int[]> blocks = new ArrayList<>(nb);
            int maxI = 0, maxJ = 0, maxK = 0;
            for (int i = 0; i < nb; i++) {
                int bi = d.readUnsignedByte(), bj = d.readUnsignedByte(), bk = d.readUnsignedByte(), p = d.readUnsignedShort();
                blocks.add(new int[] {bi + x0, bj + y0, bk + z0, p});
                maxI = Math.max(maxI, bi);
                maxJ = Math.max(maxJ, bj);
                maxK = Math.max(maxK, bk);
            }
            int nx = maxI + 1, ny = maxJ + 1, nz = maxK + 1;
            BlockState[] grid = new BlockState[nx * ny * nz];
            for (int[] b : blocks) grid[((b[0] - x0) * ny + (b[1] - y0)) * nz + (b[2] - z0)] = pal[b[3]];
            SanctuaryScene sc = new SanctuaryScene(pal, flags, blocks, new SceneLevel(grid, x0, y0, z0, nx, ny, nz));
            int na = d.readInt();
            for (int i = 0; i < na; i++) {
                String name = d.readUTF();
                sc.anchors.put(name, new float[] {d.readFloat(), d.readFloat(), d.readFloat(), d.readFloat()});
            }
            int nc = d.readInt();
            for (int i = 0; i < nc; i++) {
                float[] k = new float[8];
                for (int j = 0; j < 8; j++) k[j] = d.readFloat();
                sc.camera.add(k);
            }
            float[] s = sc.anchor("sun");
            sc.sun.set(s[0], s[1], s[2]).normalize();
            float[] c = sc.anchor("crate");
            sc.crate.set(c[0], c[1], c[2]);
            return sc;
        }
    }

    // --------------------------------------------------------------------------------------------------- la luz
    /** Distancia de un punto al eje del haz de sol (el que cae sobre la crate). */
    float beamOffset(float x, float y, float z) {
        float dx = x - crate.x, dy = y - (crate.y + 0.6F), dz = z - crate.z;
        float along = dx * sun.x + dy * sun.y + dz * sun.z;
        float ox = dx - sun.x * along, oy = dy - sun.y * along, oz = dz - sun.z * along;
        return (float)Math.sqrt(ox * ox + oy * oy + oz * oz);
    }

    /**
     * La luz de la escena en un punto (multiplicador de r, g, b): sombra del bosque fresca (0,74, algo azulada),
     * el haz de sol cálido y entero sobre la crate, manchas de sol entre las hojas en el suelo, y las copas de los
     * árboles más claras arriba (les da el sol).
     */
    void light(float x, float y, float z, float[] out) {
        float off = beamOffset(x, y, z);
        float beam = (float)Math.exp(-off * off / 9.0F);
        float dapple = dapple(x, z) * (y < 2.5F ? 1.0F : 0.4F);
        float top = Math.max(0.0F, Math.min(1.0F, (y - 6.0F) / 8.0F));
        float f = 0.74F + 0.26F * beam + 0.16F * dapple + 0.12F * top;
        float warm = Math.min(1.0F, beam * 1.2F + dapple * 0.5F + top * 0.4F);
        out[0] = Math.min(1.0F, f * (0.90F + 0.10F * warm));
        out[1] = Math.min(1.0F, f * (0.95F + 0.02F * warm));
        out[2] = Math.min(1.0F, f * (1.00F - 0.12F * warm));
    }

    /** Manchas de sol entre las hojas (ruido suave con umbral). */
    private static float dapple(float x, float z) {
        float n = vnoise(x * 0.35F, z * 0.35F) * 0.65F + vnoise(x * 0.9F + 13.1F, z * 0.9F - 7.7F) * 0.35F;
        float t = (n - 0.56F) / 0.16F;
        return t <= 0 ? 0 : t >= 1 ? 1 : t * t * (3 - 2 * t);
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    static float vnoise(float x, float z) {
        int ix = (int)Math.floor(x), iz = (int)Math.floor(z);
        float fx = x - ix, fz = z - iz;
        float sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        float a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        float ab = a + (b - a) * sx, cd = c + (d - c) * sx;
        return ab + (cd - ab) * sz;
    }

    /** Pasa los vértices con el color multiplicado por la luz de la escena en su sitio (o por un color fijo). */
    final class Shade implements VertexConsumer {
        VertexConsumer to;
        final float[] l = new float[3];
        boolean fixed;
        float fr = 1, fg = 1, fb = 1;

        Shade on(VertexConsumer to) {
            this.to = to;
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            if (fixed) {
                to.vertex(x, y, z, r * fr, g * fg, b * fb, a, u, v, overlay, light, nx, ny, nz);
            } else {
                light(x, y, z, l);
                to.vertex(x, y, z, r * l[0], g * l[1], b * l[2], a, u, v, overlay, light, nx, ny, nz);
            }
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            to.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            to.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            to.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            to.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            to.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            to.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            to.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            to.unsetDefaultColor();
        }
    }

    // ------------------------------------------------------------------------------------------ construir la escena
    /** Una planta que se mece: su sitio, su modelo, su capa, su tinte y su luz. */
    private record Plant(float x, float y, float z, BlockState state, BakedModel model, RenderType type, float r, float g, float b,
                         float lr, float lg, float lb, boolean upper, float amp, float phase) {}

    private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(),
            SceneRenderTypes.WATER};

    private static RenderType layerOf(RenderType rt) {
        if (rt == RenderType.solid() || rt == RenderType.cutoutMipped() || rt == RenderType.cutout()) return rt;
        return SceneRenderTypes.WATER;
    }

    /** Construye los búferes si hace falta (la primera vez o tras recargar los recursos). */
    public void ensureBuilt() {
        Minecraft mc = Minecraft.getInstance();
        Object key = mc.getBlockRenderer().getBlockModel(Blocks.STONE.defaultBlockState());
        if (key == builtFor && !buffers.isEmpty()) return;
        close();
        builtFor = key;
        long t0 = System.nanoTime();
        BlockRenderDispatcher disp = mc.getBlockRenderer();
        ModelBlockRenderer mbr = disp.getModelRenderer();
        RandomSource rand = RandomSource.create();
        PoseStack pose = new PoseStack();
        Shade shade = new Shade();
        int quads = 0;
        plants.clear();
        waterSprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation("minecraft", "block/water_still"));
        for (int[] b : blocks) {
            BlockState st = palette[b[3]];
            if ((flags[b[3]] & F_SWAY) != 0 && st.getRenderShape() == RenderShape.MODEL) {
                addPlant(st, new BlockPos(b[0], b[1], b[2]), disp.getBlockModel(st));
            }
        }
        for (RenderType layer : LAYERS) {
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(layer.mode(), layer.format());
            shade.on(bb);
            for (int[] b : blocks) {
                BlockState st = palette[b[3]];
                int fl = flags[b[3]];
                if (st.isAir() || (fl & F_SWAY) != 0) continue;
                BlockPos pos = new BlockPos(b[0], b[1], b[2]);
                if ((fl & F_WATER) != 0) {
                    if (layer == SceneRenderTypes.WATER) quads += water(bb, b[0], b[1], b[2]);
                    continue;
                }
                if (st.getRenderShape() != RenderShape.MODEL) continue;
                BakedModel model = disp.getBlockModel(st);
                long seed = st.getSeed(pos);
                rand.setSeed(seed);
                ChunkRenderTypeSet types = model.getRenderTypes(st, rand, ModelData.EMPTY);
                for (RenderType rt : types) {
                    if (layerOf(rt) != layer) continue;
                    pose.pushPose();
                    pose.translate(b[0], b[1], b[2]);
                    rand.setSeed(seed);
                    mbr.tesselateBlock(level, model, st, pos, pose, shade, true, rand, seed, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, rt);
                    pose.popPose();
                }
            }
            if (layer == SceneRenderTypes.WATER) {
                float[] c = camera.isEmpty() ? new float[8] : camera.get(Math.min(camera.size() - 1, 5));
                bb.setQuadSorting(VertexSorting.byDistance(c[1], c[2], c[3]));
            }
            BufferBuilder.RenderedBuffer rb = bb.endOrDiscardIfEmpty();
            if (rb != null) {
                VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vb.bind();
                vb.upload(rb);
                VertexBuffer.unbind();
                buffers.put(layer, vb);
            }
        }
        FSCrates.LOGGER.info("[TF Crates] Santuario construido: {} bloques, {} plantas al viento, {} ms", blocks.size(), plants.size(),
                (System.nanoTime() - t0) / 1_000_000);
    }

    private void addPlant(BlockState st, BlockPos pos, BakedModel model) {
        Minecraft mc = Minecraft.getInstance();
        int tint = mc.getBlockColors().getColor(st, level, pos, 0);
        if (tint == -1) tint = 0xFFFFFF;
        float[] l = new float[3];
        light(pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F, l);
        boolean upper = st.hasProperty(DoublePlantBlock.HALF) && st.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        boolean doubleP = st.hasProperty(DoublePlantBlock.HALF);
        float amp = doubleP ? 0.085F : (st.is(Blocks.GRASS) || st.is(Blocks.FERN) ? 0.07F : 0.05F);
        float phase = hash(pos.getX() * 3 + 1, pos.getZ() * 5 - 2) * 6.2832F;
        for (RenderType t : model.getRenderTypes(st, RandomSource.create(st.getSeed(pos)), ModelData.EMPTY)) {
            plants.add(new Plant(pos.getX(), pos.getY(), pos.getZ(), st, model, t, ((tint >> 16) & 255) / 255.0F,
                    ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F, l[0], l[1], l[2], upper, amp, phase));
        }
    }

    /** La superficie del agua (14/16 de alto si no hay agua encima), con la textura del agua quieta y su color. */
    private int water(BufferBuilder bb, int x, int y, int z) {
        BlockState above = level.get(x, y + 1, z);
        if (!above.getFluidState().isEmpty()) return 0;
        TextureAtlasSprite sp = waterSprite;
        float h = y + 0.875F;
        float[] l = new float[3];
        int c = SceneLevel.WATER;
        float r = ((c >> 16) & 255) / 255.0F, g = ((c >> 8) & 255) / 255.0F, b = (c & 255) / 255.0F;
        int light = LightTexture.FULL_BRIGHT;
        float[][] corners = {{x, z}, {x, z + 1}, {x + 1, z + 1}, {x + 1, z}};
        float[][] uv = {{sp.getU0(), sp.getV0()}, {sp.getU0(), sp.getV1()}, {sp.getU1(), sp.getV1()}, {sp.getU1(), sp.getV0()}};
        for (int i = 0; i < 4; i++) {
            light(corners[i][0], h, corners[i][1], l);
            bb.vertex(corners[i][0], h, corners[i][1]).color(r * l[0], g * l[1], b * l[2], 0.78F).uv(uv[i][0], uv[i][1]).uv2(light)
                    .normal(0, 1, 0).endVertex();
        }
        return 1;
    }

    // ----------------------------------------------------------------------------------------------------- dibujar
    /**
     * Dibuja lo quieto (con la cámara y la proyección dadas; la niebla y lo demás, de RenderSystem). Lo translúcido
     * (el agua) va aparte, después de lo dinámico opaco: drawWater.
     */
    public void drawOpaque(Matrix4f view, Matrix4f proj) {
        for (int i = 0; i < 3; i++) draw(LAYERS[i], view, proj);
    }

    public void drawWater(Matrix4f view, Matrix4f proj) {
        draw(SceneRenderTypes.WATER, view, proj);
    }

    private void draw(RenderType rt, Matrix4f view, Matrix4f proj) {
        VertexBuffer vb = buffers.get(rt);
        if (vb == null) return;
        rt.setupRenderState();
        ShaderInstance sh = RenderSystem.getShader();
        if (sh != null) {
            if (sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
            vb.bind();
            vb.drawWithShader(view, proj, sh);
            VertexBuffer.unbind();
        }
        rt.clearRenderState();
    }

    /**
     * La hierba, las flores y los helechos, mecidos por el viento (se doblan desde el suelo: la mitad de arriba de
     * las plantas altas sigue a la de abajo). {@code pose} ya lleva la cámara; {@code gust} sopla más fuerte (la
     * apertura de la crate).
     */
    public void drawPlants(PoseStack pose, MultiBufferSource buffers, float time, float gust) {
        ModelBlockRenderer mbr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        Shade shade = new Shade();
        shade.fixed = true;
        Matrix4f shear = new Matrix4f();
        for (Plant p : plants) {
            float w = (float)(Math.sin(time * 1.7F + p.x * 0.35F + p.z * 0.22F + p.phase * 0.3F) * 0.6F
                    + Math.sin(time * 2.9F + p.x * 0.13F - p.z * 0.4F + p.phase) * 0.4F);
            float sx = p.amp * (w * 0.8F + 0.45F + gust * 2.4F * radialX(p));
            float sz = p.amp * (w * 0.35F + gust * 2.4F * radialZ(p));
            pose.pushPose();
            pose.translate(p.x + (p.upper ? sx : 0.0F), p.y, p.z + (p.upper ? sz : 0.0F));
            shear.identity();
            shear.m10(sx);
            shear.m12(sz);
            pose.last().pose().mul(shear);
            shade.fr = p.lr;
            shade.fg = p.lg;
            shade.fb = p.lb;
            mbr.renderModel(pose.last(), shade.on(buffers.getBuffer(p.type)), p.state, p.model, p.r, p.g, p.b, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, p.type);
            pose.popPose();
        }
    }

    private float radialX(Plant p) {
        float dx = p.x + 0.5F - crate.x, dz = p.z + 0.5F - crate.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz) + 1.0F;
        return dx / d / (1.0F + d * 0.15F);
    }

    private float radialZ(Plant p) {
        float dx = p.x + 0.5F - crate.x, dz = p.z + 0.5F - crate.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz) + 1.0F;
        return dz / d / (1.0F + d * 0.15F);
    }

    /** Un consumidor que multiplica el color de todo lo que pasa por (r, g, b): la luz del sitio para lo que se mueve. */
    public VertexConsumer tinted(VertexConsumer to, float r, float g, float b) {
        Shade s = new Shade();
        s.fixed = true;
        s.fr = r;
        s.fg = g;
        s.fb = b;
        return s.on(to);
    }

    public Vector3f sunDir() {
        return new Vector3f(sun);
    }

    /** Libera los búferes de la tarjeta gráfica. */
    public void close() {
        for (VertexBuffer vb : buffers.values()) vb.close();
        buffers.clear();
    }

    /** Para las pruebas: cuántas plantas y capas hay. */
    public String stats() {
        return blocks.size() + " bloques, " + plants.size() + " plantas, capas " + buffers.size();
    }

    static VertexFormat blockFormat() {
        return DefaultVertexFormat.BLOCK;
    }
}
