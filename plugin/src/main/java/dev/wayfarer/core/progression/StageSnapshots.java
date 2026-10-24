package dev.wayfarer.core.progression;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stage snapshots: when a player earns a marker advancement, one CSV line with every counter, for tuning ladder
 * thresholds against real play. Advancements are written without the minecraft: namespace ("story/mine_diamond").
 */
public record StageSnapshots(List<String> markers) {
    public static final String HEADER = "time,player,name,advancement,counters";
    static final List<String> DEFAULT_MARKERS = List.of("story/smelt_iron", "story/mine_diamond",
            "story/enter_the_nether", "nether/netherite_armor", "end/kill_dragon", "end/elytra");

    public StageSnapshots {
        markers = List.copyOf(markers);
    }

    public static StageSnapshots defaults() {
        return new StageSnapshots(DEFAULT_MARKERS);
    }

    /** The CSV line for this advancement, or empty when it is not a marker. Counters are sorted, as key=value;... */
    public Optional<String> line(String advancement, long epochMillis, UUID player, String name, Map<String, Long> counters) {
        if (!markers.contains(advancement)) {
            return Optional.empty();
        }
        String all = new TreeMap<>(counters).entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
        return Optional.of(String.join(",", Instant.ofEpochMilli(epochMillis).toString(), player.toString(),
                quote(name), advancement, quote(all)));
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** The {@code snapshots.markers} list; unknown advancements are skipped with a problem. */
    static StageSnapshots parse(Object raw, ProgressionSettings.Names names, List<String> problems) {
        if (!(raw instanceof Map<?, ?> map) || !(map.get("markers") instanceof List<?> list)) {
            return defaults();
        }
        List<String> markers = new ArrayList<>();
        for (Object item : list) {
            String name = String.valueOf(item).toLowerCase(Locale.ROOT);
            name = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
            if (names.isAdvancement(name)) {
                markers.add(name);
            } else {
                problems.add("snapshots.markers: unknown advancement '" + item + "' skipped");
            }
        }
        return new StageSnapshots(markers);
    }
}
