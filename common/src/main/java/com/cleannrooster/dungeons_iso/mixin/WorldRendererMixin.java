package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.api.cullers.room.CullingBackdrop;
import com.cleannrooster.dungeons_iso.util.EntityVisibility;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips vanilla sky rendering while room culling supplies an opaque backdrop. */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    /**
     * The vanilla entity loop skips the focused player while the camera reports first person.
     * The displaced camera can briefly report that state during perspective changes, so keep the
     * protected player area on the render path even while the camera state catches up.
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"
            )
    )
    private boolean dungeons$keepProtectedEntityVisible(Camera camera) {
        return camera.isThirdPerson()
                || (camera.getFocusedEntity() != null
                && EntityVisibility.isProtected(camera.getFocusedEntity()));
    }

    /**
     * A displaced camera can be outside the section containing a nearby entity. Letting that
     * section fail the normal readiness check makes the entity disappear at certain camera angles,
     * even though it is close to the player and should remain available to render.
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z"
            )
    )
    private boolean dungeons$allowNearbyEntityRender(WorldRenderer renderer, BlockPos pos) {
        if (EntityVisibility.isProtected(pos)) {
            return true;
        }
        return renderer.isRenderingReady(pos);
    }

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

}
