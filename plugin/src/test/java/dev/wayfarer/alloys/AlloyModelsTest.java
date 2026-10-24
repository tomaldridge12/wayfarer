package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every model and worn look the shipped alloys point at exists in the resource pack. */
class AlloyModelsTest {
    private static final Path PACK = Path.of(System.getProperty("wayfarer.pack.dir", "../resourcepack"), "assets/wayfarer");

    @Test
    void everyAlloyItemHasItsModelAndEveryAlloyItsEquipment() {
        List<String> missing = new ArrayList<>();
        for (AlloyDefinition a : AlloyFixtures.rules().alloys()) {
            List<String> models = new ArrayList<>(List.of(a.id() + "_ingot", a.id() + "_template"));
            for (GearShape s : GearShape.values()) {
                models.add(new AlloyRules.Gear(a.id(), s).model());
            }
            for (String m : models) {
                if (!Files.exists(PACK.resolve("items/" + m + ".json"))) {
                    missing.add("items/" + m + ".json");
                }
            }
            if (!Files.exists(PACK.resolve("equipment/" + a.id() + ".json"))) {
                missing.add("equipment/" + a.id() + ".json");
            }
        }
        assertEquals(List.of(), missing);
    }
}
