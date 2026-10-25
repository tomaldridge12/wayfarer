package dev.wayfarer.core;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEventSource;

/**
 * The one way modules tell the whole server about something earned. A category the config has switched off says
 * nothing. Main thread.
 */
public final class Announcer {
    private final Consumer<Component> everyone;
    private volatile AnnounceSettings settings = AnnounceSettings.defaults();

    /** @param everyone sends a line to every player (and the console) */
    public Announcer(Consumer<Component> everyone) {
        this.everyone = everyone;
    }

    /** Replaces the switches, on enable and on reload. */
    public void configure(AnnounceSettings settings) {
        this.settings = settings;
    }

    public boolean enabled(AnnounceCategory category) {
        return settings.on(category);
    }

    /**
     * Tells the server {@code line} if the category is on.
     *
     * @param hover shown when the line is hovered: text, or an item; null for none
     * @return whether it was sent
     */
    public boolean announce(AnnounceCategory category, Component line, HoverEventSource<?> hover) {
        if (!enabled(category)) {
            return false;
        }
        everyone.accept(hover == null ? line : line.hoverEvent(hover));
        return true;
    }
}
