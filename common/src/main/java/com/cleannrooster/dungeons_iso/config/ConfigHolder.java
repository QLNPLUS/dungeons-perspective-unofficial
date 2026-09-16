package com.cleannrooster.dungeons_iso.config;

/** File-backed configuration holder used by the mod's runtime code. */
public final class ConfigHolder<T> {

    private final ConfigBackend<T> backend;

    private ConfigHolder(ConfigBackend<T> backend) {
        this.backend = backend;
    }

    /** Creates a holder backed by the standard JSON config file. */
    public static ConfigHolder<Config> create(Config defaults) {
        return new ConfigHolder<>(new GsonConfigBackend<>(Config.class, defaults, "dungeons_iso_v5.json"));
    }

    public T instance() {
        return this.backend.instance();
    }

    public void load() {
        this.backend.load();
    }

    public void save() {
        this.backend.save();
    }

}
