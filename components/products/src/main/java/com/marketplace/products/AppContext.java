package com.marketplace.products;

import com.marketplace.products.config.AppConfig;
import com.marketplace.products.repository.DatabaseException;
import com.marketplace.products.repository.MongoProductRepository;
import com.marketplace.products.service.ImageStorageService;
import com.marketplace.products.service.ProductService;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.concurrent.TimeUnit;

/**
 * Wires the application together: settings, MongoDB connection, repository and services.
 * connect() talks to the network, so the UI must call it from a background thread.
 * If MongoDB is down connect() throws a DatabaseException; the app never falls back to a fake database.
 */
public class AppContext implements AutoCloseable {

    private static final int SERVER_SELECTION_TIMEOUT_SECONDS = 3;
    private static final int CONNECT_TIMEOUT_SECONDS = 3;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final AppConfig config;
    private final ImageStorageService imageStorage;
    // volatile: the UI thread reads these while a background thread may be inside connect().
    private volatile MongoClient mongoClient;
    private volatile ProductService productService;

    public AppContext(AppConfig config) {
        this.config = config;
        this.imageStorage = new ImageStorageService(config.getDataDir());
    }

    /** Opens (or re-opens) the MongoDB connection, checks it with a ping and prepares the indexes. */
    public synchronized void connect() {
        closeClient();
        MongoClient client = null;
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(config.getMongoUri()))
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .build();
            client = MongoClients.create(settings);

            MongoDatabase database = client.getDatabase(config.getDatabaseName());
            database.runCommand(new Document("ping", 1));

            MongoProductRepository repository = new MongoProductRepository(database);
            repository.ensureIndexes();

            this.mongoClient = client;
            this.productService = new ProductService(repository, imageStorage);
        } catch (MongoException e) {
            closeQuietly(client);
            throw MongoProductRepository.translate(e);
        } catch (IllegalArgumentException e) {
            closeQuietly(client);
            throw new DatabaseException("MONGODB_URI is not a valid MongoDB connection string. "
                    + "Example: mongodb://localhost:27017", e);
        }
    }

    // Not synchronized on purpose: connect() holds the lock for seconds while MongoDB is unreachable,
    // and the UI thread must never wait for that.
    public boolean isConnected() {
        return productService != null;
    }

    public ProductService getProductService() {
        ProductService service = productService;
        if (service == null) {
            throw new IllegalStateException("Not connected to MongoDB yet.");
        }
        return service;
    }

    public ImageStorageService getImageStorage() {
        return imageStorage;
    }

    public AppConfig getConfig() {
        return config;
    }

    @Override
    public synchronized void close() {
        closeClient();
    }

    private void closeClient() {
        closeQuietly(mongoClient);
        mongoClient = null;
        productService = null;
    }

    private static void closeQuietly(MongoClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // nothing useful to do if closing fails
            }
        }
    }
}
