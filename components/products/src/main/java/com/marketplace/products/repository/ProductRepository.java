package com.marketplace.products.repository;

import com.marketplace.products.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * What the service layer needs from storage. MongoProductRepository is the real implementation;
 * unit tests use a small in-memory fake so they run without a database.
 */
public interface ProductRepository {

    /** Returns a new unique product ID such as "PRD-00007". Never returns the same ID twice. */
    String nextProductId();

    void insert(Product product);

    /** Saves changes to an existing product (matched by ID). Returns false if no such product exists. */
    boolean update(Product product);

    /** Returns true if a product was deleted, false if the ID was not found. */
    boolean deleteById(String id);

    Optional<Product> findById(String id);

    List<Product> findAll();
}
