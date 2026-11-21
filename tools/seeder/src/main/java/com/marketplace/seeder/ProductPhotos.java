package com.marketplace.seeder;

import org.bson.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The photos of the sample products.
 *
 * In seed-data/products.json every product has one image path such as "images/sample-yoga-mat-6-mm.jpg".
 * The photo itself is kept in the Products component:
 *   components/products/src/main/resources/com/marketplace/products/sample-images/yoga-mat-6-mm.jpg
 * The Products app shows images from its data folder (components/products/data, or PRODUCTS_DATA_DIR),
 * so the seeder copies each photo to  data/images/sample-yoga-mat-6-mm.jpg  there.
 * Everyone gets the same file names and the same pictures.
 */
final class ProductPhotos {

    static final String PATH_PREFIX = "images/sample-";
    private static final String PHOTO_FOLDER =
            "components/products/src/main/resources/com/marketplace/products/sample-images";

    /** Outcome of copying: how many photos were copied, how many were already there, and where to. */
    record CopyResult(int copied, int alreadyThere, Path imagesFolder) {
    }

    private final Path photoFolder;
    private final Path imagesFolder;

    private ProductPhotos(Path photoFolder, Path imagesFolder) {
        this.photoFolder = photoFolder;
        this.imagesFolder = imagesFolder;
    }

    /**
     * @param repositoryRoot the folder that contains seed-data/ and components/
     * @param dataDirOverride the Products data folder if PRODUCTS_DATA_DIR is set, otherwise null
     */
    static ProductPhotos forRepository(Path repositoryRoot, String dataDirOverride) {
        Path dataDir = dataDirOverride != null && !dataDirOverride.isBlank()
                ? Path.of(dataDirOverride.trim())
                : repositoryRoot.resolve("components/products/data");
        return new ProductPhotos(repositoryRoot.resolve(PHOTO_FOLDER), dataDir.resolve("images"));
    }

    /** One message per image path that does not follow the rule or whose photo is missing from the repository. */
    List<String> check(List<Document> products) {
        List<String> errors = new ArrayList<>();
        for (Document product : products) {
            Object id = product.get("_id");
            for (String path : imagePaths(product)) {
                if (!path.startsWith(PATH_PREFIX) || !path.endsWith(".jpg")) {
                    errors.add("products " + id + ": imagePaths entry \"" + path + "\" must look like "
                            + "\"images/sample-<product-name>.jpg\"");
                } else if (!Files.isRegularFile(source(path))) {
                    errors.add("products " + id + ": the photo for \"" + path + "\" is missing. Add "
                            + PHOTO_FOLDER + "/" + fileName(path));
                }
            }
        }
        return errors;
    }

    /** Copies every product photo into the Products data folder (overwrites, so a changed photo is updated). */
    CopyResult copy(List<Document> products) throws IOException {
        Files.createDirectories(imagesFolder);
        int copied = 0;
        int alreadyThere = 0;
        for (Document product : products) {
            for (String path : imagePaths(product)) {
                Path source = source(path);
                Path target = imagesFolder.resolve(path.substring("images/".length()));
                if (Files.isRegularFile(target) && Files.mismatch(source, target) == -1) {
                    alreadyThere++;
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }
        }
        return new CopyResult(copied, alreadyThere, imagesFolder);
    }

    private Path source(String imagePath) {
        return photoFolder.resolve(fileName(imagePath));
    }

    /** "images/sample-yoga-mat-6-mm.jpg" -> "yoga-mat-6-mm.jpg" */
    private static String fileName(String imagePath) {
        return imagePath.substring(PATH_PREFIX.length());
    }

    private static List<String> imagePaths(Document product) {
        List<String> paths = new ArrayList<>();
        Object value = product.get("imagePaths");
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text) {
                    paths.add(text);
                }
            }
        }
        return paths;
    }
}
