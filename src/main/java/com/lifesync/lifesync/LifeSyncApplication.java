package com.lifesync.lifesync;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Main entry point for the LifeSync JavaFX application.
 *
 * This class extends {@link Application}, which is the standard way to
 * launch a JavaFX app.  It initializes the {@link SceneManager} with the
 * primary stage and immediately shows the login screen.
 *
 * <h3>Application lifecycle</h3>
 * <ol>
 *   <li>{@link Launcher#main(String[])} calls {@code Application.launch()}</li>
 *   <li>JavaFX creates this class and calls {@link #start(Stage)}</li>
 *   <li>We configure the window and show the login screen</li>
 *   <li>After successful login, {@link SceneManager#showMain()} loads the dashboard</li>
 * </ol>
 */
public class LifeSyncApplication extends Application {

    /**
     * Called by JavaFX after the Application is constructed.
     * Sets up the primary stage and shows the login screen.
     *
     * @param stage the primary stage provided by the JavaFX runtime
     */
    @Override
    public void start(Stage stage) {
        // Configure the primary stage
        stage.setTitle("LifeSync");
        stage.setMinWidth(1100);   // Prevent the window from becoming too small
        stage.setMinHeight(700);

        // Initialize the SceneManager so all navigation flows through it
        SceneManager.initialize(stage);

        // Show the login screen as the first view
        SceneManager.showLogin();
    }

    /**
     * Called when the application is closed.
     * Use this for any cleanup (saving state, closing connections, etc.).
     */
    @Override
    public void stop() {
        System.out.println("[LifeSync] Application shutting down.");
    }
}
