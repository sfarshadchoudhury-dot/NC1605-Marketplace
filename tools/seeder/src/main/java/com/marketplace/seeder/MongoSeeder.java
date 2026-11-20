package com.marketplace.seeder;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.eq;

/**
 * Writes the checked seed documents into MongoDB.
 * By default it only ADDS documents that are missing: it never overwrites or deletes anyone's data.
 */
public final class MongoSeeder {

    /** What happened to one collection. */
    public record Result(String collection, int added, int unchanged, int different, long counterValue) {
    }

    private static final Pattern NUMBER_AT_END = Pattern.compile("(\\d+)$");

    private final MongoDatabase database;

    public MongoSeeder(MongoDatabase database) {
        this.database = database;
    }

    /** Deletes every document in the collection and its counter, so it can be loaded from scratch. */
    public void reset(String collection) {
        database.getCollection(collection).drop();
        database.getCollection("counters").deleteOne(eq("_id", collection));
    }

    /**
     * Adds each seed document whose _id is not in the collection yet.
     * A document that exists with different content is left alone and counted as "different".
     */
    public Result load(String collection, List<Document> documents) {
        MongoCollection<Document> target = database.getCollection(collection);
        int added = 0;
        int unchanged = 0;
        int different = 0;
        long highest = 0;

        for (Document document : documents) {
            String id = document.getString("_id");
            highest = Math.max(highest, numberIn(id));
            Document existing = target.find(eq("_id", id)).first();
            if (existing == null) {
                target.insertOne(document);
                added++;
            } else if (existing.equals(document)) {
                unchanged++;
            } else {
                different++;
            }
        }

        long counterValue = moveCounterPast(collection, highest);
        return new Result(collection, added, unchanged, different, counterValue);
    }

    /**
     * Makes sure counters.seq is at least the highest seed number, so IDs made later in the apps
     * (for example PRD-00016) never clash with seed IDs. $max never lowers a counter.
     */
    private long moveCounterPast(String collection, long highest) {
        MongoCollection<Document> counters = database.getCollection("counters");
        if (highest > 0) {
            counters.updateOne(eq("_id", collection), Updates.max("seq", highest), new UpdateOptions().upsert(true));
        }
        Document counter = counters.find(eq("_id", collection)).first();
        return counter == null || !(counter.get("seq") instanceof Number seq) ? 0 : seq.longValue();
    }

    /**
     * Creates the indexes that protect the shared rules (unique e-mail, one stock record per product per warehouse)
     * and speed up the links other components look up. Safe to repeat: MongoDB skips indexes that exist.
     * Returns a warning message, or null when everything worked.
     */
    public String ensureIndexes(String collection) {
        try {
            MongoCollection<Document> target = database.getCollection(collection);
            switch (collection) {
                case "customers" -> target.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
                case "inventory" -> target.createIndex(Indexes.ascending("productId", "warehouseId"),
                        new IndexOptions().unique(true));
                case "orders" -> target.createIndex(Indexes.ascending("customerId"));
                case "shipments" -> target.createIndex(Indexes.ascending("orderId"));
                case "reviews" -> target.createIndex(Indexes.ascending("productId"));
                case "transactions" -> target.createIndex(Indexes.ascending("orderId"));
                default -> {
                    // products creates its own indexes; warehouses is tiny
                }
            }
            return null;
        } catch (MongoException e) {
            return "Could not create an index on " + collection + " (" + e.getMessage() + "). "
                    + "Your own data probably breaks a rule, such as two customers with the same e-mail.";
        }
    }

    /** "PRD-00015" gives 15, "WHS-003" gives 3. */
    static long numberIn(String id) {
        Matcher matcher = NUMBER_AT_END.matcher(id == null ? "" : id);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0;
    }
}
