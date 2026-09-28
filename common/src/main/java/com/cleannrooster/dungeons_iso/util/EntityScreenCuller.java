package com.cleannrooster.dungeons_iso.util;

import com.cleannrooster.dungeons_iso.config.Config;
import com.cleannrooster.dungeons_iso.mod.Mod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Holds the exact orthographic screen frustum alongside the wider world-culling frustum. */
public final class EntityScreenCuller {
    private static volatile Frustum screenFrustum;

    private EntityScreenCuller() {
    }

    /** Captures the projection currently used to draw the screen before the world frustum is widened. */
    public static void capture(MatrixStack matrices, Vec3d cameraPosition) {
        screenFrustum = null;
        if (!Mod.enabled || !Config.GSON.instance().ortho) {
            return;
        }

        Frustum frustum = new Frustum(
                new Matrix4f(matrices.peek().getPositionMatrix()),
                new Matrix4f(RenderSystem.getProjectionMatrix()));
        frustum.setPosition(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        screenFrustum = frustum;
    }

    /** Returns true only if the entity is outside the actual orthographic screen projection. */
    public static boolean isOutsideScreen(Entity entity) {
        Frustum frustum = screenFrustum;
        if (frustum == null || entity.ignoreCameraFrustum) {
            return false;
        }

        Box bounds = entity.getVisibilityBoundingBox().expand(0.5D);
        if (bounds.isNaN() || bounds.getAverageSideLength() == 0.0D) {
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();
            bounds = new Box(x - 2.0D, y - 2.0D, z - 2.0D,
                    x + 2.0D, y + 2.0D, z + 2.0D);
        }
        return !frustum.isVisible(bounds);
    }
}
