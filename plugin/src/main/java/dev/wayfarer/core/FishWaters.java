package dev.wayfarer.core;

import java.util.Optional;

/**
 * What the collections module tells others about its fish: the water a species lives in (fresh, salt, brackish or any),
 * so a recipe can ask for a freshwater or a saltwater fish. Empty for a species it does not know.
 */
@FunctionalInterface
public interface FishWaters {
    Optional<String> waterOf(String speciesId);
}
