package dev.wayfarer.objectives;

import dev.wayfarer.core.progression.Ladder;
import dev.wayfarer.core.progression.ProgressionData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Drawing objectives by stage, progress as a counter delta, rerolls and streaks. No server types. */
public final class ObjectiveRules {
    private ObjectiveRules() {
    }

    /** The selector's value: the sum of its counters, or how many different ones are above zero. */
    public static long value(ObjectiveDefinition def, Map<String, Long> counters) {
        ProgressionData data = new ProgressionData();
        counters.forEach(data::set);
        return new Ladder("objective", def.name(), "", def.selector(), List.of(), false, def.distinct()).progress(data);
    }

    /** Progress since the window opened: the value now minus the value then, so nothing done before counts. */
    public static long progress(ObjectiveDefinition def, Map<String, Long> counters, long baseline) {
        return Math.max(0, value(def, counters) - baseline);
    }

    /**
     * Draws {@code count} objectives from those at or below the player's stage, never two the same, never one in
     * {@code exclude}, preferring a theme (table) not drawn yet so a day is varied.
     *
     * @param stages the stage names in order; a definition's stage must be at or before {@code stage}
     */
    public static List<ObjectiveDefinition> draw(Collection<ObjectiveDefinition> pool, List<String> stages, String stage,
            int count, Set<String> exclude, RandomGenerator random) {
        int rank = stages.indexOf(stage);
        List<ObjectiveDefinition> open = new ArrayList<>();
        for (ObjectiveDefinition d : pool) {
            if (stages.indexOf(d.stage()) <= rank && !exclude.contains(d.id())) {
                open.add(d);
            }
        }
        List<ObjectiveDefinition> out = new ArrayList<>();
        Set<String> themes = new HashSet<>();
        while (out.size() < count && !open.isEmpty()) {
            List<ObjectiveDefinition> fresh = open.stream().filter(d -> !themes.contains(d.table())).toList();
            List<ObjectiveDefinition> from = fresh.isEmpty() ? open : fresh;
            ObjectiveDefinition pick = from.get(random.nextInt(from.size()));
            out.add(pick);
            themes.add(pick.table());
            open.remove(pick);
        }
        return out;
    }

    /** The XP levels a paid reroll costs, after {@code paid} paid rerolls today. */
    public static int rerollCost(int base, int step, int paid) {
        return base + step * paid;
    }

    /** A streak after a day with every daily done: one more, and whether this day pays the streak bonus. */
    public record Streak(int days, boolean bonus) {
    }

    public static Streak completeDay(int streak, long lastDay, long today, int every) {
        if (lastDay == today) {
            return new Streak(streak, false); // already counted today
        }
        int days = streak + 1; // a missed day only pauses the streak
        return new Streak(days, every > 0 && days % every == 0);
    }
}
