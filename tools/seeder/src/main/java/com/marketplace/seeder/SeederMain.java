package com.marketplace.seeder;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The shared seeder. Run it from tools/seeder:
 *
 * <pre>
 *   mvn -q compile exec:java                                   load missing seed records (safe to repeat)
 *   mvn -q compile exec:java -Dexec.args="--check"             only check seed-data/*.json, no MongoDB needed
 *   mvn -q compile exec:java -Dexec.args="--reset"             wipe the seeded collections, then load everything
 *   mvn -q compile exec:java -Dexec.args="--only orders,reviews"   load (or reset) only these collections
 * </pre>
 *
 * Full guide: docs/SEED-DATA-GUIDE.md
 */
public final class SeederMain {

    private static final String DEFAULT_URI = "mongodb://localhost:27017";
    private static final String DEFAULT_DATABASE = "marketplace";

    private SeederMain() {
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args);
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR: " + e.getMessage());
            System.out.println();
            printHelp();
            exitCode = 2;
        }
        System.exit(exitCode);
    }

    /** Runs the seeder and returns the exit code: 0 = success, 1 = problems found, 2 = wrong options. */
    static int run(String[] args) {
        Options options = Options.parse(args);
        if (options.help) {
            printHelp();
            return 0;
        }

        // 1. Find and read the seed files.
        Path folder = options.folder != null ? options.folder : findSeedFolder();
        if (folder == null || !Files.isDirectory(folder)) {
            System.out.println("ERROR: cannot find the seed-data folder. Run the seeder from tools/seeder inside the "
                    + "repository, or pass --dir <path to seed-data>.");
            return 1;
        }
        System.out.println("Seed files:  " + folder.toAbsolutePath().normalize());
        SeedFiles files = SeedFiles.load(folder);
        files.notes().forEach(note -> System.out.println("  note: " + note));

        // 2. Check them. Nothing is written if there is any error.
        SeedChecker checker = SeedChecker.check(files);
        printCheckResult(files, checker);
        // The product photos must be in the repository too (see ProductPhotos).
        ProductPhotos photos = ProductPhotos.forRepository(folder.toAbsolutePath().normalize().getParent(),
                System.getenv("PRODUCTS_DATA_DIR"));
        List<String> photoErrors = photos.check(files.documentsOf("products"));
        photoErrors.forEach(error -> System.out.println("ERROR: " + error));
        if (!checker.errors().isEmpty() || !photoErrors.isEmpty()) {
            System.out.println();
            System.out.println("Nothing was written to MongoDB. Fix the errors above, then run the seeder again.");
            return 1;
        }
        if (options.checkOnly) {
            System.out.println();
            System.out.println("Check passed. (Nothing was written: --check only reads the files.)");
            return 0;
        }

        // 3. Work out which collections to load.
        List<String> collections = new ArrayList<>();
        for (CollectionRules.Rule rule : CollectionRules.ALL) {
            boolean wanted = options.only.isEmpty() || options.only.contains(rule.collection());
            if (wanted && files.documents().containsKey(rule.collection())) {
                collections.add(rule.collection());
            }
        }

        // 4. Connect and load.
        String uri = options.uri != null ? options.uri : envOr("MONGODB_URI", DEFAULT_URI);
        String databaseName = options.database != null ? options.database : envOr("MONGODB_DATABASE", DEFAULT_DATABASE);
        System.out.println();
        System.out.println("MongoDB:     " + hidePassword(uri) + "  database \"" + databaseName + "\"");

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .build();
        try (MongoClient client = MongoClients.create(settings)) {
            MongoDatabase database = client.getDatabase(databaseName);
            database.runCommand(new Document("ping", 1));

            if (options.reset && !confirmReset(collections, databaseName, options.yes)) {
                System.out.println("Reset cancelled. Nothing was changed.");
                return 0;
            }
            int result = load(new MongoSeeder(database), files, collections, options.reset);
            if (result == 0 && collections.contains("products")) {
                copyPhotos(photos, files);
            }
            System.out.println("Done.");
            return result;
        } catch (MongoException e) {
            System.out.println();
            System.out.println("ERROR: cannot use MongoDB at " + hidePassword(uri) + ".");
            System.out.println("  Is MongoDB Community Server running? Windows: net start MongoDB");
            System.out.println("  macOS: brew services start mongodb-community   (see docs/TEAM-GUIDE.md, step 7)");
            System.out.println("  (Details: " + e.getMessage() + ")");
            return 1;
        }
    }

    private static int load(MongoSeeder seeder, SeedFiles files, List<String> collections, boolean reset) {
        System.out.println();
        System.out.printf("%-14s %6s %10s %10s   %s%n", "Collection", "added", "unchanged", "different", "next new ID");
        int totalDifferent = 0;
        List<String> indexWarnings = new ArrayList<>();
        for (String collection : collections) {
            if (reset) {
                seeder.reset(collection);
            }
            MongoSeeder.Result result = seeder.load(collection, files.documentsOf(collection));
            String warning = seeder.ensureIndexes(collection);
            if (warning != null) {
                indexWarnings.add(warning);
            }
            totalDifferent += result.different();
            System.out.printf("%-14s %6d %10d %10d   %s%n", collection, result.added(), result.unchanged(),
                    result.different(), nextId(collection, result.counterValue()));
        }

        indexWarnings.forEach(warning -> System.out.println("  WARNING: " + warning));
        System.out.println();
        if (totalDifferent > 0) {
            System.out.println("\"different\" = a record with that ID already exists in your database but is not the same as");
            System.out.println("the seed version (you edited it, or made it in your app before seeding). It was left alone.");
            System.out.println("To get exactly the shared data, run again with --reset.");
            System.out.println();
        }
        return 0;
    }

    /** Copies the sample product photos to where the Products app looks for them. */
    private static void copyPhotos(ProductPhotos photos, SeedFiles files) {
        try {
            ProductPhotos.CopyResult copied = photos.copy(files.documentsOf("products"));
            System.out.println("Product photos: " + copied.copied() + " copied, " + copied.alreadyThere()
                    + " already there  ->  " + copied.imagesFolder().toAbsolutePath().normalize());
        } catch (java.io.IOException e) {
            System.out.println("WARNING: could not copy the product photos: " + e.getMessage());
            System.out.println("  The products are loaded; the Products app will show \"Image unavailable\".");
        }
    }

    // ------------------------------------------------------------------ output

    private static void printCheckResult(SeedFiles files, SeedChecker checker) {
        int documents = files.documents().values().stream().mapToInt(List::size).sum();
        System.out.println();
        System.out.println("Checked " + documents + " documents in " + files.documents().size() + " files against "
                + "SHARED-CONVENTIONS.md: " + checker.errors().size() + " error(s), "
                + checker.warnings().size() + " warning(s).");
        checker.errors().forEach(error -> System.out.println("  ERROR:   " + error));
        checker.warnings().forEach(warning -> System.out.println("  WARNING: " + warning));
    }

    private static boolean confirmReset(List<String> collections, String databaseName, boolean alreadyConfirmed) {
        System.out.println();
        System.out.println("--reset DELETES every document in these collections of \"" + databaseName
                + "\" on this computer,");
        System.out.println("including records you made in your own app, then loads the shared seed data:");
        System.out.println("  " + String.join(", ", collections));
        if (alreadyConfirmed) {
            return true;
        }
        System.out.print("Type yes to continue: ");
        Scanner scanner = new Scanner(System.in);
        return scanner.hasNextLine() && scanner.nextLine().trim().equalsIgnoreCase("yes");
    }

    private static void printHelp() {
        System.out.println("""
                Shared seeder: loads seed-data/*.json into your local MongoDB (database "marketplace").

                Run from tools/seeder:
                  mvn -q compile exec:java                                  add missing seed records (safe to repeat)
                  mvn -q compile exec:java -Dexec.args="--check"            only check the files (no MongoDB needed)
                  mvn -q compile exec:java -Dexec.args="--reset"            delete the collections, then load them again

                Options (put them inside -Dexec.args="..."):
                  --check              check seed-data/*.json against SHARED-CONVENTIONS.md and stop
                  --reset              delete everything in the seeded collections first (asks you to type yes)
                  --yes                skip the "type yes" question of --reset
                  --only a,b           only load (or reset) these collections, e.g. --only orders,shipments
                  --dir <folder>       where the seed files are (default: the repository's seed-data folder)
                  --uri <uri>          MongoDB connection (default: MONGODB_URI, else mongodb://localhost:27017)
                  --db <name>          database name (default: MONGODB_DATABASE, else marketplace)
                  --help               show this help

                Guide: docs/SEED-DATA-GUIDE.md""");
    }

    // ------------------------------------------------------------------ helpers

    /** Looks for a "seed-data" folder in the current folder and up to five folders above it. */
    private static Path findSeedFolder() {
        Path current = Path.of("").toAbsolutePath();
        for (int level = 0; level <= 5 && current != null; level++) {
            Path candidate = current.resolve("seed-data");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String nextId(String collection, long counterValue) {
        CollectionRules.Rule rule = CollectionRules.forCollection(collection);
        String prefix = rule.idExample().substring(0, 4);
        int digits = rule.idExample().length() - 4;
        return prefix + String.format("%0" + digits + "d", counterValue + 1);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /** Shows "mongodb+srv://farshad:****@cluster..." instead of the real password. */
    static String hidePassword(String uri) {
        return uri.replaceAll("(://[^:/@]+):[^@/]*@", "$1:****@");
    }

    /** The command-line options. */
    private static final class Options {
        boolean help;
        boolean checkOnly;
        boolean reset;
        boolean yes;
        Set<String> only = new LinkedHashSet<>();
        Path folder;
        String uri;
        String database;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i].trim();
                switch (arg.toLowerCase(Locale.ROOT)) {
                    case "" -> {
                        // extra spaces in -Dexec.args
                    }
                    case "--help", "-h" -> options.help = true;
                    case "--check" -> options.checkOnly = true;
                    case "--reset" -> options.reset = true;
                    case "--yes", "-y" -> options.yes = true;
                    case "--only" -> {
                        for (String name : value(args, ++i, arg).split(",")) {
                            String collection = name.trim().toLowerCase(Locale.ROOT);
                            if (CollectionRules.forCollection(collection) == null) {
                                throw new IllegalArgumentException("--only: \"" + collection + "\" is not a collection. "
                                        + "Use: warehouses, products, customers, inventory, orders, shipments, "
                                        + "transactions, reviews");
                            }
                            options.only.add(collection);
                        }
                    }
                    case "--dir" -> options.folder = Path.of(value(args, ++i, arg));
                    case "--uri" -> options.uri = value(args, ++i, arg);
                    case "--db" -> options.database = value(args, ++i, arg);
                    default -> throw new IllegalArgumentException("unknown option \"" + arg + "\"");
                }
            }
            if (options.checkOnly && options.reset) {
                throw new IllegalArgumentException("use either --check or --reset, not both");
            }
            return options;
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException(option + " needs a value after it");
            }
            return args[index];
        }
    }
}
