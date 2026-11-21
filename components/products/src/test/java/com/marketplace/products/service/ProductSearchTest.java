package com.marketplace.products.service;

import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import com.marketplace.products.model.StockLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.marketplace.products.service.TestProducts.product;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductSearchTest {

    private List<Product> catalogue;

    @BeforeEach
    void buildCatalogue() {
        catalogue = new ArrayList<>(List.of(
                product("P1", "Desk Lamp", "39.90", 15, true, "Home & Kitchen", "Office"),
                product("P2", "Gaming Keyboard", "89.00", 40, true, "Electronics", "Office"),
                product("P3", "Wireless Mouse", "24.99", 0, true, "Electronics", "Office"),
                product("P4", "Paperback Novel", "9.99", 0, false, "Books"),
                product("P5", "Hiking Jacket", "119.00", 3, true, "Clothing", "Sports & Outdoors"),
                product("P6", "Lamp Shade", "12.00", 7, false, "Home & Kitchen")));
    }

    private List<String> idsOf(List<Product> products) {
        List<String> ids = new ArrayList<>();
        for (Product p : products) {
            ids.add(p.getId());
        }
        return ids;
    }

    private List<String> search(ProductFilter filter) {
        return idsOf(ProductSearch.filter(catalogue, filter));
    }

    // ---------- single filters ----------

    @Test
    void emptyFilterReturnsEveryProduct() {
        assertEquals(6, search(new ProductFilter()).size());
    }

    @Test
    void nameSearchIsCaseInsensitiveAndMatchesAnywhereInTheName() {
        assertEquals(List.of("P1", "P6"), search(new ProductFilter().withNameQuery("lamp")));
        assertEquals(List.of("P1", "P6"), search(new ProductFilter().withNameQuery("LAMP")));
        assertEquals(List.of("P3"), search(new ProductFilter().withNameQuery("less mou")));
    }

    @Test
    void nameSearchIgnoresSurroundingSpacesAndBlankSearchMatchesAll() {
        assertEquals(List.of("P1", "P6"), search(new ProductFilter().withNameQuery("  lamp  ")));
        assertEquals(6, search(new ProductFilter().withNameQuery("   ")).size());
    }

    @Test
    void nameSearchWithNoMatchReturnsEmptyList() {
        assertTrue(search(new ProductFilter().withNameQuery("zzz")).isEmpty());
    }

    @Test
    void categoryFilterMatchesIfAnyOfTheProductsCategoriesMatches() {
        assertEquals(List.of("P1", "P2", "P3"), search(new ProductFilter().withCategory("Office")));
        assertEquals(List.of("P2", "P3"), search(new ProductFilter().withCategory("electronics")));
        assertEquals(List.of("P5"), search(new ProductFilter().withCategory("Sports & Outdoors")));
    }

    @Test
    void priceRangeIsInclusiveAtBothEnds() {
        // 39.90 is stored with 2 decimals; the bounds below have different scales on purpose
        ProductFilter filter = new ProductFilter()
                .withMinPrice(new BigDecimal("12"))
                .withMaxPrice(new BigDecimal("39.9"));
        assertEquals(List.of("P1", "P3", "P6"), search(filter));
    }

    @Test
    void minOnlyAndMaxOnlyBoundsWork() {
        assertEquals(List.of("P2", "P5"), search(new ProductFilter().withMinPrice(new BigDecimal("50"))));
        assertEquals(List.of("P4", "P6"), search(new ProductFilter().withMaxPrice(new BigDecimal("12.00"))));
    }

    @Test
    void availabilityFilterUsesStockQuantity() {
        assertEquals(List.of("P1", "P2", "P5", "P6"),
                search(new ProductFilter().withAvailability(ProductFilter.Availability.IN_STOCK)));
        assertEquals(List.of("P3", "P4"),
                search(new ProductFilter().withAvailability(ProductFilter.Availability.OUT_OF_STOCK)));
    }

    @Test
    void statusFilterSeparatesActiveFromInactive() {
        assertEquals(List.of("P4", "P6"), search(new ProductFilter().withStatus(ProductFilter.Status.INACTIVE)));
        assertEquals(List.of("P1", "P2", "P3", "P5"), search(new ProductFilter().withStatus(ProductFilter.Status.ACTIVE)));
    }

    // ---------- combined filters (every active filter must pass) ----------

    @Test
    void nameAndCategoryAreCombined() {
        assertEquals(List.of("P1"), search(new ProductFilter().withNameQuery("lamp").withCategory("Office")));
    }

    @Test
    void categoryAndAvailabilityAreCombined() {
        assertEquals(List.of("P3"), search(new ProductFilter()
                .withCategory("Electronics").withAvailability(ProductFilter.Availability.OUT_OF_STOCK)));
    }

    @Test
    void categoryPriceAndStatusAreCombined() {
        ProductFilter filter = new ProductFilter()
                .withCategory("Office")
                .withMaxPrice(new BigDecimal("50"))
                .withStatus(ProductFilter.Status.ACTIVE);
        assertEquals(List.of("P1", "P3"), search(filter));
    }

    @Test
    void allFiltersTogetherNarrowToOneProduct() {
        ProductFilter filter = new ProductFilter()
                .withNameQuery("mouse")
                .withCategory("Electronics")
                .withMinPrice(new BigDecimal("20"))
                .withMaxPrice(new BigDecimal("30"))
                .withAvailability(ProductFilter.Availability.OUT_OF_STOCK)
                .withStatus(ProductFilter.Status.ACTIVE);
        assertEquals(List.of("P3"), search(filter));
    }

    @Test
    void contradictoryFiltersReturnNothing() {
        ProductFilter filter = new ProductFilter()
                .withCategory("Books")
                .withStatus(ProductFilter.Status.ACTIVE);       // the only Books product is inactive
        assertTrue(search(filter).isEmpty());
    }

    @Test
    void filteringDoesNotChangeTheOriginalList() {
        search(new ProductFilter().withNameQuery("lamp"));
        assertEquals(6, catalogue.size());
    }

    // ---------- helpers used by the status bar and category drop-down ----------

    @Test
    void stockLevelCountsAreIndexedByStockLevelOrdinal() {
        int[] counts = ProductSearch.countByStockLevel(catalogue);
        assertEquals(2, counts[StockLevel.OUT_OF_STOCK.ordinal()]);   // P3, P4
        assertEquals(1, counts[StockLevel.LOW_STOCK.ordinal()]);      // P5 (3 units)
        assertEquals(3, counts[StockLevel.IN_STOCK.ordinal()]);       // P1, P2, P6 (7 units is above the threshold)
    }

    @Test
    void stockLevelBoundaries() {
        assertEquals(StockLevel.OUT_OF_STOCK, StockLevel.fromQuantity(0));
        assertEquals(StockLevel.LOW_STOCK, StockLevel.fromQuantity(1));
        assertEquals(StockLevel.LOW_STOCK, StockLevel.fromQuantity(StockLevel.LOW_STOCK_THRESHOLD));
        assertEquals(StockLevel.IN_STOCK, StockLevel.fromQuantity(StockLevel.LOW_STOCK_THRESHOLD + 1));
    }

    @Test
    void collectedCategoriesIncludeDefaultsAndCustomOnesWithoutDuplicates() {
        catalogue.add(product("P7", "Charger", "9.99", 10, true, "Accessories", "electronics"));
        List<String> categories = ProductSearch.collectCategories(catalogue);

        assertTrue(categories.contains("Accessories"));
        assertTrue(categories.contains("Books"));
        long electronicsEntries = categories.stream().filter(c -> c.equalsIgnoreCase("electronics")).count();
        assertEquals(1, electronicsEntries, "Electronics and electronics must be one entry");
        assertEquals("Accessories", categories.get(0), "sorted A-Z ignoring case");
    }
}
