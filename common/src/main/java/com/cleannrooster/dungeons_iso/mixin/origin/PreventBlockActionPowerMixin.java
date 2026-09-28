package com.cleannrooster.dungeons_iso.mixin.origin;

import io.github.edwinmindcraft.apoli.common.power.PreventBlockActionPower;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.NonNullSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PreventBlockActionPower.class, remap = false)
public abstract class PreventBlockActionPowerMixin {

    @Inject(method = "isSelectionPrevented", at = @At("HEAD"), cancellable = true)
    private static void dungeons$allowBlockSelection(
            Entity entity,
            BlockPos pos,
            NonNullSupplier<BlockState> stateGetter,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
