package com.cleannrooster.dungeons_iso.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static final ConfigStore GSON = new ConfigStore();

    public boolean onStartup = true;
    public boolean force = false;
    public boolean allowManualToggle = true;
    public boolean scrollWheelZoom = true;
    public boolean dynamicCamera = false;
    public boolean forceNoDefer = false;
    public boolean cameraRelative = true;
    public boolean turnToMouse = true;
    public boolean forceAutoJump = true;
    public boolean rollTowardsCursor = true;
    public boolean lockOnTargeting = false;
    public float moveFactor_v3 = 0.5F;
    public float fov = 45.0F;
    public float zoomFactor = 1F;
    public boolean clickToMove = false;
    public boolean ortho = false;

    public static final class ConfigStore {
        private Config instance = new Config();

        public Config instance() {
            return instance;
        }

        public void load() {
            Path path = configPath();
            if (!Files.exists(path)) {
                return;
            }

            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Config loaded = JSON.fromJson(reader, Config.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | JsonParseException ignored) {
                // Keep defaults when a stale or malformed config cannot be read.
            }
        }

        public void save() {
            Path path = configPath();
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    JSON.toJson(instance, writer);
                }
            } catch (IOException ignored) {
                // Configuration is optional; gameplay should continue if it cannot be written.
            }
        }

        private Path configPath() {
            return FMLPaths.CONFIGDIR.get().resolve("dungeons_iso.json");
        }
    }
}
