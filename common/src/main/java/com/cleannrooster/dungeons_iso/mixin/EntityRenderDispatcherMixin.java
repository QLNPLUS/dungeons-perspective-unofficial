package com.cleannrooster.dungeons_iso.mixin;

import com.cleannrooster.dungeons_iso.api.cullers.room.CullDebug;
import com.cleannrooster.dungeons_iso.mod.Mod;
import com.cleannrooster.dungeons_iso.util.EntityScreenCuller;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses the closer logical or displaced camera position for entity distance checks. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Redirect(
            method = "shouldRender",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EntityRenderer;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"
            )
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean dungeons$useNearestRenderDistanceOrigin(
            EntityRenderer renderer, Entity entity, Frustum frustum,
            double cameraX, double cameraY, double cameraZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Mod.enabled || client.player == null) {
            return renderer.shouldRender(entity, frustum, cameraX, cameraY, cameraZ);
        }

        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();
        double playerDistanceSquared = entity.squaredDistanceTo(playerX, playerY, playerZ);
        double cameraDistanceSquared = entity.squaredDistanceTo(cameraX, cameraY, cameraZ);
        boolean usePlayerPosition = playerDistanceSquared < cameraDistanceSquared;
        double distanceX = usePlayerPosition ? playerX : cameraX;
        double distanceY = usePlayerPosition ? playerY : cameraY;
        double distanceZ = usePlayerPosition ? playerZ : cameraZ;

        if (CullDebug.isCollectingEntityVisibility()
                && !entity.shouldRender(cameraX, cameraY, cameraZ)
                && entity.shouldRender(distanceX, distanceY, distanceZ)) {
            CullDebug.recordEntityDistanceRescue(
                    entity,
                    Math.sqrt(entity.squaredDistanceTo(cameraX, cameraY, cameraZ)),
                    Math.sqrt(entity.squaredDistanceTo(playerX, playerY, playerZ)));
        }

        // EntityRenderer first applies the entity's distance limit, then tests its bounding box
        // against the supplied frustum. Use whichever origin is closer for the distance check;
        // keep the actual camera frustum unchanged so off-screen entities are still culled.
        return renderer.shouldRender(entity, frustum, distanceX, distanceY, distanceZ);
    }

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private <E extends Entity> void dungeons$recordVisibilityDecision(
            E entity,
            Frustum frustum,
            double cameraX,
            double cameraY,
            double cameraZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && EntityScreenCuller.isOutsideScreen(entity)) {
            cir.setReturnValue(false);
            CullDebug.recordEntityScreenCull(entity);
        }
        CullDebug.recordEntityRenderCheck(
                entity,
                Boolean.TRUE.equals(cir.getReturnValue()),
                cameraX,
                cameraY,
                cameraZ);
    }
}
