package dev.wayfarer.alloys;

import java.util.Locale;
import java.util.Optional;

/** How hot a crucible burns, set by the block beneath it. A hotter heat melts every cooler recipe too. */
public enum Heat {
    LOW, HIGH, SOUL;

    public boolean reaches(Heat needed) {
        return compareTo(needed) >= 0;
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<Heat> parse(Object text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(String.valueOf(text).toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
