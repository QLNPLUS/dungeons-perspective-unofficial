package com.cleannrooster.dungeons_iso.mixin.compat.embeddium;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Mapped Minecraft-only view of Embeddium's optional block-build context. */
public interface EmbeddiumBlockRenderContextAccess {
    BlockPos dungeons$getPos();

    BlockState dungeons$getState();
}
