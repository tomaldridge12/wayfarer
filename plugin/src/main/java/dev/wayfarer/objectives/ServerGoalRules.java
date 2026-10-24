package dev.wayfarer.objectives;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The server goal's rules: the stage most players have reached, a goal drawn at it, progress as the sum of everyone's
 * contribution, quarter-way announcements and each contributor's share. No server types.
 */
public final class ServerGoalRules {
    private ServerGoalRules() {
    }

    /** The stage most players are at (ties go to the earlier stage); the first stage when nobody has been seen. */
    public static String commonStage(Collection<String> playerStages, List<String> stages) {
        String best = stages.getFirst();
        long most = 0;
        for (String stage : stages) {
            long n = playerStages.stream().filter(stage::equals).count();
            if (n > most) {
                most = n;
                best = stage;
            }
        }
        return best;
    }

    /** A goal at that stage (or the nearest below it with any); null when the pool is empty. */
    public static ObjectiveDefinition choose(Collection<ObjectiveDefinition> pool, List<String> stages, String stage,
            RandomGenerator random) {
        for (int rank = stages.indexOf(stage); rank >= 0; rank--) {
            String at = stages.get(rank);
            List<ObjectiveDefinition> here = pool.stream().filter(d -> d.stage().equals(at)).toList();
            if (!here.isEmpty()) {
                return here.get(random.nextInt(here.size()));
            }
        }
        return pool.isEmpty() ? null : pool.iterator().next();
    }

    public static long total(Map<String, Long> contributions) {
        return contributions.values().stream().mapToLong(Long::longValue).sum();
    }

    /** The quarter marks (25, 50, 75) passed when progress went from {@code before} to {@code after}. */
    public static List<Integer> quarters(long before, long after, long target) {
        return List.of(25, 50, 75).stream().filter(q -> before * 100 < q * target && after * 100 >= q * target).toList();
    }

    /** A contributor's share of the goal, in whole per cent of the target (at least 1 for any contribution). */
    public static int share(long contribution, long target) {
        if (contribution <= 0 || target <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.min(100, contribution * 100 / target));
    }
}
