package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WayfarerSetTest {
    private static final WayfarerSet.Settings FOUR = WayfarerSet.Settings.defaults();

    private static RelicDefinition piece(String family, RelicDefinition.Slot slot, int tier) {
        return new RelicDefinition(family + "_" + slot, family, family, 600, tier, slot);
    }

    @Test
    void fourDifferentFamiliesAtAnyTierCompleteTheSet() {
        List<RelicDefinition> worn = List.of(piece("zombie", RelicDefinition.Slot.CHEST, 1),
                piece("skeleton", RelicDefinition.Slot.HEAD, 2), piece("spider", RelicDefinition.Slot.FEET, 1),
                piece("enderman", RelicDefinition.Slot.OFFHAND, 2));
        assertTrue(WayfarerSet.complete(worn, FOUR));
        assertFalse(WayfarerSet.complete(worn.subList(0, 3), FOUR));
    }

    @Test
    void weaponsAndRepeatedFamiliesDoNotCount() {
        List<RelicDefinition> worn = new ArrayList<>(List.of(piece("zombie", RelicDefinition.Slot.CHEST, 1),
                piece("zombie", RelicDefinition.Slot.HEAD, 2), piece("spider", RelicDefinition.Slot.FEET, 1),
                piece("creeper", RelicDefinition.Slot.WEAPON, 1)));
        assertFalse(WayfarerSet.complete(worn, FOUR));
        worn.add(piece("creeper", RelicDefinition.Slot.LEGS, 1));
        assertFalse(WayfarerSet.complete(worn, FOUR)); // still three families of armour
        worn.add(piece("enderman", RelicDefinition.Slot.OFFHAND, 1));
        assertTrue(WayfarerSet.complete(worn, FOUR));
    }

    @Test
    void settingsParseAndBadValuesFallBack() {
        List<String> problems = new ArrayList<>();
        assertEquals(new WayfarerSet.Settings(3, 15, 2),
                WayfarerSet.Settings.parse(Map.of("families", 3, "damage-reduction", 15, "hearts", 2), problems));
        assertEquals(FOUR, WayfarerSet.Settings.parse(Map.of("families", 9), problems));
        assertEquals(1, problems.size());
    }

    @Test
    void theSetEdgeReducesHostileDamageAlongsideArmour() {
        BoostRules.Edge setEdge = WayfarerSet.edge(FOUR);
        assertEquals(new BoostRules.Edge("wayfarer_set", MobFamily.ALL, 10), setEdge);
    }
}
