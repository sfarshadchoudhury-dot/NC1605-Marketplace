package com.marketplace.products.service;

import com.marketplace.products.model.Product;

import java.util.List;

/**
 * What one search returns to the UI: the matching products plus catalogue-wide numbers
 * for the status bar and the category drop-down.
 *
 * @param matches          products that passed the filter, newest first
 * @param totalCount       number of products in the whole catalogue (ignoring the filter)
 * @param stockLevelCounts catalogue-wide counts; index with StockLevel.ordinal()
 * @param categories       all known categories, sorted
 */
public record SearchResult(List<Product> matches, int totalCount, int[] stockLevelCounts, List<String> categories) {
}
