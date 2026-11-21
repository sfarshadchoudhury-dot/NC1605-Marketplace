package com.marketplace.products.service;

import com.marketplace.products.model.Categories;
import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import com.marketplace.products.model.StockLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Searching and filtering as plain Java loops and conditions (no database needed, so it is easy to test).
 * A product is kept only if it passes EVERY filter that has been switched on.
 */
public final class ProductSearch {

    private ProductSearch() {
    }

    /** Returns the products that satisfy the filter, in their original order. */
    public static List<Product> filter(List<Product> products, ProductFilter filter) {
        List<Product> result = new ArrayList<>();
        for (Product product : products) {
            if (matches(product, filter)) {
                result.add(product);
            }
        }
        return result;
    }

    public static boolean matches(Product product, ProductFilter filter) {
        if (!matchesName(product, filter)) {
            return false;
        }
        if (!matchesCategory(product, filter)) {
            return false;
        }
        if (!matchesPrice(product, filter)) {
            return false;
        }
        if (!matchesAvailability(product, filter)) {
            return false;
        }
        return matchesStatus(product, filter);
    }

    /** Case-insensitive "contains" match on the product name. An empty search text matches everything. */
    static boolean matchesName(Product product, ProductFilter filter) {
        String query = filter.getNameQuery().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return true;
        }
        return product.getName().toLowerCase(Locale.ROOT).contains(query);
    }

    /** A product has several categories; it matches if ANY of them equals the chosen one. */
    static boolean matchesCategory(Product product, ProductFilter filter) {
        String wanted = filter.getCategory();
        if (wanted == null || wanted.isBlank()) {
            return true;
        }
        for (String category : product.getCategories()) {
            if (category.equalsIgnoreCase(wanted.trim())) {
                return true;
            }
        }
        return false;
    }

    /** Inclusive price range. BigDecimal values are compared with compareTo, never == or equals. */
    static boolean matchesPrice(Product product, ProductFilter filter) {
        if (filter.getMinPrice() != null && product.getPrice().compareTo(filter.getMinPrice()) < 0) {
            return false;
        }
        if (filter.getMaxPrice() != null && product.getPrice().compareTo(filter.getMaxPrice()) > 0) {
            return false;
        }
        return true;
    }

    static boolean matchesAvailability(Product product, ProductFilter filter) {
        switch (filter.getAvailability()) {
            case IN_STOCK:
                return product.getStockQuantity() > 0;
            case OUT_OF_STOCK:
                return product.getStockQuantity() == 0;
            default:
                return true;
        }
    }

    static boolean matchesStatus(Product product, ProductFilter filter) {
        switch (filter.getStatus()) {
            case ACTIVE:
                return product.isActive();
            case INACTIVE:
                return !product.isActive();
            default:
                return true;
        }
    }

    /** Counts products per stock level. The array index is StockLevel.ordinal(). */
    public static int[] countByStockLevel(List<Product> products) {
        int[] counts = new int[StockLevel.values().length];
        for (Product product : products) {
            counts[product.getStockLevel().ordinal()]++;
        }
        return counts;
    }

    /** Every category in use plus the default ones, sorted A-Z ignoring case, without duplicates. */
    public static List<String> collectCategories(List<Product> products) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String defaultCategory : Categories.DEFAULTS) {
            names.add(defaultCategory);
        }
        for (Product product : products) {
            names.addAll(product.getCategories());
        }
        return new ArrayList<>(names);
    }
}
