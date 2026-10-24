package dev.wayfarer.core.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PackOfferTest {
    private static final String SHA = "6866db5b1ebee77e2acf59d988af336df05f3575";
    private static final String URL = "https://github.com/tomaldridge12/wayfarer/releases/download/pack-6866db5b1ebe/wayfarer-pack.zip";

    private static Properties jar() {
        Properties p = new Properties();
        p.setProperty("sha1", SHA);
        p.setProperty("tag", "pack-6866db5b1ebe");
        p.setProperty("url", URL);
        return p;
    }

    @Test
    void theBuiltPackIsOfferedByDefault() {
        List<String> problems = new ArrayList<>();
        PackOffer offer = PackOffer.from(Map.of(), jar(), problems).orElseThrow();
        assertEquals(URL, offer.url());
        assertEquals(SHA, offer.sha1());
        assertEquals("pack-6866db5b1ebe", offer.tag());
        assertEquals(PackOffer.DEFAULT_PROMPT, offer.prompt());
        assertEquals(List.of(), problems);
        assertEquals(offer.id(), PackOffer.from(Map.of(), jar(), problems).orElseThrow().id());
    }

    @Test
    void itCanBeSwitchedOff() {
        assertEquals(Optional.empty(), PackOffer.from(Map.of("enabled", false), jar(), new ArrayList<>()));
    }

    @Test
    void badValuesAreReportedAndDefaultsUsed() {
        List<String> problems = new ArrayList<>();
        PackOffer offer = PackOffer.from(Map.of("enabled", "false", "prompt", 5), jar(), problems).orElseThrow();
        assertEquals(PackOffer.DEFAULT_PROMPT, offer.prompt());
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void aUrlOverrideNeedsItsHash() {
        List<String> problems = new ArrayList<>();
        String other = "a".repeat(40);
        PackOffer custom = PackOffer.from(Map.of("url", "https://example.org/p.zip", "sha1", other.toUpperCase()),
                jar(), problems).orElseThrow();
        assertEquals("https://example.org/p.zip", custom.url());
        assertEquals(other, custom.sha1());

        PackOffer fallback = PackOffer.from(Map.of("url", "https://example.org/p.zip"), jar(), problems).orElseThrow();
        assertEquals(URL, fallback.url());
        assertEquals(1, problems.size());
        PackOffer.from(Map.of("url", "http://example.org/p.zip", "sha1", other), jar(), problems);
        assertEquals(2, problems.size());
    }

    @Test
    void noPropertiesInTheJarMeansNoOffer() {
        List<String> problems = new ArrayList<>();
        assertEquals(Optional.empty(), PackOffer.from(Map.of(), new Properties(), problems));
        assertEquals(1, problems.size());
    }

    @Test
    void decliningKicksAndFailuresOnlyWarn() {
        assertEquals(PackOffer.Action.KICK, PackOffer.onStatus("DECLINED"));
        for (String failed : List.of("FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD")) {
            assertEquals(PackOffer.Action.WARN, PackOffer.onStatus(failed), failed);
        }
        for (String fine : List.of("ACCEPTED", "DOWNLOADED", "SUCCESSFULLY_LOADED", "DISCARDED")) {
            assertEquals(PackOffer.Action.NONE, PackOffer.onStatus(fine), fine);
        }
    }

    @Test
    void theShippedPackYmlOffersTheBuiltPack() throws Exception {
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/pack.yml")) {
            root = new Yaml().load(in);
        }
        List<String> problems = new ArrayList<>();
        assertTrue(PackOffer.from(root, jar(), problems).isPresent());
        assertEquals(List.of(), problems);
    }
}
