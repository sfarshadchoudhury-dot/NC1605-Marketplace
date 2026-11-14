package com.marketplace.products.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks that each FXML file loads and that every @FXML field in its controller really got a control
 * (a misspelt fx:id would otherwise leave the field null and only crash later, when the button is clicked).
 *
 * It needs the JavaFX toolkit, which needs a graphical session. If the toolkit cannot start (for example on a
 * machine with no display) the tests are SKIPPED, not passed.
 */
class FxmlLoadTest {

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        System.setProperty("java.awt.headless", "true");
        CountDownLatch started = new CountDownLatch(1);
        boolean toolkitAvailable = true;
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();          // another test class in this JVM already started it
        } catch (Throwable cannotStart) {
            toolkitAvailable = false;
        }
        assumeTrue(toolkitAvailable && started.await(15, TimeUnit.SECONDS),
                "JavaFX toolkit could not start (no graphical session?) - FXML tests skipped");
    }

    /** Runs code on the JavaFX application thread and waits for its result. */
    private static <T> T onFxThread(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    private static List<String> unwiredFields(Object controller) throws IllegalAccessException {
        List<String> missing = new ArrayList<>();
        for (Field field : controller.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(FXML.class)) {
                field.setAccessible(true);
                if (field.get(controller) == null) {
                    missing.add(field.getName());
                }
            }
        }
        return missing;
    }

    @Test
    void mainViewLoadsAndEveryFxIdIsWired() throws Exception {
        URL fxml = MainController.class.getResource("main-view.fxml");
        assertNotNull(fxml, "main-view.fxml must be on the classpath");

        List<String> missing = onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            new Scene(root);              // also applies the stylesheet
            return unwiredFields(loader.getController());
        });

        assertEquals(List.of(), missing, "these @FXML fields have no matching fx:id in main-view.fxml");
    }

    @Test
    void productFormLoadsAndEveryFxIdIsWired() throws Exception {
        URL fxml = ProductFormController.class.getResource("product-form.fxml");
        assertNotNull(fxml, "product-form.fxml must be on the classpath");

        List<String> missing = onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            new Scene(root);
            return unwiredFields(loader.getController());
        });

        assertEquals(List.of(), missing, "these @FXML fields have no matching fx:id in product-form.fxml");
    }

    @Test
    void stylesheetIsOnTheClasspath() {
        assertNotNull(MainController.class.getResource("application.css"));
    }
}
