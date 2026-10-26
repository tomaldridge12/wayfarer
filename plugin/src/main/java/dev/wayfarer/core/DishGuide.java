package dev.wayfarer.core;

import java.util.Optional;

/**
 * What the foods module tells the Journal about a dish: its family (the Cookbook's tab) and the hint shown until the
 * dish has been eaten. Empty for a dish it does not know.
 */
@FunctionalInterface
public interface DishGuide {
    record Info(String family, String hint) {
    }

    Optional<Info> dish(String id);
}
