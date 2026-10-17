package net.tierrasfantasticas.tfclient.vacio;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * El generador de The Void: vacío por todas partes salvo la isla ({@link VoidIsland}), que cada chunk copia al nacer.
 * Sin cuevas, sin superficie, sin criaturas al generar y sin estructuras (su bioma no está en ninguna lista).
 */
public class VoidIslandGenerator extends ChunkGenerator {
    public static final Codec<VoidIslandGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, i.stable(VoidIslandGenerator::new)));

    public VoidIslandGenerator(BiomeSource source) {
        super(source);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes, StructureManager structures,
                             ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 256;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random,
                                                        StructureManager structures, ChunkAccess chunk) {
        VoidIsland island = VoidIsland.get();
        if (island != null) island.fill(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        VoidIsland island = VoidIsland.get();
        if (island == null) return level.getMinBuildHeight();
        return island.height(x, z, level.getMinBuildHeight(), type.isOpaque());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        VoidIsland island = VoidIsland.get();
        int min = level.getMinBuildHeight(), h = level.getHeight();
        if (island == null) {
            net.minecraft.world.level.block.state.BlockState[] air = new net.minecraft.world.level.block.state.BlockState[h];
            java.util.Arrays.fill(air, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            return new NoiseColumn(min, air);
        }
        return new NoiseColumn(min, island.column(x, z, min, h));
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("TF Void: isla del vacío");
    }
}
