package com.marketplace.products.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private Path dataDir;
    private ImageStorageService storage;

    @BeforeEach
    void setUp() {
        sourceDir = tempDir.resolve("user-pictures");
        dataDir = tempDir.resolve("app-data");
        storage = new ImageStorageService(dataDir);
    }

    // ---------- accepted images ----------

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpg", "gif", "bmp"})
    void genuineImagesOfEverySupportedFormatAreCopiedIntoTheDataFolder(String format) throws IOException {
        Path source = TestProducts.writeImage(sourceDir, "holiday." + format, format);

        String reference = storage.storeImage(source);

        assertTrue(reference.startsWith("images/"), reference);
        assertTrue(reference.endsWith("." + format), reference);
        assertTrue(Files.isRegularFile(storage.resolve(reference)), "copy should exist in the data folder");
        assertTrue(Files.isRegularFile(source), "the original must not be moved or deleted");
    }

    @Test
    void referenceIsRelativeAndPortable() throws IOException {
        Path source = TestProducts.writeImage(sourceDir, "pic.png", "png");

        String reference = storage.storeImage(source);

        assertFalse(Path.of(reference).isAbsolute());
        assertFalse(reference.contains("\\"), "always forward slashes so it works on Windows and macOS");
        assertFalse(reference.contains(sourceDir.toString()), "must not contain the original location");
        assertFalse(reference.contains(dataDir.toString()), "must not contain the machine-specific data folder");
    }

    @Test
    void copyStillWorksAfterTheOriginalFileIsDeleted() throws IOException {
        Path source = TestProducts.writeImage(sourceDir, "pic.png", "png");
        String reference = storage.storeImage(source);

        Files.delete(source);

        assertTrue(storage.exists(reference));
    }

    @Test
    void movingTheWholeDataFolderKeepsReferencesWorking() throws IOException {
        String reference = storage.storeImage(TestProducts.writeImage(sourceDir, "pic.png", "png"));
        Path movedDataDir = tempDir.resolve("moved-to-another-computer");
        Files.move(dataDir, movedDataDir);

        ImageStorageService afterMove = new ImageStorageService(movedDataDir);

        assertTrue(afterMove.exists(reference));
    }

    @Test
    void sameFileStoredTwiceGetsTwoDifferentNames() throws IOException {
        Path source = TestProducts.writeImage(sourceDir, "pic.png", "png");
        assertFalse(storage.storeImage(source).equals(storage.storeImage(source)));
    }

    @Test
    void extensionCheckIsCaseInsensitive() throws IOException {
        Path source = TestProducts.writeImage(sourceDir, "PHOTO.PNG", "png");
        assertTrue(storage.storeImage(source).endsWith(".png"));
    }

    @Test
    void storedExtensionFollowsTheRealContentNotTheFileName() throws IOException {
        Path jpegNamedPng = TestProducts.writeImage(sourceDir, "actually-a-jpeg.png", "jpg");
        assertTrue(storage.storeImage(jpegNamedPng).endsWith(".jpg"));
    }

    @Test
    void imageExactlyAtTheSizeLimitIsAccepted() throws IOException {
        Path source = sourceDir.resolve("limit.png");
        writeFileWithHeader(source, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                (int) ImageStorageService.MAX_FILE_SIZE_BYTES);
        assertTrue(storage.storeImage(source).endsWith(".png"));
    }

    // ---------- rejected images ----------

    @Test
    void missingFileIsRejectedWithAClearMessage() {
        ImageStorageException e = assertThrows(ImageStorageException.class,
                () -> storage.storeImage(sourceDir.resolve("does-not-exist.png")));
        assertEquals("The selected file does not exist.", e.getMessage());
    }

    @Test
    void nullPathIsRejected() {
        assertThrows(ImageStorageException.class, () -> storage.storeImage(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notes.txt", "report.pdf", "picture.webp", "picture.tiff", "archive.zip", "noextension"})
    void unsupportedFileTypesAreRejectedByExtension(String fileName) throws IOException {
        Path source = sourceDir.resolve(fileName);
        Files.createDirectories(sourceDir);
        Files.write(source, "hello".getBytes(StandardCharsets.UTF_8));

        ImageStorageException e = assertThrows(ImageStorageException.class, () -> storage.storeImage(source));
        assertTrue(e.getMessage().contains("not a supported image type"), e.getMessage());
    }

    @Test
    void textFileRenamedToPngIsRejectedByContent() throws IOException {
        Path source = sourceDir.resolve("fake.png");
        Files.createDirectories(sourceDir);
        Files.write(source, "This is really just text, not a PNG".getBytes(StandardCharsets.UTF_8));

        ImageStorageException e = assertThrows(ImageStorageException.class, () -> storage.storeImage(source));
        assertTrue(e.getMessage().contains("not a valid"), e.getMessage());
    }

    @Test
    void emptyFileIsRejected() throws IOException {
        Path source = sourceDir.resolve("empty.png");
        Files.createDirectories(sourceDir);
        Files.write(source, new byte[0]);

        ImageStorageException e = assertThrows(ImageStorageException.class, () -> storage.storeImage(source));
        assertTrue(e.getMessage().contains("empty"), e.getMessage());
    }

    @Test
    void oversizedFileIsRejected() throws IOException {
        Path source = sourceDir.resolve("huge.png");
        writeFileWithHeader(source, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A},
                (int) ImageStorageService.MAX_FILE_SIZE_BYTES + 1);

        ImageStorageException e = assertThrows(ImageStorageException.class, () -> storage.storeImage(source));
        assertTrue(e.getMessage().contains("larger than 5 MB"), e.getMessage());
    }

    @Test
    void rejectedImageLeavesNothingInTheDataFolder() throws IOException {
        Path source = sourceDir.resolve("fake.png");
        Files.createDirectories(sourceDir);
        Files.write(source, "text".getBytes(StandardCharsets.UTF_8));

        assertThrows(ImageStorageException.class, () -> storage.storeImage(source));

        assertFalse(Files.exists(dataDir.resolve("images")) && Files.list(dataDir.resolve("images")).findAny().isPresent());
    }

    @Test
    void validateImageFileThrowsWithoutCopyingAnything() throws IOException {
        Path good = TestProducts.writeImage(sourceDir, "good.png", "png");
        storage.validateImageFile(good);
        assertFalse(Files.exists(dataDir.resolve("images")));
    }

    // ---------- file-signature detection ----------

    @Test
    void detectFormatRecognisesEachSignatureAndRejectsOthers() {
        assertEquals("png", ImageStorageService.detectFormat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}));
        assertEquals("jpg", ImageStorageService.detectFormat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertEquals("gif", ImageStorageService.detectFormat("GIF89a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("gif", ImageStorageService.detectFormat("GIF87a....".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("bmp", ImageStorageService.detectFormat("BM......".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ImageStorageService.detectFormat("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ImageStorageService.detectFormat(new byte[0]));
        assertNull(ImageStorageService.detectFormat(new byte[]{(byte) 0x89, 'P'}), "too short to be a full PNG header");
    }

    // ---------- resolve / delete safety ----------

    @Test
    void resolveRefusesPathsOutsideTheDataFolder() {
        assertThrows(ImageStorageException.class, () -> storage.resolve("../secret.txt"));
        assertThrows(ImageStorageException.class, () -> storage.resolve("images/../../secret.txt"));
        assertThrows(ImageStorageException.class, () -> storage.resolve(tempDir.resolve("other.png").toString()));
        assertThrows(ImageStorageException.class, () -> storage.resolve(""));
        assertThrows(ImageStorageException.class, () -> storage.resolve(null));
    }

    @Test
    void missingImageIsHandledGracefully() {
        assertFalse(storage.exists("images/never-stored.png"));
        assertFalse(storage.exists("../escape.png"));
        assertFalse(storage.deleteImage("images/never-stored.png"), "deleting a missing file is not an error");
        assertFalse(storage.deleteImage("../escape.png"), "a bad reference is ignored, not followed");
    }

    @Test
    void deleteImageRemovesOnlyThatFile() throws IOException {
        String keep = storage.storeImage(TestProducts.writeImage(sourceDir, "a.png", "png"));
        String drop = storage.storeImage(TestProducts.writeImage(sourceDir, "b.png", "png"));

        assertTrue(storage.deleteImage(drop));

        assertTrue(storage.exists(keep));
        assertFalse(storage.exists(drop));
    }

    /** Creates a file of exactly `size` bytes that starts with the given header bytes. */
    private static void writeFileWithHeader(Path file, byte[] header, int size) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] content = new byte[size];
        System.arraycopy(header, 0, content, 0, header.length);
        Files.write(file, content);
    }
}
