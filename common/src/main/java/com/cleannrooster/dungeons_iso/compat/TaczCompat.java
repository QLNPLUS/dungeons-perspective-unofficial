package com.cleannrooster.dungeons_iso.compat;

import net.minecraft.entity.Entity;

/** Optional TACZ identification, resolved lazily so TACZ stays a soft dependency. */
public final class TaczCompat {
    private static Class<?> bulletClass;
    private static boolean resolved;

    private TaczCompat() {}

    /** True when the entity is a TACZ kinetic bullet. Costs one class lookup, then an instanceof. */
    public static boolean isKineticBullet(Entity entity) {
        if (!resolved) {
            resolved = true;
            try {
                bulletClass = Class.forName("com.tacz.guns.entity.EntityKineticBullet", false,
                        TaczCompat.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError absent) {
                // TACZ not installed: identification stays off and every other entity is untouched.
            }
        }
        return bulletClass != null && bulletClass.isInstance(entity);
    }
}
