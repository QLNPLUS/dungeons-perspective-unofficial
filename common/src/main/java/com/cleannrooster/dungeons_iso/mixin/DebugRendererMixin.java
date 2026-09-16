package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.ClientInit;
import com.cleannrooster.dungeons_iso.api.cullers.room.GhostRenderer;
import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws mod overlays immediately before vanilla's debug render pass. */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void drawWorldOverlaysXIV(MatrixStack matrices,
                                      VertexConsumerProvider.Immediate buffers,
                                      double cameraX, double cameraY, double cameraZ,
                                      CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        GameRenderer gameRenderer = client.gameRenderer;
        Camera camera = gameRenderer.getCamera();
        try {
            GhostRenderer.render(matrices, buffers, camera, gameRenderer);
        } catch (Exception ignored) {
        }
        if (!Mod.enabled || !Config.GSON.instance().isContextualInteract()) {
            return;
        }
        BlockPos pos = Mod.targetedInteractable;
        if (pos == null || client.world == null || client.player == null) {
            return;
        }
        World world = client.world;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(camera.getFocusedEntity()));
        if (shape.isEmpty()) {
            return;
        }
        Vec3d cam = camera.getPos();
        VertexConsumer lines = buffers.getBuffer(RenderLayer.getLines());
        ClientInit.drawCuboidShapeOutline(matrices, lines, shape,
                pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                1.0F, 0.82F, 0.15F, 0.9F);
    }
}
