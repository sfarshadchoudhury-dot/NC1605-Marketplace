package com.marketplace.products.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Copies product images into the application's own data folder and hands back a portable
 * relative reference such as "images/3f2a9c.png". Only that relative text is saved in MongoDB,
 * so the data folder can be moved to another computer without breaking any product.
 */
public class ImageStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;   // 5 MB
    public static final String[] ALLOWED_EXTENSIONS = {"png", "jpg", "jpeg", "gif", "bmp"};
    public static final String IMAGES_FOLDER = "images";

    // The first bytes of a real image file identify its format ("magic numbers").
    // SIGNATURES[i] is the byte pattern for the format named SIGNATURE_FORMATS[i].
    private static final byte[][] SIGNATURES = {
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
            "GIF87a".getBytes(StandardCharsets.US_ASCII),
            "GIF89a".getBytes(StandardCharsets.US_ASCII),
            {'B', 'M'}
    };
    private static final String[] SIGNATURE_FORMATS = {"png", "jpg", "gif", "gif", "bmp"};
    private static final int HEADER_LENGTH = 8;

    private final Path dataDir;
    private final Path imagesDir;

    public ImageStorageService(Path dataDir) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.imagesDir = this.dataDir.resolve(IMAGES_FOLDER);
    }

    public Path getDataDir() {
        return dataDir;
    }

    /**
     * Checks the file, copies it into the images folder under a new random name and returns the
     * relative reference ("images/&lt;uuid&gt;.&lt;ext&gt;"). Throws ImageStorageException if the file is rejected.
     */
    public String storeImage(Path source) {
        String format = checkAndDetectFormat(source);
        String storedName = UUID.randomUUID() + "." + format;
        try {
            Files.createDirectories(imagesDir);
            Files.copy(source, imagesDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ImageStorageException("Could not copy the image into the data folder: " + e.getMessage(), e);
        }
        return IMAGES_FOLDER + "/" + storedName;
    }

    /** Throws ImageStorageException if the file is not an acceptable image. */
    public void validateImageFile(Path source) {
        checkAndDetectFormat(source);
    }

    /** Converts a stored relative reference to the real file location, refusing anything outside the data folder. */
    public Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ImageStorageException("The image reference is empty.");
        }
        Path resolved = dataDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(dataDir)) {
            throw new ImageStorageException("The image reference points outside the data folder.");
        }
        return resolved;
    }

    public boolean exists(String relativePath) {
        try {
            return Files.isRegularFile(resolve(relativePath));
        } catch (ImageStorageException e) {
            return false;
        }
    }

    /** Deletes a stored image. A missing file is not an error. Returns true if a file was removed. */
    public boolean deleteImage(String relativePath) {
        try {
            return Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | ImageStorageException e) {
            System.err.println("Could not delete image " + relativePath + ": " + e.getMessage());
            return false;
        }
    }

    /** Runs every image check and returns the real format found in the file ("png", "jpg", "gif" or "bmp"). */
    private String checkAndDetectFormat(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new ImageStorageException("The selected file does not exist.");
        }
        String fileName = source.getFileName().toString();

        if (!isAllowedExtension(extensionOf(fileName))) {
            throw new ImageStorageException("\"" + fileName + "\" is not a supported image type. "
                    + "Use PNG, JPG, GIF or BMP.");
        }

        long size;
        try {
            size = Files.size(source);
        } catch (IOException e) {
            throw new ImageStorageException("\"" + fileName + "\" could not be read.", e);
        }
        if (size == 0) {
            throw new ImageStorageException("\"" + fileName + "\" is empty.");
        }
        if (size > MAX_FILE_SIZE_BYTES) {
            throw new ImageStorageException("\"" + fileName + "\" is larger than "
                    + (MAX_FILE_SIZE_BYTES / (1024 * 1024)) + " MB.");
        }

        String format = detectFormat(readHeader(source));
        if (format == null) {
            throw new ImageStorageException("\"" + fileName + "\" is not a valid PNG, JPG, GIF or BMP image.");
        }
        return format;
    }

    private static byte[] readHeader(Path source) {
        byte[] header = new byte[HEADER_LENGTH];
        try (InputStream in = Files.newInputStream(source)) {
            int bytesRead = in.read(header);
            if (bytesRead < HEADER_LENGTH) {
                byte[] shorter = new byte[Math.max(bytesRead, 0)];
                System.arraycopy(header, 0, shorter, 0, shorter.length);
                return shorter;
            }
        } catch (IOException e) {
            throw new ImageStorageException("\"" + source.getFileName() + "\" could not be read.", e);
        }
        return header;
    }

    /** Compares the file's first bytes with each known signature. Returns the format name or null. */
    static String detectFormat(byte[] header) {
        for (int i = 0; i < SIGNATURES.length; i++) {
            if (startsWith(header, SIGNATURES[i])) {
                return SIGNATURE_FORMATS[i];
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllowedExtension(String extension) {
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (allowed.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
