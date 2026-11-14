package com.marketplace.products.service;

import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductForm;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small helpers that build test data. */
final class TestProducts {

    private TestProducts() {
    }

    static Product product(String id, String name, String price, int stock, boolean active, String... categories) {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        return new Product(id, name, "", new BigDecimal(price), stock, active,
                Arrays.asList(categories), new ArrayList<>(), now, now);
    }

    static ProductForm form(String name, String price, String stock, String... categories) {
        return new ProductForm(name, "A description", price, stock, true, Arrays.asList(categories),
                new ArrayList<>());
    }

    static ProductForm formWithImages(String name, String price, String stock, List<String> images,
                                      String... categories) {
        return new ProductForm(name, "A description", price, stock, true, Arrays.asList(categories), images);
    }

    /** Writes a tiny but genuine image file (format is "png", "jpg", "gif" or "bmp"). */
    static Path writeImage(Path folder, String fileName, String format) throws IOException {
        System.setProperty("java.awt.headless", "true");
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Path file = folder.resolve(fileName);
        Files.createDirectories(folder);
        if (!ImageIO.write(image, format, file.toFile())) {
            throw new IOException("No ImageIO writer for " + format);
        }
        return file;
    }
}
