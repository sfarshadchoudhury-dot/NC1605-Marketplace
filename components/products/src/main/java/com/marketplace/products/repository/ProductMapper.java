package com.marketplace.products.repository;

import com.marketplace.products.model.Product;
import org.bson.Document;
import org.bson.types.Decimal128;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Converts between a Product object and the MongoDB Document stored in the "products" collection.
 * Money is written as Decimal128 (exact decimal) and read back as BigDecimal, never as double.
 */
public final class ProductMapper {

    public static final String FIELD_ID = "_id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_STOCK = "stockQuantity";
    public static final String FIELD_ACTIVE = "active";
    public static final String FIELD_CATEGORIES = "categories";
    public static final String FIELD_IMAGE_PATHS = "imagePaths";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_UPDATED_AT = "updatedAt";

    private ProductMapper() {
    }

    public static Document toDocument(Product product) {
        Document document = new Document();
        document.put(FIELD_ID, product.getId());
        document.put(FIELD_NAME, product.getName());
        document.put(FIELD_DESCRIPTION, product.getDescription());
        document.put(FIELD_PRICE, new Decimal128(product.getPrice()));
        document.put(FIELD_STOCK, product.getStockQuantity());
        document.put(FIELD_ACTIVE, product.isActive());
        document.put(FIELD_CATEGORIES, new ArrayList<>(product.getCategories()));
        document.put(FIELD_IMAGE_PATHS, new ArrayList<>(product.getImagePaths()));
        document.put(FIELD_CREATED_AT, Date.from(product.getCreatedAt()));
        document.put(FIELD_UPDATED_AT, Date.from(product.getUpdatedAt()));
        return document;
    }

    public static Product fromDocument(Document document) {
        List<String> categories = document.getList(FIELD_CATEGORIES, String.class, new ArrayList<>());
        List<String> imagePaths = document.getList(FIELD_IMAGE_PATHS, String.class, new ArrayList<>());

        Date createdAt = document.getDate(FIELD_CREATED_AT);
        Date updatedAt = document.getDate(FIELD_UPDATED_AT);
        Instant created = createdAt == null ? Instant.EPOCH : createdAt.toInstant();
        Instant updated = updatedAt == null ? created : updatedAt.toInstant();

        return new Product(
                document.getString(FIELD_ID),
                document.getString(FIELD_NAME),
                document.getString(FIELD_DESCRIPTION) == null ? "" : document.getString(FIELD_DESCRIPTION),
                readPrice(document.get(FIELD_PRICE)),
                readStock(document.get(FIELD_STOCK)),
                document.getBoolean(FIELD_ACTIVE, true),
                categories,
                imagePaths,
                created,
                updated);
    }

    /** Reads a price stored as Decimal128. A hand-edited number is converted through its text, not via double maths. */
    private static BigDecimal readPrice(Object raw) {
        if (raw instanceof Decimal128) {
            return ((Decimal128) raw).bigDecimalValue();
        }
        if (raw instanceof Number) {
            return new BigDecimal(raw.toString());
        }
        return BigDecimal.ZERO;
    }

    private static int readStock(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        return 0;
    }
}
