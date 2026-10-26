package dev.wayfarer.foods;

import java.util.ArrayList;
import java.util.List;

/** The forage roll: a small chance, with a ceiling so a long run of bad luck always ends. Plain rules, no server. */
final class ForageRules {
    private ForageRules() {
    }

    /** The base chance times the event multiplier, never above certain. */
    static double chance(double base, double multiplier) {
        return Math.min(1.0, base * Math.max(0.0, multiplier));
    }

    /**
     * Whether this qualifying break gives the ingredient. {@code streak} is how many qualifying breaks in a row have
     * given nothing; once it reaches {@code pity} the next one is certain. {@code roll} is a random number in [0, 1).
     */
    static boolean drops(int streak, int pity, double chance, double roll) {
        return streak >= pity || roll < chance;
    }

    /** The streak after a break: back to nothing on a drop, one more otherwise. */
    static int nextStreak(int streak, boolean dropped) {
        return dropped ? 0 : streak + 1;
    }

    /** The ingredients a broken plant can give, in config order. */
    static List<ForageDefinition> forPlant(List<ForageDefinition> all, String plant) {
        List<ForageDefinition> out = new ArrayList<>();
        for (ForageDefinition d : all) {
            if (d.plants().contains(plant)) {
                out.add(d);
            }
        }
        return out;
    }
}
