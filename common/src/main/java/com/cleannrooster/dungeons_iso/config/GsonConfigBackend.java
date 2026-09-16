package com.cleannrooster.dungeons_iso.config;

import com.cleannrooster.dungeons_iso.ModCompat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON persistence using the Gson runtime already shipped with Minecraft. */
public final class GsonConfigBackend<T> implements ConfigBackend<T> {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Class<T> type;
    private final T instance;
    private final Path path;

    public GsonConfigBackend(Class<T> type, T instance, String fileName) {
        this.type = type;
        this.instance = instance;
        this.path = ModCompat.getConfigDir().resolve(fileName);
    }

    @Override
    public T instance() {
        return this.instance;
    }

    @Override
    public void load() {
        if (Files.isRegularFile(this.path)) {
            try (Reader reader = Files.newBufferedReader(this.path)) {
                com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);
                if (root.isJsonObject()) {
                    copyFields(root.getAsJsonObject(), this.instance);
                }
            } catch (Exception ignored) {
                // A corrupt or half-written file leaves the defaults standing.
            }
        }
        if (!Files.isRegularFile(this.path)) {
            save();
        }
    }

    @Override
    public void save() {
        try {
            Files.createDirectories(this.path.getParent());
            try (Writer writer = Files.newBufferedWriter(this.path)) {
                GSON.toJson(this.instance, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private void copyFields(com.google.gson.JsonObject values, T to) {
        for (Field field : this.type.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || !values.has(field.getName())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = GSON.fromJson(values.get(field.getName()), field.getGenericType());
                if (value != null || !field.getType().isPrimitive()) {
                    field.set(to, value);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
