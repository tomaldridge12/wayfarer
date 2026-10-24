package dev.wayfarer.banners;

import dev.wayfarer.core.progression.Activity;
import dev.wayfarer.core.progression.Ladder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The banners module's ladders for core: the all-mobs ladder, one per mob family (every {@code kill:<type>} in the
 * family) and one per boss family ({@code defeat:<boss>}, also read by its base family when count-toward-base is
 * on). Rungs are the family's banners, trophy heads and relics; drop-only relics never come from counting.
 */
final class BannerLadders {
    private BannerLadders() {
    }

    /** @param allMobTypes the types the all-mobs ladder reads (hostile ones, or all when configured {@code any}) */
    static List<Ladder> build(BannerSet set, Set<String> allMobTypes, boolean announce) {
        List<String> bosses = set.families().keySet().stream().filter(set::isBossFamily).toList();
        List<Ladder> ladders = new ArrayList<>();
        Set<String> all = new LinkedHashSet<>();
        allMobTypes.forEach(type -> all.add(kill(type)));
        bosses.forEach(boss -> all.add(defeat(boss)));
        ladders.add(new Ladder(MobFamily.ALL, "All mobs", "kill", all, rungs(set, MobFamily.ALL), announce));
        for (MobFamily family : set.families().values()) {
            Set<String> selector = new LinkedHashSet<>();
            if (set.isBossFamily(family.id())) {
                selector.add(defeat(family.id()));
            } else {
                family.mobs().forEach(type -> selector.add(kill(type)));
                if (set.bossLoot().countTowardBase()) {
                    bosses.stream().filter(b -> family.id().equals(set.baseFamilyOf(b)))
                            .forEach(b -> selector.add(defeat(b)));
                }
            }
            List<Ladder.Rung> rungs = rungs(set, family.id());
            if (!rungs.isEmpty()) {
                ladders.add(new Ladder(family.id(), BannerText.title(family.id()),
                        set.isBossFamily(family.id()) ? "defeat" : "kill", selector, rungs, announce));
            }
        }
        return ladders;
    }

    private static List<Ladder.Rung> rungs(BannerSet set, String family) {
        return set.rewards().stream().filter(r -> r.family().equals(family))
                .map(r -> new Ladder.Rung(r.kills() == RelicDefinition.DROP_ONLY ? Ladder.Rung.NEVER : r.kills(),
                        r.rewardKey().toString()))
                .toList();
    }

    /** A family's mob name ("husk" or "minecraft:husk") as its kill activity. */
    static String kill(String mobType) {
        String type = mobType.toLowerCase(Locale.ROOT);
        return Activity.KILL + ":" + (type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type);
    }

    private static String defeat(String boss) {
        return Activity.DEFEAT + ":" + boss;
    }
}
