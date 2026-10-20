package net.tierrasfantasticas.tfclient.crates.client.scene;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * El «mundo» del santuario para el renderizador de bloques de Minecraft: los bloques de la escena (para que cada cara
 * sepa si la tapa un vecino y para la oclusión ambiental), la luz (todo a 15: la luz y el sombreado de la escena los
 * pone SanctuaryScene en el color) y los colores de la hierba, las hojas y el agua (los de un bosque de flores).
 */
final class SceneLevel implements BlockAndTintGetter {
    /** Colores del bioma de la escena: hierba y hojas del pantano (verdes vivos) y el agua (azul, como en los tráileres). */
    static final int GRASS = 0x58A845, FOLIAGE = 0x45A032, WATER = 0x3C8CFF;

    private final BlockState[] states;
    final int x0, y0, z0, nx, ny, nz;

    SceneLevel(BlockState[] states, int x0, int y0, int z0, int nx, int ny, int nz) {
        this.states = states;
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    BlockState get(int x, int y, int z) {
        int i = x - x0, j = y - y0, k = z - z0;
        if (i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz) return Blocks.AIR.defaultBlockState();
        BlockState s = states[(i * ny + j) * nz + k];
        return s == null ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinBuildHeight() {
        return -64;
    }

    @Override
    public float getShade(Direction dir, boolean shade) {
        if (!shade) return 1.0F;
        return switch (dir) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            default -> 0.6F;
        };
    }

    /** No hay motor de luz: getBrightness y getRawBrightness no lo usan (todo a 15). */
    @Override
    public LevelLightEngine getLightEngine() {
        return null;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return Math.max(0, 15 - darkening);
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return true;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return FOLIAGE;
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return WATER;
        return GRASS;
    }
}
