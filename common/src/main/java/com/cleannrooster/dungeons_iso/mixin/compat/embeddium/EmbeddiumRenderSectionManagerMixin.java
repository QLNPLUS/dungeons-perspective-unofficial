package com.cleannrooster.dungeons_iso.mixin.compat.embeddium;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.api.cullers.room.TerrainCulling;
import com.cleannrooster.dungeons_iso.api.cullers.room.ChunkRebuildScheduler;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Embeddium's section visibility graph from hiding terrain behind a culling opening.
 *
 * <p>The graph is rebuilt when the scanners report affected sections. Keeping Embeddium's normal
 * occlusion path active is important: disabling it makes every section in the frustum eligible for
 * drawing and can turn a camera orbit into a large frame-time spike. The explicit config switch is
 * retained for installations that still encounter stale visibility data.</p>
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class EmbeddiumRenderSectionManagerMixin {

    /**
     * Embeddium can retain a stale occlusion graph after a custom chunk mesh removes a large
     * silhouette. If it reports only a handful of visible sections while the culler is active,
     * briefly run without the graph so the renderer can recover instead of showing a black world.
     */
    private static volatile long dungeons$occlusionFallbackUntil;

    private static boolean dungeons$needsOcclusionFallback() {
        long now = System.nanoTime();
        if (now < dungeons$occlusionFallbackUntil) {
            return true;
        }
        if (!Mod.enabled || !Mod.shouldRebuild() || TerrainCulling.idle()) {
            return false;
        }
        int visible = ChunkRebuildScheduler.get().visibleSectionCount();
        // With the current isometric reach, 0-4 visible sections is a stale-graph signature, not
        // a useful occlusion result. Keep the fallback latched briefly to avoid frame-by-frame
        // oscillation while Embeddium rebuilds its visibility data.
        if (visible >= 0 && visible <= 4) {
            dungeons$occlusionFallbackUntil = now + 1_000_000_000L;
            return true;
        }
        return false;
    }

    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true, remap = false)
    private void dungeons$extendEffectiveRenderDistance(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(dungeons$cameraAwareRenderDistance(cir.getReturnValue()));
    }

    @Inject(method = "getRenderDistance", at = @At("RETURN"), cancellable = true, remap = false)
    private void dungeons$extendRenderDistance(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(dungeons$cameraAwareRenderDistance(cir.getReturnValue()));
    }

    private float dungeons$cameraAwareRenderDistance(float distance) {
        if (!Mod.enabled || MinecraftClient.getInstance().player == null
                || !Config.GSON.instance().renderDistanceCap) {
            return distance;
        }
        float cameraReach = Mod.getZoom() * Mod.zoomMetric;
        return Math.max(distance, (cameraReach + 64.0F) * 1.15F);
    }

    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true, remap = false)
    private void dungeons$disableConfiguredOcclusion(Camera camera, boolean spectator,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (Mod.enabled && Mod.shouldRebuild() && !TerrainCulling.idle()
                && (Config.GSON.instance().disableOcclusionCulling
                || dungeons$needsOcclusionFallback())) {
            cir.setReturnValue(false);
        }
    }
}
