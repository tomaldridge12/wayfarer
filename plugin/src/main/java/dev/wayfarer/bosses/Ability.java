package dev.wayfarer.bosses;

import java.util.Locale;
import java.util.Optional;

/** The boss ability library. Behaviour lives in code; config assigns abilities to variants by these names. */
public enum Ability {
    CHARGE, GROUND_SLAM, SUMMON_MINIONS, POISON_CLOUD, ARROW_VOLLEY, BLINK, FIRE_RING, PULL, SHIELD, ENRAGE;

    public static Optional<Ability> parse(String name) {
        try {
            return Optional.of(valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
