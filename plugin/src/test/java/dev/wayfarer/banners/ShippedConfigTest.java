package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The banners.yml shipped in the jar must parse cleanly; catches broken YAML before a server boot does. */
class ShippedConfigTest {
    private static final Vocabulary ANY = new Vocabulary() {
        @Override
        public boolean isMob(String name) {
            return true;
        }

        @Override
        public boolean isPattern(String name) {
            return true;
        }

        @Override
        public boolean isColour(String name) {
            return true;
        }

        @Override
        public boolean isSpawnReason(String name) {
            return true;
        }

        @Override
        public boolean isParticle(String name) {
            return true;
        }

        @Override
        public boolean isEffect(String name) {
            return true;
        }
    };

    @Test
    void shippedConfigLoadsFiveStarterBannersWithoutProblems() throws Exception {
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/banners.yml")) {
            root = new Yaml().load(in);
        }
        BannerSet set = BannerConfigParser.parse(root, ANY);
        assertEquals(List.of(), set.problems());
        assertEquals(List.of("zombie", "skeleton", "creeper", "spider", "enderman"), List.copyOf(set.banners().keySet()));
        set.banners().values().forEach(b -> {
            assertEquals(100, b.kills());
            assertEquals(10, b.damageBonusPercent());
            assertEquals(10, b.damageReductionPercent());
            assertEquals(16, b.radius());
        });
        List<String> problems = new java.util.ArrayList<>();
        FeedbackSettings feedback = FeedbackSettings.parse(root.get("feedback"), ANY, problems);
        assertEquals(List.of(), problems);
        assertFalse(feedback.edgeParticles());
        assertFalse(feedback.markerEffect());
    }
}
