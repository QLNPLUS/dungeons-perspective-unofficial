package com.cleannrooster.dungeons_iso.mixin.compat.embeddium;

import com.cleannrooster.dungeons_iso.compat.embeddium.EmbeddiumBlockRenderContextAccess;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes only the two Minecraft values needed by the renderer hook. */
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext",
        remap = false)
public abstract class EmbeddiumBlockRenderContextMixin implements EmbeddiumBlockRenderContextAccess {

    @Shadow(remap = false)
    public abstract BlockPos pos();

    @Shadow(remap = false)
    public abstract BlockState state();

    @Override
    public final BlockPos dungeons$getPos() {
        return pos();
    }

    @Override
    public final BlockState dungeons$getState() {
        return state();
    }
}
