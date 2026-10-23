package com.lifesync.lifesync.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lifesync.lifesync.SceneManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages application-wide theming for LifeSync, switching between the default
 * warm Light/Orange scheme and the sleek Dark/Purple scheme.
 *
 * <p>Persists the user's theme selection to {@code data/theme.json} so that
 * the chosen theme is preserved across sessions and screen transitions.</p>
 *
 * @author LifeSync Development Team
 * @version 1.0 (Phase 3 – Dynamic Theming)
 */
public class ThemeManager {

    /** Relative resource path to the dark theme stylesheet. */
    public static final String DARK_THEME_CSS_PATH = "css/dark-theme.css";

    /** File location for theme persistence. */
    private static File themeFile() {
        return DataStore.dataDirectory().resolve("theme.json").toFile();
    }

    /** Current active theme state (false = Light/Orange, true = Dark/Purple). */
    private static boolean darkMode = false;

    /** Registered listeners notified when the active theme toggles. */
    private static final List<ThemeChangeListener> listeners = new ArrayList<>();

    private static final Gson GSON = new Gson();

    static {
        loadPersistedTheme();
    }

    private ThemeManager() {
        // Static utility class
    }

    /**
     * Functional interface for subscribing to theme change events.
     */
    @FunctionalInterface
    public interface ThemeChangeListener {
        void onThemeChanged(boolean isDark);
    }

    /**
     * Returns true if dark mode (dark/purple scheme) is currently active.
     *
     * @return true for Dark/Purple, false for Light/Orange
     */
    public static boolean isDarkMode() {
        return darkMode;
    }

    /**
     * Sets the active theme and updates the current scene and all listeners.
     *
     * @param dark true to activate Dark/Purple mode; false for Light/Orange
     */
    public static void setDarkMode(boolean dark) {
        darkMode = dark;
        persistTheme();
        applyCurrentTheme();
        notifyListeners();
    }

    /**
     * Toggles between Light/Orange and Dark/Purple mode.
     */
    public static void toggleTheme() {
        setDarkMode(!darkMode);
    }

    /**
     * Registers a listener to be notified when the theme changes.
     *
     * @param listener the change listener
     */
    public static void addListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a registered theme change listener.
     *
     * @param listener the listener to remove
     */
    public static void removeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Applies the current theme to the specified Scene.
     *
     * @param scene the JavaFX Scene to style
     */
    public static void applyTheme(Scene scene) {
        if (scene == null) return;

        try {
            var cssResource = ThemeManager.class.getResource("/com/lifesync/lifesync/" + DARK_THEME_CSS_PATH);
            String darkCssUrl = (cssResource != null) ? cssResource.toExternalForm() : null;

            if (darkMode) {
                if (darkCssUrl != null && !scene.getStylesheets().contains(darkCssUrl)) {
                    scene.getStylesheets().add(darkCssUrl);
                }
                if (scene.getRoot() != null && !scene.getRoot().getStyleClass().contains("dark-theme")) {
                    scene.getRoot().getStyleClass().add("dark-theme");
                }
            } else {
                if (darkCssUrl != null) {
                    scene.getStylesheets().remove(darkCssUrl);
                }
                if (scene.getRoot() != null) {
                    scene.getRoot().getStyleClass().remove("dark-theme");
                }
            }
        } catch (Exception e) {
            System.err.println("[ThemeManager] Failed to apply theme stylesheet: " + e.getMessage());
        }
    }

    /**
     * Applies the current theme stylesheets to a JavaFX DialogPane.
     *
     * @param dialogPane the DialogPane to style
     */
    public static void applyTheme(DialogPane dialogPane) {
        if (dialogPane == null) return;

        try {
            var lightResource = ThemeManager.class.getResource("/com/lifesync/lifesync/css/styles.css");
            if (lightResource != null && !dialogPane.getStylesheets().contains(lightResource.toExternalForm())) {
                dialogPane.getStylesheets().add(lightResource.toExternalForm());
            }
            var darkResource = ThemeManager.class.getResource("/com/lifesync/lifesync/" + DARK_THEME_CSS_PATH);
            String darkCssUrl = (darkResource != null) ? darkResource.toExternalForm() : null;

            if (darkMode) {
                if (darkCssUrl != null && !dialogPane.getStylesheets().contains(darkCssUrl)) {
                    dialogPane.getStylesheets().add(darkCssUrl);
                }
                if (!dialogPane.getStyleClass().contains("dark-theme")) {
                    dialogPane.getStyleClass().add("dark-theme");
                }
            } else {
                if (darkCssUrl != null) {
                    dialogPane.getStylesheets().remove(darkCssUrl);
                }
                dialogPane.getStyleClass().remove("dark-theme");
            }
        } catch (Exception e) {
            System.err.println("[ThemeManager] Failed to apply theme to dialog pane: " + e.getMessage());
        }
    }

    /**
     * Applies the current theme to the primary stage's scene if available.
     */
    public static void applyCurrentTheme() {
        Stage stage = SceneManager.getPrimaryStage();
        if (stage != null && stage.getScene() != null) {
            applyTheme(stage.getScene());
        }
    }

    /**
     * Notifies all registered listeners of a theme toggle.
     */
    private static void notifyListeners() {
        for (ThemeChangeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onThemeChanged(darkMode);
            } catch (Exception e) {
                System.err.println("[ThemeManager] Error notifying theme listener: " + e.getMessage());
            }
        }
    }

    /**
     * Loads the saved theme preference from disk.
     */
    private static void loadPersistedTheme() {
        File file = themeFile();
        if (!file.exists()) {
            darkMode = false;
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
            if (obj.has("darkMode")) {
                darkMode = obj.get("darkMode").getAsBoolean();
            }
        } catch (Exception e) {
            System.err.println("[ThemeManager] Could not read theme preference: " + e.getMessage());
            darkMode = false;
        }
    }

    /**
     * Saves the current theme preference to disk.
     */
    private static void persistTheme() {
        File file = themeFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter writer = new FileWriter(file)) {
            JsonObject obj = new JsonObject();
            obj.addProperty("darkMode", darkMode);
            GSON.toJson(obj, writer);
        } catch (IOException e) {
            System.err.println("[ThemeManager] Failed to persist theme preference: " + e.getMessage());
        }
    }
}
