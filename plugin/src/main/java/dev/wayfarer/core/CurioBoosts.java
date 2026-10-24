package dev.wayfarer.core;

import java.util.UUID;

/** Offered by the curios module: how much a player's carried curios boost something by name ({@code treasure}). */
public interface CurioBoosts {
    /** The multiplier, 1 when no carried curio boosts it. */
    double factor(UUID player, String what);
}
