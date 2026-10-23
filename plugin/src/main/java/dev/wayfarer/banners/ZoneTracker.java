package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Which boost zone each player stands in, per mob family, and the enter/leave transitions between checks.
 * Within a family the zone shown is the strongest banner covering the player (bonus first, then reduction).
 */
public final class ZoneTracker {
    public enum Change {
        ENTER, LEAVE
    }

    public record Transition(Change change, BannerDefinition banner) {
    }

    private static final Comparator<BannerDefinition> STRONGEST = Comparator
            .comparingInt(BannerDefinition::damageBonusPercent)
            .thenComparingInt(BannerDefinition::damageReductionPercent)
            .thenComparing(BannerDefinition::id, Comparator.reverseOrder());

    private final Map<UUID, Map<String, BannerDefinition>> current = new HashMap<>();

    /** The strongest covering banner per family at this point. */
    public static Map<String, BannerDefinition> zonesAt(BoostRules.Point at, Stream<PlacedBannerRegistry.Placed> nearby,
            BannerSet set) {
        Map<String, BannerDefinition> best = new HashMap<>();
        nearby.filter(p -> p.world().equals(at.world()))
                .forEach(p -> set.find(p.bannerId())
                        .filter(def -> BoostRules.inZone(at, p.pos(), def.radius()))
                        .ifPresent(def -> best.merge(def.family(), def,
                                (a, b) -> STRONGEST.compare(a, b) >= 0 ? a : b)));
        return best;
    }

    /** Records where the player is now and returns what changed since the last update. */
    public List<Transition> update(UUID player, Map<String, BannerDefinition> now) {
        Map<String, BannerDefinition> before = current.getOrDefault(player, Map.of());
        List<Transition> changes = new ArrayList<>();
        before.forEach((family, old) -> {
            BannerDefinition next = now.get(family);
            if (next == null) {
                changes.add(new Transition(Change.LEAVE, old));
            } else if (!next.id().equals(old.id())) {
                changes.add(new Transition(Change.ENTER, next)); // switched to a stronger or different banner
            }
        });
        now.forEach((family, def) -> {
            if (!before.containsKey(family)) {
                changes.add(new Transition(Change.ENTER, def));
            }
        });
        if (now.isEmpty()) {
            current.remove(player);
        } else {
            current.put(player, Map.copyOf(now));
        }
        return changes;
    }

    public boolean inAnyZone(UUID player) {
        return current.containsKey(player);
    }

    public void forget(UUID player) {
        current.remove(player);
    }

    /** The action bar text for a transition. */
    public static String message(Transition t) {
        BannerDefinition d = t.banner();
        if (t.change() == Change.LEAVE) {
            return "Left the " + d.name() + " zone";
        }
        return "Entered the " + d.name() + " zone: " + LabelText.lines(d.id(), Optional.of(d)).get(1);
    }
}
