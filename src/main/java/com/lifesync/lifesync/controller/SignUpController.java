package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.SceneManager;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.UserService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/**
 * Controller for the registration screen (signup-view.fxml) with integrated BMI setup.
 *
 * Validates registration inputs, collects health & body metrics (age, biological sex,
 * height, weight, activity level), calculates the user's initial BMI and TDEE calorie
 * goals, and saves the personalized profile.
 */
public class SignUpController {

    // ── Account Fields ─────────────────────────────────────────────────
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;

    // ── Body Metrics & BMI Fields ──────────────────────────────────────
    @FXML private TextField ageField;
    @FXML private ComboBox<String> genderCombo;
    @FXML private TextField heightField;
    @FXML private TextField weightField;
    @FXML private ComboBox<String> activityLevelCombo;
    @FXML private TextField dietaryPreferencesField;

    @FXML private Label errorLabel;

    /**
     * Initializes the view and configures combo box choices.
     */
    @FXML
    public void initialize() {
        errorLabel.setText("");

        // Gender options
        genderCombo.setItems(FXCollections.observableArrayList("Male", "Female"));
        genderCombo.getSelectionModel().select("Male");

        // Activity level options
        activityLevelCombo.setItems(FXCollections.observableArrayList(
                "Sedentary (Little or no exercise)",
                "Lightly Active (1-3 days/week)",
                "Moderately Active (3-5 days/week)",
                "Very Active (6-7 days/week)",
                "Extra Active (Very heavy physical work)"
        ));
        activityLevelCombo.getSelectionModel().select(2); // Default to Moderately Active
    }

    /**
     * Handles account creation with BMI & TDEE calculation.
     */
    @FXML
    private void handleSignUp() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // 1. Basic Account Validation
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty()
                || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill in all account credentials.");
            return;
        }

        if (username.length() < 3) {
            showError("Username must be at least 3 characters long.");
            return;
        }

        if (!email.contains("@") || !email.contains(".")) {
            showError("Please enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters long.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        // 2. Body Metrics Validation
        int age;
        try {
            age = Integer.parseInt(ageField.getText().trim());
            if (age < 10 || age > 120) {
                showError("Please enter a realistic age between 10 and 120.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Age must be a valid whole number.");
            return;
        }

        double heightCm;
        try {
            heightCm = Double.parseDouble(heightField.getText().trim());
            if (heightCm < 60 || heightCm > 260) {
                showError("Height must be between 60 cm and 260 cm.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Height must be a valid number in centimeters (e.g. 175).");
            return;
        }

        double weightKg;
        try {
            weightKg = Double.parseDouble(weightField.getText().trim());
            if (weightKg < 20 || weightKg > 350) {
                showError("Weight must be between 20 kg and 350 kg.");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Weight must be a valid number in kilograms (e.g. 70).");
            return;
        }

        // Biological Sex
        User.Gender gender = "Female".equalsIgnoreCase(genderCombo.getValue())
                ? User.Gender.FEMALE
                : User.Gender.MALE;

        // Activity Level
        int activityIndex = activityLevelCombo.getSelectionModel().getSelectedIndex();
        User.ActivityLevel activityLevel = switch (activityIndex) {
            case 0 -> User.ActivityLevel.SEDENTARY;
            case 1 -> User.ActivityLevel.LIGHTLY_ACTIVE;
            case 3 -> User.ActivityLevel.VERY_ACTIVE;
            case 4 -> User.ActivityLevel.EXTRA_ACTIVE;
            default -> User.ActivityLevel.MODERATELY_ACTIVE;
        };

        // 3. Compute BMI
        double bmi = UserService.calculateBMI(weightKg, heightCm);
        User.BMICategory bmiCategory = UserService.getBMICategory(bmi);

        // 4. Dietary preferences
        String dietaryPreferences = dietaryPreferencesField != null ? dietaryPreferencesField.getText().trim() : "";

        // 5. Delegate to AuthService
        boolean success = AuthService.signUp(
                username, email, password, firstName, lastName,
                gender, age, heightCm, weightKg, activityLevel, dietaryPreferences
        );

        if (success) {
            errorLabel.setText("");

            // Show friendly onboarding alert with BMI results
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Account Created • BMI Analysis");
            alert.setHeaderText("Welcome to LifeSync, " + firstName + "!");

            String healthyRange = UserService.getHealthyWeightRange(heightCm);
            String bmiTip = switch (bmiCategory) {
                case UNDERWEIGHT -> "Focus on nutrient-dense calorie surplus, lean protein, and strength training.";
                case NORMAL -> "Great job! Your weight is in the healthy zone. Focus on balanced nutrition and fitness variety.";
                case OVERWEIGHT -> "We've adapted your meal plans with high-satiety fiber & protein to support gradual weight control.";
                case OBESE -> "Let's take small, consistent steps with low-impact workouts and balanced, wholesome meals!";
            };

            String content = String.format(
                    "Your Body Profile has been registered:\n\n" +
                    "• Calculated BMI: %.1f (%s)\n" +
                    "• Healthy Weight Range for your height: %s\n\n" +
                    "Personalized Advice:\n%s\n\n" +
                    "LifeSync has adapted your daily goals and recipe recommendations based on your BMI. Please log in to get started!",
                    bmi, bmiCategory.getDisplayName(), healthyRange, bmiTip
            );

            alert.setContentText(content);
            alert.getDialogPane().setPrefWidth(480);
            alert.showAndWait();

            // Redirect to login
            SceneManager.showLogin();
        } else {
            showError("Username or email address is already taken.");
        }
    }

    private void showError(String message) {
        errorLabel.setText("❌ " + message);
    }

    /**
     * Navigates back to the login screen.
     */
    @FXML
    private void handleBack() {
        SceneManager.showLogin();
    }
}
