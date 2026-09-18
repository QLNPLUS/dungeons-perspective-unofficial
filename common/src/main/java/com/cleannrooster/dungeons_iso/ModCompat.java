package com.cleannrooster.dungeons_iso;

/**
 * Cross-platform mod detection and environment utilities.
 * Works at any point in the startup lifecycle — including mixin application time.
 *
 * Strategy: try Fabric's FabricLoader first; if it is missing at runtime, fall back to the
 * Forge or NeoForge loader through reflection so this class never has a hard loader dependency.
 */
public class ModCompat {

    /**
     * Embeddium 0.3.x exposes the old Sodium-compatible renderer under its own mod ids. Keep this
     * test in one place because the renderer hooks and the rebuild scheduler must agree about
     * which chunk pipeline is active.
     */
    public static boolean isSodiumLikeLoaded() {
        return isModLoaded("sodium") || isModLoaded("embeddium") || isModLoaded("rubidium");
    }

    public static boolean isModLoaded(String modId) {
        // Fabric
        try {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId)) {
                return true;
            }
        } catch (Throwable ignored) {}
        // Forge / NeoForge
        for (String className : new String[]{"net.minecraftforge.fml.ModList", "net.neoforged.fml.ModList"}) {
            try {
                Class<?> modListClass = Class.forName(className);
                Object modList = modListClass.getMethod("get").invoke(null);
                if (modList != null) {
                    return (boolean) modListClass.getMethod("isLoaded", String.class).invoke(modList, modId);
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * True when the class is on the classpath. Preferred over {@link #isModLoaded} whenever the
     * question is "can I call this code", because it is answerable at any point in startup —
     * NeoForge's {@code ModList} does not exist yet while the earliest mixins are running.
     */
    public static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, ModCompat.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Checks optional target availability without defining it during Mixin selection. */
    public static boolean isClassResourcePresent(String className) {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader ownLoader = ModCompat.class.getClassLoader();
        return (contextLoader != null && contextLoader.getResource(resource) != null)
                || (ownLoader != null && ownLoader != contextLoader
                    && ownLoader.getResource(resource) != null)
                || ClassLoader.getSystemResource(resource) != null;
    }

    /** The loader's config directory used by the JSON config backend. */
    public static java.nio.file.Path getConfigDir() {
        // Fabric
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        } catch (Throwable ignored) {}
        // Forge / NeoForge
        for (String className : new String[]{
                "net.minecraftforge.fml.loading.FMLPaths",
                "net.neoforged.fml.loading.FMLPaths"}) {
            try {
                Class<?> fmlPaths = Class.forName(className);
                Object configDir = fmlPaths.getField("CONFIGDIR").get(null);
                return (java.nio.file.Path) fmlPaths.getMethod("get").invoke(configDir);
            } catch (Throwable ignored) {
            }
        }
        // Absolute, because callers take getParent() of this to find the game root and
        // Path.of("config").getParent() is null.
        return java.nio.file.Path.of("config").toAbsolutePath();
    }

    public static boolean isDevelopmentEnvironment() {
        // Fabric
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Throwable ignored) {}
        // Forge / NeoForge — FMLLoader.isProduction() returns true when NOT in dev
        for (String className : new String[]{
                "net.minecraftforge.fml.loading.FMLLoader",
                "net.neoforged.fml.loading.FMLLoader"}) {
            try {
                Class<?> fmlLoaderClass = Class.forName(className);
                return !(boolean) fmlLoaderClass.getMethod("isProduction").invoke(null);
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
