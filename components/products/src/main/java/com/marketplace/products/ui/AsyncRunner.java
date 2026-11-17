package com.marketplace.products.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs slow work (MongoDB calls, copying image files) on a background thread so the window never freezes,
 * then hands the result back on the JavaFX application thread.
 *
 * <p>JavaFX rule: only the application thread may touch UI controls. A Task's onSucceeded / onFailed
 * handlers are always run on that thread, so the callbacks passed in here may update the UI safely.
 * One worker thread means operations run one after another, in the order they were requested.
 */
public final class AsyncRunner {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "products-worker");
        thread.setDaemon(true);   // do not keep the JVM alive after the window closes
        return thread;
    });

    private AsyncRunner() {
    }

    /**
     * @param work      runs on the background thread (do NOT touch UI controls in here)
     * @param onSuccess receives work's result, on the JavaFX thread
     * @param onFailure receives the exception work threw, on the JavaFX thread
     */
    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        WORKER.execute(task);
    }

    public static void shutdown() {
        WORKER.shutdownNow();
    }
}
