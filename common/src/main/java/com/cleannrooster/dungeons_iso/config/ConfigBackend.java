package com.cleannrooster.dungeons_iso.config;

/** Persistence contract for the file-backed configuration. */
public interface ConfigBackend<T> {

    T instance();

    void load();

    void save();
}
