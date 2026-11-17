package com.marketplace.products;

import com.marketplace.products.config.AppConfig;
import com.marketplace.products.repository.DatabaseException;
import com.marketplace.products.service.SampleDataSeeder;

/**
 * The explicit seed command. Run it with:   mvn compile exec:java@seed
 * (or run this class from Eclipse). It never runs automatically, and running it again is safe.
 */
public class SeedMain {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");   // image drawing needs no screen

        AppConfig config = AppConfig.load();
        System.out.println("Seeding sample products into MongoDB " + config.getSafeMongoUri()
                + " / database '" + config.getDatabaseName() + "'");
        System.out.println("Image folder: " + config.getDataDir().resolve("images"));

        try (AppContext context = new AppContext(config)) {
            context.connect();
            SampleDataSeeder seeder = new SampleDataSeeder(context.getProductService(), context.getImageStorage());
            SampleDataSeeder.SeedResult result = seeder.seed();
            System.out.println("Done. Added " + result.added() + " product(s), skipped " + result.skipped()
                    + " that already existed (of " + SampleDataSeeder.sampleCount() + " samples).");
        } catch (DatabaseException e) {
            System.err.println("SEED FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}
