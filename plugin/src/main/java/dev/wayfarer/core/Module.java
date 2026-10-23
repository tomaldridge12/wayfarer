package dev.wayfarer.core;

/** A self-contained feature hosted by Wayfarer. Modules reach each other only through {@link Core}. */
public interface Module {
    /** Stable id; also the name of the module's config file (`<id>.yml`). */
    String id();

    /** Start the module. Throwing skips this module; the plugin and other modules keep running. */
    void enable(Core core) throws Exception;

    void disable();
}
