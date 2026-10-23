package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.SceneManager;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.UserService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for the Profile editing screen (profile-view.fxml).
 *
 * Allows the user to view and edit their personal information (name, body
 * metrics, activity level, dietary preferences). When saved, the profile
 * is persisted to JSON and BMR/TDEE are recalculated automatically.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>On initialize, loads the current user's data into all form fields.</li>
 *   <li>BMR/TDEE are displayed as read-only calculated values that update
 *       when the profile is saved.</li>
 *   <li>The Save button validates numeric fields, copies values back to
 *       the User model, recalculates TDEE, and persists via
 *       {@link UserService#recalculateAndSave(User)}.</li>
 *   <li>The Logout button clears the session via {@link AuthService#logout()}
 *       and navigates back to the login screen.</li>
 * </ol>
 */
public class ProfileController {

    // ── FXML-injected fields ───────────────────────────────────────────

    // Profile identity card
    @FXML private Label avatarInitialsLabel;
    @FXML private Label profileFullNameLabel;
    @FXML private Label profileUsernameLabel;
    @FXML private Label memberSinceLabel;

    // Account info form
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private TextField usernameDisplayField;

    // Body metrics form
    @FXML private TextField ageField;
    @FXML private ComboBox<String> genderCombo;
    @FXML private TextField heightField;
    @FXML private TextField weightField;
    @FXML private ComboBox<String> activityLevelCombo;

    // Nutrition & BMI calculator
    @FXML private Label bmiValueLabel;
    @FXML private Label bmiCategoryBadge;
    @FXML private Label bmiHealthyRangeLabel;
    @FXML private Label bmrValueLabel;
    @FXML private Label tdeeValueLabel;
    @FXML private Label nutritionHintLabel;

    // Dietary preferences
    @FXML private TextArea dietaryPreferencesArea;

    // Status message
    @FXML private Label statusLabel;

    /** Reference to the currently logged-in user. */
    private User currentUser;

    /**
     * Called automatically after FXML injection.
     * Populates combo boxes and loads the user's current profile data
     * into the form fields.
     */
    @FXML
    public void initialize() {
        currentUser = AuthService.getCurrentUser();
        if (currentUser == null) return;

        // Set up combo box options
        setupCombos();

        // Load current user data into form
        loadProfile();

        // Calculate and display BMR/TDEE
        updateNutritionCalculator();
    }

    // ── Combo Setup ────────────────────────────────────────────────────

    /**
     * Populates the Gender and Activity Level combo boxes with human-readable
     * options that map to the User.Gender and User.ActivityLevel enums.
     */
    private void setupCombos() {
        // Gender options
        genderCombo.setItems(FXCollections.observableArrayList("Male", "Female"));

        // Activity level options with descriptive text
        activityLevelCombo.setItems(FXCollections.observableArrayList(
                "Sedentary — Little or no exercise",
                "Lightly Active — Exercise 1-3 days/week",
                "Moderately Active — Exercise 3-5 days/week",
                "Very Active — Hard exercise 6-7 days/week",
                "Extra Active — Very hard exercise + physical job"
        ));
    }

    // ── Load Profile Data ──────────────────────────────────────────────

    /**
     * Copies the current user's data into the form fields and display labels.
     */
    private void loadProfile() {
        // Profile identity card
        String initials = getInitials(currentUser.getFirstName(), currentUser.getLastName());
        avatarInitialsLabel.setText(initials);
        profileFullNameLabel.setText(currentUser.getFullName().trim());
        profileUsernameLabel.setText("@" + currentUser.getUsername());

        if (currentUser.getCreatedDate() != null) {
            memberSinceLabel.setText("Member since " + currentUser.getCreatedDate());
        } else {
            memberSinceLabel.setText("Member");
        }

        // Account info (some read-only)
        firstNameField.setText(safeStr(currentUser.getFirstName()));
        lastNameField.setText(safeStr(currentUser.getLastName()));
        emailField.setText(safeStr(currentUser.getEmail()));
        usernameDisplayField.setText(safeStr(currentUser.getUsername()));

        // Body metrics
        if (currentUser.getAge() > 0) {
            ageField.setText(String.valueOf(currentUser.getAge()));
        }

        // Gender combo
        if (currentUser.getGender() != null) {
            genderCombo.setValue(currentUser.getGender() == User.Gender.MALE ? "Male" : "Female");
        }

        // Height and weight
        if (currentUser.getHeightCm() > 0) {
            heightField.setText(String.format("%.1f", currentUser.getHeightCm()));
        }
        if (currentUser.getWeightKg() > 0) {
            weightField.setText(String.format("%.1f", currentUser.getWeightKg()));
        }

        // Activity level combo
        if (currentUser.getActivityLevel() != null) {
            int index = currentUser.getActivityLevel().ordinal();
            if (index < activityLevelCombo.getItems().size()) {
                activityLevelCombo.getSelectionModel().select(index);
            }
        }

        // Dietary preferences
        dietaryPreferencesArea.setText(safeStr(currentUser.getDietaryPreferences()));
    }

    // ── Save Profile ───────────────────────────────────────────────────

    /**
     * Handles the Save button click.
     *
     * Validates numeric fields (age, height, weight), copies all form
     * values back to the User model, recalculates BMR/TDEE, persists
     * the profile, and shows a success/error message.
     */
    @FXML
    private void handleSave() {
        // Validate required fields
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty()) {
            showStatus("❌ First name and last name are required.", true);
            return;
        }

        // Parse numeric fields with validation
        int age = 0;
        double height = 0;
        double weight = 0;

        try {
            String ageText = ageField.getText().trim();
            if (!ageText.isEmpty()) {
                age = Integer.parseInt(ageText);
                if (age < 1 || age > 150) {
                    showStatus("❌ Age must be between 1 and 150.", true);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            showStatus("❌ Age must be a whole number.", true);
            return;
        }

        try {
            String heightText = heightField.getText().trim();
            if (!heightText.isEmpty()) {
                height = Double.parseDouble(heightText);
                if (!Double.isFinite(height) || height < 30 || height > 300) {
                    showStatus("❌ Height must be between 30 and 300 cm.", true);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            showStatus("❌ Height must be a number.", true);
            return;
        }

        try {
            String weightText = weightField.getText().trim();
            if (!weightText.isEmpty()) {
                weight = Double.parseDouble(weightText);
                if (!Double.isFinite(weight) || weight < 10 || weight > 500) {
                    showStatus("❌ Weight must be between 10 and 500 kg.", true);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            showStatus("❌ Weight must be a number.", true);
            return;
        }

        // Copy form values to the user model
        currentUser.setFirstName(firstName);
        currentUser.setLastName(lastName);
        currentUser.setAge(age);
        currentUser.setHeightCm(height);
        currentUser.setWeightKg(weight);

        // Gender
        String genderStr = genderCombo.getValue();
        if (genderStr != null) {
            currentUser.setGender(genderStr.equals("Male") ? User.Gender.MALE : User.Gender.FEMALE);
        }

        // Activity level (mapped by index to enum ordinal)
        int levelIndex = activityLevelCombo.getSelectionModel().getSelectedIndex();
        if (levelIndex >= 0) {
            currentUser.setActivityLevel(User.ActivityLevel.values()[levelIndex]);
        }

        // Dietary preferences
        currentUser.setDietaryPreferences(dietaryPreferencesArea.getText().trim());

        // Recalculate TDEE and save to JSON
        UserService.recalculateAndSave(currentUser);

        // Refresh the display
        loadProfile();
        updateNutritionCalculator();

        showStatus("✅ Profile saved successfully!", false);
    }

    // ── Logout ─────────────────────────────────────────────────────────

    /** Verifies the current password before replacing the account's recovery code. */
    @FXML
    private void handleRecoveryCode() {
        if (currentUser != null) AccountRecoveryDialogs.showGenerateCode(currentUser.getUsername());
    }

    /**
     * Handles the Logout button click.
     * Clears the current session and navigates to the login screen.
     */
    @FXML
    private void handleLogout() {
        // Confirm logout
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Log Out");
        confirm.setHeaderText("Are you sure you want to log out?");
        confirm.setContentText("Any unsaved changes will be lost.");
        com.lifesync.lifesync.service.ThemeManager.applyTheme(confirm.getDialogPane());

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                AuthService.logout();
                SceneManager.showLogin();
            }
        });
    }

    // ── Nutrition Calculator ───────────────────────────────────────────

    /**
     * Calculates and displays BMI, healthy weight range, BMR, and TDEE values
     * based on current user body metrics. Shows actionable lifestyle hints.
     */
    private void updateNutritionCalculator() {
        double bmr = UserService.calculateBMR(currentUser);
        double tdee = UserService.calculateTDEE(currentUser);
        double bmi = currentUser != null ? currentUser.getBmi() : 0.0;

        if (bmi > 0) {
            bmiValueLabel.setText(String.format("%.1f", bmi));
            User.BMICategory cat = currentUser.getBmiCategory();
            bmiCategoryBadge.setText(cat.getDisplayName());

            // Color-code category badge via CSS classes for perfect theme adaptability
            bmiCategoryBadge.getStyleClass().removeAll("badge-underweight", "badge-normal", "badge-overweight", "badge-obese", "badge-pending");
            bmiCategoryBadge.getStyleClass().add("badge-" + cat.name().toLowerCase());

            String range = UserService.getHealthyWeightRange(currentUser.getHeightCm());
            bmiHealthyRangeLabel.setText("Healthy weight for your height: " + range);
        } else {
            bmiValueLabel.setText("--.-");
            bmiCategoryBadge.setText("Pending");
            bmiCategoryBadge.getStyleClass().removeAll("badge-underweight", "badge-normal", "badge-overweight", "badge-obese");
            bmiCategoryBadge.getStyleClass().add("badge-pending");
            bmiHealthyRangeLabel.setText("Healthy range: 18.5 – 24.9");
        }

        if (bmr > 0) {
            bmrValueLabel.setText(String.format("%,.0f kcal", bmr));
            tdeeValueLabel.setText(String.format("%,.0f kcal", tdee));
            nutritionHintLabel.setText(UserService.getBMISuggestion(currentUser));
        } else {
            bmrValueLabel.setText("-- kcal");
            tdeeValueLabel.setText("-- kcal");
            nutritionHintLabel.setText("Fill in your age, gender, height, weight, "
                    + "and activity level to calculate your BMR and daily calorie target.");
        }
    }

    // ── Utility Methods ────────────────────────────────────────────────

    /**
     * Extracts initials from first and last name (e.g., "John Doe" → "JD").
     *
     * @param firstName user's first name
     * @param lastName  user's last name
     * @return uppercase initials string, or "U" if both names are empty
     */
    private String getInitials(String firstName, String lastName) {
        StringBuilder sb = new StringBuilder();
        if (firstName != null && !firstName.isEmpty()) {
            sb.append(firstName.charAt(0));
        }
        if (lastName != null && !lastName.isEmpty()) {
            sb.append(lastName.charAt(0));
        }
        return sb.length() > 0 ? sb.toString().toUpperCase() : "U";
    }

    /**
     * Null-safe string converter. Returns empty string if input is null.
     *
     * @param s the input string (may be null)
     * @return the input string, or "" if null
     */
    private String safeStr(String s) {
        return s != null ? s : "";
    }

    /**
     * Shows a status message below the Save button.
     *
     * @param message the message to display
     * @param isError true for red text, false for green text
     */
    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll("status-error-text", "status-success-text");
        statusLabel.getStyleClass().add(isError ? "status-error-text" : "status-success-text");
    }
}
