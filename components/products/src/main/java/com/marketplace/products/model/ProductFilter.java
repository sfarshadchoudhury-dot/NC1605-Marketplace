package com.marketplace.products.model;

import java.math.BigDecimal;

/**
 * The search and filter settings chosen in the UI. All settings are combined with AND:
 * a product must satisfy every setting that has been switched on.
 * A null / empty / ANY value means "do not filter on this".
 */
public class ProductFilter {

    /** Stock-based availability. */
    public enum Availability {
        ANY("Any availability"),
        IN_STOCK("In stock"),
        OUT_OF_STOCK("Out of stock");

        private final String label;

        Availability(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Whether the product is switched on (active) or off (inactive) in the catalogue. */
    public enum Status {
        ANY("Active and inactive"),
        ACTIVE("Active only"),
        INACTIVE("Inactive only");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private String nameQuery = "";
    private String category;            // null = all categories
    private BigDecimal minPrice;        // null = no lower limit
    private BigDecimal maxPrice;        // null = no upper limit
    private Availability availability = Availability.ANY;
    private Status status = Status.ANY;

    public String getNameQuery() {
        return nameQuery;
    }

    public ProductFilter withNameQuery(String nameQuery) {
        this.nameQuery = nameQuery == null ? "" : nameQuery;
        return this;
    }

    public String getCategory() {
        return category;
    }

    public ProductFilter withCategory(String category) {
        this.category = category;
        return this;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public ProductFilter withMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
        return this;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public ProductFilter withMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
        return this;
    }

    public Availability getAvailability() {
        return availability;
    }

    public ProductFilter withAvailability(Availability availability) {
        this.availability = availability == null ? Availability.ANY : availability;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    public ProductFilter withStatus(Status status) {
        this.status = status == null ? Status.ANY : status;
        return this;
    }
}
