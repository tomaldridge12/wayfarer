package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ItemCatalogueTest {
    private static void add(ItemCatalogue c, String owner, String id) {
        c.add(owner, id, Component.text(id), Material.STONE, viewer -> List.of());
    }

    @Test
    void categoriesKeepTheOrderModulesRegisteredThem() {
        ItemCatalogue c = new ItemCatalogue();
        add(c, "alloys", "alloy_gear");
        add(c, "banners", "banners");
        add(c, "banners", "relics");
        assertEquals(List.of("alloy_gear", "banners", "relics"), c.categories().stream().map(ItemCatalogue.Category::id).toList());
    }

    @Test
    void anOwnerReplacingACategoryKeepsItsPlaceAndAnotherOwnerCannotTakeIt() {
        ItemCatalogue c = new ItemCatalogue();
        add(c, "banners", "banners");
        add(c, "curios", "curios");
        add(c, "banners", "banners");
        assertEquals(List.of("banners", "curios"), c.categories().stream().map(ItemCatalogue.Category::id).toList());
        assertThrows(IllegalArgumentException.class, () -> add(c, "marks", "banners"));
    }

    @Test
    void droppingAnOwnerRemovesOnlyItsCategories() {
        ItemCatalogue c = new ItemCatalogue();
        add(c, "banners", "banners");
        add(c, "banners", "relics");
        add(c, "curios", "curios");
        c.drop("banners");
        assertEquals(List.of("curios"), c.categories().stream().map(ItemCatalogue.Category::id).toList());
    }

    @Test
    void pagesAreAtLeastOneAndHoldFortyFiveItems() {
        assertEquals(1, ItemBrowser.pages(0));
        assertEquals(1, ItemBrowser.pages(45));
        assertEquals(2, ItemBrowser.pages(46));
        assertEquals(3, ItemBrowser.pages(91));
    }
}
