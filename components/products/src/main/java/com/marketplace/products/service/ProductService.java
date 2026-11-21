package com.marketplace.products.service;

import com.marketplace.products.model.Categories;
import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import com.marketplace.products.model.ProductForm;
import com.marketplace.products.repository.ProductRepository;
import com.marketplace.products.validation.ProductValidator;
import com.marketplace.products.validation.ValidationException;
import com.marketplace.products.validation.ValidationResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The business rules of the Products component. The UI talks only to this class
 * (never directly to MongoDB). Other components (Orders, Reviews, Inventory) are meant
 * to call getById / getAllProducts / search when they are integrated.
 */
public class ProductService {

    private final ProductRepository repository;
    private final ImageStorageService imageStorage;

    public ProductService(ProductRepository repository, ImageStorageService imageStorage) {
        this.repository = repository;
        this.imageStorage = imageStorage;
    }

    /** Loads the catalogue, applies the filter and returns the matches sorted by name. */
    public SearchResult search(ProductFilter filter) {
        List<Product> all = repository.findAll();
        List<Product> matches = ProductSearch.filter(all, filter);
        matches.sort(Comparator.comparing((Product p) -> p.getName().toLowerCase(Locale.ROOT))
                .thenComparing(Product::getId));
        return new SearchResult(matches, all.size(), ProductSearch.countByStockLevel(all),
                ProductSearch.collectCategories(all));
    }

    public List<Product> getAllProducts() {
        return repository.findAll();
    }

    public Product getById(String productId) {
        return repository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
    }

    /** Validates the form, creates a product with a brand-new ID and saves it. */
    public Product addProduct(ProductForm form) {
        requireValid(form);

        Instant now = Instant.now();
        Product product = new Product(
                repository.nextProductId(),
                form.name().trim(),
                cleanDescription(form.description()),
                ProductValidator.toPrice(form.priceText()),
                ProductValidator.toStock(form.stockText()),
                form.active(),
                Categories.cleanList(form.categories()),
                form.imagePaths(),
                now,
                now);
        repository.insert(product);
        return product;
    }

    /**
     * Changes every editable detail of an existing product. The ID and creation time are never touched.
     * Image files that were removed from the product are deleted from the data folder after the save succeeds.
     */
    public Product updateProduct(String productId, ProductForm form) {
        requireValid(form);
        Product product = getById(productId);

        List<String> removedImages = new ArrayList<>(product.getImagePaths());
        removedImages.removeAll(form.imagePaths());

        product.setName(form.name().trim());
        product.setDescription(cleanDescription(form.description()));
        product.setPrice(ProductValidator.toPrice(form.priceText()));
        product.setStockQuantity(ProductValidator.toStock(form.stockText()));
        product.setActive(form.active());
        product.setCategories(Categories.cleanList(form.categories()));
        product.setImagePaths(form.imagePaths());
        product.setUpdatedAt(Instant.now());

        if (!repository.update(product)) {
            throw new ProductNotFoundException(productId);
        }
        for (String imagePath : removedImages) {
            imageStorage.deleteImage(imagePath);
        }
        return product;
    }

    /** Switches a product on or off in the catalogue without deleting anything. */
    public Product setActive(String productId, boolean active) {
        Product product = getById(productId);
        if (product.isActive() == active) {
            return product;
        }
        product.setActive(active);
        product.setUpdatedAt(Instant.now());
        if (!repository.update(product)) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }

    /** Permanently removes a product and its image files. */
    public void deleteProduct(String productId) {
        Product product = getById(productId);
        if (!repository.deleteById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        for (String imagePath : product.getImagePaths()) {
            imageStorage.deleteImage(imagePath);
        }
    }

    private static void requireValid(ProductForm form) {
        ValidationResult result = ProductValidator.validate(form);
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    private static String cleanDescription(String description) {
        return description == null ? "" : description.trim();
    }
}
