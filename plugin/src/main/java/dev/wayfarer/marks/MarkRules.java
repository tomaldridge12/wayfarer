package dev.wayfarer.marks;

import dev.wayfarer.core.progression.Ladder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Every decision the marks module makes, from plain facts: the ladders it registers, whether the smithing table
 * marks a tool, which blocks a Dune shovel digs and which crops a Reaper hoe takes, and each rank's numbers. Built
 * from marks.yml by {@link MarkConfigParser}; no server types.
 */
public final class MarkRules {
    public static final String MARK = "mark";
    public static final String BOOK = "book";

    /** A mark of some rank, on a tool or a template: written {@code dune:2} on items. */
    public record OnTool(String mark, int rank) {
        public String key() {
            return mark + ":" + rank;
        }

        public static Optional<OnTool> parse(String text) {
            int cut = text == null ? -1 : text.indexOf(':');
            if (cut <= 0) {
                return Optional.empty();
            }
            try {
                return Optional.of(new OnTool(text.substring(0, cut), Integer.parseInt(text.substring(cut + 1))));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }

    private static final Set<String> DUNE_SAND = Set.of("sand", "red_sand");

    private final Map<String, MarkDefinition> marks;
    private final Map<String, BookDefinition> books;

    public MarkRules(Map<String, MarkDefinition> marks, Map<String, BookDefinition> books) {
        this.marks = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(marks));
        this.books = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(books));
    }

    public static MarkRules empty() {
        return new MarkRules(Map.of(), Map.of());
    }

    public List<MarkDefinition> marks() {
        return List.copyOf(marks.values());
    }

    public List<BookDefinition> books() {
        return List.copyOf(books.values());
    }

    public Optional<MarkDefinition> mark(String id) {
        return Optional.ofNullable(marks.get(id));
    }

    public Optional<BookDefinition> book(String id) {
        return Optional.ofNullable(books.get(id));
    }

    /** {@code dune_2} -> (dune, 2), for a rank this config has. */
    public Optional<OnTool> rewardId(String id) {
        int cut = id.lastIndexOf('_');
        if (cut <= 0) {
            return Optional.empty();
        }
        try {
            OnTool t = new OnTool(id.substring(0, cut), Integer.parseInt(id.substring(cut + 1)));
            return mark(t.mark()).filter(m -> t.rank() >= 1 && t.rank() <= m.topRank()).map(m -> t);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** {@code veinminer_3} -> (veinminer, 3), for a level this config has. */
    public Optional<OnTool> bookId(String id) {
        int cut = id.lastIndexOf('_');
        if (cut <= 0) {
            return Optional.empty();
        }
        try {
            OnTool t = new OnTool(id.substring(0, cut), Integer.parseInt(id.substring(cut + 1)));
            return book(t.mark()).filter(b -> t.rank() >= 1 && t.rank() <= b.thresholds().size()).map(b -> t);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** One ladder per mark ({@code mark:dune_1} ...) and per book ({@code book:veinminer_1} ...); only the top rung announces. */
    public List<Ladder> ladders() {
        List<Ladder> out = new ArrayList<>();
        for (MarkDefinition m : marks.values()) {
            out.add(new Ladder(m.id(), m.name() + " Mark", m.unit(), m.selector(), rungs(MARK, m.id(), m.thresholds()), false));
        }
        for (BookDefinition b : books.values()) {
            out.add(new Ladder(b.id(), b.name(), b.unit(), b.selector(), rungs(BOOK, b.id(), b.thresholds()), false));
        }
        return out;
    }

    private static List<Ladder.Rung> rungs(String type, String id, List<Long> thresholds) {
        List<Ladder.Rung> out = new ArrayList<>();
        for (int i = 0; i < thresholds.size(); i++) {
            out.add(new Ladder.Rung(thresholds.get(i), type + ":" + id + "_" + (i + 1), i == thresholds.size() - 1));
        }
        return out;
    }

    /** Every ExcellentEnchants enchant the books give, at the highest level. */
    public Map<String, Integer> rewardEnchants() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        books.values().forEach(b -> out.merge(b.enchant(), b.thresholds().size(), Math::max));
        return out;
    }

    // --- Smithing -------------------------------------------------------------------------------------------------

    /**
     * What template + tool + material make: the template's mark on the tool, when the tool is of the mark's type,
     * the material is one of the mark's, and the tool has no equal or higher rank of it already.
     */
    public Optional<OnTool> smith(OnTool template, String toolKey, OnTool current, String materialKey) {
        if (template == null || toolKey == null || materialKey == null) {
            return Optional.empty();
        }
        Optional<MarkDefinition> mark = mark(template.mark());
        if (mark.isEmpty() || template.rank() < 1 || template.rank() > mark.get().topRank()
                || !mark.get().tool().fits(toolKey) || !mark.get().materials().contains(materialKey)) {
            return Optional.empty();
        }
        if (current != null && current.mark().equals(template.mark()) && current.rank() >= template.rank()) {
            return Optional.empty();
        }
        return Optional.of(template);
    }

    // --- Areas ----------------------------------------------------------------------------------------------------

    /**
     * Offsets of a {@code width} x {@code depth} area around a broken block, the block itself left out, on the face
     * the player is digging. On a top or bottom face the area lies flat: width runs across the way the player faces
     * and depth along it. On a side face it stands up: width across the face, depth upwards and downwards.
     * Depth 1 is a single row across.
     */
    public static List<Offset> area(int width, int depth, Face face, Face facing) {
        int w = width / 2;
        int d = depth / 2;
        boolean acrossX = face.vertical() ? facing == Face.NORTH || facing == Face.SOUTH
                : face == Face.NORTH || face == Face.SOUTH;
        List<Offset> out = new ArrayList<>();
        for (int a = -w; a <= w; a++) {
            for (int b = -d; b <= d; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                if (face.vertical()) {
                    out.add(acrossX ? new Offset(a, 0, b) : new Offset(b, 0, a));
                } else {
                    out.add(acrossX ? new Offset(a, b, 0) : new Offset(0, b, a));
                }
            }
        }
        return out;
    }

    /** Whether a Dune shovel of this rank digs this block (and so starts its area). */
    public boolean duneDigs(int rank, String blockKey) {
        return mark("dune").map(m -> DUNE_SAND.contains(blockKey) || (m.rank(rank).gravel() && blockKey.equals("gravel")))
                .orElse(false);
    }

    /** The other blocks a Dune shovel of this rank digs around one it broke: only sand (and gravel at III). */
    public List<Offset> dune(int rank, Face face, Face facing, Function<Offset, String> blockAt) {
        Optional<MarkDefinition> m = mark("dune");
        if (m.isEmpty()) {
            return List.of();
        }
        MarkDefinition.Rank r = m.get().rank(rank);
        return area(r.width(), r.depth(), face, facing).stream().filter(o -> duneDigs(rank, blockAt.apply(o))).toList();
    }

    /** The crops around a broken one a Reaper hoe of this rank harvests: a flat area across the way the player faces. */
    public List<Offset> reaper(int rank, Face facing) {
        return mark("reaper").map(m -> area(m.rank(rank).width(), m.rank(rank).depth(), Face.UP, facing)).orElse(List.of());
    }
}
