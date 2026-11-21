package com.marketplace.products.service;

import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seed logic tested with the in-memory fake: re-running must never duplicate or overwrite. */
class SampleDataSeederTest {

    @TempDir
    Path tempDir;

    private InMemoryProductRepository repository;
    private ImageStorageService imageStorage;
    private ProductService service;
    private SampleDataSeeder seeder;

    @BeforeEach
    void setUp() {
        System.setProperty("java.awt.headless", "true");
        repository = new InMemoryProductRepository();
        imageStorage = new ImageStorageService(tempDir.resolve("data"));
        service = new ProductService(repository, imageStorage);
        seeder = new SampleDataSeeder(service, imageStorage);
    }

    @Test
    void firstRunAddsAllSamples() {
        SampleDataSeeder.SeedResult result = seeder.seed();

        assertEquals(SampleDataSeeder.sampleCount(), result.added());
        assertEquals(0, result.skipped());
        assertEquals(SampleDataSeeder.sampleCount(), service.getAllProducts().size());
        assertTrue(SampleDataSeeder.sampleCount() >= 14 && SampleDataSeeder.sampleCount() <= 16, "about 15 samples");
    }

    @Test
    void runningTheSeedAgainDoesNotDuplicateAnything() {
        seeder.seed();
        List<Product> before = service.getAllProducts();

        SampleDataSeeder.SeedResult second = seeder.seed();
        SampleDataSeeder.SeedResult third = seeder.seed();

        assertEquals(0, second.added());
        assertEquals(SampleDataSeeder.sampleCount(), second.skipped());
        assertEquals(0, third.added());
        assertEquals(before.size(), service.getAllProducts().size());
    }

    @Test
    void secondRunDoesNotCreateExtraImageFiles() throws Exception {
        seeder.seed();
        long imagesAfterFirstRun = countImageFiles();

        seeder.seed();

        assertEquals(imagesAfterFirstRun, countImageFiles());
    }

    @Test
    void existingProductsAreNeverOverwritten() {
        // A user already has a product with the same name as a sample, but a different price and stock.
        Product mine = service.addProduct(TestProducts.form("mechanical keyboard", "1.23", "999", "Office"));

        SampleDataSeeder.SeedResult result = seeder.seed();

        assertEquals(1, result.skipped());
        assertEquals(SampleDataSeeder.sampleCount() - 1, result.added());
        Product after = service.getById(mine.getId());
        assertEquals("mechanical keyboard", after.getName());
        assertEquals("1.23", after.getPrice().toPlainString());
        assertEquals(999, after.getStockQuantity());
        assertEquals(1, service.search(new ProductFilter().withNameQuery("Mechanical Keyboard")).matches().size());
    }

    @Test
    void aDeletedSampleIsAddedBackOnTheNextRunAndNothingElseChanges() {
        seeder.seed();
        Product victim = service.getAllProducts().get(0);
        service.deleteProduct(victim.getId());

        SampleDataSeeder.SeedResult result = seeder.seed();

        assertEquals(1, result.added());
        assertEquals(SampleDataSeeder.sampleCount(), service.getAllProducts().size());
    }

    @Test
    void sampleDataIsVariedAndEveryImageFileExists() {
        seeder.seed();
        List<Product> products = service.getAllProducts();

        boolean hasInactive = false;
        boolean hasOutOfStock = false;
        boolean hasLowStock = false;
        boolean hasManyCategories = false;
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();

        for (Product product : products) {
            ids.add(product.getId());
            names.add(product.getName().toLowerCase(Locale.ROOT));
            hasInactive |= !product.isActive();
            hasOutOfStock |= product.getStockQuantity() == 0;
            hasLowStock |= product.getStockQuantity() > 0 && product.getStockQuantity() <= 5;
            assertEquals(1, product.getImagePaths().size(), "every sample gets its one photo: " + product.getName());
            hasManyCategories |= product.getCategories().size() >= 2;
            assertTrue(product.getPrice().signum() > 0);
            for (String imagePath : product.getImagePaths()) {
                assertTrue(imageStorage.exists(imagePath), "missing image file " + imagePath);
                assertTrue(imagePath.startsWith("images/"));
            }
        }

        assertTrue(hasInactive, "need an inactive sample");
        assertTrue(hasOutOfStock, "need an out-of-stock sample");
        assertTrue(hasLowStock, "need a low-stock sample");
        assertTrue(hasManyCategories, "need a sample with multiple categories");
        assertEquals(products.size(), ids.size(), "IDs are unique");
        assertEquals(products.size(), names.size(), "names are unique");
    }

    @Test
    void everySampleHasItsPhotoInTheProject() {
        for (String name : SampleDataSeeder.sampleNames()) {
            String resource = SampleDataSeeder.photoResource(name);
            assertNotNull(SampleDataSeeder.class.getResource(resource), "missing photo " + resource + " for " + name);
        }
        assertEquals("/com/marketplace/products/sample-images/beginner-s-guide-to-java-programming.jpg",
                SampleDataSeeder.photoResource("Beginner's Guide to Java Programming"));
    }

    private long countImageFiles() throws Exception {
        Path images = tempDir.resolve("data").resolve("images");
        if (!Files.isDirectory(images)) {
            return 0;
        }
        try (var files = Files.list(images)) {
            return files.count();
        }
    }
}
