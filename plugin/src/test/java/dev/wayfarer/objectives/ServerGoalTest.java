package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The server goal: the common stage, choosing at it, totals, quarter announcements, shares and the saved state. */
class ServerGoalTest {
    private static final PoolConfigParser.Result SHIPPED = PoolConfigParser.parse(ObjectiveFixtures.shippedYaml(), ObjectiveFixtures.SERVER,
            key -> true);
    private static final List<String> STAGES = SHIPPED.settings().stages();

    @TempDir
    Path dir;

    @Test
    void theShippedGoalPoolLoads() {
        assertEquals(15, SHIPPED.goal().size());
        assertTrue(SHIPPED.tables().has("farming", "goal"));
    }

    @Test
    void theGoalIsAtTheStageMostPlayersHaveReached() {
        assertEquals("iron", ServerGoalRules.commonStage(List.of("start", "iron", "iron", "nether"), STAGES));
        assertEquals("start", ServerGoalRules.commonStage(List.of("start", "iron"), STAGES), "a tie goes to the earlier stage");
        assertEquals("start", ServerGoalRules.commonStage(List.of(), STAGES));
        Random random = new Random(4);
        for (int i = 0; i < 50; i++) {
            assertEquals("nether", ServerGoalRules.choose(SHIPPED.goal().values(), STAGES, "nether", random).stage());
        }
        ObjectiveDefinition onlyStart = SHIPPED.goal().get("great_harvest");
        assertEquals("great_harvest", ServerGoalRules.choose(List.of(onlyStart), STAGES, "end", random).id(),
                "falls back to the nearest stage below with a goal");
    }

    @Test
    void progressIsEveryonesSumAndEachQuarterIsAnnouncedOnce() {
        assertEquals(700, ServerGoalRules.total(Map.of("a", 500L, "b", 200L)));
        assertEquals(List.of(25, 50), ServerGoalRules.quarters(100, 520, 1000));
        assertEquals(List.of(), ServerGoalRules.quarters(520, 600, 1000));
        assertEquals(List.of(75), ServerGoalRules.quarters(749, 750, 1000));
        assertEquals(4, ServerGoalRules.share(212, 5000));
        assertEquals(1, ServerGoalRules.share(3, 5000), "any help is at least 1%");
        assertEquals(0, ServerGoalRules.share(0, 5000));
    }

    @Test
    void theStateSurvivesARestart() throws Exception {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ServerGoalState s = new ServerGoalState();
        s.weekStart = 123;
        s.goal = "great_harvest";
        s.stage = "start";
        s.baselines.put("a", 40L);
        s.contributions.put("a", 512L);
        s.paid.add("b");
        s.playerStages.put("a", "iron");
        s.event = "drowned_tide";
        Path file = dir.resolve("server-goal.json");
        new dev.wayfarer.core.StateFile(file, log).close(s.json());
        ServerGoalState back = ServerGoalState.read(file, log);
        assertEquals(123, back.weekStart);
        assertEquals("great_harvest", back.goal);
        assertEquals(512L, back.contributions.get("a"));
        assertEquals(40L, back.baselines.get("a"));
        assertTrue(back.paid.contains("b"));
        assertEquals("drowned_tide", back.event);
        assertTrue(Files.readString(file).startsWith("{\"version\":1,"));
    }

    @Test
    void anUnreadableStateIsSetAsideAndStartsFresh() throws Exception {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        Path file = dir.resolve("server-goal.json");
        Files.writeString(file, "{not json");
        ServerGoalState back = ServerGoalState.read(file, log);
        assertEquals(null, back.goal);
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("server-goal.json.corrupt-")));
        }
    }
}
