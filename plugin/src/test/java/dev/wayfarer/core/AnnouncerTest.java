package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;

class AnnouncerTest {
    private final List<Component> sent = new ArrayList<>();
    private final Announcer announcer = new Announcer(sent::add);

    @Test
    void everyCategoryIsOnByDefault() {
        for (AnnounceCategory category : AnnounceCategory.values()) {
            assertTrue(announcer.announce(category, Component.text("hi"), null), category.switchName());
        }
        assertEquals(AnnounceCategory.values().length, sent.size());
    }

    @Test
    void aSwitchedOffCategoryIsSilentAndTheOthersStillSpeak() {
        announcer.configure(AnnounceSettings.parse(Map.of("feat", false), new ArrayList<>()));

        assertFalse(announcer.announce(AnnounceCategory.FEAT, Component.text("feat"), null));
        assertTrue(announcer.announce(AnnounceCategory.LADDER_RUNG, Component.text("rung"), null));
        assertEquals(List.of(Component.text("rung")), sent);
    }

    @Test
    void hoverTextIsAttachedToTheLineSent() {
        Component reward = Component.text("a moss heart");
        announcer.announce(AnnounceCategory.FEAT, Component.text("line"), reward);

        assertEquals(HoverEvent.showText(reward), sent.get(0).hoverEvent());
    }

    @Test
    void parsingKeepsMissingSwitchesOnAndReportsBadOnes() {
        List<String> problems = new ArrayList<>();
        AnnounceSettings settings = AnnounceSettings.parse(
                Map.of("streak", false, "server-goal", "no", "fish-record", true), problems);

        assertFalse(settings.on(AnnounceCategory.STREAK));
        assertTrue(settings.on(AnnounceCategory.SERVER_GOAL), "a non-boolean leaves it on");
        assertTrue(settings.on(AnnounceCategory.FEAT));
        assertEquals(2, problems.size());
        assertEquals(AnnounceSettings.defaults(), AnnounceSettings.parse(null, new ArrayList<>()));
    }
}
