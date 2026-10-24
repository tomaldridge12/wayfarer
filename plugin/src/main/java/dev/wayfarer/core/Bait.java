package dev.wayfarer.core;

import java.util.UUID;

/** Offered by the curios module: a player's stored bait, used up one catch at a time by the Almanac. */
public interface Bait {
    /** Uses one baited catch if the player has any: the factor on rare odds (2 when baited, else 1). Main thread. */
    double useCast(UUID player);
}
