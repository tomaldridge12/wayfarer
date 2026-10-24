package dev.wayfarer.marks;

import java.util.List;
import java.util.Set;

/**
 * One mark from marks.yml: its ladder (selector with tags already expanded, one threshold per rank), the tool type
 * it goes on, the materials that apply it at the smithing table, and what each rank does. No server types.
 */
public record MarkDefinition(String id, String name, int colour, String unit, Set<String> selector,
        List<Long> thresholds, ToolType tool, Set<String> materials, List<Rank> ranks) {

    /**
     * What one rank does; each mark reads its own fields. {@code width} x {@code depth} is an area (Dune, Reaper;
     * depth 1 is a row across the way the player faces), {@code radius} in blocks (Shepherd), {@code biteWait} a bite-wait
     * multiplier (Angler), {@code chance} extra wool (Shepherd) or treasure (Angler), {@code gravel} Dune also digs it.
     */
    public record Rank(int width, int depth, int radius, double biteWait, double chance, boolean gravel) {
    }

    /** The marks with an effect written in code; any other id in config is a problem. */
    public static final List<String> KNOWN = List.of("dune", "shepherd", "angler", "reaper");

    public int topRank() {
        return ranks.size();
    }

    public Rank rank(int rank) {
        return ranks.get(Math.max(1, Math.min(rank, ranks.size())) - 1);
    }
}
