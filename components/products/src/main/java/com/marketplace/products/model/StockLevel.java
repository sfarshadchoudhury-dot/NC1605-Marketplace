package com.marketplace.products.model;

/**
 * How healthy a product's stock quantity is. Used for the coloured stock badges
 * and for the "low stock / out of stock" counts in the status bar.
 */
public enum StockLevel {
    OUT_OF_STOCK("Out of stock"),
    LOW_STOCK("Low stock"),
    IN_STOCK("In stock");

    /** At or below this quantity (but above zero) a product counts as "low stock". */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private final String label;

    StockLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static StockLevel fromQuantity(int quantity) {
        if (quantity <= 0) {
            return OUT_OF_STOCK;
        }
        if (quantity <= LOW_STOCK_THRESHOLD) {
            return LOW_STOCK;
        }
        return IN_STOCK;
    }
}
