package com.marketplace.products.repository;

import com.marketplace.products.model.Product;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.mongodb.client.model.Filters.eq;

/**
 * Stores products in the MongoDB "products" collection.
 * The product ID is the document's _id, so it is unique and MongoDB refuses to change it.
 * Every MongoDB failure is translated into a DatabaseException with a helpful message.
 */
public class MongoProductRepository implements ProductRepository {

    public static final String PRODUCTS_COLLECTION = "products";
    public static final String COUNTERS_COLLECTION = "counters";
    private static final String PRODUCT_COUNTER_ID = "products";
    private static final String ID_PREFIX = "PRD-";

    private final MongoCollection<Document> products;
    private final MongoCollection<Document> counters;

    public MongoProductRepository(MongoDatabase database) {
        this.products = database.getCollection(PRODUCTS_COLLECTION);
        this.counters = database.getCollection(COUNTERS_COLLECTION);
    }

    /** Creates the indexes (safe to call every start-up; MongoDB ignores ones that already exist). */
    public void ensureIndexes() {
        run(() -> {
            products.createIndex(Indexes.ascending(ProductMapper.FIELD_NAME));
            products.createIndex(Indexes.ascending(ProductMapper.FIELD_CATEGORIES));
            products.createIndex(Indexes.ascending(ProductMapper.FIELD_PRICE));
            return null;
        });
    }

    /**
     * Atomically adds 1 to a counter document and returns the new value, so two users can
     * never be given the same number. The ID is the number padded to 5 digits: PRD-00001, PRD-00002...
     */
    @Override
    public String nextProductId() {
        return run(() -> {
            Document counter = counters.findOneAndUpdate(
                    eq("_id", PRODUCT_COUNTER_ID),
                    Updates.inc("seq", 1),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            long sequence = ((Number) counter.get("seq")).longValue();
            return ID_PREFIX + String.format("%05d", sequence);
        });
    }

    @Override
    public String peekNextProductId() {
        return run(() -> {
            Document counter = counters.find(eq("_id", PRODUCT_COUNTER_ID)).first();
            long current = counter == null ? 0 : ((Number) counter.get("seq")).longValue();
            return ID_PREFIX + String.format("%05d", current + 1);
        });
    }

    @Override
    public void insert(Product product) {
        run(() -> {
            products.insertOne(ProductMapper.toDocument(product));
            return null;
        });
    }

    @Override
    public boolean update(Product product) {
        return run(() -> {
            // replaceOne matches on _id; the replacement carries the same _id, so the ID cannot change.
            long matched = products.replaceOne(eq("_id", product.getId()), ProductMapper.toDocument(product))
                    .getMatchedCount();
            return matched > 0;
        });
    }

    @Override
    public boolean deleteById(String id) {
        return run(() -> products.deleteOne(eq("_id", id)).getDeletedCount() > 0);
    }

    @Override
    public Optional<Product> findById(String id) {
        return run(() -> {
            Document document = products.find(eq("_id", id)).first();
            if (document == null) {
                return Optional.<Product>empty();
            }
            return Optional.of(ProductMapper.fromDocument(document));
        });
    }

    @Override
    public List<Product> findAll() {
        return run(() -> {
            List<Product> result = new ArrayList<>();
            for (Document document : products.find()) {
                result.add(ProductMapper.fromDocument(document));
            }
            return result;
        });
    }

    /** Runs a database action and converts any MongoException into a DatabaseException. */
    private <T> T run(Supplier<T> action) {
        try {
            return action.get();
        } catch (MongoException e) {
            throw translate(e);
        }
    }

    /** Turns a raw driver exception into a message that tells the user what to do. */
    public static DatabaseException translate(MongoException e) {
        if (e instanceof MongoTimeoutException || e instanceof MongoSocketException) {
            return new DatabaseException("Cannot reach MongoDB. Make sure it is running "
                    + "(for example: docker compose up -d) and that MONGODB_URI is correct, then try again.", e);
        }
        if (e instanceof MongoSecurityException) {
            return new DatabaseException("MongoDB rejected the username or password in MONGODB_URI. "
                    + "Check the credentials in your environment variable or config/application.properties.", e);
        }
        return new DatabaseException("The database operation failed: " + e.getMessage(), e);
    }
}
