package dev.wayfarer.marks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every mark template the shipped marks point at has its model in the resource pack. */
class MarkModelsTest {
    private static final Path PACK = Path.of(System.getProperty("wayfarer.pack.dir", "../resourcepack"), "assets/wayfarer");

    @Test
    void everyMarkTemplateHasItsModel() {
        List<String> missing = MarkFixtures.rules().marks().stream().map(m -> "items/" + m.id() + "_mark_template.json")
                .filter(p -> !Files.exists(PACK.resolve(p))).toList();
        assertEquals(List.of(), missing);
    }
}
