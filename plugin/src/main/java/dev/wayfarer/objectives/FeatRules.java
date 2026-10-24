package dev.wayfarer.objectives;

import dev.wayfarer.core.progression.Ladder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Feats as ladders, and where a player stands on one. No server types. */
public final class FeatRules {
    public static final String FEAT = "feat";

    /** Where a player stands on a feat. */
    public record Status(boolean earned, long progress, long at) {
        public boolean shown(Feat feat) {
            return earned || !feat.hidden();
        }
    }

    private FeatRules() {
    }

    public static String ladderId(Feat feat) {
        return "feat_" + feat.id();
    }

    /**
     * A one-rung ladder: each reward at the feat's count, then the feat's own marker, last so its title card ("Moss
     * Tender") is the one left on screen; only the marker is announced.
     */
    public static Ladder ladder(Feat feat) {
        List<Ladder.Rung> rungs = new ArrayList<>();
        feat.rewards().forEach(r -> rungs.add(new Ladder.Rung(feat.at(), r, false)));
        rungs.add(new Ladder.Rung(feat.at(), feat.marker(), true));
        return new Ladder(ladderId(feat), feat.name(), "", feat.selector(), rungs, false, feat.distinct());
    }

    public static Status status(Feat feat, Map<String, Long> counters, Map<String, Long> earned) {
        dev.wayfarer.core.progression.ProgressionData data = new dev.wayfarer.core.progression.ProgressionData();
        counters.forEach(data::set);
        return new Status(earned.containsKey(feat.marker()), ladder(feat).progress(data), feat.at());
    }

    /** "4,213" with thousands separators. */
    public static String number(long n) {
        return String.format(java.util.Locale.UK, "%,d", n);
    }
}
