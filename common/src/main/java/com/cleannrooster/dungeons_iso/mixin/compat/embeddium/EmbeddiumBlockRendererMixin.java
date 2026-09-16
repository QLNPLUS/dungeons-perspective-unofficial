package com.cleannrooster.dungeons_iso.mixin.compat.embeddium;

import com.cleannrooster.dungeons_iso.api.cullers.room.TerrainCulling;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Embeddium 0.3.31's block mesher lives under the old {@code me.jellysquid} namespace. The
 * arguments stay as {@code Object} so this source has no hard optional dependency on Embeddium.
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
        remap = false)
public abstract class EmbeddiumBlockRendererMixin {

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void dungeons$cullBlock(Object context, Object buffers, CallbackInfo ci) {
        if (!Mod.enabled || TerrainCulling.idle()
                || !(context instanceof EmbeddiumBlockRenderContextAccess access)) {
            return;
        }
        try {
            BlockPos pos = access.dungeons$getPos();
            if (pos != null && TerrainCulling.shouldRemove(access.dungeons$getState(),
                    pos.getX(), pos.getY(), pos.getZ())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "isFaceVisible", at = @At("HEAD"), cancellable = true, remap = false)
    private void dungeons$restoreFace(Object context, Direction direction,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!Mod.enabled || TerrainCulling.idle() || direction == null
                || !(context instanceof EmbeddiumBlockRenderContextAccess access)) {
            return;
        }
        try {
            BlockPos pos = access.dungeons$getPos();
            BlockPos neighbour = pos.offset(direction);
            if (TerrainCulling.isRemoved(neighbour.getX(), neighbour.getY(), neighbour.getZ())) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }
}
