package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemTagsTest {
    private static ItemTags.Holder holder(Map<String, String> store) {
        return new ItemTags.Holder() {
            @Override
            public String get(String key) {
                return store.get(key);
            }

            @Override
            public void set(String key, String value) {
                store.put(key, value);
            }
        };
    }

    @Test
    void writtenValueReadsBackPerModule() {
        ItemTags.Holder h = holder(new HashMap<>());
        ItemTags.write(h, "banners", "zombie");
        ItemTags.write(h, "gates", "spawn");
        assertEquals(Optional.of("zombie"), ItemTags.read(h, "banners"));
        assertEquals(Optional.of("spawn"), ItemTags.read(h, "gates"));
    }

    @Test
    void untaggedItemReadsEmptyAndBadModuleIdIsRejected() {
        ItemTags.Holder h = holder(new HashMap<>());
        assertEquals(Optional.empty(), ItemTags.read(h, "banners"));
        assertThrows(IllegalArgumentException.class, () -> ItemTags.write(h, "Bad Id", "x"));
    }
}
