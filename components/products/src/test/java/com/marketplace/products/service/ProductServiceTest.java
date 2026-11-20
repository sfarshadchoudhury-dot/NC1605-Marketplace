package com.marketplace.products.service;

import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import com.marketplace.products.model.ProductForm;
import com.marketplace.products.validation.ProductValidator;
import com.marketplace.products.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.marketplace.products.service.TestProducts.form;
import static com.marketplace.products.service.TestProducts.formWithImages;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Service rules tested against the in-memory fake repository (no MongoDB needed). */
class ProductServiceTest {

    @TempDir
    Path tempDir;

    private InMemoryProductRepository repository;
    private ImageStorageService imageStorage;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductRepository();
        imageStorage = new ImageStorageService(tempDir.resolve("data"));
        service = new ProductService(repository, imageStorage);
    }

    // ---------- adding ----------

    @Test
    void addProductSavesItWithCleanedValuesAndAnId() {
        Product saved = service.addProduct(form("  Desk Lamp  ", "39.9", " 15 ", "Office", "Home & Kitchen"));

        assertEquals("PRD-00001", saved.getId());
        assertEquals("Desk Lamp", saved.getName());
        assertEquals("39.90", saved.getPrice().toPlainString());
        assertEquals(15, saved.getStockQuantity());
        assertTrue(saved.isActive());
        assertEquals(List.of("Office", "Home & Kitchen"), saved.getCategories());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void everyNewProductGetsADifferentId() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            ids.add(service.addProduct(form("Product " + i, "1.00", "1", "Books")).getId());
        }
        assertEquals(25, ids.size());
    }

    @Test
    void invalidProductIsNotSavedAndDoesNotUseUpAnId() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> service.addProduct(form("   ", "-1", "2.5")));

        assertTrue(error.getResult().hasError(ProductValidator.FIELD_NAME));
        assertTrue(error.getResult().hasError(ProductValidator.FIELD_PRICE));
        assertTrue(error.getResult().hasError(ProductValidator.FIELD_STOCK));
        assertTrue(error.getResult().hasError(ProductValidator.FIELD_CATEGORIES));
        assertTrue(repository.findAll().isEmpty());

        Product next = service.addProduct(form("Real product", "5", "1", "Books"));
        assertEquals("PRD-00001", next.getId(), "a rejected form must not consume an ID");
    }

    @Test
    void duplicateAndBlankCategoriesAreCleaned() {
        Product saved = service.addProduct(form("Mug", "4", "1", "Home & Kitchen", " home & kitchen ", "  ", "Gifts"));
        assertEquals(List.of("Home & Kitchen", "Gifts"), saved.getCategories());
    }

    // ---------- editing: the ID is stable ----------

    @Test
    void editingChangesEveryDetailButNeverTheId() {
        Product original = service.addProduct(form("Desk Lamp", "39.90", "15", "Office"));
        String id = original.getId();

        ProductForm edited = new ProductForm("Desk Lamp Pro", "Brighter", "49.99", "20", false,
                List.of("Office", "Electronics"), new ArrayList<>());
        Product updated = service.updateProduct(id, edited);

        assertEquals(id, updated.getId());
        Product reloaded = service.getById(id);
        assertEquals(id, reloaded.getId());
        assertEquals("Desk Lamp Pro", reloaded.getName());
        assertEquals("Brighter", reloaded.getDescription());
        assertEquals(new BigDecimal("49.99"), reloaded.getPrice());
        assertEquals(20, reloaded.getStockQuantity());
        assertFalse(reloaded.isActive());
        assertEquals(List.of("Office", "Electronics"), reloaded.getCategories());
        assertEquals(original.getCreatedAt(), reloaded.getCreatedAt(), "creation time never changes");
        assertEquals(1, repository.findAll().size(), "editing must not create a second product");
    }

    @Test
    void editingManyTimesKeepsTheSameId() {
        String id = service.addProduct(form("Mug", "4", "1", "Home & Kitchen")).getId();
        for (int i = 1; i <= 5; i++) {
            service.updateProduct(id, form("Mug v" + i, "4." + i + "0", String.valueOf(i), "Home & Kitchen"));
        }
        assertEquals(id, service.getById(id).getId());
        assertEquals(1, repository.findAll().size());
        assertEquals("Mug v5", service.getById(id).getName());
    }

    @Test
    void invalidEditIsRejectedAndLeavesTheProductUnchanged() {
        String id = service.addProduct(form("Desk Lamp", "39.90", "15", "Office")).getId();

        assertThrows(ValidationException.class, () -> service.updateProduct(id, form("", "0", "-3")));

        Product unchanged = service.getById(id);
        assertEquals("Desk Lamp", unchanged.getName());
        assertEquals(new BigDecimal("39.90"), unchanged.getPrice());
        assertEquals(15, unchanged.getStockQuantity());
    }

    @Test
    void editingAnUnknownProductThrowsNotFound() {
        assertThrows(ProductNotFoundException.class,
                () -> service.updateProduct("PRD-99999", form("X", "1", "1", "Books")));
    }

    // ---------- activation / deactivation ----------

    @Test
    void deactivatingKeepsTheProductButTakesItOutOfSale() {
        Product product = service.addProduct(form("Mug", "4", "10", "Home & Kitchen"));
        assertTrue(product.isAvailableForSale());

        Product deactivated = service.setActive(product.getId(), false);

        assertFalse(deactivated.isActive());
        assertFalse(service.getById(product.getId()).isActive());
        assertFalse(service.getById(product.getId()).isAvailableForSale());
        assertEquals(1, service.getAllProducts().size(), "deactivated products are kept, not deleted");
        assertEquals(10, service.getById(product.getId()).getStockQuantity(), "stock is untouched");
    }

    @Test
    void reactivatingPutsTheProductBackOnSale() {
        String id = service.addProduct(form("Mug", "4", "10", "Home & Kitchen")).getId();
        service.setActive(id, false);
        service.setActive(id, true);
        assertTrue(service.getById(id).isActive());
        assertTrue(service.getById(id).isAvailableForSale());
    }

    @Test
    void activeProductWithNoStockIsNotAvailableForSale() {
        Product product = service.addProduct(form("Mug", "4", "0", "Home & Kitchen"));
        assertTrue(product.isActive());
        assertFalse(product.isAvailableForSale());
    }

    @Test
    void deactivatedProductsCanBeFoundWithTheStatusFilter() {
        String id1 = service.addProduct(form("Mug", "4", "10", "Home & Kitchen")).getId();
        service.addProduct(form("Plate", "6", "10", "Home & Kitchen"));
        service.setActive(id1, false);

        SearchResult inactive = service.search(new ProductFilter().withStatus(ProductFilter.Status.INACTIVE));
        SearchResult active = service.search(new ProductFilter().withStatus(ProductFilter.Status.ACTIVE));

        assertEquals(1, inactive.matches().size());
        assertEquals(id1, inactive.matches().get(0).getId());
        assertEquals(1, active.matches().size());
        assertEquals(2, inactive.totalCount());
    }

    @Test
    void deactivatingAnUnknownProductThrowsNotFound() {
        assertThrows(ProductNotFoundException.class, () -> service.setActive("PRD-00042", false));
    }

    // ---------- deleting ----------

    @Test
    void deleteRemovesTheProductAndItsImageFiles() throws IOException {
        Path source = TestProducts.writeImage(tempDir.resolve("src"), "photo.png", "png");
        String imageRef = imageStorage.storeImage(source);
        Product product = service.addProduct(formWithImages("Mug", "4", "1", List.of(imageRef), "Gifts"));
        assertTrue(Files.exists(imageStorage.resolve(imageRef)));

        service.deleteProduct(product.getId());

        assertTrue(service.getAllProducts().isEmpty());
        assertFalse(Files.exists(imageStorage.resolve(imageRef)), "image file should be cleaned up");
        assertThrows(ProductNotFoundException.class, () -> service.getById(product.getId()));
    }

    @Test
    void deletingAnUnknownProductThrowsNotFound() {
        assertThrows(ProductNotFoundException.class, () -> service.deleteProduct("PRD-12345"));
    }

    @Test
    void idsAreNeverReusedAfterADelete() {
        String first = service.addProduct(form("A", "1", "1", "Books")).getId();
        service.deleteProduct(first);
        String second = service.addProduct(form("B", "1", "1", "Books")).getId();
        assertNotEquals(first, second);
    }

    // ---------- images on edit ----------

    @Test
    void removingAnImageInTheEditFormDeletesItsFileOnSave() throws IOException {
        Path folder = tempDir.resolve("src");
        String keep = imageStorage.storeImage(TestProducts.writeImage(folder, "a.png", "png"));
        String drop = imageStorage.storeImage(TestProducts.writeImage(folder, "b.jpg", "jpg"));
        Product product = service.addProduct(formWithImages("Mug", "4", "1", List.of(keep, drop), "Gifts"));

        service.updateProduct(product.getId(), formWithImages("Mug", "4", "1", List.of(keep), "Gifts"));

        assertEquals(List.of(keep), service.getById(product.getId()).getImagePaths());
        assertTrue(Files.exists(imageStorage.resolve(keep)));
        assertFalse(Files.exists(imageStorage.resolve(drop)));
    }

    @Test
    void tooManyImagesAreRejected() {
        List<String> seven = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            seven.add("images/fake-" + i + ".png");
        }
        ValidationException error = assertThrows(ValidationException.class,
                () -> service.addProduct(formWithImages("Mug", "4", "1", seven, "Gifts")));
        assertTrue(error.getResult().hasError(ProductValidator.FIELD_IMAGES));
    }

    // ---------- search through the service ----------

    @Test
    void previewNextProductIdDoesNotUseUpTheNumber() {
        String preview = service.previewNextProductId();
        assertEquals(preview, service.previewNextProductId(), "peeking twice gives the same ID");
        Product added = service.addProduct(form("Desk lamp", "20", "4", "Office"));
        assertEquals(preview, added.getId(), "the next product really gets the previewed ID");
    }

    @Test
    void searchReturnsMatchesNewestFirstWithCatalogueWideCounts() {
        service.addProduct(form("banana stand", "10", "0", "Home & Kitchen"));
        service.addProduct(form("Apple slicer", "5", "3", "Home & Kitchen"));
        service.addProduct(form("cherry pitter", "7", "50", "Home & Kitchen"));

        SearchResult result = service.search(new ProductFilter().withNameQuery("pitter"));

        List<String> names = new ArrayList<>();
        for (Product p : result.matches()) {
            names.add(p.getName());
        }
        assertEquals(List.of("cherry pitter"), names);

        SearchResult all = service.search(new ProductFilter());
        List<String> allNames = new ArrayList<>();
        for (Product p : all.matches()) {
            allNames.add(p.getName());
        }
        assertEquals(List.of("cherry pitter", "Apple slicer", "banana stand"), allNames, "newest first");
        assertEquals(3, all.totalCount());
        assertEquals(1, all.stockLevelCounts()[com.marketplace.products.model.StockLevel.OUT_OF_STOCK.ordinal()]);
    }
}
