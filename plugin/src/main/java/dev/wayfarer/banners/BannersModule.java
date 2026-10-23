package dev.wayfarer.banners;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;

/** Trophy banners; the rules, items and listeners arrive in later tickets. */
public final class BannersModule implements Module {
    @Override
    public String id() {
        return "banners";
    }

    @Override
    public void enable(Core core) {
    }

    @Override
    public void disable() {
    }
}
