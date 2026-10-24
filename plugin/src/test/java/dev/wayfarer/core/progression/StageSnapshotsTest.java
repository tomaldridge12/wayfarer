package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageSnapshotsTest {
    private static final UUID TOM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final long NOON = 1_792_843_200_000L; // 2026-10-24T12:00:00Z

    @Test
    void theSixDefaultMarkersAreTheStageAdvancements() {
        assertEquals(List.of("story/smelt_iron", "story/mine_diamond", "story/enter_the_nether",
                "nether/netherite_armor", "end/kill_dragon", "end/elytra"), StageSnapshots.defaults().markers());
    }

    @Test
    void aMarkerWritesOneLineWithEveryCounterSorted() {
        Optional<String> line = StageSnapshots.defaults().line("story/mine_diamond", NOON, TOM, "Tom",
                Map.of("mine:sand", 30L, "kill:husk", 3L));
        assertEquals(Optional.of("2026-10-24T12:00:00Z,00000000-0000-0000-0000-000000000001,\"Tom\","
                + "story/mine_diamond,\"kill:husk=3;mine:sand=30\""), line);
    }

    @Test
    void otherAdvancementsWriteNothing() {
        assertEquals(Optional.empty(), StageSnapshots.defaults().line("story/root", NOON, TOM, "Tom", Map.of()));
        assertEquals(Optional.empty(), StageSnapshots.defaults().line("recipes/misc/bread", NOON, TOM, "Tom", Map.of()));
    }

    @Test
    void theMarkerListIsConfigurableAndUnknownOnesAreSkipped() {
        List<String> problems = new ArrayList<>();
        ProgressionSettings.Names names = new ProgressionSettings.Names() {
            @Override
            public boolean isSpawnReason(String name) {
                return true;
            }

            @Override
            public boolean isItem(String name) {
                return true;
            }

            @Override
            public boolean isMob(String name) {
                return true;
            }

            @Override
            public boolean isAdvancement(String name) {
                return !name.equals("story/nope");
            }
        };
        StageSnapshots parsed = StageSnapshots.parse(Map.of("markers", List.of("minecraft:story/mine_diamond", "story/nope")),
                names, problems);
        assertEquals(List.of("story/mine_diamond"), parsed.markers());
        assertEquals(1, problems.size());
        assertEquals(StageSnapshots.defaults(), StageSnapshots.parse(null, names, problems));
    }
}
