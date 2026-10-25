package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.core.Trackable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackerTest {
    @Test
    void aSixthIsRefusedAndOrderIsKept() {
        List<String> tracked = new ArrayList<>();
        for (String id : List.of("a", "b", "c", "d", "e")) {
            assertEquals(TrackerRules.Result.ADDED, TrackerRules.add(tracked, id, 5));
        }
        assertEquals(TrackerRules.Result.FULL, TrackerRules.add(tracked, "f", 5));
        assertEquals(TrackerRules.Result.ALREADY, TrackerRules.add(tracked, "c", 5));
        assertTrue(TrackerRules.remove(tracked, "b"));
        assertEquals(TrackerRules.Result.ADDED, TrackerRules.add(tracked, "f", 5));
        assertEquals(List.of("a", "c", "d", "e", "f"), tracked);
    }

    @Test
    void finishedAndVanishedThingsLeaveAndFreeTheirPlace() {
        List<String> tracked = new ArrayList<>(List.of("daily:x", "daily:gone", "weekly:y"));
        Map<String, Trackable> known = Map.of("daily:x", new Trackable("daily:x", "Mine 32 coal", 32, 32),
                "weekly:y", new Trackable("weekly:y", "Catch 40 fish", 12, 40));

        List<Trackable> shown = TrackerRules.live(tracked, id -> Optional.ofNullable(known.get(id)));

        assertEquals(List.of("weekly:y"), shown.stream().map(Trackable::id).toList());
        assertEquals(List.of("weekly:y"), tracked);
        assertEquals("Catch 40 fish 12/40", TrackerRules.line(shown.get(0)));
    }

    @Test
    void trackedIdsAndTheHiddenFlagSurviveASaveAndLoad() {
        ObjectivesPlayerData.Codec codec = new ObjectivesPlayerData.Codec();
        ObjectivesPlayerData data = new ObjectivesPlayerData();
        data.tracked.addAll(List.of("daily:x", "weekly:y"));
        data.sidebarHidden = true;

        ObjectivesPlayerData back = codec.decode(ObjectivesPlayerData.VERSION, codec.encode(data));

        assertEquals(List.of("daily:x", "weekly:y"), back.tracked);
        assertTrue(back.sidebarHidden);
    }
}
