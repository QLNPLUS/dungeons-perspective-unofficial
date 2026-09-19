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
    private static final Method SCHEDULE_TERRAIN_UPDATE;
    private static final Method GET_VISIBLE_CHUNKS;

    static {
        Method instance = null;
        Method scheduleArea = null;
        Method scheduleTerrainUpdate = null;
        Method getVisibleChunks = null;
        try {
            Class<?> renderer = Class.forName(RENDERER, false,
                    EmbeddiumRebuildScheduler.class.getClassLoader());
            instance = renderer.getMethod("instanceNullable");
            try {
                scheduleArea = renderer.getMethod("scheduleRebuildForBlockArea",
                        int.class, int.class, int.class, int.class, int.class, int.class, boolean.class);
            } catch (Throwable ignored) {
            }
            try {
                scheduleTerrainUpdate = renderer.getMethod("scheduleTerrainUpdate");
            } catch (Throwable ignored) {
            }
            try {
                getVisibleChunks = renderer.getMethod("getVisibleChunkCount");
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        INSTANCE = instance;
        SCHEDULE_AREA = scheduleArea;
        SCHEDULE_TERRAIN_UPDATE = scheduleTerrainUpdate;
        GET_VISIBLE_CHUNKS = getVisibleChunks;
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

    @Override
    public void scheduleVisibilityUpdate() {
        if (INSTANCE == null || SCHEDULE_TERRAIN_UPDATE == null) {
            return;
        }
        try {
            Object renderer = INSTANCE.invoke(null);
            if (renderer != null) {
                SCHEDULE_TERRAIN_UPDATE.invoke(renderer);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public int visibleSectionCount() {
        if (INSTANCE == null || GET_VISIBLE_CHUNKS == null) {
            return -1;
        }
        try {
            Object renderer = INSTANCE.invoke(null);
            if (renderer == null) {
                return -1;
            }
            return ((Number) GET_VISIBLE_CHUNKS.invoke(renderer)).intValue();
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
