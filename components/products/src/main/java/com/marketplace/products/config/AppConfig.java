package com.marketplace.products.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

/**
 * Connection and storage settings. Each setting is looked up in this order:
 * <ol>
 *   <li>environment variable (MONGODB_URI, MONGODB_DATABASE, PRODUCTS_DATA_DIR)</li>
 *   <li>local file config/application.properties (keys mongodb.uri, mongodb.database, data.dir)</li>
 *   <li>built-in default (local MongoDB, database "marketplace", folder "data")</li>
 * </ol>
 * No credentials are ever stored in the source code.
 */
public final class AppConfig {

    public static final String DEFAULT_MONGO_URI = "mongodb://localhost:27017";
    public static final String DEFAULT_DATABASE = "marketplace";
    public static final String DEFAULT_DATA_DIR = "data";
    public static final String DEFAULT_CONFIG_FILE = "config/application.properties";

    private final String mongoUri;
    private final String databaseName;
    private final Path dataDir;

    public AppConfig(String mongoUri, String databaseName, Path dataDir) {
        this.mongoUri = mongoUri;
        this.databaseName = databaseName;
        this.dataDir = dataDir;
    }

    /** Loads the real settings from the process environment and the default config file. */
    public static AppConfig load() {
        Map<String, String> env = System.getenv();
        String configFile = env.getOrDefault("PRODUCTS_CONFIG_FILE", DEFAULT_CONFIG_FILE);
        return load(env, Paths.get(configFile));
    }

    /** Same as load(), but with the environment and file passed in so tests can control them. */
    public static AppConfig load(Map<String, String> env, Path configFile) {
        Properties fileSettings = readProperties(configFile);

        String uri = pick(env.get("MONGODB_URI"), fileSettings.getProperty("mongodb.uri"), DEFAULT_MONGO_URI);
        String database = pick(env.get("MONGODB_DATABASE"), fileSettings.getProperty("mongodb.database"),
                DEFAULT_DATABASE);
        String dir = pick(env.get("PRODUCTS_DATA_DIR"), fileSettings.getProperty("data.dir"), DEFAULT_DATA_DIR);

        return new AppConfig(uri, database, Paths.get(dir).toAbsolutePath().normalize());
    }

    /** Returns the first value that is not null/blank. */
    private static String pick(String fromEnvironment, String fromFile, String defaultValue) {
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return defaultValue;
    }

    private static Properties readProperties(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Could not read " + file + ": " + e.getMessage() + " (using defaults)");
            }
        }
        return properties;
    }

    public String getMongoUri() {
        return mongoUri;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public Path getDataDir() {
        return dataDir;
    }

    /** The URI with any "user:password@" part hidden, safe to show on screen or in logs. */
    public String getSafeMongoUri() {
        return mongoUri.replaceAll("(?i)^(mongodb(?:\\+srv)?://)[^@/]+@", "$1***@");
    }
}
