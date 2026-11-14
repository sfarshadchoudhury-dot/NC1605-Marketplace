package com.marketplace.products.ui;

import com.marketplace.products.model.Product;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

/** Small helpers for the pop-up dialogues. */
public final class Dialogs {

    private Dialogs() {
    }

    /** Asks "are you sure?" before a product is permanently deleted. Cancel is the default button. */
    public static boolean confirmDelete(Window owner, Product product) {
        ButtonType deleteType = new ButtonType("Delete permanently", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", deleteType, cancelType);
        style(alert, owner);
        alert.setTitle("Delete product");
        alert.setHeaderText("Delete \"" + product.getName() + "\"?");
        alert.setContentText("Product " + product.getId() + " and all of its images will be permanently removed. "
                + "This cannot be undone.\n\nTip: choose Deactivate instead if you only want to hide it from sale.");

        DialogPane pane = alert.getDialogPane();
        ((Button) pane.lookupButton(deleteType)).setDefaultButton(false);
        ((Button) pane.lookupButton(cancelType)).setDefaultButton(true);

        return alert.showAndWait().orElse(cancelType) == deleteType;
    }

    public static void showError(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        style(alert, owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private static void style(Alert alert, Window owner) {
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.getDialogPane().getStylesheets().add(
                Dialogs.class.getResource("application.css").toExternalForm());
    }
}
