package dev.wayfarer.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every curio, supply and vanilla-based special gear piece the shipped config names has its model in the pack. */
class CurioModelsTest {
    private static final Path PACK = Path.of(System.getProperty("wayfarer.pack.dir", "../resourcepack"), "assets/wayfarer");

    @Test
    void everyCurioSupplyAndGearModelExists() {
        Map<String, Object> root = CurioFixtures.shippedYaml();
        List<String> models = new ArrayList<>();
        CurioConfigParser.parse(root.get("curios"), CurioFixtures.SERVER).curios().values().forEach(c -> models.add(c.model()));
        GearSupplyParser.Result g = GearSupplyParser.parse(root.get("gear"), root.get("supplies"), GearSupplyTest.NAMES);
        g.supplies().values().forEach(s -> models.add(s.model()));
        g.gear().values().stream().filter(p -> p.model() != null).forEach(p -> models.add(p.model()));
        List<String> missing = models.stream().map(m -> "items/" + m + ".json").filter(p -> !Files.exists(PACK.resolve(p))).toList();
        assertEquals(List.of(), missing);
    }
}
