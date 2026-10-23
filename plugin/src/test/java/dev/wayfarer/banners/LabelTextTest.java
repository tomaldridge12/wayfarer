package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LabelTextTest {
    @Test
    void labelShowsNameThenOneEffectLine() {
        BannerDefinition def = new BannerDefinition("zombie", "Zombie Banner", "green", List.of(), "zombie", 100, 10, 15, 16);
        assertEquals(List.of("Zombie Banner", "+10% damage, -15% taken vs zombie family, 16 blocks"),
                LabelText.lines("zombie", Optional.of(def)));
    }

    @Test
    void zeroEffectsAndInertBannersSaySo() {
        BannerDefinition none = new BannerDefinition("x", "X", "white", List.of(), "zombie", 100, 0, 0, 8);
        assertEquals("No effect vs zombie family, 8 blocks", LabelText.lines("x", Optional.of(none)).get(1));
        assertEquals(List.of("gone", "Inactive: not in config"), LabelText.lines("gone", Optional.empty()));
    }
}
