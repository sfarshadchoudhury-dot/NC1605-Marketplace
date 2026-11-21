package com.marketplace.products.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Category helpers. Categories are plain text labels stored on each product,
 * so a product can have several and new ones can be created just by typing them.
 */
public final class Categories {

    /** Categories offered on the product form before any custom ones exist. */
    public static final String[] DEFAULTS = {
            "Electronics", "Home & Kitchen", "Clothing", "Books",
            "Sports & Outdoors", "Toys & Games", "Beauty", "Office"
    };

    public static final int MAX_NAME_LENGTH = 30;

    private Categories() {
    }

    /** Trims the text and squeezes runs of spaces into one space. Null becomes "". */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    public static boolean containsIgnoreCase(List<String> categories, String name) {
        for (String existing : categories) {
            if (existing.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Returns a new list with blanks removed and case-insensitive duplicates dropped. */
    public static List<String> cleanList(List<String> rawCategories) {
        List<String> cleaned = new ArrayList<>();
        if (rawCategories == null) {
            return cleaned;
        }
        for (String raw : rawCategories) {
            String name = normalise(raw);
            if (!name.isEmpty() && !containsIgnoreCase(cleaned, name)) {
                cleaned.add(name);
            }
        }
        return cleaned;
    }
}
