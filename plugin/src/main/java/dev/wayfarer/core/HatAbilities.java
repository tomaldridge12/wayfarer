package dev.wayfarer.core;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

/**
 * Hat abilities: the curios module offers the catalogue (small perks from its effect library, listed under
 * {@code hat-abilities} in curios.yml) and the objectives module offers what each player is wearing. Main thread.
 */
public final class HatAbilities {
    /** Offered by curios: which abilities exist and how to describe them. */
    public interface Catalogue {
        boolean exists(String ability);

        /** The ability's name and one or two description lines, or empty when unknown. */
        List<String> describe(String ability);
    }

    /** Offered by objectives: the ability of the hat a player is wearing right now (none while dead or offline). */
    public interface Worn {
        Optional<String> of(UUID player);
    }

    private HatAbilities() {
    }
}
