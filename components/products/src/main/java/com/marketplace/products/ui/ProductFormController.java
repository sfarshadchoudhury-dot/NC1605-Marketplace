package com.marketplace.products.ui;

import com.marketplace.products.AppContext;
import com.marketplace.products.model.Categories;
import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductForm;
import com.marketplace.products.repository.DatabaseException;
import com.marketplace.products.service.ImageStorageException;
import com.marketplace.products.service.ImageStorageService;
import com.marketplace.products.service.ProductNotFoundException;
import com.marketplace.products.validation.ProductValidator;
import com.marketplace.products.validation.ValidationException;
import com.marketplace.products.validation.ValidationResult;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Controller for product-form.fxml: the add / edit form. Like the Figma design it is shown inside the main
 * window, in place of the catalogue (MainController puts it there and takes it away again).
 * Validation messages appear under the field they belong to, with a banner that counts them. Saving and copying image files run on a
 * background thread (AsyncRunner); the result handlers update the form on the JavaFX thread.
 */
public class ProductFormController {

    private static final int PREVIEW_WIDTH = 300;
    private static final int PREVIEW_HEIGHT = 200;
    private static final int THUMB_SIZE = 96;
    private static final String FOOTER_DEFAULT = "Nothing is saved until you press Save product.";
    private static final String FOOTER_VALID = "All fields are valid.";

    /** What load() returns: the form's root node (to place in the window) and its controller. */
    public record LoadedForm(Parent root, ProductFormController controller) {
    }

    /** Outcome of importing several picked files: the stored references and one message per rejected file. */
    private record ImageImportResult(List<String> storedPaths, List<String> errors) {
    }

    @FXML private Label breadcrumbLabel;
    @FXML private Label detailsSubtitle;
    @FXML private Button backButton;
    @FXML private TextField nameField;
    @FXML private Label nameError;
    @FXML private TextArea descriptionArea;
    @FXML private Label descriptionError;
    @FXML private TextField priceField;
    @FXML private Label priceError;
    @FXML private Label priceHint;
    @FXML private Label stockHint;
    @FXML private TextField stockField;
    @FXML private Label stockError;
    @FXML private CheckBox activeCheck;
    @FXML private FlowPane categoryBox;
    @FXML private TextField newCategoryField;
    @FXML private Label categoriesError;
    @FXML private StackPane previewHolder;
    @FXML private FlowPane thumbnailStrip;
    @FXML private Button addImagesButton;
    @FXML private Label noImagesLabel;
    @FXML private Label imagesError;
    @FXML private Label formError;
    @FXML private Label footerStatus;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator busyIndicator;

    private AppContext context;
    private Product existing;                 // null when adding a new product
    private Consumer<Product> onClose;        // told the saved product, or null when the user cancelled
    private boolean busy;
    private boolean closed;

    private final List<String> imageRefs = new ArrayList<>();         // images currently on the form, in order
    private final List<String> newlyAddedRefs = new ArrayList<>();    // copied while the form is open, not yet saved
    private final Map<String, CheckBox> categoryChecks = new LinkedHashMap<>();
    private int previewIndex = -1;            // -1 = no large preview showing

    /**
     * Loads the form so the caller can place it in the window.
     *
     * @param existing the product to edit, or null to add a new one
     * @param onClose  called once when the form is finished: with the saved product, or with null if the
     *                 user cancelled (Cancel, Back to catalogue or Esc)
     */
    public static LoadedForm load(AppContext context, Product existing, List<String> knownCategories,
                                  Consumer<Product> onClose) throws IOException {
        FXMLLoader loader = new FXMLLoader(ProductFormController.class.getResource("product-form.fxml"));
        Parent root = loader.load();
        ProductFormController controller = loader.getController();
        controller.setup(context, existing, knownCategories, onClose);
        return new LoadedForm(root, controller);
    }

    /** Puts the keyboard cursor in the Name box once the form is on screen. */
    public void focusFirstField() {
        Platform.runLater(nameField::requestFocus);
    }

    // ======================================================================
    // Setup
    // ======================================================================

    private void setup(AppContext context, Product existing, List<String> knownCategories,
                       Consumer<Product> onClose) {
        this.context = context;
        this.existing = existing;
        this.onClose = onClose;

        List<String> categoryNames = new ArrayList<>();
        for (String category : knownCategories) {
            if (!Categories.containsIgnoreCase(categoryNames, category)) {
                categoryNames.add(category);
            }
        }
        if (existing != null) {
            for (String category : existing.getCategories()) {
                if (!Categories.containsIgnoreCase(categoryNames, category)) {
                    categoryNames.add(category);
                }
            }
        }
        for (String name : categoryNames) {
            boolean selected = existing != null && Categories.containsIgnoreCase(existing.getCategories(), name);
            addCategoryCheckBox(name, selected);
        }

        if (existing == null) {
            breadcrumbLabel.setText("Products  ›  Add product");
            detailsSubtitle.setText("New product · ID assigned on save");
            activeCheck.setSelected(true);
            showNextIdPreview();
        } else {
            breadcrumbLabel.setText("Products  ›  Edit " + existing.getId());
            detailsSubtitle.setText(existing.getId() + " · created " + Formatters.date(existing.getCreatedAt())
                    + " · the ID never changes");
            nameField.setText(existing.getName());
            descriptionArea.setText(existing.getDescription());
            priceField.setText(existing.getPrice().toPlainString());
            stockField.setText(String.valueOf(existing.getStockQuantity()));
            activeCheck.setSelected(existing.isActive());
            imageRefs.addAll(existing.getImagePaths());
        }

        // A hint is shown under the price and stock boxes until there is an error to show instead.
        priceHint.managedProperty().bind(priceHint.visibleProperty());
        priceHint.visibleProperty().bind(priceError.visibleProperty().not());
        stockHint.managedProperty().bind(stockHint.visibleProperty());
        stockHint.visibleProperty().bind(stockError.visibleProperty().not());

        // The error under a field disappears as soon as the user edits that field again,
        // and the footer says "All fields are valid." once everything would pass.
        nameField.textProperty().addListener((obs, oldText, newText) -> onFieldEdited(nameError, nameField));
        descriptionArea.textProperty().addListener(
                (obs, oldText, newText) -> onFieldEdited(descriptionError, descriptionArea));
        priceField.textProperty().addListener((obs, oldText, newText) -> onFieldEdited(priceError, priceField));
        stockField.textProperty().addListener((obs, oldText, newText) -> onFieldEdited(stockError, stockField));

        renderImages();
        updateFooterStatus();
    }

    /** Shows "(next: PRD-00016)" in the details card. Only a preview: the real ID is assigned on save. */
    private void showNextIdPreview() {
        AsyncRunner.run(() -> context.getProductService().previewNextProductId(),
                nextId -> detailsSubtitle.setText("New product · ID assigned on save (next: " + nextId + ")"),
                error -> { });   // not important enough to report; the subtitle simply stays without it
    }

    private void onFieldEdited(Label errorLabel, Node input) {
        clearError(errorLabel, input);
        updateFooterStatus();
    }

    /** Footer text; once every field would pass, the "please fix" banner is no longer true, so it goes. */
    private void updateFooterStatus() {
        boolean valid = ProductValidator.validate(readForm()).isValid();
        footerStatus.setText(valid ? FOOTER_VALID : FOOTER_DEFAULT);
        if (valid && !busy) {
            hideFormError();
        }
    }

    // ======================================================================
    // Categories
    // ======================================================================

    private void addCategoryCheckBox(String name, boolean selected) {
        CheckBox checkBox = new CheckBox(name);
        checkBox.setSelected(selected);
        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> onFieldEdited(categoriesError, categoryBox));
        categoryChecks.put(name, checkBox);
        categoryBox.getChildren().add(checkBox);
    }

    @FXML
    private void onAddCategory() {
        String name = Categories.normalise(newCategoryField.getText());
        if (name.isEmpty()) {
            showError(categoriesError, categoryBox, "Type a category name first.");
            return;
        }
        if (name.length() > Categories.MAX_NAME_LENGTH) {
            showError(categoriesError, categoryBox,
                    "Category names must be " + Categories.MAX_NAME_LENGTH + " characters or fewer.");
            return;
        }
        for (Map.Entry<String, CheckBox> entry : categoryChecks.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                entry.getValue().setSelected(true);      // already exists: just tick it
                newCategoryField.clear();
                return;
            }
        }
        addCategoryCheckBox(name, true);
        newCategoryField.clear();
        onFieldEdited(categoriesError, categoryBox);
    }

    // ======================================================================
    // Images
    // ======================================================================

    @FXML
    private void onAddImages() {
        int room = ProductValidator.MAX_IMAGES - imageRefs.size();
        if (room <= 0) {
            showError(imagesError, null, "A product can have at most " + ProductValidator.MAX_IMAGES + " images.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose product images");
        List<String> patterns = new ArrayList<>();
        for (String extension : ImageStorageService.ALLOWED_EXTENSIONS) {
            patterns.add("*." + extension);
        }
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images (PNG, JPG, GIF, BMP)", patterns),
                // Lets the user pick any file; ImageStorageService then rejects the wrong ones with a clear message.
                new FileChooser.ExtensionFilter("All files", "*.*"));

        List<File> chosen = chooser.showOpenMultipleDialog(addImagesButton.getScene().getWindow());
        if (chosen == null || chosen.isEmpty()) {
            return;
        }

        List<Path> paths = new ArrayList<>();
        for (File file : chosen) {
            paths.add(file.toPath());
        }
        clearError(imagesError, null);
        setBusy(true);
        ImageStorageService storage = context.getImageStorage();
        int maxToAdd = room;
        AsyncRunner.run(() -> importImages(storage, paths, maxToAdd), result -> {
            setBusy(false);
            for (String storedPath : result.storedPaths()) {
                imageRefs.add(storedPath);
                newlyAddedRefs.add(storedPath);
            }
            renderImages();
            updateFooterStatus();
            if (!result.errors().isEmpty()) {
                showError(imagesError, null, String.join("\n", result.errors()));
            }
        }, error -> {
            setBusy(false);
            showError(imagesError, null, "Could not add the images: " + error.getMessage());
        });
    }

    /** Background thread: validate and copy each picked file. A bad file is reported but does not stop the others. */
    private static ImageImportResult importImages(ImageStorageService storage, List<Path> paths, int maxToAdd) {
        List<String> stored = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path path : paths) {
            if (stored.size() >= maxToAdd) {
                errors.add("Skipped \"" + path.getFileName() + "\": the limit of " + ProductValidator.MAX_IMAGES
                        + " images was reached.");
                continue;
            }
            try {
                stored.add(storage.storeImage(path));
            } catch (ImageStorageException e) {
                errors.add(e.getMessage());
            }
        }
        return new ImageImportResult(stored, errors);
    }

    private void removeImage(String imagePath) {
        imageRefs.remove(imagePath);
        if (newlyAddedRefs.remove(imagePath)) {
            // never saved anywhere, so the file can go now (an already-saved image is deleted when the form is saved)
            ImageStorageService storage = context.getImageStorage();
            AsyncRunner.run(() -> storage.deleteImage(imagePath), deleted -> { }, error -> { });
        }
        previewIndex = -1;
        clearError(imagesError, null);
        renderImages();
        updateFooterStatus();
    }

    /**
     * Redraws the thumbnails from imageRefs (the first one is marked "Thumbnail") and the large preview,
     * which only shows after a thumbnail is clicked; clicking the same thumbnail again hides it.
     */
    private void renderImages() {
        ImageStorageService storage = context.getImageStorage();
        if (previewIndex >= 0 && previewIndex < imageRefs.size()) {
            previewHolder.getChildren().setAll(
                    ProductImageLoader.createView(storage, imageRefs.get(previewIndex), PREVIEW_WIDTH, PREVIEW_HEIGHT));
        } else {
            previewIndex = -1;
            previewHolder.getChildren().clear();
        }

        thumbnailStrip.getChildren().clear();
        for (int i = 0; i < imageRefs.size(); i++) {
            final int index = i;
            final String imagePath = imageRefs.get(i);

            StackPane cell = new StackPane();
            cell.getStyleClass().add("thumb-cell");
            if (i == previewIndex) {
                cell.getStyleClass().add("thumb-selected");
            }
            Button removeButton = new Button("×");
            removeButton.getStyleClass().add("thumb-remove");
            removeButton.setOnAction(event -> removeImage(imagePath));
            StackPane.setAlignment(removeButton, Pos.TOP_RIGHT);
            cell.getChildren().addAll(ProductImageLoader.createView(storage, imagePath, THUMB_SIZE, THUMB_SIZE),
                    removeButton);
            if (i == 0) {
                Label thumbnailTag = new Label("Thumbnail");
                thumbnailTag.getStyleClass().add("thumb-tag");
                StackPane.setAlignment(thumbnailTag, Pos.BOTTOM_LEFT);
                cell.getChildren().add(thumbnailTag);
            }
            cell.setOnMouseClicked(event -> {
                previewIndex = previewIndex == index ? -1 : index;
                renderImages();
            });
            thumbnailStrip.getChildren().add(cell);
        }

        noImagesLabel.setVisible(imageRefs.isEmpty());
        noImagesLabel.setManaged(imageRefs.isEmpty());
        addImagesButton.setDisable(imageRefs.size() >= ProductValidator.MAX_IMAGES);
    }

    /** Deletes image files that were copied while the form was open but never attached to a saved product. */
    private void discardNewImages() {
        if (newlyAddedRefs.isEmpty()) {
            return;
        }
        List<String> toDelete = new ArrayList<>(newlyAddedRefs);
        newlyAddedRefs.clear();
        ImageStorageService storage = context.getImageStorage();
        AsyncRunner.run(() -> {
            for (String imagePath : toDelete) {
                storage.deleteImage(imagePath);
            }
            return null;
        }, result -> { }, error -> { });
    }

    /**
     * Same as discardNewImages, but deletes straight away on the calling thread.
     * Used when the window is closing, because the background threads are about to stop.
     */
    public void discardNewImagesNow() {
        ImageStorageService storage = context.getImageStorage();
        for (String imagePath : newlyAddedRefs) {
            try {
                storage.deleteImage(imagePath);
            } catch (RuntimeException ignored) {
                // a leftover file is harmless; the window is closing anyway
            }
        }
        newlyAddedRefs.clear();
    }

    // ======================================================================
    // Save / cancel
    // ======================================================================

    @FXML
    private void onSave() {
        ProductForm form = readForm();

        // 1. Check the input here first so every problem is shown next to its field straight away.
        ValidationResult result = ProductValidator.validate(form);
        showValidation(result);
        if (!result.isValid()) {
            showProblemCount(result);
            focusField(result.getFirstErrorField());
            return;
        }

        // 2. Save on a background thread; the callbacks run back on the JavaFX thread.
        hideFormError();
        setBusy(true);
        String existingId = existing == null ? null : existing.getId();
        AsyncRunner.run(() -> {
            if (existingId == null) {
                return context.getProductService().addProduct(form);
            }
            return context.getProductService().updateProduct(existingId, form);
        }, product -> {
            newlyAddedRefs.clear();      // they now belong to the saved product
            close(product);
        }, this::onSaveFailed);
    }

    private void onSaveFailed(Throwable error) {
        setBusy(false);
        if (error instanceof ValidationException) {
            ValidationResult result = ((ValidationException) error).getResult();
            showValidation(result);
            showProblemCount(result);
        } else if (error instanceof DatabaseException || error instanceof ProductNotFoundException) {
            showFormError(error.getMessage());
        } else {
            error.printStackTrace();
            showFormError("Unexpected error while saving: " + error.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        if (busy) {
            return;      // wait for the save or image copy to finish
        }
        discardNewImages();
        close(null);
    }

    /** Tells MainController the form is finished. Runs at most once. */
    private void close(Product savedProduct) {
        if (closed) {
            return;
        }
        closed = true;
        onClose.accept(savedProduct);
    }

    private ProductForm readForm() {
        List<String> selectedCategories = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : categoryChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedCategories.add(entry.getKey());
            }
        }
        return new ProductForm(nameField.getText(), descriptionArea.getText(), priceField.getText(),
                stockField.getText(), activeCheck.isSelected(), selectedCategories, new ArrayList<>(imageRefs));
    }

    // ======================================================================
    // Showing validation messages
    // ======================================================================

    private void showValidation(ValidationResult result) {
        showError(nameError, nameField, result.getError(ProductValidator.FIELD_NAME));
        showError(descriptionError, descriptionArea, result.getError(ProductValidator.FIELD_DESCRIPTION));
        showError(priceError, priceField, result.getError(ProductValidator.FIELD_PRICE));
        showError(stockError, stockField, result.getError(ProductValidator.FIELD_STOCK));
        showError(categoriesError, categoryBox, result.getError(ProductValidator.FIELD_CATEGORIES));
        showError(imagesError, null, result.getError(ProductValidator.FIELD_IMAGES));
    }

    /** Moves the keyboard cursor to the first field that has an error. */
    private void focusField(String field) {
        if (ProductValidator.FIELD_NAME.equals(field)) {
            nameField.requestFocus();
        } else if (ProductValidator.FIELD_DESCRIPTION.equals(field)) {
            descriptionArea.requestFocus();
        } else if (ProductValidator.FIELD_PRICE.equals(field)) {
            priceField.requestFocus();
        } else if (ProductValidator.FIELD_STOCK.equals(field)) {
            stockField.requestFocus();
        } else if (ProductValidator.FIELD_CATEGORIES.equals(field)) {
            newCategoryField.requestFocus();
        }
    }

    /** Shows the message under the field (or hides the label when message is null) and outlines the field in red. */
    private static void showError(Label errorLabel, Node input, String message) {
        boolean hasError = message != null;
        errorLabel.setText(hasError ? message : "");
        errorLabel.setVisible(hasError);
        errorLabel.setManaged(hasError);
        if (input != null) {
            input.getStyleClass().remove("invalid");
            if (hasError) {
                input.getStyleClass().add("invalid");
            }
        }
    }

    private static void clearError(Label errorLabel, Node input) {
        showError(errorLabel, input, null);
    }

    /** The red banner above the cards: "Please fix 4 problems before saving. Nothing has been saved." */
    private void showProblemCount(ValidationResult result) {
        int count = result.getErrors().size();
        showFormError("⚠  Please fix " + count + (count == 1 ? " problem" : " problems")
                + " before saving. Nothing has been saved.");
    }

    private void showFormError(String message) {
        formError.setText(message);
        formError.setVisible(true);
        formError.setManaged(true);
    }

    private void hideFormError() {
        formError.setVisible(false);
        formError.setManaged(false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        busyIndicator.setVisible(busy);
        saveButton.setDisable(busy);
        cancelButton.setDisable(busy);
        backButton.setDisable(busy);
        addImagesButton.setDisable(busy || imageRefs.size() >= ProductValidator.MAX_IMAGES);
    }
}
