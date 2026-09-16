package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.api.cullers.room.GhostRenderer;
import com.cleannrooster.dungeons_iso.api.cullers.room.CullingBackdrop;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips vanilla sky rendering while room culling supplies an opaque backdrop. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Inject(
            method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void hideSkyBehindCulledCaves(MatrixStack matrices, Matrix4f projectionMatrix,
                                          float tickDelta, Camera camera, boolean thickFog,
                                          Runnable fogCallback, CallbackInfo ci) {
        if (CullingBackdrop.hidesSky()) {
            // Do not depend on an earlier framebuffer clear retaining BackgroundRenderer's colour:
            // loader hooks and renderer replacements can move that work around. The sky stage is
            // before terrain, so explicitly replacing only the colour buffer here is deterministic
            // and cannot erase terrain or depth that has already been drawn.
            RenderSystem.clearColor(CullingBackdrop.red(), CullingBackdrop.green(),
                    CullingBackdrop.blue(), 1.0F);
            RenderSystem.clear(0x4000, MinecraftClient.IS_SYSTEM_MAC);
            ci.cancel();
        }
    }

    /**
     * Keep the original world-render ordering without relying on MixinExtras local capture. The
     * Forge 1.20.1 artifact does not ship MixinExtras, while a redirect receives the exact matrix
     * stack and immediate buffer passed to DebugRenderer.
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V"
            )
    )
    private void renderDebugWithGhost(DebugRenderer debugRenderer, MatrixStack matrices,
                                      VertexConsumerProvider.Immediate buffers,
                                      double cameraX, double cameraY, double cameraZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            GhostRenderer.render(matrices, buffers, client.gameRenderer.getCamera(), client.gameRenderer);
        } catch (Exception ignored) {
            // The fallback must never take down the vanilla world render.
        }
        debugRenderer.render(matrices, buffers, cameraX, cameraY, cameraZ);
    }
}
