package com.marketplace.products.ui;

import com.marketplace.products.service.ImageStorageException;
import com.marketplace.products.service.ImageStorageService;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds picture nodes for the table, the details panel and the form.
 * Pictures are loaded by JavaFX in the background (backgroundLoading = true), so no file reading
 * happens on the UI thread. A missing, unreadable or corrupt image shows a text placeholder instead of crashing.
 */
public final class ProductImageLoader {

    private static final int MAX_CACHED_IMAGES = 300;

    // Only touched on the JavaFX thread, so a plain HashMap is enough.
    private static final Map<String, Image> CACHE = new HashMap<>();

    private ProductImageLoader() {
    }

    /**
     * @param relativePath the stored reference such as "images/abc.png"; null means the product has no image
     * @return a fixed-size box containing the picture, or a "No image" / "Image unavailable" placeholder
     */
    public static Node createView(ImageStorageService storage, String relativePath, double width, double height) {
        StackPane frame = new StackPane();
        frame.getStyleClass().add("image-frame");
        frame.setMinSize(width, height);
        frame.setPrefSize(width, height);
        frame.setMaxSize(width, height);

        Label placeholder = new Label(relativePath == null ? "No image" : "Image unavailable");
        placeholder.getStyleClass().add("image-placeholder");

        Image image = relativePath == null ? null : loadImage(storage, relativePath, width, height);
        if (image == null) {
            frame.getChildren().add(placeholder);
            return frame;
        }

        ImageView view = new ImageView(image);
        view.setFitWidth(width);
        view.setFitHeight(height);
        view.setPreserveRatio(true);
        // The image loads in the background; if it turns out to be broken, swap the picture for the placeholder.
        placeholder.visibleProperty().bind(image.errorProperty());
        view.visibleProperty().bind(image.errorProperty().not());
        frame.getChildren().addAll(placeholder, view);
        return frame;
    }

    /** Returns null when the reference is invalid (for example it points outside the data folder). */
    private static Image loadImage(ImageStorageService storage, String relativePath, double width, double height) {
        String key = relativePath + "@" + width + "x" + height;
        Image cached = CACHE.get(key);
        if (cached != null && !cached.isError()) {
            return cached;
        }
        Path file;
        try {
            file = storage.resolve(relativePath);
        } catch (ImageStorageException e) {
            return null;
        }
        if (CACHE.size() >= MAX_CACHED_IMAGES) {
            CACHE.clear();
        }
        // Ask for twice the display size so pictures stay sharp on high-resolution screens.
        Image image = new Image(file.toUri().toString(), width * 2, height * 2, true, true, true);
        CACHE.put(key, image);
        return image;
    }
}
