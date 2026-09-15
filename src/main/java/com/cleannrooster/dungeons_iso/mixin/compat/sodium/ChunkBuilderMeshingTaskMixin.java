package com.cleannrooster.dungeons_iso.mixin.compat.sodium;

import com.cleannrooster.dungeons_iso.compat.SodiumCompat;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import me.jellysquid.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import me.jellysquid.mods.sodium.client.util.task.CancellationToken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkBuilderMeshingTask.class)
@Pseudo
public abstract class ChunkBuilderMeshingTaskMixin {
    @Inject(method = "execute", at = @At("HEAD"), remap = false)
    private void dungeons$beginSnapshot(ChunkBuildContext context, CancellationToken cancellationToken,
                                        CallbackInfoReturnable<ChunkBuildOutput> cir) {
        SodiumCompat.beginChunkBuild();
    }

    @Inject(method = "execute", at = @At("RETURN"), remap = false)
    private void dungeons$endSnapshot(ChunkBuildContext context, CancellationToken cancellationToken,
                                      CallbackInfoReturnable<ChunkBuildOutput> cir) {
        SodiumCompat.endChunkBuild();
    }
}
