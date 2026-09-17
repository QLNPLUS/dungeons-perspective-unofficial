package com.cleannrooster.dungeons_iso.compat.embeddium;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * Mapped Minecraft-only view of Embeddium's optional block-build context.
 *
 * This interface intentionally lives outside the mixin package because Embeddium's target
 * classes must be able to reference it while the mixin package is being transformed.
 */
public interface EmbeddiumBlockRenderContextAccess {
    BlockPos dungeons$getPos();

    BlockState dungeons$getState();
}
