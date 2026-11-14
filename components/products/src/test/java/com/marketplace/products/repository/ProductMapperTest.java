package com.marketplace.products.repository;

import com.marketplace.products.model.Product;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the Product <-> MongoDB document conversion without needing a database. */
class ProductMapperTest {

    private Product sample() {
        return new Product("PRD-00007", "Desk Lamp", "Bright", new BigDecimal("39.90"), 15, true,
                List.of("Office", "Home & Kitchen"), List.of("images/a.png", "images/b.jpg"),
                Instant.parse("2026-02-03T10:15:30Z"), Instant.parse("2026-02-04T11:00:00Z"));
    }

    @Test
    void priceIsStoredAsDecimal128NotAsDouble() {
        Document document = ProductMapper.toDocument(sample());

        assertInstanceOf(Decimal128.class, document.get("price"));
        assertEquals("39.90", ((Decimal128) document.get("price")).bigDecimalValue().toPlainString());
    }

    @Test
    void productIdBecomesTheDocumentId() {
        Document document = ProductMapper.toDocument(sample());
        assertEquals("PRD-00007", document.get("_id"));
        assertTrue(!document.containsKey("id") && !document.containsKey("productId"), "only one ID field");
    }

    @Test
    void stockIsAnIntegerAndActiveIsABoolean() {
        Document document = ProductMapper.toDocument(sample());
        assertEquals(15, document.get("stockQuantity"));
        assertInstanceOf(Integer.class, document.get("stockQuantity"));
        assertEquals(Boolean.TRUE, document.get("active"));
    }

    @Test
    void roundTripKeepsEveryField() {
        Product original = sample();

        Product copy = ProductMapper.fromDocument(ProductMapper.toDocument(original));

        assertEquals(original.getId(), copy.getId());
        assertEquals("Desk Lamp", copy.getName());
        assertEquals("Bright", copy.getDescription());
        assertEquals(0, original.getPrice().compareTo(copy.getPrice()));
        assertEquals("39.90", copy.getPrice().toPlainString(), "no precision lost or added");
        assertEquals(15, copy.getStockQuantity());
        assertTrue(copy.isActive());
        assertEquals(List.of("Office", "Home & Kitchen"), copy.getCategories());
        assertEquals(List.of("images/a.png", "images/b.jpg"), copy.getImagePaths());
        assertEquals(original.getCreatedAt(), copy.getCreatedAt());
        assertEquals(original.getUpdatedAt(), copy.getUpdatedAt());
    }

    @Test
    void moneyArithmeticStaysExact() {
        Product a = ProductMapper.fromDocument(ProductMapper.toDocument(new Product("A", "A", "", new BigDecimal("0.10"),
                1, true, List.of("x"), List.of(), Instant.EPOCH, Instant.EPOCH)));
        Product b = ProductMapper.fromDocument(ProductMapper.toDocument(new Product("B", "B", "", new BigDecimal("0.20"),
                1, true, List.of("x"), List.of(), Instant.EPOCH, Instant.EPOCH)));
        assertEquals("0.30", a.getPrice().add(b.getPrice()).toPlainString());
    }

    @Test
    void aHandEditedDoublePriceIsReadViaItsTextNotWithBinaryNoise() {
        Document legacy = ProductMapper.toDocument(sample());
        legacy.put("price", 19.99);   // someone typed a plain number in a Mongo shell

        assertEquals("19.99", ProductMapper.fromDocument(legacy).getPrice().toPlainString());
    }

    @Test
    void missingOptionalFieldsFallBackToSafeDefaults() {
        Document sparse = new Document("_id", "PRD-00001").append("name", "Bare");

        Product product = ProductMapper.fromDocument(sparse);

        assertEquals("", product.getDescription());
        assertEquals(BigDecimal.ZERO, product.getPrice());
        assertEquals(0, product.getStockQuantity());
        assertTrue(product.isActive());
        assertTrue(product.getCategories().isEmpty());
        assertTrue(product.getImagePaths().isEmpty());
    }
}
