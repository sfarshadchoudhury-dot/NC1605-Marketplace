package com.marketplace.products.ui;

import com.marketplace.products.AppContext;
import com.marketplace.products.model.Product;
import com.marketplace.products.model.ProductFilter;
import com.marketplace.products.model.StockLevel;
import com.marketplace.products.repository.DatabaseException;
import com.marketplace.products.service.ProductNotFoundException;
import com.marketplace.products.service.SearchResult;
import com.marketplace.products.validation.ProductValidator;
import com.marketplace.products.validation.ValidationException;
import com.marketplace.products.validation.ValidationResult;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Controller for main-view.fxml: the catalogue table, search/filter bar and details panel.
 * Every database call goes through AsyncRunner so the window never freezes; the result handlers
 * run on the JavaFX thread and are the only places that update the controls.
 */
public class MainController {

    /** Which layer of the table area is visible. */
    private enum View { LOADING, TABLE, EMPTY, NO_MATCH, ERROR }

    private enum MessageType { SUCCESS, ERROR, INFO }

    private static final String ALL_CATEGORIES = "All categories";

    // ---- header and filter bar ----
    @FXML private Label dbStatusLabel;
    @FXML private Button refreshButton;
    @FXML private Button addButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private TextField minPriceField;
    @FXML private TextField maxPriceField;
    @FXML private ComboBox<ProductFilter.Availability> availabilityCombo;
    @FXML private ComboBox<ProductFilter.Status> statusCombo;
    @FXML private Label priceFilterError;
    @FXML private Button resetButton;

    // ---- summary cards ----
    @FXML private Label statTotal;
    @FXML private Label statInStock;
    @FXML private Label statLow;
    @FXML private Label statOut;

    // ---- catalogue table and its state layers ----
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Product> imageColumn;
    @FXML private TableColumn<Product, Product> nameColumn;
    @FXML private TableColumn<Product, Product> idColumn;
    @FXML private TableColumn<Product, Product> categoriesColumn;
    @FXML private TableColumn<Product, Product> priceColumn;
    @FXML private TableColumn<Product, Product> stockColumn;
    @FXML private TableColumn<Product, Product> statusColumn;
    @FXML private VBox loadingBox;
    @FXML private VBox emptyBox;
    @FXML private Label emptyTitle;
    @FXML private Label emptyMessage;
    @FXML private Button emptyResetButton;
    @FXML private VBox errorBox;
    @FXML private Label errorMessage;

    // ---- details panel ----
    @FXML private VBox detailsPlaceholder;
    @FXML private VBox detailsContent;
    @FXML private StackPane mainImageHolder;
    @FXML private FlowPane thumbnailStrip;
    @FXML private Label detailName;
    @FXML private Label detailId;
    @FXML private Label detailPrice;
    @FXML private Label detailStatusBadge;
    @FXML private Label detailStockBadge;
    @FXML private FlowPane detailCategories;
    @FXML private Label detailDescription;
    @FXML private Label detailDates;
    @FXML private Button toggleActiveButton;

    // ---- toast message and busy spinner ----
    @FXML private ProgressIndicator busyIndicator;
    @FXML private Label statusMessage;
    @FXML private Label summaryLabel;

    private AppContext context;
    private boolean connected;
    private int busyTasks;
    private long searchTicket;                 // lets us ignore a slow search that finishes after a newer one
    private boolean suppressFilterEvents;      // true while the code (not the user) changes the filter controls
    private List<String> knownCategories = new ArrayList<>();

    private final ObservableList<Product> shownProducts = FXCollections.observableArrayList();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(300));
    private final PauseTransition messageTimer = new PauseTransition(Duration.seconds(6));

    // ======================================================================
    // Start-up
    // ======================================================================

    @FXML
    private void initialize() {
        categoryCombo.getItems().setAll(ALL_CATEGORIES);
        categoryCombo.setValue(ALL_CATEGORIES);
        availabilityCombo.getItems().setAll(ProductFilter.Availability.values());
        availabilityCombo.setValue(ProductFilter.Availability.ANY);
        statusCombo.getItems().setAll(ProductFilter.Status.values());
        statusCombo.setValue(ProductFilter.Status.ANY);

        configureTable();

        // Typing waits 300 ms after the last key press before searching; drop-downs search straight away.
        searchDelay.setOnFinished(event -> reloadProducts(null));
        searchField.textProperty().addListener((obs, oldText, newText) -> onFilterTyped());
        minPriceField.textProperty().addListener((obs, oldText, newText) -> onFilterTyped());
        maxPriceField.textProperty().addListener((obs, oldText, newText) -> onFilterTyped());
        categoryCombo.valueProperty().addListener((obs, oldValue, newValue) -> onFilterChosen());
        availabilityCombo.valueProperty().addListener((obs, oldValue, newValue) -> onFilterChosen());
        statusCombo.valueProperty().addListener((obs, oldValue, newValue) -> onFilterChosen());

        busyIndicator.managedProperty().bind(busyIndicator.visibleProperty());   // takes no space when hidden
        // The toast is only drawn while it has a message to show.
        statusMessage.visibleProperty().bind(statusMessage.textProperty().isNotEmpty());
        statusMessage.managedProperty().bind(statusMessage.visibleProperty());
        messageTimer.setOnFinished(event -> statusMessage.setText(""));
        productTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldProduct, newProduct) -> showDetails(newProduct));

        showDetails(null);
        showView(View.LOADING);
        clearSummary();
    }

    /** Called by MainApp once the AppContext is available. */
    public void setAppContext(AppContext context) {
        this.context = context;
    }

    /** Called by MainApp after the window is showing. */
    public void start() {
        connectAndLoad();
    }

    private void configureTable() {
        SortedList<Product> sortedProducts = new SortedList<>(shownProducts);
        sortedProducts.comparatorProperty().bind(productTable.comparatorProperty());
        productTable.setItems(sortedProducts);
        productTable.setFixedCellSize(60);
        productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        productTable.setPlaceholder(new Label(""));   // our own state layers replace the default placeholder

        configureColumn(imageColumn, product -> ProductImageLoader.createView(
                context.getImageStorage(), product.getFirstImagePath(), 40, 40), null);

        configureColumn(nameColumn, this::buildNameNode,
                Comparator.comparing((Product p) -> p.getName().toLowerCase(Locale.ROOT)));

        configureColumn(idColumn, product -> label(product.getId(), "id-label"),
                Comparator.comparing(Product::getId));

        configureColumn(categoriesColumn, product -> {
            Label label = label(String.join(", ", product.getCategories()), "cell-text");
            label.setWrapText(true);
            label.setMaxHeight(52);
            return label;
        }, Comparator.comparing((Product p) -> String.join(",", p.getCategories()).toLowerCase(Locale.ROOT)));

        configureColumn(priceColumn, product -> label(Formatters.price(product.getPrice()), "price-cell"),
                Comparator.comparing(Product::getPrice));      // BigDecimal sorts numerically

        configureColumn(stockColumn, product -> stockBadge(product, true),
                Comparator.comparingInt(Product::getStockQuantity));

        configureColumn(statusColumn, product -> statusBadge(product),
                Comparator.comparing(Product::isActive));

        // Inactive rows are greyed out; double-click a row to edit it.
        productTable.setRowFactory(table -> {
            TableRow<Product> row = new TableRow<>() {
                @Override
                protected void updateItem(Product product, boolean empty) {
                    super.updateItem(product, empty);
                    getStyleClass().remove("inactive-row");
                    if (!empty && product != null && !product.isActive()) {
                        getStyleClass().add("inactive-row");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openProductForm(row.getItem());
                }
            });
            return row;
        });

        nameColumn.setSortType(TableColumn.SortType.ASCENDING);
    }

    /** Each column shows a node built from the whole Product, so one helper configures them all. */
    private void configureColumn(TableColumn<Product, Product> column, Function<Product, Node> nodeBuilder,
                                 Comparator<Product> comparator) {
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                setText(null);
                if (empty || product == null) {
                    setGraphic(null);
                } else {
                    Node node = nodeBuilder.apply(product);
                    if (node instanceof Label) {
                        // keep text inside the column and cut it with "..." instead of overflowing
                        ((Label) node).maxWidthProperty().bind(col.widthProperty().subtract(20));
                    }
                    setGraphic(node);
                }
            }
        });
        if (comparator == null) {
            column.setSortable(false);
        } else {
            column.setComparator(comparator);
        }
    }

    private Node buildNameNode(Product product) {
        Label name = label(product.getName(), "cell-name");
        Label description = label(product.getDescription(), "cell-description");
        VBox box = new VBox(2, name, description);
        box.setAlignment(Pos.CENTER_LEFT);
        // Labels inside the box need a width limit for the "..." to appear.
        name.maxWidthProperty().bind(nameColumn.widthProperty().subtract(24));
        description.maxWidthProperty().bind(nameColumn.widthProperty().subtract(24));
        return box;
    }

    // ======================================================================
    // Loading data (always on a background thread)
    // ======================================================================

    /** Connects to MongoDB (again) and loads the catalogue. Used at start-up and by the Retry button. */
    private void connectAndLoad() {
        ProductFilter filter = buildFilter();
        ProductFilter filterToUse = filter != null ? filter : new ProductFilter();

        showView(View.LOADING);
        setConnectionStatus("Connecting...", "db-connecting");
        long ticket = ++searchTicket;
        beginBusy();
        AsyncRunner.run(() -> {
            context.connect();
            return context.getProductService().search(filterToUse);
        }, result -> {
            endBusy();
            if (ticket != searchTicket) {
                return;
            }
            connected = true;
            setConnectionStatus("Connected · " + context.getConfig().getDatabaseName(), "db-connected");
            applyResult(result, null);
        }, error -> {
            endBusy();
            handleFailure(error);
        });
    }

    /**
     * Reads the filter controls and searches. If a product ID is given it is selected afterwards
     * (used after adding or editing so the user sees the result).
     */
    private void reloadProducts(String selectProductId) {
        if (!connected) {
            return;
        }
        ProductFilter filter = buildFilter();
        if (filter == null) {
            return;   // invalid price range: field errors are already showing
        }
        String idToSelect = selectProductId != null ? selectProductId : selectedProductId();
        long ticket = ++searchTicket;
        beginBusy();
        AsyncRunner.run(() -> context.getProductService().search(filter), result -> {
            endBusy();
            if (ticket == searchTicket) {          // ignore results of searches that are already out of date
                applyResult(result, idToSelect);
            }
        }, error -> {
            endBusy();
            if (ticket == searchTicket) {
                handleFailure(error);
            }
        });
    }

    /** Puts a search result on screen. Runs on the JavaFX thread. */
    private void applyResult(SearchResult result, String selectProductId) {
        knownCategories = result.categories();
        boolean categoryWasReset = refreshCategoryCombo(result.categories());

        shownProducts.setAll(result.matches());
        if (result.totalCount() == 0) {
            emptyTitle.setText("No products yet");
            emptyMessage.setText("Click \"+ Add product\" to create the first one, or load the sample catalogue "
                    + "with the seed command (see the README).");
            emptyResetButton.setVisible(false);
            emptyResetButton.setManaged(false);
            showView(View.EMPTY);
        } else if (result.matches().isEmpty()) {
            emptyTitle.setText("No products match your filters");
            emptyMessage.setText("Try a different name, widen the price range, or reset the filters.");
            emptyResetButton.setVisible(true);
            emptyResetButton.setManaged(true);
            showView(View.NO_MATCH);
        } else {
            showView(View.TABLE);
        }
        updateSummary(result);

        if (selectProductId != null) {
            for (Product product : shownProducts) {
                if (product.getId().equals(selectProductId)) {
                    productTable.getSelectionModel().select(product);
                    productTable.scrollTo(product);
                    break;
                }
            }
        }
        if (categoryWasReset) {
            reloadProducts(null);   // the chosen category no longer exists, so search again with "All categories"
        }
    }

    /** Fills the list subtitle and the four summary cards (catalogue-wide numbers). */
    private void updateSummary(SearchResult result) {
        int[] counts = result.stockLevelCounts();
        summaryLabel.setText("Showing " + result.matches().size() + " of " + result.totalCount() + " products");
        statTotal.setText(String.valueOf(result.totalCount()));
        statInStock.setText(String.valueOf(counts[StockLevel.IN_STOCK.ordinal()]));
        statLow.setText(String.valueOf(counts[StockLevel.LOW_STOCK.ordinal()]));
        statOut.setText(String.valueOf(counts[StockLevel.OUT_OF_STOCK.ordinal()]));
    }

    /** Used while loading and when the database cannot be reached. */
    private void clearSummary() {
        summaryLabel.setText("");
        for (Label stat : new Label[] {statTotal, statInStock, statLow, statOut}) {
            stat.setText("-");
        }
    }

    /** Rebuilds the category drop-down, keeping the user's choice. Returns true if the choice had to be reset. */
    private boolean refreshCategoryCombo(List<String> categories) {
        String current = categoryCombo.getValue();
        boolean reset = false;
        suppressFilterEvents = true;
        try {
            List<String> items = new ArrayList<>();
            items.add(ALL_CATEGORIES);
            items.addAll(categories);
            categoryCombo.getItems().setAll(items);
            if (current != null && items.contains(current)) {
                categoryCombo.setValue(current);
            } else {
                categoryCombo.setValue(ALL_CATEGORIES);
                reset = current != null && !ALL_CATEGORIES.equals(current);
            }
        } finally {
            suppressFilterEvents = false;
        }
        return reset;
    }

    // ======================================================================
    // Search and filter controls
    // ======================================================================

    private void onFilterTyped() {
        if (!suppressFilterEvents) {
            searchDelay.playFromStart();
        }
    }

    private void onFilterChosen() {
        if (!suppressFilterEvents) {
            reloadProducts(null);
        }
    }

    /** Builds a ProductFilter from the controls, or returns null (after showing errors) if the price boxes are invalid. */
    private ProductFilter buildFilter() {
        ValidationResult range = ProductValidator.validatePriceRange(minPriceField.getText(), maxPriceField.getText());
        showPriceFilterErrors(range);
        if (!range.isValid()) {
            return null;
        }

        ProductFilter filter = new ProductFilter().withNameQuery(searchField.getText());
        String category = categoryCombo.getValue();
        if (category != null && !ALL_CATEGORIES.equals(category)) {
            filter.withCategory(category);
        }
        if (!isBlank(minPriceField.getText())) {
            filter.withMinPrice(new BigDecimal(minPriceField.getText().trim()));
        }
        if (!isBlank(maxPriceField.getText())) {
            filter.withMaxPrice(new BigDecimal(maxPriceField.getText().trim()));
        }
        return filter.withAvailability(availabilityCombo.getValue()).withStatus(statusCombo.getValue());
    }

    private void showPriceFilterErrors(ValidationResult range) {
        String minError = range.getError(ProductValidator.FIELD_MIN_PRICE);
        String maxError = range.getError(ProductValidator.FIELD_MAX_PRICE);
        setInvalid(minPriceField, minError != null);
        setInvalid(maxPriceField, maxError != null);

        String message = minError != null ? minError : maxError;
        if (minError != null && maxError != null) {
            message = minError + "  " + maxError;
        }
        if (message != null) {
            message += "  The list is not updated until this is fixed.";
        }
        priceFilterError.setText(message == null ? "" : message);
        priceFilterError.setVisible(message != null);
        priceFilterError.setManaged(message != null);
    }

    @FXML
    private void onResetFilters() {
        suppressFilterEvents = true;
        try {
            searchField.clear();
            minPriceField.clear();
            maxPriceField.clear();
            categoryCombo.setValue(ALL_CATEGORIES);
            availabilityCombo.setValue(ProductFilter.Availability.ANY);
            statusCombo.setValue(ProductFilter.Status.ANY);
        } finally {
            suppressFilterEvents = false;
        }
        searchDelay.stop();
        reloadProducts(null);
    }

    // ======================================================================
    // Buttons
    // ======================================================================

    @FXML
    private void onRefresh() {
        if (connected) {
            reloadProducts(null);
            showMessage("Catalogue refreshed.", MessageType.INFO);
        } else {
            connectAndLoad();
        }
    }

    @FXML
    private void onRetry() {
        connectAndLoad();
    }

    @FXML
    private void onAddProduct() {
        if (!connected) {
            showMessage("Not connected to MongoDB. Press Retry connection first.", MessageType.ERROR);
            return;
        }
        openProductForm(null);
    }

    @FXML
    private void onEditProduct() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            openProductForm(selected);
        }
    }

    /** Opens the add/edit dialogue (existing == null means "add"). Blocks until it is closed. */
    private void openProductForm(Product existing) {
        if (!connected) {
            showMessage("Not connected to MongoDB. Press Retry connection first.", MessageType.ERROR);
            return;
        }
        try {
            Product saved = ProductFormController.open(context, addButton.getScene().getWindow(), existing,
                    knownCategories);
            if (saved != null) {
                showMessage((existing == null ? "Added \"" : "Saved changes to \"") + saved.getName()
                        + "\" (" + saved.getId() + ").", MessageType.SUCCESS);
                reloadProducts(saved.getId());
            }
        } catch (IOException e) {
            Dialogs.showError(addButton.getScene().getWindow(), "Could not open the form",
                    "The product form failed to load: " + e.getMessage());
        }
    }

    @FXML
    private void onToggleActive() {
        Product product = productTable.getSelectionModel().getSelectedItem();
        if (product == null) {
            return;
        }
        boolean makeActive = !product.isActive();
        String productId = product.getId();
        beginBusy();
        AsyncRunner.run(() -> context.getProductService().setActive(productId, makeActive), updated -> {
            endBusy();
            showMessage("\"" + updated.getName() + "\" is now " + (makeActive ? "active." : "inactive."),
                    MessageType.SUCCESS);
            reloadProducts(updated.getId());
        }, error -> {
            endBusy();
            handleFailure(error);
        });
    }

    @FXML
    private void onDeleteProduct() {
        Product product = productTable.getSelectionModel().getSelectedItem();
        if (product == null) {
            return;
        }
        if (!Dialogs.confirmDelete(addButton.getScene().getWindow(), product)) {
            return;   // the user pressed Cancel
        }
        String productId = product.getId();
        String productName = product.getName();
        beginBusy();
        AsyncRunner.run(() -> {
            context.getProductService().deleteProduct(productId);
            return productId;
        }, deletedId -> {
            endBusy();
            productTable.getSelectionModel().clearSelection();
            showMessage("Deleted \"" + productName + "\" (" + deletedId + ").", MessageType.SUCCESS);
            reloadProducts(null);
        }, error -> {
            endBusy();
            handleFailure(error);
        });
    }

    // ======================================================================
    // Details panel
    // ======================================================================

    private void showDetails(Product product) {
        boolean hasProduct = product != null;
        detailsPlaceholder.setVisible(!hasProduct);
        detailsPlaceholder.setManaged(!hasProduct);
        detailsContent.setVisible(hasProduct);
        detailsContent.setManaged(hasProduct);
        if (!hasProduct) {
            return;
        }

        detailName.setText(product.getName());
        detailId.setText(product.getId());
        detailPrice.setText(Formatters.price(product.getPrice()));
        applyBadge(detailStatusBadge, product.isActive() ? "Active" : "Inactive",
                product.isActive() ? "badge-active" : "badge-inactive");
        applyBadge(detailStockBadge, stockText(product, false), stockBadgeClass(product.getStockLevel()));

        detailCategories.getChildren().clear();
        for (String category : product.getCategories()) {
            detailCategories.getChildren().add(label(category, "chip"));
        }
        detailDescription.setText(product.getDescription().isEmpty() ? "No description." : product.getDescription());
        detailDates.setText("Created " + Formatters.dateTime(product.getCreatedAt())
                + "   |   Updated " + Formatters.dateTime(product.getUpdatedAt()));
        toggleActiveButton.setText(product.isActive() ? "Deactivate" : "Activate");

        showDetailImages(product, 0);
    }

    /** Large preview of one image plus a strip of thumbnails; clicking a thumbnail changes the preview. */
    private void showDetailImages(Product product, int selectedIndex) {
        List<String> images = product.getImagePaths();
        String selected = images.isEmpty() ? null : images.get(selectedIndex);
        mainImageHolder.getChildren().setAll(
                ProductImageLoader.createView(context.getImageStorage(), selected, 320, 240));

        thumbnailStrip.getChildren().clear();
        if (images.size() < 2) {
            return;
        }
        for (int i = 0; i < images.size(); i++) {
            final int index = i;
            Node thumb = ProductImageLoader.createView(context.getImageStorage(), images.get(i), 64, 48);
            StackPane cell = new StackPane(thumb);
            cell.getStyleClass().add("thumb-cell");
            if (i == selectedIndex) {
                cell.getStyleClass().add("thumb-selected");
            }
            cell.setOnMouseClicked(event -> showDetailImages(product, index));
            thumbnailStrip.getChildren().add(cell);
        }
    }

    // ======================================================================
    // Table layers, messages and errors
    // ======================================================================

    private void showView(View view) {
        setLayerVisible(productTable, view == View.TABLE);
        setLayerVisible(loadingBox, view == View.LOADING);
        setLayerVisible(emptyBox, view == View.EMPTY || view == View.NO_MATCH);
        setLayerVisible(errorBox, view == View.ERROR);
    }

    private static void setLayerVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** One place decides how each kind of failure is shown to the user. */
    private void handleFailure(Throwable error) {
        if (error instanceof DatabaseException) {
            connected = false;
            errorMessage.setText(error.getMessage());
            showView(View.ERROR);
            setConnectionStatus("Not connected", "db-error");
            clearSummary();
            showMessage("Database error. See the message in the catalogue area.", MessageType.ERROR);
        } else if (error instanceof ProductNotFoundException) {
            showMessage(error.getMessage(), MessageType.ERROR);
            reloadProducts(null);
        } else if (error instanceof ValidationException) {
            showMessage("The product data is not valid: " + ((ValidationException) error).getResult(),
                    MessageType.ERROR);
        } else {
            error.printStackTrace();
            showMessage("Unexpected error: " + error.getMessage(), MessageType.ERROR);
        }
    }

    private void showMessage(String text, MessageType type) {
        statusMessage.getStyleClass().removeAll("status-success", "status-error", "status-info");
        switch (type) {
            case SUCCESS:
                statusMessage.getStyleClass().add("status-success");
                break;
            case ERROR:
                statusMessage.getStyleClass().add("status-error");
                break;
            default:
                statusMessage.getStyleClass().add("status-info");
                break;
        }
        statusMessage.setText(text);
        if (type == MessageType.ERROR) {
            messageTimer.stop();     // errors stay until replaced
        } else {
            messageTimer.playFromStart();
        }
    }

    private void setConnectionStatus(String text, String styleClass) {
        dbStatusLabel.getStyleClass().removeAll("db-connected", "db-connecting", "db-error");
        dbStatusLabel.getStyleClass().add(styleClass);
        dbStatusLabel.setText(text);
    }

    private void beginBusy() {
        busyTasks++;
        busyIndicator.setVisible(true);
    }

    private void endBusy() {
        busyTasks--;
        busyIndicator.setVisible(busyTasks > 0);
    }

    // ======================================================================
    // Small UI helpers
    // ======================================================================

    private String selectedProductId() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    private static Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Label statusBadge(Product product) {
        Label badge = new Label(product.isActive() ? "Active" : "Inactive");
        badge.getStyleClass().addAll("badge", product.isActive() ? "badge-active" : "badge-inactive");
        return badge;
    }

    /** Colour-coded stock badge: green = in stock, amber = low, red = out of stock. */
    private static Label stockBadge(Product product, boolean compact) {
        Label badge = new Label(stockText(product, compact));
        badge.getStyleClass().addAll("badge", stockBadgeClass(product.getStockLevel()));
        return badge;
    }

    private static String stockText(Product product, boolean compact) {
        String levelLabel = product.getStockLevel().getLabel();
        if (compact) {
            return product.getStockQuantity() + "  " + levelLabel;
        }
        return product.getStockQuantity() + " units  |  " + levelLabel;
    }

    private static String stockBadgeClass(StockLevel level) {
        switch (level) {
            case OUT_OF_STOCK:
                return "badge-out";
            case LOW_STOCK:
                return "badge-low";
            default:
                return "badge-ok";
        }
    }

    private static void applyBadge(Label badge, String text, String colourClass) {
        badge.setText(text);
        badge.getStyleClass().removeAll("badge", "badge-active", "badge-inactive", "badge-ok", "badge-low",
                "badge-out");
        badge.getStyleClass().addAll("badge", colourClass);
    }

    private static void setInvalid(TextField field, boolean invalid) {
        field.getStyleClass().remove("invalid");
        if (invalid) {
            field.getStyleClass().add("invalid");
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
