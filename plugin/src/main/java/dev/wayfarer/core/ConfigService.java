package dev.wayfarer.core;

/** Gives each module its own config file. */
public interface ConfigService {
    /** The module's on/off switch (`enabled` in its config file); defaults to true. */
    boolean enabled(String moduleId);
}
