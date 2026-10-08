package net.tierrasfantasticas.tfclient.claims.client;

import net.tierrasfantasticas.tfclient.TFClient;

import net.tierrasfantasticas.tfclient.claims.ClaimBlocks;
import net.tierrasfantasticas.tfclient.claims.client.ClientBorderStore;
import net.tierrasfantasticas.tfclient.claims.data.ClaimTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=TFClient.MOD_ID, value={Dist.CLIENT}, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimOutlineRenderer {
    private ClaimOutlineRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent renderlevelstageevent) {
        if (renderlevelstageevent.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer localplayer = minecraft.player;
            if (localplayer != null && minecraft.level != null) {
                Vec3 vec3 = renderlevelstageevent.getCamera().getPosition();
                PoseStack posestack = renderlevelstageevent.getPoseStack();
                MultiBufferSource.BufferSource buffersource = minecraft.renderBuffers().bufferSource();
                VertexConsumer vertexconsumer = buffersource.getBuffer(RenderType.lines());
                posestack.pushPose();
                posestack.translate(-vec3.x, -vec3.y, -vec3.z);
                for (double[] adouble : ClientBorderStore.current()) {
                    LevelRenderer.renderLineBox((PoseStack)posestack, (VertexConsumer)vertexconsumer, (double)adouble[0], (double)adouble[1], (double)adouble[2], (double)adouble[3], (double)adouble[4], (double)adouble[5], (float)((float)adouble[6]), (float)((float)adouble[7]), (float)((float)adouble[8]), (float)0.9f);
                }
                ClaimTier claimtier = ClaimBlocks.readTier(localplayer.getMainHandItem());
                if (claimtier == null) {
                    claimtier = ClaimBlocks.readTier(localplayer.getOffhandItem());
                }
                if (claimtier != null) {
                    BlockPos blockpos = localplayer.blockPosition();
                    double d0 = blockpos.getX() - claimtier.radius;
                    double d1 = blockpos.getX() + claimtier.radius + 1;
                    double d2 = blockpos.getZ() - claimtier.radius;
                    double d3 = blockpos.getZ() + claimtier.radius + 1;
                    double d4 = blockpos.getY() - claimtier.height;
                    double d5 = minecraft.level.getMaxBuildHeight();
                    LevelRenderer.renderLineBox((PoseStack)posestack, (VertexConsumer)vertexconsumer, (double)d0, (double)d4, (double)d2, (double)d1, (double)d5, (double)d3, (float)claimtier.r, (float)claimtier.g, (float)claimtier.b, (float)1.0f);
                }
                posestack.popPose();
                buffersource.endBatch(RenderType.lines());
            }
        }
    }
}

