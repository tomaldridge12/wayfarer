package dev.wayfarer.core.progression;

import java.util.Arrays;
import java.util.UUID;

/** The players who have entered one structure, stored on the structure itself as comma-separated UUIDs. */
public final class StructureVisitors {
    private StructureVisitors() {
    }

    /** Whether this player is not yet in the stored list ({@code stored} may be null). */
    public static boolean firstVisit(String stored, UUID player) {
        return stored == null || Arrays.stream(stored.split(",")).noneMatch(player.toString()::equals);
    }

    /** The stored list with this player added. */
    public static String with(String stored, UUID player) {
        return stored == null || stored.isEmpty() ? player.toString() : stored + "," + player;
    }
}
