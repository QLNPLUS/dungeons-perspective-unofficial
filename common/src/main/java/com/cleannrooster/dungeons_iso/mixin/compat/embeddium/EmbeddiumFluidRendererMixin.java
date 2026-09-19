package com.cleannrooster.dungeons_iso.mixin.compat.embeddium;

import com.cleannrooster.dungeons_iso.api.cullers.room.CullDebug;
import com.cleannrooster.dungeons_iso.api.cullers.room.TerrainCulling;
import com.cleannrooster.dungeons_iso.mod.Mod;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the same culling decision to Embeddium's separate fluid mesh pass. */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.FluidRenderer",
        remap = false)
public abstract class EmbeddiumFluidRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void dungeons$cullFluid(WorldSlice world, FluidState fluidState, BlockPos pos,
                                    BlockPos origin, ChunkBuildBuffers buffers, CallbackInfo ci) {
        if (!Mod.enabled || TerrainCulling.idle() || fluidState == null || fluidState.isEmpty()) {
            return;
        }
        try {
            if (TerrainCulling.shouldRemoveFluid(fluidState, pos.getX(), pos.getY(), pos.getZ())) {
                CullDebug.recordFluidCulled();
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
