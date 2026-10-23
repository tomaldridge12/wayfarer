package dev.wayfarer.core;

import java.util.logging.Logger;

/** Shared services every module uses. */
public interface Core {
    Logger logger();

    ConfigService config();

    ItemTagService itemTags();
}
