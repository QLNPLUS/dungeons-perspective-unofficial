package com.cleannrooster.dungeons_iso.util;

import com.cleannrooster.dungeons_iso.mod.Mod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;

/** Keeps entities near the real player visible while the camera is displaced. */
public final class EntityVisibility {
    private static final double PROTECTED_RADIUS = 48.0D;
    private static final double PROTECTED_RADIUS_SQUARED = PROTECTED_RADIUS * PROTECTED_RADIUS;

    private EntityVisibility() {
    }

    public static boolean isProtected(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        return Mod.enabled
                && client.player != null
                && (entity == client.player
                || entity.squaredDistanceTo(client.player) <= PROTECTED_RADIUS_SQUARED);
    }

    public static boolean isProtected(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        return Mod.enabled
                && client.player != null
                && pos.toCenterPos().squaredDistanceTo(client.player.getPos()) <= PROTECTED_RADIUS_SQUARED;
    }
}
