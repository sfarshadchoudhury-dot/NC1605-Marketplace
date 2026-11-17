package com.marketplace.products.service;

/** No product exists with the given ID (for example it was deleted by someone else). */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product " + productId + " no longer exists. Refresh the list and try again.");
    }
}
