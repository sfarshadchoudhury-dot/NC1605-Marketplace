package com.marketplace.products.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One product in the catalogue.
 *
 * <p>The {@code id} is final and has no setter: once a product exists its ID can never change,
 * which keeps links from Orders, Reviews and Inventory valid.
 * Price is a {@link BigDecimal} (never double) because it is money.
 */
public class Product {

    private final String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private boolean active;
    private List<String> categories;
    private List<String> imagePaths;     // relative references such as "images/3f2a....png"
    private final Instant createdAt;
    private Instant updatedAt;

    public Product(String id, String name, String description, BigDecimal price, int stockQuantity,
                   boolean active, List<String> categories, List<String> imagePaths,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.active = active;
        this.categories = new ArrayList<>(categories);
        this.imagePaths = new ArrayList<>(imagePaths);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = new ArrayList<>(categories);
    }

    public List<String> getImagePaths() {
        return imagePaths;
    }

    public void setImagePaths(List<String> imagePaths) {
        this.imagePaths = new ArrayList<>(imagePaths);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public StockLevel getStockLevel() {
        return StockLevel.fromQuantity(stockQuantity);
    }

    /** A product can be sold only when it is active AND has stock. */
    public boolean isAvailableForSale() {
        return active && stockQuantity > 0;
    }

    /** The first image is the catalogue thumbnail; returns null when the product has no images. */
    public String getFirstImagePath() {
        if (imagePaths.isEmpty()) {
            return null;
        }
        return imagePaths.get(0);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Product)) {
            return false;
        }
        return id.equals(((Product) other).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id + " - " + name;
    }
}
