package dev.wayfarer.core;

import java.io.IOException;
import java.util.Map;

/** Gives each module its own config file. */
public interface ConfigService {
    /** The module's on/off switch (`enabled` in its config file); defaults to true. */
    boolean enabled(String moduleId);

    /** Reads the module's config file afresh as nested maps and lists. Throws if the file is unreadable. */
    Map<String, Object> load(String moduleId) throws IOException;
}
