package com.marketplace.products;

import com.marketplace.products.config.AppConfig;
import com.marketplace.products.ui.AsyncRunner;
import com.marketplace.products.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/** The JavaFX application: creates the main window and hands it the shared AppContext. */
public class MainApp extends Application {

    private AppContext context;

    /** Runs on the JavaFX launcher thread (not the UI thread), so reading the config file here is fine. */
    @Override
    public void init() {
        context = new AppContext(AppConfig.load());
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("ui/main-view.fxml"));
        Parent root = loader.load();
        MainController controller = loader.getController();
        controller.setAppContext(context);

        stage.setTitle("Marketplace - Product Catalogue");
        stage.setScene(new Scene(root, 1360, 840));
        stage.setMinWidth(1040);
        stage.setMinHeight(660);
        stage.show();

        controller.start();   // connects to MongoDB on a background thread
    }

    @Override
    public void stop() {
        AsyncRunner.shutdown();
        context.close();
    }
}
