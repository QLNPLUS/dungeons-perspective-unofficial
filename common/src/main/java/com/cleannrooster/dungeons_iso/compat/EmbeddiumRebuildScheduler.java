package com.cleannrooster.dungeons_iso.compat;

import com.cleannrooster.dungeons_iso.api.cullers.room.ChunkRebuildScheduler;

import java.lang.reflect.Method;

/**
 * Rebuilds sections through the Sodium-compatible renderer shipped by Embeddium 0.3.x.
 *
 * <p>The dependency is deliberately reflective: Forge installs without Embeddium, and this class
 * must still verify and load on a vanilla-renderer client.</p>
 */
public final class EmbeddiumRebuildScheduler implements ChunkRebuildScheduler {

    private static final String RENDERER =
            "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer";
    private static final Method INSTANCE;
    private static final Method SCHEDULE_AREA;

    static {
        Method instance = null;
        Method scheduleArea = null;
        try {
            Class<?> renderer = Class.forName(RENDERER, false,
                    EmbeddiumRebuildScheduler.class.getClassLoader());
            instance = renderer.getMethod("instanceNullable");
            scheduleArea = renderer.getMethod("scheduleRebuildForBlockArea",
                    int.class, int.class, int.class, int.class, int.class, int.class, boolean.class);
        } catch (Throwable ignored) {
        }
        INSTANCE = instance;
        SCHEDULE_AREA = scheduleArea;
    }

    @Override
    public void scheduleSection(int sectionX, int sectionY, int sectionZ) {
        if (INSTANCE == null || SCHEDULE_AREA == null) {
            return;
        }
        try {
            Object renderer = INSTANCE.invoke(null);
            if (renderer == null) {
                return;
            }
            int minX = sectionX << 4;
            int minY = sectionY << 4;
            int minZ = sectionZ << 4;
            SCHEDULE_AREA.invoke(renderer, minX, minY, minZ,
                    minX + 15, minY + 15, minZ + 15, false);
        } catch (Throwable ignored) {
        }
    }
}
