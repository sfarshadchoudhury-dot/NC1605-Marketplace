package com.marketplace.products.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppConfigTest {

    @TempDir
    Path tempDir;

    private Path writeConfig(String text) throws IOException {
        Path file = tempDir.resolve("application.properties");
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void defaultsAreUsedWhenThereIsNoEnvironmentAndNoFile() {
        AppConfig config = AppConfig.load(Map.of(), tempDir.resolve("missing.properties"));

        assertEquals("mongodb://localhost:27017", config.getMongoUri());
        assertEquals("marketplace", config.getDatabaseName());
        assertTrue(config.getDataDir().isAbsolute());
        assertEquals("data", config.getDataDir().getFileName().toString());
    }

    @Test
    void fileSettingsOverrideDefaults() throws IOException {
        Path file = writeConfig("mongodb.uri=mongodb://db.example:27018\nmongodb.database=shop\ndata.dir=my-data\n");

        AppConfig config = AppConfig.load(Map.of(), file);

        assertEquals("mongodb://db.example:27018", config.getMongoUri());
        assertEquals("shop", config.getDatabaseName());
        assertEquals("my-data", config.getDataDir().getFileName().toString());
    }

    @Test
    void environmentVariablesOverrideTheFile() throws IOException {
        Path file = writeConfig("mongodb.uri=mongodb://from-file:27017\nmongodb.database=from_file\n");
        Map<String, String> env = Map.of("MONGODB_URI", "mongodb://from-env:27017", "MONGODB_DATABASE", "from_env");

        AppConfig config = AppConfig.load(env, file);

        assertEquals("mongodb://from-env:27017", config.getMongoUri());
        assertEquals("from_env", config.getDatabaseName());
    }

    @Test
    void blankEnvironmentValuesAreIgnored() throws IOException {
        Path file = writeConfig("mongodb.database=from_file\n");

        AppConfig config = AppConfig.load(Map.of("MONGODB_DATABASE", "   "), file);

        assertEquals("from_file", config.getDatabaseName());
    }

    @Test
    void credentialsAreHiddenWhenTheUriIsShownToTheUser() {
        AppConfig config = new AppConfig("mongodb://alice:s3cret@db.example:27017/?authSource=admin", "shop", tempDir);

        assertFalse(config.getSafeMongoUri().contains("alice"));
        assertFalse(config.getSafeMongoUri().contains("s3cret"));
        assertEquals("mongodb://***@db.example:27017/?authSource=admin", config.getSafeMongoUri());
        assertEquals("mongodb://localhost:27017", new AppConfig("mongodb://localhost:27017", "x", tempDir).getSafeMongoUri());
    }

    @Test
    void theSuppliedExampleConfigFileContainsNoCredentials() throws IOException {
        Path example = Path.of("config", "application.properties.example");   // Maven runs tests from the project folder

        assertTrue(Files.isRegularFile(example), "config/application.properties.example must exist");
        String text = Files.readString(example);
        assertFalse(text.matches("(?s).*://[^\\s/@]+:[^\\s/@]+@.*"), "example must not contain user:password@");
        assertTrue(text.contains("mongodb.uri"));
    }
}
