package dev.wayfarer.core;

/**
 * What the foods module asks of whatever runs events: how much the forage chance is multiplied right now (1.0 when no
 * event changes it). The objectives module offers it; with it off, foraging is simply unchanged.
 */
@FunctionalInterface
public interface ForageMultiplier {
    double current();
}
