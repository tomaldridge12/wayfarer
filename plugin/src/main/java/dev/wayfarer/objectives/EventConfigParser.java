package dev.wayfarer.objectives;

import dev.wayfarer.core.progression.Selectors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The events section of objectives.yml: the rotation and each event. A bad event is skipped with a problem. */
public final class EventConfigParser {
    public record Result(List<String> rotation, Map<String, EventDefinition> events, List<String> problems) {
    }

    private EventConfigParser() {
    }

    public static Result parse(Object section, ObjectiveVocabulary names, RewardTables tables) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> root = ObjectiveConfigParser.asMap(section);
        Map<String, EventDefinition> events = new LinkedHashMap<>();
        ObjectiveConfigParser.asMap(root.get("list")).forEach((id, v) -> {
            Map<String, Object> m = ObjectiveConfigParser.asMap(v);
            String where = "Event " + id;
            String modifier = String.valueOf(m.get("modifier")).toLowerCase(Locale.ROOT);
            String table = String.valueOf(m.getOrDefault("table", "event")).toLowerCase(Locale.ROOT);
            Map<String, Object> g = ObjectiveConfigParser.asMap(m.get("goal"));
            Set<String> selector = Selectors.parse(g.get("counts"), names::tag, problems::add, where + " goal");
            long target = g.get("target") instanceof Number n ? n.longValue() : 0;
            if (!EventRules.MODIFIERS.contains(modifier)) {
                problems.add(where + " skipped: modifier must be one of " + EventRules.MODIFIERS);
            } else if (modifier.equals("mob_drops") && !(m.get("mobs") instanceof List<?>)) {
                problems.add(where + " skipped: mob_drops needs mobs");
            } else if (selector.isEmpty() || target < 1 || g.get("text") == null) {
                problems.add(where + " skipped: goal needs text, counts and a target");
            } else if (!tables.has(table, "event")) {
                problems.add(where + " skipped: table " + table + " has no event tier");
            } else {
                ObjectiveDefinition goal = new ObjectiveDefinition("event_" + id, String.valueOf(g.getOrDefault("name", m.getOrDefault("name", id))),
                        String.valueOf(g.get("text")), selector, Boolean.TRUE.equals(g.get("distinct")), target, "start", table, 0);
                Map<String, Object> settings = new LinkedHashMap<>(m);
                List.of("name", "blurb", "modifier", "goal", "table").forEach(settings::remove);
                events.put(id, new EventDefinition(id, String.valueOf(m.getOrDefault("name", id)), String.valueOf(m.getOrDefault("blurb", "")),
                        modifier, settings, goal, table));
            }
        });
        List<String> rotation = new ArrayList<>();
        for (Object o : root.get("rotation") instanceof List<?> l ? l : List.of()) {
            String id = String.valueOf(o);
            if (events.containsKey(id)) {
                rotation.add(id);
            } else {
                problems.add("Event rotation: '" + id + "' is not a loaded event; left out");
            }
        }
        return new Result(List.copyOf(rotation), events, List.copyOf(problems));
    }
}
