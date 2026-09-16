package com.cleannrooster.dungeons_iso.util;

import com.cleannrooster.dungeons_iso.api.cullers.room.TerrainCulling;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/** Visibility tests that agree with the blocks removed from the client terrain mesh. */
public final class CulledVisibility {

    private CulledVisibility() {
    }

    /** Returns false only when a real, non-culled collision block blocks the ray. */
    public static boolean canSee(MinecraftClient client, Vec3d start, Vec3d end,
                                 @Nullable BlockPos targetBlock) {
        if (client.world == null || client.player == null) {
            return false;
        }

        Vec3d rayStart = start;
        for (int pass = 0; pass < 64; pass++) {
            BlockHitResult hit = client.world.raycast(new RaycastContext(
                    rayStart, end, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, client.player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return true;
            }

            BlockPos hitPos = hit.getBlockPos();
            if (targetBlock != null && hitPos.equals(targetBlock)) {
                return true;
            }
            if (hitPos.equals(BlockPos.ofFloored(end))) {
                return true;
            }
            if (!TerrainCulling.shouldRemove(client.world.getBlockState(hitPos),
                    hitPos.getX(), hitPos.getY(), hitPos.getZ())) {
                return false;
            }

            Vec3d direction = end.subtract(rayStart);
            if (direction.lengthSquared() < 1.0E-8) {
                return true;
            }
            rayStart = hit.getPos().add(direction.normalize().multiply(1.0E-4));
        }
        return false;
    }
}
