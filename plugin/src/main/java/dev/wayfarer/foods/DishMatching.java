package dev.wayfarer.foods;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Whether what is in a crafting grid is what a dish asks for, beyond the item types vanilla already checked. Plain rules. */
final class DishMatching {
    /** A fish in the grid: its species and water if it is a Wayfarer fish, nulls for a vanilla one. */
    record Held(String species, String water) {
    }

    private static final Set<String> FRESH = Set.of("fresh", "brackish", "any");
    private static final Set<String> SALT = Set.of("salt", "brackish", "any");

    private DishMatching() {
    }

    /** The forage a recipe wants against the forage in the grid, as lists with repeats; order does not matter. */
    static boolean forage(List<String> required, List<String> actual) {
        List<String> left = new ArrayList<>(actual);
        for (String id : required) {
            if (!left.remove(id)) {
                return false;
            }
        }
        return left.isEmpty();
    }

    /** One fish need ({@code any}, {@code fresh}, {@code salt} or a species id) against one fish. */
    static boolean accepts(String need, Held fish) {
        return switch (need) {
            case "any" -> true;
            case "fresh" -> fish.water() != null && FRESH.contains(fish.water());
            case "salt" -> fish.water() != null && SALT.contains(fish.water());
            default -> need.equals(fish.species());
        };
    }

    /** Every need gets its own fish and no fish is left over; found by matching, since a brackish fish suits fresh and salt both. */
    static boolean fish(List<String> needs, List<Held> held) {
        if (needs.size() != held.size()) {
            return false;
        }
        int[] owner = new int[held.size()];
        java.util.Arrays.fill(owner, -1);
        for (int need = 0; need < needs.size(); need++) {
            if (!assign(need, needs, held, owner, new boolean[held.size()])) {
                return false;
            }
        }
        return true;
    }

    private static boolean assign(int need, List<String> needs, List<Held> held, int[] owner, boolean[] seen) {
        for (int fish = 0; fish < held.size(); fish++) {
            if (!seen[fish] && accepts(needs.get(need), held.get(fish))) {
                seen[fish] = true;
                if (owner[fish] < 0 || assign(owner[fish], needs, held, owner, seen)) {
                    owner[fish] = need;
                    return true;
                }
            }
        }
        return false;
    }
}
