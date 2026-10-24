package dev.wayfarer.collections;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The treasures section of collections.yml. A bad treasure is skipped with a problem; the rest load. */
public final class TreasureConfigParser {
    static final Set<String> KINDS = Set.of("gem", "statue", "charm", "chart", "fossil", "coin");

    public record Settings(double brushChance, int chartSeconds, Map<String, List<String>> sites) {
    }

    public record Result(Settings settings, List<TreasureDefinition> treasures, List<String> problems) {
    }

    private TreasureConfigParser() {
    }

    public static Result parse(Map<String, Object> section) {
        List<String> problems = new ArrayList<>();
        double chance = section.get("brush-chance") instanceof Number n ? n.doubleValue() : 0.12;
        if (chance <= 0 || chance > 1) {
            problems.add("Treasures brush-chance must be above 0 and at most 1; using 0.12");
            chance = 0.12;
        }
        int chartSeconds = section.get("chart-seconds") instanceof Number n ? n.intValue() : 600;
        Map<String, List<String>> sites = new LinkedHashMap<>();
        CollectionConfigParser.asMap(section.get("sites")).forEach((site, tables) -> sites.put(site,
                CollectionConfigParser.strings(tables).stream().map(t -> t.startsWith("minecraft:") ? t.substring(10) : t).toList()));
        List<TreasureDefinition> out = new ArrayList<>();
        CollectionConfigParser.asMap(section.get("list")).forEach((id, value) -> {
            Map<String, Object> m = CollectionConfigParser.asMap(value);
            String where = "Treasure " + id + " skipped: ";
            String kind = String.valueOf(m.getOrDefault("kind", "")).toLowerCase(Locale.ROOT);
            String colour = String.valueOf(m.getOrDefault("colour", "#D9A55B"));
            List<String> brush = CollectionConfigParser.strings(m.get("brush"));
            Map<String, Double> chests = new LinkedHashMap<>();
            boolean badChest = false;
            for (var e : CollectionConfigParser.asMap(m.get("chests")).entrySet()) {
                String table = e.getKey().toLowerCase(Locale.ROOT);
                table = table.startsWith("minecraft:") ? table.substring(10) : table;
                if (!(e.getValue() instanceof Number n) || n.doubleValue() <= 0 || n.doubleValue() > 1) {
                    badChest = true;
                } else {
                    chests.put(table, n.doubleValue());
                }
            }
            String chart = m.get("chart") == null ? null : String.valueOf(m.get("chart")).toLowerCase(Locale.ROOT);
            int weight = m.get("weight") instanceof Number n ? n.intValue() : 1;
            if (!id.matches("[a-z0-9_]+")) {
                problems.add(where + "ids are lower-case letters, digits and _");
            } else if (!KINDS.contains(kind)) {
                problems.add(where + "kind must be gem, statue, charm, chart, fossil or coin");
            } else if (!colour.matches("#[0-9a-fA-F]{6}")) {
                problems.add(where + "colour must be #RRGGBB");
            } else if (!sites.keySet().containsAll(brush)) {
                problems.add(where + "brush lists a site that sites does not define (" + sites.keySet() + ")");
            } else if (badChest) {
                problems.add(where + "every chest chance must be above 0 and at most 1");
            } else if (brush.isEmpty() && chests.isEmpty()) {
                problems.add(where + "it turns up nowhere (no brush sites and no chests)");
            } else if (kind.equals("chart") != (chart != null) || chart != null && !chart.contains(":")) {
                problems.add(where + "a chart needs chart: <structure key>, and only charts have one");
            } else if (weight < 1) {
                problems.add(where + "weight must be 1 or more");
            } else {
                out.add(new TreasureDefinition(id, String.valueOf(m.getOrDefault("name", CollectionRules.title(id))),
                        Integer.parseInt(colour.substring(1), 16), kind, String.valueOf(m.getOrDefault("flavour", "")), brush,
                        chests, chart, weight));
            }
        });
        return new Result(new Settings(chance, chartSeconds, Map.copyOf(sites)), List.copyOf(out), List.copyOf(problems));
    }
}
