package com.cleannrooster.dungeons_iso.mixin.compat.sodium;

import com.cleannrooster.dungeons_iso.compat.SodiumCompat;
import com.cleannrooster.dungeons_iso.mod.Mod;
import me.jellysquid.mods.sodium.client.world.WorldSlice;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Embeddium's section compiler see the blocks removed by the perspective culler as air.
 * This must happen before its VisGraph is populated; cancelling only renderModel leaves the
 * original solid block in the occlusion graph and can hide an entire chain of sections.
 */
@Mixin(WorldSlice.class)
@Pseudo
public abstract class WorldSliceMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true, remap = false)
    private void dungeons$hideCulledBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (Mod.enabled && SodiumCompat.shouldCull(x, y, z, cir.getReturnValue())) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }
}
