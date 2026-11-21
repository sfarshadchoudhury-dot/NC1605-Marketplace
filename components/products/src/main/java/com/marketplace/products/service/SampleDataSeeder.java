package com.marketplace.products.service;

import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductForm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adds about 15 varied sample products. It is only run by the explicit seed command (SeedMain).
 * Safe to run again: a sample whose name already exists is skipped, so nothing is overwritten or duplicated.
 *
 * Each sample's photo is stored in the project itself (src/main/resources/.../sample-images/), so every team
 * member who runs the seed command gets exactly the same pictures. The file name is the product name in
 * lower case with dashes, e.g. "Yoga Mat 6 mm" -> sample-images/yoga-mat-6-mm.jpg. If a photo is missing,
 * simple placeholder pictures are drawn instead.
 */
public class SampleDataSeeder {

    /** One row of sample data. Price is text so it goes through the same validation as the form. */
    record SampleProduct(String name, String description, String price, int stock, boolean active,
                         String[] categories, int imageCount) {
    }

    public record SeedResult(int added, int skipped) {
    }

    private static final String[] VIEW_CAPTIONS = {"Front view", "Side view", "Detail view"};
    private static final String PHOTO_FOLDER = "/com/marketplace/products/sample-images/";

    private static final SampleProduct[] SAMPLES = {
            new SampleProduct("Wireless Noise-Cancelling Headphones",
                    "Over-ear Bluetooth headphones with 30-hour battery life and active noise cancelling.",
                    "129.99", 24, true, new String[]{"Electronics", "Audio"}, 2),
            new SampleProduct("Bluetooth Portable Speaker",
                    "Water-resistant pocket speaker with a deep bass driver and 12 hours of playtime.",
                    "49.50", 3, true, new String[]{"Electronics", "Audio"}, 1),
            new SampleProduct("Mechanical Keyboard",
                    "Tenkeyless keyboard with hot-swappable switches and white backlighting.",
                    "89.00", 40, true, new String[]{"Electronics", "Office"}, 3),
            new SampleProduct("Ceramic Pour-Over Coffee Set",
                    "Hand-glazed dripper and carafe for a clean, bright cup of filter coffee.",
                    "34.95", 18, true, new String[]{"Home & Kitchen"}, 2),
            new SampleProduct("Stainless Steel Water Bottle 750 ml",
                    "Double-walled insulated bottle that keeps drinks cold for 24 hours.",
                    "19.99", 0, true, new String[]{"Home & Kitchen", "Sports & Outdoors"}, 1),
            new SampleProduct("Cotton Crew-Neck T-Shirt",
                    "Soft everyday t-shirt made from 100% organic cotton. Available in several colours.",
                    "14.00", 120, true, new String[]{"Clothing"}, 2),
            new SampleProduct("Waterproof Hiking Jacket",
                    "Lightweight three-layer shell with taped seams and an adjustable hood.",
                    "119.00", 8, true, new String[]{"Clothing", "Sports & Outdoors"}, 2),
            new SampleProduct("Beginner's Guide to Java Programming",
                    "A friendly introduction to conditions, loops, arrays and objects, with exercises.",
                    "29.99", 55, true, new String[]{"Books"}, 1),
            new SampleProduct("Science Fiction Paperback Collection",
                    "Boxed set of three classic science fiction novels. Discontinued edition.",
                    "21.75", 0, false, new String[]{"Books"}, 1),
            new SampleProduct("Wooden Building Blocks 100 Pieces",
                    "Natural beech blocks in assorted shapes, packed in a cotton storage bag.",
                    "24.99", 31, true, new String[]{"Toys & Games"}, 1),
            new SampleProduct("Yoga Mat 6 mm",
                    "Non-slip, extra-cushioned mat with a carry strap.",
                    "22.00", 4, true, new String[]{"Sports & Outdoors", "Fitness"}, 1),
            new SampleProduct("Vitamin C Face Serum",
                    "Brightening serum with 15% vitamin C and hyaluronic acid. 30 ml bottle.",
                    "17.49", 62, true, new String[]{"Beauty"}, 1),
            new SampleProduct("Ergonomic Desk Lamp",
                    "LED lamp with adjustable colour temperature and a built-in USB charging port.",
                    "39.90", 15, true, new String[]{"Home & Kitchen", "Office"}, 2),
            new SampleProduct("Standing Desk Converter",
                    "Sit-stand workstation that rests on your desk. Waiting for the next delivery.",
                    "179.99", 5, false, new String[]{"Office", "Home & Kitchen"}, 0),
            new SampleProduct("USB-C Charging Cable 2 m",
                    "Braided fast-charging cable that supports up to 100 W power delivery.",
                    "9.99", 250, true, new String[]{"Electronics", "Accessories"}, 1)
    };

    private final ProductService productService;
    private final ImageStorageService imageStorage;

    public SampleDataSeeder(ProductService productService, ImageStorageService imageStorage) {
        this.productService = productService;
        this.imageStorage = imageStorage;
    }

    public static int sampleCount() {
        return SAMPLES.length;
    }

    /** Adds every sample that is not already in the catalogue (matched by name, ignoring case). */
    public SeedResult seed() {
        Set<String> existingNames = new HashSet<>();
        for (Product product : productService.getAllProducts()) {
            existingNames.add(product.getName().toLowerCase(Locale.ROOT));
        }

        int added = 0;
        int skipped = 0;
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("products-seed-");
            for (int i = 0; i < SAMPLES.length; i++) {
                SampleProduct sample = SAMPLES[i];
                if (existingNames.contains(sample.name().toLowerCase(Locale.ROOT))) {
                    skipped++;
                    continue;
                }
                addSample(sample, i, tempDir);
                added++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create placeholder images: " + e.getMessage(), e);
        } finally {
            deleteQuietly(tempDir);
        }
        return new SeedResult(added, skipped);
    }

    private void addSample(SampleProduct sample, int sampleIndex, Path tempDir) throws IOException {
        List<String> imagePaths = new ArrayList<>();
        try {
            Path photo = copyPhotoToTemp(sample.name(), tempDir);
            if (photo != null) {
                imagePaths.add(imageStorage.storeImage(photo));
            }
            for (int n = 0; photo == null && n < sample.imageCount(); n++) {
                Path file = PlaceholderImageGenerator.generate(tempDir, "sample-" + sampleIndex + "-" + n + ".png",
                        sample.name(), VIEW_CAPTIONS[n % VIEW_CAPTIONS.length], sampleIndex + n);
                imagePaths.add(imageStorage.storeImage(file));
            }
            productService.addProduct(new ProductForm(sample.name(), sample.description(), sample.price(),
                    String.valueOf(sample.stock()), sample.active(), Arrays.asList(sample.categories()),
                    imagePaths));
        } catch (RuntimeException e) {
            // Do not leave image files behind for a product that was never saved.
            for (String imagePath : imagePaths) {
                imageStorage.deleteImage(imagePath);
            }
            throw e;
        }
    }

    /** Where the photo of a sample is kept inside the project, e.g. "Yoga Mat 6 mm" -> .../yoga-mat-6-mm.jpg. */
    public static String photoResource(String productName) {
        String slug = productName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return PHOTO_FOLDER + slug + ".jpg";
    }

    /** Names of all sample products (used by the tests to check every photo is present). */
    public static List<String> sampleNames() {
        List<String> names = new ArrayList<>();
        for (SampleProduct sample : SAMPLES) {
            names.add(sample.name());
        }
        return names;
    }

    /** Copies the sample's photo out of the project resources into the temp folder, or returns null if there is none. */
    private static Path copyPhotoToTemp(String productName, Path tempDir) throws IOException {
        try (InputStream in = SampleDataSeeder.class.getResourceAsStream(photoResource(productName))) {
            if (in == null) {
                return null;
            }
            Path file = tempDir.resolve("photo-" + Math.abs(productName.hashCode()) + ".jpg");
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // temp files; the operating system cleans them up eventually
        }
    }
}
