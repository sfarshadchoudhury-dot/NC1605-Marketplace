package com.marketplace.products.repository;

import com.marketplace.products.AppContext;
import com.marketplace.products.config.AppConfig;
import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import com.marketplace.products.model.ProductForm;
import com.marketplace.products.service.ImageStorageService;
import com.marketplace.products.service.ProductService;
import com.marketplace.products.service.SampleDataSeeder;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests against a REAL MongoDB. They are skipped (and reported as skipped) when no MongoDB is reachable.
 * Run them with:   mvn verify -Pintegration
 * They use their own temporary database (marketplace_it_xxxxxxxx) which is dropped afterwards,
 * so your real "marketplace" database is never touched.
 */
class MongoProductRepositoryIT {

    private static final String URI = System.getenv().getOrDefault("MONGODB_URI", "mongodb://localhost:27017");
    private static final String DATABASE = "marketplace_it_" + UUID.randomUUID().toString().substring(0, 8);

    @TempDir
    static Path dataDir;

    private static MongoClient rawClient;
    private AppContext context;
    private ProductService service;

    @BeforeAll
    static void connectOrSkip() {
        boolean available = false;
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(URI))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(2, TimeUnit.SECONDS))
                    .build();
            MongoClient client = MongoClients.create(settings);
            try {
                client.getDatabase("admin").runCommand(new Document("ping", 1));
                rawClient = client;
                available = true;
            } catch (RuntimeException e) {
                client.close();
            }
        } catch (RuntimeException e) {
            available = false;
        }
        assumeTrue(available, "MongoDB is not reachable at " + URI + " - start MongoDB Community Server (Windows: net start MongoDB; macOS: brew services start mongodb-community)");
    }

    @AfterAll
    static void dropTestDatabase() {
        if (rawClient != null) {
            rawClient.getDatabase(DATABASE).drop();
            rawClient.close();
        }
    }

    @BeforeEach
    void freshDatabase() {
        System.setProperty("java.awt.headless", "true");
        rawClient.getDatabase(DATABASE).drop();
        context = newContext();
        service = context.getProductService();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    private AppContext newContext() {
        AppContext newContext = new AppContext(new AppConfig(URI, DATABASE, dataDir));
        newContext.connect();
        return newContext;
    }

    private MongoCollection<Document> productsCollection() {
        return rawClient.getDatabase(DATABASE).getCollection("products");
    }

    private static ProductForm form(String name, String price, String stock, String... categories) {
        return new ProductForm(name, "desc", price, stock, true, List.of(categories), new ArrayList<>());
    }

    // ---------- storage format ----------

    @Test
    void productIsStoredWithDecimal128PriceAndTheProductIdAsDocumentId() {
        Product saved = service.addProduct(form("Desk Lamp", "39.90", "15", "Office"));

        Document raw = productsCollection().find(new Document("_id", saved.getId())).first();

        assertInstanceOf(String.class, raw.get("_id"));
        assertTrue(raw.getString("_id").matches("PRD-\\d{5}"));
        assertInstanceOf(Decimal128.class, raw.get("price"), "price must be Decimal128, not a double");
        assertEquals("39.90", ((Decimal128) raw.get("price")).bigDecimalValue().toPlainString());
        assertInstanceOf(Integer.class, raw.get("stockQuantity"));
        assertEquals(List.of("Office"), raw.getList("categories", String.class));
    }

    @Test
    void indexesAreCreatedAtConnectTime() {
        List<String> names = new ArrayList<>();
        for (Document index : productsCollection().listIndexes()) {
            names.add(index.getString("name"));
        }
        assertTrue(names.contains("_id_"), names.toString());
        assertTrue(names.contains("categories_1"), names.toString());
        assertTrue(names.contains("price_1"), names.toString());
        assertTrue(names.contains("name_1"), names.toString());
    }

    // ---------- persistence across restarts ----------

    @Test
    void productsAreStillThereAfterTheApplicationRestarts() {
        Product saved = service.addProduct(form("Desk Lamp", "39.90", "15", "Office", "Home & Kitchen"));
        service.setActive(service.addProduct(form("Mug", "4.50", "0", "Home & Kitchen")).getId(), false);
        context.close();                         // "close the application"

        AppContext restarted = newContext();     // "start it again"
        try {
            List<Product> all = restarted.getProductService().getAllProducts();
            assertEquals(2, all.size());
            Product reloaded = restarted.getProductService().getById(saved.getId());
            assertEquals("Desk Lamp", reloaded.getName());
            assertEquals("39.90", reloaded.getPrice().toPlainString());
            assertEquals(15, reloaded.getStockQuantity());
            assertEquals(List.of("Office", "Home & Kitchen"), reloaded.getCategories());
            assertEquals(1, restarted.getProductService()
                    .search(new ProductFilter().withStatus(ProductFilter.Status.INACTIVE)).matches().size());
        } finally {
            restarted.close();
        }
    }

    // ---------- IDs ----------

    @Test
    void idsAreSequentialAndFormatted() {
        assertEquals("PRD-00001", service.addProduct(form("A", "1", "1", "Books")).getId());
        assertEquals("PRD-00002", service.addProduct(form("B", "1", "1", "Books")).getId());
        assertEquals("PRD-00003", service.addProduct(form("C", "1", "1", "Books")).getId());
    }

    @Test
    void twentyThreadsAskingForIdsAtOnceNeverGetTheSameOne() throws Exception {
        MongoProductRepository repository = new MongoProductRepository(rawClient.getDatabase(DATABASE));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                Callable<String> task = repository::nextProductId;
                futures.add(pool.submit(task));
            }
            Set<String> ids = new HashSet<>();
            for (Future<String> future : futures) {
                ids.add(future.get(20, TimeUnit.SECONDS));
            }
            assertEquals(40, ids.size(), "every requested ID must be unique");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void editKeepsTheSameDocumentIdAndDoesNotCreateANewDocument() {
        Product original = service.addProduct(form("Desk Lamp", "39.90", "15", "Office"));

        service.updateProduct(original.getId(), form("Desk Lamp Pro", "49.99", "20", "Office", "Electronics"));

        assertEquals(1, productsCollection().countDocuments());
        Document raw = productsCollection().find().first();
        assertEquals(original.getId(), raw.getString("_id"));
        assertEquals("Desk Lamp Pro", raw.getString("name"));
        assertEquals("49.99", ((Decimal128) raw.get("price")).bigDecimalValue().toPlainString());
        assertEquals(20, raw.getInteger("stockQuantity"));
    }

    // ---------- repository edge cases ----------

    @Test
    void updateAndDeleteOfAnUnknownIdReportFalse() {
        MongoProductRepository repository = new MongoProductRepository(rawClient.getDatabase(DATABASE));
        Product ghost = service.addProduct(form("Ghost", "1", "1", "Books"));
        repository.deleteById(ghost.getId());

        assertFalse(repository.update(ghost));
        assertFalse(repository.deleteById("PRD-99999"));
        assertFalse(repository.findById("PRD-99999").isPresent());
    }

    @Test
    void deactivateAndDeletePersist() {
        Product product = service.addProduct(form("Mug", "4.50", "5", "Home & Kitchen"));

        service.setActive(product.getId(), false);
        assertFalse(service.getById(product.getId()).isActive());

        service.deleteProduct(product.getId());
        assertEquals(0, productsCollection().countDocuments());
    }

    @Test
    void moneyStaysExactAfterAnotherRoundTripThroughMongo() {
        String idA = service.addProduct(form("A", "0.10", "1", "Books")).getId();
        String idB = service.addProduct(form("B", "0.20", "1", "Books")).getId();

        BigDecimal sum = service.getById(idA).getPrice().add(service.getById(idB).getPrice());

        assertEquals("0.30", sum.toPlainString());
    }

    @Test
    void searchWorksOnRealData() {
        service.addProduct(form("Desk Lamp", "39.90", "15", "Office", "Home & Kitchen"));
        service.addProduct(form("Gaming Keyboard", "89.00", "0", "Electronics", "Office"));
        service.addProduct(form("Wireless Mouse", "24.99", "0", "Electronics", "Office"));

        ProductFilter filter = new ProductFilter()
                .withCategory("Office")
                .withAvailability(ProductFilter.Availability.OUT_OF_STOCK)
                .withMaxPrice(new BigDecimal("30"));

        List<Product> matches = service.search(filter).matches();
        assertEquals(1, matches.size());
        assertEquals("Wireless Mouse", matches.get(0).getName());
    }

    // ---------- seed command against real MongoDB ----------

    @Test
    void seedingTwiceCreatesNoDuplicatesInMongo() {
        ImageStorageService images = context.getImageStorage();
        SampleDataSeeder seeder = new SampleDataSeeder(service, images);

        SampleDataSeeder.SeedResult first = seeder.seed();
        long afterFirst = productsCollection().countDocuments();
        SampleDataSeeder.SeedResult second = seeder.seed();
        long afterSecond = productsCollection().countDocuments();

        assertEquals(SampleDataSeeder.sampleCount(), first.added());
        assertEquals(SampleDataSeeder.sampleCount(), afterFirst);
        assertEquals(0, second.added());
        assertEquals(afterFirst, afterSecond);
    }
}
