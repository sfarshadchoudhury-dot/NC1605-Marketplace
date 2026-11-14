package com.marketplace.products.model;

import java.util.List;

/**
 * Raw, unvalidated input from the add/edit form. Price and stock are still text here
 * because that is what the user typed; {@code ProductValidator} checks them
 * and converts them to BigDecimal / int.
 */
public record ProductForm(String name,
                          String description,
                          String priceText,
                          String stockText,
                          boolean active,
                          List<String> categories,
                          List<String> imagePaths) {
}
