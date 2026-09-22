package com.cleannrooster.dungeons_iso.mixin.compat.entityculling;

import com.cleannrooster.dungeons_iso.mod.Mod;
import com.cleannrooster.dungeons_iso.util.EntityVisibility;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Entity Culling keep the displaced camera's protected entities renderable. */
@Pseudo
@Mixin(targets = "dev.tr7zw.entityculling.NMSCullingHelper", remap = false)
public abstract class EntityCullingHelperMixin {
    @Inject(method = "ignoresCulling", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dungeons$keepProtectedEntities(Entity entity,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (Mod.enabled && EntityVisibility.isProtected(entity)) {
            cir.setReturnValue(true);
        }
    }
}
