package com.cleannrooster.dungeons_iso.api.cullers.room;

import com.cleannrooster.dungeons_iso.ModCompat;

/**
 * Re-meshes a chunk section, whichever chunk renderer is installed.
 *
 * <p>Culling is baked into chunk meshes, so a change in what the scanners cull only becomes visible
 * once the affected sections are rebuilt. Sodium and vanilla expose unrelated APIs for that, and
 * this is the seam between them.
 */
public interface ChunkRebuildScheduler {

    /** The fast renderer path is enabled for the detected Sodium-compatible implementation. */
    boolean ENABLE_SODIUM_COMPAT = true;

    /** Schedules a rebuild of the section at the given section coordinates. */
    void scheduleSection(int sectionX, int sectionY, int sectionZ);

    /** Marks the renderer's section visibility graph dirty after culling geometry changes. */
    default void scheduleVisibilityUpdate() {
    }

    /** Returns the renderer's current visible-section count, or -1 when unavailable. */
    default int visibleSectionCount() {
        return -1;
    }

    /**
     * The implementation for this installation, resolved once on first use.
     *
     * <p>Loaded reflectively on purpose. A direct {@code new SodiumRebuildScheduler()} here would
     * be resolved when <em>this</em> method is verified, not when the branch runs, which means a
     * no-Sodium install would hit a {@link NoClassDefFoundError} on the way to deciding it does not
     * have Sodium. Going through {@link Class#forName} keeps every reference to Sodium's classes
     * inside a class that is only ever loaded when Sodium is actually present.
     */
    static ChunkRebuildScheduler get() {
        return Holder.INSTANCE;
    }

    final class Holder {
        static final ChunkRebuildScheduler INSTANCE = resolve();

        private Holder() {
        }

        private static ChunkRebuildScheduler resolve() {
            if (ENABLE_SODIUM_COMPAT && ModCompat.isSodiumLikeLoaded()) {
                try {
                    String scheduler = ModCompat.isModLoaded("embeddium") || ModCompat.isModLoaded("rubidium")
                            ? "com.cleannrooster.dungeons_iso.compat.EmbeddiumRebuildScheduler"
                            : "com.cleannrooster.dungeons_iso.compat.SodiumRebuildScheduler";
                    return (ChunkRebuildScheduler) Class.forName(scheduler)
                            .getDeclaredConstructor()
                            .newInstance();
                } catch (Throwable ignored) {
                    // Sodium present but not the version we compile against — fall back rather
                    // than leaving culling with no way to show its results.
                }
            }
            return new VanillaRebuildScheduler();
        }
    }
}
