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
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LiquidBlock;
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
    private static final int F_SWAY = 1, F_WATER = 2, F_GLOW = 4;
    private static SanctuaryScene instance;
    private static boolean failed;

    /** Puntos de la escena (x, y, z, w): crate, pool, sun, fall, fall_z, pond, flowers0..2. */
    public final Map<String, float[]> anchors = new HashMap<>();
    /** La ruta de la cámara: tick, ojo (3), mira (3), campo de visión. */
    public final List<float[]> camera = new ArrayList<>();
    /** Las luces mágicas (x, y, z, r, g, b, tamaño): faroles, setas, liquen, bayas, linternas de mar, amatistas... */
    public final List<float[]> lights = new ArrayList<>();
    /** Las luces por celdas de 4 bloques (para la luz de alrededor). */
    private final Map<Long, List<float[]>> lightGrid = new HashMap<>();
    final SceneLevel level;
    private final List<int[]> blocks;          // x, y, z, paleta
    private final BlockState[] palette;
    private final int[] flags;
    private final Vector3f sun = new Vector3f(), crate = new Vector3f();

    private final Map<RenderType, VertexBuffer> buffers = new HashMap<>();
    /** El agua para su shader (crate_water): superficie y cascadas, con la orilla, el brillo del fondo y la luz. */
    private VertexBuffer waterFx;
    private final List<Plant> plants = new ArrayList<>();
    private Object builtFor;

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
            int version = d.readInt();
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
            if (version >= 2) {
                int nl = d.readInt();
                for (int i = 0; i < nl; i++) {
                    float[] l = new float[7];
                    for (int j = 0; j < 7; j++) l[j] = d.readFloat();
                    sc.lights.add(l);
                    for (int gx = -1; gx <= 1; gx++) {
                        for (int gy = -1; gy <= 1; gy++) {
                            for (int gz = -1; gz <= 1; gz++) {
                                long key = cell((int)Math.floor(l[0] / 4) + gx, (int)Math.floor(l[1] / 4) + gy, (int)Math.floor(l[2] / 4) + gz);
                                sc.lightGrid.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
                            }
                        }
                    }
                }
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

    private static long cell(int x, int y, int z) {
        return ((long)(x & 0xFFFFF) << 40) | ((long)(y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    /**
     * La luz del Bosque de las Hadas en un punto (multiplicador de r, g, b): la penumbra del atardecer, fresca y algo
     * violeta; el haz de sol dorado que entra por el hueco de la cúpula y cae sobre la crate; manchas de sol en el
     * suelo; las copas, rosadas arriba (les da el último sol); y la luz de las luces mágicas de alrededor (faroles,
     * setas lámpara, liquen, bayas, el fondo del estanque), cada una de su color.
     */
    void light(float x, float y, float z, float[] out) {
        float off = beamOffset(x, y, z);
        float beam = (float)Math.exp(-off * off / 5.0F);
        float dapple = dapple(x, z) * (y < 2.5F ? 1.0F : 0.4F);
        float top = Math.max(0.0F, Math.min(1.0F, (y - 9.0F) / 9.0F));
        // el estanque alumbra de azul lo que tiene cerca (el pedestal, el borde), desde abajo
        float px = x - crate.x, pz = z - crate.z;
        float pond = (float)Math.exp(-(px * px + pz * pz) / 40.0F) * Math.max(0.0F, Math.min(1.0F, (4.0F - y) / 3.0F));
        float r = 0.34F, g = 0.40F, b = 0.56F;
        r += 1.25F * beam + 0.05F * dapple + 0.06F * top + 0.06F * pond;
        g += 1.05F * beam + 0.07F * dapple + 0.03F * top + 0.22F * pond;
        b += 0.62F * beam + 0.10F * dapple + 0.12F * top + 0.34F * pond;
        List<float[]> near = lightGrid.get(cell((int)Math.floor(x / 4), (int)Math.floor(y / 4), (int)Math.floor(z / 4)));
        if (near != null) {
            for (float[] l : near) {
                float dx = x - l[0], dy = y - l[1], dz = z - l[2];
                float d2 = dx * dx + dy * dy + dz * dz;
                float reach = 3.5F * l[6] + 1.5F;
                if (d2 >= reach * reach) continue;
                float k = 1.0F - (float)Math.sqrt(d2) / reach;
                k = k * k * 1.0F * Math.min(1.2F, l[6] + 0.3F);
                r += l[3] * k;
                g += l[4] * k;
                b += l[5] * k;
            }
        }
        out[0] = Math.min(1.0F, r);
        out[1] = Math.min(1.0F, g);
        out[2] = Math.min(1.0F, b);
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

    /**
     * Para el agua que dibuja el juego (LiquidBlockRenderer): pone sus vértices (relativos a su sección de 16) en su
     * sitio y les da la luz de la escena, con un mínimo para que el agua mágica se vea azul y viva en la penumbra.
     */
    final class Liquid implements VertexConsumer {
        VertexConsumer to;
        float ox, oy, oz;
        final float[] l = new float[3];

        Liquid on(VertexConsumer to, int x, int y, int z) {
            this.to = to;
            ox = x - (x & 15);
            oy = y - (y & 15);
            oz = z - (z & 15);
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            vertex((double)x, y, z);
            color(r, g, b, a);
            uv(u, v);
            uv2(light);
            normal(nx, ny, nz);
            endVertex();
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            float wx = (float)x + ox, wy = (float)y + oy, wz = (float)z + oz;
            light(wx, wy, wz, l);
            for (int i = 0; i < 3; i++) l[i] = Math.min(1.0F, Math.max(l[i], WATER_GLOW[i]) * 1.15F);
            to.vertex(wx, wy, wz);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            to.color((int)(r * l[0]), (int)(g * l[1]), (int)(b * l[2]), (int)(a * 0.86F));
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

    /** Lo menos que brilla el agua (es agua de hadas: azul y viva aunque esté en la sombra). */
    private static final float[] WATER_GLOW = {0.62F, 0.72F, 0.85F};

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
        Liquid liquid = new Liquid();
        int quads = 0;
        plants.clear();
        for (int[] b : blocks) {
            BlockState st = palette[b[3]];
            if (sways(b) && st.getRenderShape() == RenderShape.MODEL) {
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
                if (st.isAir() || sways(b)) continue;
                BlockPos pos = new BlockPos(b[0], b[1], b[2]);
                if ((fl & F_WATER) != 0 || !st.getFluidState().isEmpty()) {
                    // el agua, como la dibuja el juego (la que cae, la cascada, con su textura que corre)
                    if (layer == SceneRenderTypes.WATER && !st.getFluidState().isEmpty()) {
                        disp.renderLiquid(pos, level, liquid.on(bb, b[0], b[1], b[2]), st, st.getFluidState());
                        quads++;
                    }
                    if ((fl & F_WATER) != 0) continue;
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
                    shade.fixed = (fl & F_GLOW) != 0;
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
        buildWaterFx();
        FSCrates.LOGGER.info("[TF Crates] Santuario construido: {} bloques, {} plantas al viento, {} ms", blocks.size(), plants.size(),
                (System.nanoTime() - t0) / 1_000_000);
    }

    /** Las plantas que se mecen: las de cerca de la crate (las de lejos no se notan y van quietas en los búferes). */
    private boolean sways(int[] b) {
        if ((flags[b[3]] & F_SWAY) == 0) return false;
        float dx = b[0] + 0.5F - crate.x, dz = b[2] + 0.5F - crate.z;
        return dx * dx + dz * dz < SWAY_R * SWAY_R && b[2] > crate.z - 12.0F;
    }

    private static final float SWAY_R = 17.0F;

    private void addPlant(BlockState st, BlockPos pos, BakedModel model) {
        Minecraft mc = Minecraft.getInstance();
        int tint = mc.getBlockColors().getColor(st, level, pos, 0);
        if (tint == -1) tint = 0xFFFFFF;
        float[] l = new float[3];
        light(pos.getX() + 0.5F, pos.getY() + 0.3F, pos.getZ() + 0.5F, l);
        boolean upper = st.hasProperty(DoublePlantBlock.HALF) && st.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
        boolean doubleP = st.hasProperty(DoublePlantBlock.HALF);
        float amp = doubleP ? 0.085F : (st.is(Blocks.GRASS) || st.is(Blocks.FERN) ? 0.07F : 0.05F);
        float phase = hash(pos.getX() * 3 + 1, pos.getZ() * 5 - 2) * 6.2832F;
        for (RenderType t : model.getRenderTypes(st, RandomSource.create(st.getSeed(pos)), ModelData.EMPTY)) {
            plants.add(new Plant(pos.getX(), pos.getY(), pos.getZ(), st, model, t, ((tint >> 16) & 255) / 255.0F,
                    ((tint >> 8) & 255) / 255.0F, (tint & 255) / 255.0F, l[0], l[1], l[2], upper, amp, phase));
        }
    }

    // --------------------------------------------------------------------------------------------- el agua (shader)
    private boolean isWater(int x, int y, int z) {
        return !level.get(x, y, z).getFluidState().isEmpty();
    }

    private boolean solidAt(int x, int y, int z) {
        BlockState st = level.get(x, y, z);
        return !st.isAir() && st.isSolidRender(level, new BlockPos(x, y, z));
    }

    private boolean glowsAt(int x, int y, int z) {
        BlockState st = level.get(x, y, z);
        return st.is(Blocks.SEA_LANTERN) || st.is(Blocks.SEA_PICKLE);
    }

    /**
     * El agua para el shader crate_water (la misma cuenta que santuario_render.water_mesh): la superficie (14/16 de alto)
     * con, en cada esquina, cuánta orilla tiene (r), el brillo del fondo (b: linternas de mar, pepinos de mar) y la luz
     * de la escena (a); y los lados donde el agua da al aire (las cascadas y las fuentes: g = 1, con uv = (a lo ancho,
     * metros desde abajo)). El agua que cae va un poco más estrecha por los lados que dan al aire (un chorro, no un cubo).
     */
    private void buildWaterFx() {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        float[] l = new float[3];
        int n = 0;
        for (int[] b : blocks) {
            int x = b[0], y = b[1], z = b[2];
            if (!isWater(x, y, z)) continue;
            BlockState st = palette[b[3]];
            boolean above = isWater(x, y + 1, z);
            boolean falling = st.hasProperty(LiquidBlock.LEVEL) && st.getValue(LiquidBlock.LEVEL) >= 8;
            float h = above ? 1.0F : 0.875F;
            float in = falling ? 0.16F : 0.0F;
            float x0 = x + (isWater(x - 1, y, z) ? 0 : in), x1 = x + 1 - (isWater(x + 1, y, z) ? 0 : in);
            float z0 = z + (isWater(x, y, z - 1) ? 0 : in), z1 = z + 1 - (isWater(x, y, z + 1) ? 0 : in);
            if (!above) {
                int[][] cs = {{x, z}, {x + 1, z}, {x + 1, z + 1}, {x, z + 1}};
                float[][] ps = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                for (int i = 0; i < 4; i++) {
                    int cx = cs[i][0], cz = cs[i][1];
                    int dry = 0, glow = 0;
                    for (int dx = -1; dx <= 0; dx++) {
                        for (int dz = -1; dz <= 0; dz++) {
                            if (!isWater(cx + dx, y, cz + dz)) dry++;
                        }
                    }
                    for (int dx = -2; dx <= 1; dx++) {
                        for (int dz = -2; dz <= 1; dz++) {
                            if (glowsAt(cx + dx, y - 1, cz + dz) || glowsAt(cx + dx, y, cz + dz)) glow++;
                        }
                    }
                    light(cx, y + h, cz, l);
                    bb.vertex(ps[i][0], y + h, ps[i][1]).color(dry / 4.0F, 0.0F, Math.min(1.0F, glow * 0.3F),
                            Math.min(1.0F, (l[0] + l[1] + l[2]) / 3.0F)).uv(0, 0).endVertex();
                }
                n++;
            }
            int y0 = y;
            while (isWater(x, y0 - 1, z) && y0 > y - 30) y0--;
            // los lados que dan al aire: norte, sur, oeste, este
            int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int[] d : dirs) {
                if (isWater(x + d[0], y, z + d[1]) || solidAt(x + d[0], y, z + d[1])) continue;
                float[][] q;
                if (d[1] != 0) {
                    float zz = d[1] < 0 ? z0 : z1;
                    q = new float[][] {{x0, y + h, zz}, {x1, y + h, zz}, {x1, y, zz}, {x0, y, zz}};
                } else {
                    float xx = d[0] < 0 ? x0 : x1;
                    q = new float[][] {{xx, y + h, z0}, {xx, y + h, z1}, {xx, y, z1}, {xx, y, z0}};
                }
                for (float[] p : q) {
                    light(p[0], p[1], p[2], l);
                    bb.vertex(p[0], p[1], p[2]).color(0.0F, 1.0F, 0.0F, Math.min(1.0F, (l[0] + l[1] + l[2]) / 3.0F))
                            .uv((p[0] + p[2]) * 0.5F, p[1] - y0).endVertex();
                }
                n++;
            }
        }
        BufferBuilder.RenderedBuffer rb = bb.endOrDiscardIfEmpty();
        if (rb != null) {
            waterFx = new VertexBuffer(VertexBuffer.Usage.STATIC);
            waterFx.bind();
            waterFx.upload(rb);
            VertexBuffer.unbind();
        }
    }

    /**
     * El agua con su shader (crate_water); false si no se puede (entonces drawWater, la del juego). {@code time} en
     * ticks; {@code eye}, la cámara.
     */
    public boolean drawWaterFx(Matrix4f view, Matrix4f proj, Vector3f eye, float time) {
        ShaderInstance sh = ScenePost.water();
        if (sh == null || waterFx == null) return false;
        sh.safeGetUniform("CamPos").set(eye.x, eye.y, eye.z);
        sh.safeGetUniform("LightDir").set(sun.x, sun.y, sun.z);
        sh.safeGetUniform("BeamPos").set(crate.x, crate.y, crate.z);
        sh.safeGetUniform("Time").set(time);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        waterFx.bind();
        waterFx.drawWithShader(view, proj, sh);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
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
        if (waterFx != null) {
            waterFx.close();
            waterFx = null;
        }
    }

    /** Para las pruebas: cuántas plantas y capas hay. */
    public String stats() {
        return blocks.size() + " bloques, " + plants.size() + " plantas, capas " + buffers.size();
    }

    static VertexFormat blockFormat() {
        return DefaultVertexFormat.BLOCK;
    }
}
