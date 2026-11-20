package com.marketplace.products;

import com.marketplace.products.config.AppConfig;
import com.marketplace.products.repository.DatabaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When MongoDB is not available the app must say so clearly, never pretend to work. */
class AppContextTest {

    @TempDir
    Path tempDir;

    @Test
    void unreachableMongoDbGivesAnActionableErrorAndNoFallbackDatabase() {
        // Port 1 is not a MongoDB server, so the connection must fail (takes about 3 seconds to time out).
        AppConfig config = new AppConfig("mongodb://localhost:1", "marketplace_unit_test", tempDir);

        try (AppContext context = new AppContext(config)) {
            DatabaseException error = assertThrows(DatabaseException.class, context::connect);

            assertTrue(error.getMessage().contains("Cannot reach MongoDB"), error.getMessage());
            assertTrue(error.getMessage().contains("MongoDB Community Server"), "message tells the user what to do");
            assertFalse(context.isConnected());
            assertThrows(IllegalStateException.class, context::getProductService);
        }
    }

    @Test
    void malformedConnectionStringGivesAClearError() {
        AppConfig config = new AppConfig("this is not a uri", "marketplace_unit_test", tempDir);

        try (AppContext context = new AppContext(config)) {
            DatabaseException error = assertThrows(DatabaseException.class, context::connect);

            assertTrue(error.getMessage().contains("not a valid MongoDB connection string"), error.getMessage());
        }
    }
}
