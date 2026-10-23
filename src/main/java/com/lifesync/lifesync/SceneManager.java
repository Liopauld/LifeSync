package com.lifesync.lifesync;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Centralized scene and content switcher for the LifeSync application.
 *
 * <h3>Design rationale</h3>
 * Instead of every controller manually loading FXML files and swapping scenes,
 * all navigation flows through this single class.  This keeps the navigation
 * logic in one place, avoids code duplication, and makes it easy to add
 * transitions or loading indicators later.
 *
 * <h3>Two modes of navigation</h3>
 * <ol>
 *   <li><strong>Full scene swap</strong> ({@link #showLogin}, {@link #showSignUp},
 *       {@link #showMain}) — replaces the entire window content.  Used for
 *       authentication screens vs. the main app shell.</li>
 *   <li><strong>Content swap</strong> ({@link #loadContent}) — replaces only the
 *       center content area inside the main shell (sidebar stays visible).
 *       Used for navigating between Home, Plan, Meals, etc.</li>
 * </ol>
 */
public class SceneManager {

    /** Reference to the primary stage, set once at application startup. */
    private static Stage primaryStage;

    /**
     * The StackPane inside main-view.fxml where tab content is loaded.
     * Set by MainController after it initializes.
     */
    private static StackPane contentArea;

    /** Width of the application window. */
    private static final double WINDOW_WIDTH = 1366;

    /** Height of the application window. */
    private static final double WINDOW_HEIGHT = 768;

    /**
     * Initializes the SceneManager with the primary stage.
     * Called once from {@link LifeSyncApplication#start(Stage)}.
     *
     * @param stage the primary stage of the JavaFX application
     */
    public static void initialize(Stage stage) {
        primaryStage = stage;
    }

    /**
     * Returns the primary stage reference.
     *
     * @return the primary stage
     */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Sets the content area reference for in-app navigation.
     * Called by MainController after it loads.
     *
     * @param area the StackPane that holds swappable content
     */
    public static void setContentArea(StackPane area) {
        contentArea = area;
    }

    // ── Full Scene Navigation ──────────────────────────────────────────

    /** Shows the login screen (replaces entire scene). */
    public static void showLogin() {
        loadScene("fxml/login-view.fxml", "LifeSync — Login");
    }

    /** Shows the sign-up screen (replaces entire scene). */
    public static void showSignUp() {
        loadScene("fxml/signup-view.fxml", "LifeSync — Sign Up");
    }

    /** Shows the main app shell with sidebar (replaces entire scene). */
    public static void showMain() {
        loadScene("fxml/main-view.fxml", "LifeSync — Dashboard");
    }

    // ── Content Area Navigation (within main shell) ────────────────────

    /**
     * Loads an FXML file into the main shell's content area.
     * The sidebar and header remain visible; only the center swaps.
     *
     * @param fxmlFile the FXML file path relative to the resources package,
     *                 e.g. "fxml/home-view.fxml"
     */
    public static void loadContent(String fxmlFile) {
        if (contentArea == null) {
            System.err.println("[SceneManager] Content area not set — cannot load " + fxmlFile);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(fxmlFile));
            Parent content = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(content);
            if (contentArea.getScene() != null) {
                com.lifesync.lifesync.service.ThemeManager.applyTheme(contentArea.getScene());
            }
        } catch (IOException e) {
            System.err.println("[SceneManager] Failed to load content: " + fxmlFile);
            e.printStackTrace();
        }
    }

    // ── Internal helper ────────────────────────────────────────────────

    /**
     * Loads an FXML file as a full scene and sets it on the primary stage.
     *
     * @param fxmlFile the FXML path relative to the resources package
     * @param title    the window title
     */
    private static void loadScene(String fxmlFile, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    SceneManager.class.getResource(fxmlFile));
            Parent root = loader.load();
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

            // Apply the global stylesheet
            String css = SceneManager.class.getResource("css/styles.css") != null
                    ? SceneManager.class.getResource("css/styles.css").toExternalForm()
                    : null;
            if (css != null) {
                scene.getStylesheets().add(css);
            }

            // Apply active theme (Light/Orange or Dark/Purple)
            com.lifesync.lifesync.service.ThemeManager.applyTheme(scene);

            primaryStage.setTitle(title);
            primaryStage.setScene(scene);
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("[SceneManager] Failed to load scene: " + fxmlFile);
            e.printStackTrace();
        }
    }
}
