package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.SceneManager;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.ThemeManager;
import com.lifesync.lifesync.model.User;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

/**
 * Controller for the main application shell (main-view.fxml).
 *
 * Manages the sidebar navigation, header bar, and content area.
 * When the user clicks a sidebar button, this controller loads the
 * corresponding FXML into the content area via SceneManager.
 *
 * The sidebar highlights the currently active navigation item by
 * adding/removing the "sidebar-btn-active" CSS class.
 *
 * <h3>Navigation mapping</h3>
 * <ul>
 *   <li>Home    → home-view.fxml</li>
 *   <li>Plan    → plan-view.fxml</li>
 *   <li>Meals   → meals-view.fxml</li>
 *   <li>Pantry  → pantry-view.fxml</li>
 *   <li>Activity→ activity-view.fxml</li>
 *   <li>Chat    → chat-view.fxml</li>
 *   <li>Profile → profile-view.fxml (via header avatar click)</li>
 * </ul>
 */
public class MainController {

    // ── FXML-injected fields ───────────────────────────────────────────

    @FXML private StackPane contentArea;      // Center content swap area
    @FXML private Label userNameLabel;         // "Hi, [Name]" in header
    @FXML private Button btnHome;              // Sidebar nav buttons
    @FXML private Button btnPlan;
    @FXML private Button btnMeals;
    @FXML private Button btnPantry;
    @FXML private Button btnActivity;
    @FXML private Button btnChat;              // AI Coach chatbot button
    @FXML private Button btnThemeToggle;       // Theme toggle (Light/Orange <-> Dark/Purple)

    /** The profile area HBox in the header — clicking it opens the profile view. */
    @FXML private HBox profileArea;

    /** Singleton instance reference for inter-controller navigation and synchronization. */
    private static MainController instance;

    /** Tracks which sidebar button is currently active. */
    private Button activeButton;

    /**
     * Returns the singleton instance of MainController.
     *
     * @return the active MainController instance
     */
    public static MainController getInstance() {
        return instance;
    }

    /**
     * Called automatically after FXML injection.
     * Sets up the content area in SceneManager, displays the user's name,
     * registers the profile click handler, and loads the Home tab as default.
     */
    @FXML
    public void initialize() {
        instance = this;
        // Register the content area so SceneManager can swap content
        SceneManager.setContentArea(contentArea);

        // Display the logged-in user's name in the header
        User currentUser = AuthService.getCurrentUser();
        if (currentUser != null) {
            String firstName = currentUser.getFirstName();
            userNameLabel.setText("Hi, " + (firstName != null ? firstName : "User") + "!");
        }

        // Make the profile area in the header clickable
        if (profileArea != null) {
            profileArea.setOnMouseClicked(e -> handleProfile());
            profileArea.getStyleClass().add("profile-clickable-area");
        }

        // Initialize theme toggle button
        updateThemeButton(ThemeManager.isDarkMode());
        ThemeManager.addListener(this::updateThemeButton);

        // Load Home as the default content
        setActiveButton(btnHome);
        SceneManager.loadContent("fxml/home-view.fxml");
    }

    // ── Sidebar Navigation Handlers ────────────────────────────────────

    /** Navigates to the Home dashboard. */
    @FXML
    private void handleHome() {
        setActiveButton(btnHome);
        SceneManager.loadContent("fxml/home-view.fxml");
    }

    /** Navigates to the Plan & Calendar view. */
    @FXML
    private void handlePlan() {
        setActiveButton(btnPlan);
        SceneManager.loadContent("fxml/plan-view.fxml");
    }

    /** Navigates to the Meals recipe browser. */
    @FXML
    private void handleMeals() {
        setActiveButton(btnMeals);
        SceneManager.loadContent("fxml/meals-view.fxml");
    }

    /** Navigates to the Pantry ingredient manager. */
    @FXML
    private void handlePantry() {
        setActiveButton(btnPantry);
        SceneManager.loadContent("fxml/pantry-view.fxml");
    }

    /** Navigates to the Activity & Fitness tracker. */
    @FXML
    private void handleActivity() {
        setActiveButton(btnActivity);
        SceneManager.loadContent("fxml/activity-view.fxml");
    }

    /** Navigates to the LifeSync AI Coach chatbot. */
    @FXML
    private void handleChat() {
        setActiveButton(btnChat);
        SceneManager.loadContent("fxml/chat-view.fxml");
    }

    /**
     * Navigates to the Profile editing screen.
     * Called when the user clicks their avatar/name in the header bar.
     * Clears the active sidebar button since Profile is not in the sidebar.
     */
    private void handleProfile() {
        setActiveButton(null); // No sidebar button is active for Profile
        SceneManager.loadContent("fxml/profile-view.fxml");
    }

    // ── External Navigation API ────────────────────────────────────────

    public void navigateToHome() {
        handleHome();
    }

    public void navigateToPlan() {
        handlePlan();
    }

    public void navigateToMeals() {
        handleMeals();
    }

    public void navigateToPantry() {
        handlePantry();
    }

    public void navigateToActivity() {
        handleActivity();
    }

    public void navigateToChat() {
        handleChat();
    }

    public void navigateToProfile() {
        handleProfile();
    }

    /**
     * Toggles the application theme between Light/Orange mode and Dark/Purple mode.
     */
    @FXML
    private void handleToggleTheme() {
        ThemeManager.toggleTheme();
    }

    /**
     * Updates the theme button label and styling based on active mode.
     *
     * @param isDark true if dark theme is currently active
     */
    private void updateThemeButton(boolean isDark) {
        if (btnThemeToggle != null) {
            btnThemeToggle.setText(isDark ? "☀ Light Mode" : "🌙 Dark Mode");
        }
    }

    /**
     * Updates the sidebar highlight to show which tab is active.
     * Removes the "sidebar-btn-active" class from the previous button
     * and adds it to the new one.
     *
     * @param button the button that was clicked, or null to clear all highlights
     */
    private void setActiveButton(Button button) {
        if (activeButton != null) {
            activeButton.getStyleClass().remove("sidebar-btn-active");
        }
        activeButton = button;
        if (activeButton != null) {
            activeButton.getStyleClass().add("sidebar-btn-active");
        }
    }
}
