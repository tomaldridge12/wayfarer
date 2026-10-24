package dev.wayfarer.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The shipped pack source validates, the jar names the pack built from it, and the validator catches breakage. */
class PackTest {
    private static final Path SOURCE = Path.of(System.getProperty("wayfarer.pack.dir", "../resourcepack"));
    private static final Path ZIP = Path.of(System.getProperty("wayfarer.pack.zip", "../build/resourcepack/wayfarer-pack.zip"));

    @TempDir
    Path dir;

    @Test
    void theShippedPackHasNoProblems() throws IOException {
        assertEquals(List.of(), PackValidator.check(SOURCE));
    }

    @Test
    void theJarNamesTheBuiltPackByItsHash() throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/wayfarer-pack.properties")) {
            props.load(in);
        }
        String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(ZIP)));
        assertEquals(sha1, props.getProperty("sha1"));
        assertEquals("pack-" + sha1.substring(0, 12), props.getProperty("tag"));
        assertEquals("https://github.com/tomaldridge12/wayfarer/releases/download/pack-" + sha1.substring(0, 12)
                + "/wayfarer-pack.zip", props.getProperty("url"));
    }

    private void write(String relative, String text) throws IOException {
        Path p = dir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private void copyTexture(String relative) throws IOException {
        Path p = dir.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.copy(SOURCE.resolve("assets/wayfarer/textures/item/placeholder.png"), p);
    }

    @Test
    void brokenReferencesBadJsonUncreditedAndOddSizedTexturesAreReported() throws IOException {
        write("pack.mcmeta", "{\"pack\": {\"description\": \"x\", \"min_format\": 96, \"max_format\": 97}}");
        write("LICENSE.txt", "CC BY-NC-SA 4.0");
        write("CREDITS.md", "- `assets/wayfarer/textures/item/uncredited.png`\n\n## Wayfarer\n\n- `assets/wayfarer/textures/item/steel_ingot.png`\n");
        write("assets/wayfarer/items/steel_ingot.json", "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"wayfarer:item/steel_ingot\"}}");
        write("assets/wayfarer/items/bronze_ingot.json", "{\"model\": {\"type\": \"minecraft:model\", \"model\": \"wayfarer:item/bronze_ingot\"}}");
        write("assets/wayfarer/models/item/steel_ingot.json", "{\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"wayfarer:item/steel_ingot\"}}");
        write("assets/wayfarer/models/item/broken.json", "{not json");
        write("assets/wayfarer/equipment/steel.json", "{\"layers\": {\"humanoid\": [{\"texture\": \"wayfarer:steel\"}]}}");
        copyTexture("assets/wayfarer/textures/item/steel_ingot.png");
        copyTexture("assets/wayfarer/textures/item/uncredited.png");
        Files.write(dir.resolve("assets/wayfarer/textures/item/odd.png"), new byte[] {1, 2, 3});
        write("assets/wayfarer/textures/item/steel_ingot.png.mcmeta", "{\"animation\": {}}");
        write("assets/wayfarer/lang/en_us.json", "{broken");

        List<String> problems = PackValidator.check(dir);
        assertTrue(problems.contains("pack.mcmeta must declare min_format and max_format 97"), problems.toString());
        assertTrue(problems.contains("assets/wayfarer/items/bronze_ingot.json: model wayfarer:item/bronze_ingot does not exist"), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("assets/wayfarer/models/item/broken.json is not valid JSON")), problems.toString());
        assertTrue(problems.contains("assets/wayfarer/equipment/steel.json: texture wayfarer:steel does not exist"), problems.toString());
        assertTrue(problems.contains("assets/wayfarer/textures/item/uncredited.png is not credited in CREDITS.md"), problems.toString());
        assertTrue(problems.contains("assets/wayfarer/textures/item/odd.png is not a PNG"), problems.toString());
        assertTrue(problems.contains("assets/wayfarer/textures/item/steel_ingot.png.mcmeta is not credited in CREDITS.md"), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.startsWith("assets/wayfarer/lang/en_us.json is not valid JSON")), problems.toString());
        assertEquals(9, problems.size(), problems.toString());
    }

    /**
     * Fixed timestamps (with Gradle's fixed entry order: files before folders at each level) are what make the same
     * source always give the same SHA-1.
     */
    @Test
    void theBuiltZipIsReproducible() throws IOException {
        List<String> names = new java.util.ArrayList<>();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(ZIP.toFile())) {
            zip.stream().forEach(entry -> {
                names.add(entry.getName());
                assertEquals(java.time.LocalDateTime.of(1980, 2, 1, 0, 0), entry.getTimeLocal(), entry.getName());
            });
        }
        assertTrue(names.contains("pack.mcmeta"));
    }
}
