package com.marketplace.products.service;

import com.marketplace.products.model.Product;
import com.marketplace.products.repository.ProductRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TEST ONLY. A fake repository that keeps products in a map so service logic can be unit-tested
 * without MongoDB. The real application never uses this class (it is not in src/main).
 * It stores copies, like a real database, so changing a returned Product does not change the stored one.
 */
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Product> store = new LinkedHashMap<>();
    private int counter = 0;

    @Override
    public String nextProductId() {
        counter++;
        return String.format("PRD-%05d", counter);
    }

    @Override
    public String peekNextProductId() {
        return String.format("PRD-%05d", counter + 1);
    }

    @Override
    public void insert(Product product) {
        store.put(product.getId(), copyOf(product));
    }

    @Override
    public boolean update(Product product) {
        if (!store.containsKey(product.getId())) {
            return false;
        }
        store.put(product.getId(), copyOf(product));
        return true;
    }

    @Override
    public boolean deleteById(String id) {
        return store.remove(id) != null;
    }

    @Override
    public Optional<Product> findById(String id) {
        Product stored = store.get(id);
        return stored == null ? Optional.empty() : Optional.of(copyOf(stored));
    }

    @Override
    public List<Product> findAll() {
        List<Product> all = new ArrayList<>();
        for (Product stored : store.values()) {
            all.add(copyOf(stored));
        }
        return all;
    }

    private static Product copyOf(Product p) {
        return new Product(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStockQuantity(),
                p.isActive(), p.getCategories(), p.getImagePaths(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
