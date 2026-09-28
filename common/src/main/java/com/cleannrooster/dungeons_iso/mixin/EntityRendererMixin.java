package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.api.cullers.room.CullDebug;
import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Gives tiny pickups and creatures the same minimum distance reach as a normal-sized entity. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Redirect(
            method = "shouldRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;shouldRender(DDD)Z"
            )
    )
    private boolean dungeons$extendPickupEntityDistance(
            Entity entity, double renderOriginX, double renderOriginY, double renderOriginZ) {
        boolean vanillaVisible = entity.shouldRender(renderOriginX, renderOriginY, renderOriginZ);
        if (vanillaVisible || !Mod.enabled
                || (!(entity instanceof ItemEntity)
                && !(entity instanceof ExperienceOrbEntity)
                && !(entity instanceof LivingEntity))) {
            return vanillaVisible;
        }

        double boxSize = entity.getBoundingBox().getAverageSideLength();
        if (Double.isNaN(boxSize) || boxSize >= 1.0D) {
            return vanillaVisible;
        }

        double distanceMultiplier = Entity.getRenderDistanceMultiplier();
        double vanillaRange = boxSize * 64.0D * distanceMultiplier;
        double extendedRange = 64.0D * distanceMultiplier;
        double distanceSquared = entity.squaredDistanceTo(renderOriginX, renderOriginY, renderOriginZ);
        boolean extendedVisible = distanceSquared < extendedRange * extendedRange;
        if (extendedVisible && CullDebug.isCollectingEntityVisibility()) {
            CullDebug.recordEntitySizeDistanceRescue(
                    entity, Math.sqrt(distanceSquared), vanillaRange, extendedRange);
        }
        return extendedVisible;
    }
}
