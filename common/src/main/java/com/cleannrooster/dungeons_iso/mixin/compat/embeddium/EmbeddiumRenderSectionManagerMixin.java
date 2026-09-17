package com.cleannrooster.dungeons_iso.mixin.compat.embeddium;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.api.cullers.room.TerrainCulling;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Embeddium's section visibility graph from hiding terrain behind a culling opening.
 *
 * <p>The graph is rebuilt by Embeddium eventually, but it can still describe the pre-cull closed
 * sections while a camera orbit is already using a new mask. During that window an entire side of
 * the world can disappear. The explicit config switch remains available; the active cull path also
 * disables the graph because correctness is more important than a stale occlusion answer here.</p>
 */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class EmbeddiumRenderSectionManagerMixin {

    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true, remap = false)
    private void dungeons$disableStaleOcclusion(Camera camera, boolean spectator,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (Mod.enabled && !TerrainCulling.idle()
                && (Config.GSON.instance().disableOcclusionCulling
                || Config.GSON.instance().roomCulling
                || Config.GSON.instance().shapeCulling)) {
            cir.setReturnValue(false);
        }
    }
}
