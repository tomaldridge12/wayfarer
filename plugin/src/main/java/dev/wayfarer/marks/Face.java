package dev.wayfarer.marks;

import java.util.Locale;
import java.util.Optional;

/** The six block faces, as the server names them (NORTH is -z, EAST is +x). */
public enum Face {
    UP, DOWN, NORTH, SOUTH, EAST, WEST;

    public boolean vertical() {
        return this == UP || this == DOWN;
    }

    static Optional<Face> parse(String name) {
        try {
            return Optional.of(valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
