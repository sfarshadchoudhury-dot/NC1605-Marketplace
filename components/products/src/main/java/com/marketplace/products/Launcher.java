package com.marketplace.products;

import javafx.application.Application;

/**
 * Start the application from this class (right-click > Run As > Java Application in Eclipse).
 * It exists because a main class that itself extends Application fails with
 * "JavaFX runtime components are missing" when JavaFX is on the classpath instead of the module path.
 */
public class Launcher {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");   // image checks use java.awt classes but need no AWT window
        Application.launch(MainApp.class, args);
    }
}
