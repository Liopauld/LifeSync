package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.SceneManager;
import com.lifesync.lifesync.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Controller for the login screen (login-view.fxml).
 *
 * Handles user authentication by validating input fields and
 * delegating credential verification to AuthService. On successful
 * login, navigates to the main dashboard. On failure, displays
 * an error message to the user.
 */
public class LoginController {

    /** Text field for entering username or email address. */
    @FXML private TextField usernameField;

    /** Password field for entering the user's password. */
    @FXML private PasswordField passwordField;

    /** Checkbox to remember the user's login session. */
    @FXML private CheckBox rememberMeCheckbox;

    /** Label used to display validation or authentication error messages. */
    @FXML private Label errorLabel;

    /**
     * Called automatically after FXML fields are injected.
     * Clears any previous error messages.
     */
    @FXML
    public void initialize() {
        errorLabel.setText("");
    }

    /**
     * Handles the Login button click.
     * Validates that fields are not empty, then attempts authentication
     * via {@link AuthService#login(String, String)}.
     * Shows the main dashboard on success, or an error message on failure.
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Input validation — ensure neither field is blank
        if (username.isEmpty()) {
            errorLabel.setText("Please enter your username or email.");
            return;
        }
        if (password.isEmpty()) {
            errorLabel.setText("Please enter your password.");
            return;
        }

        // Attempt authentication through the auth service
        if (AuthService.login(username, password)) {
            errorLabel.setText("");
            SceneManager.showMain();
        } else {
            errorLabel.setText("Invalid username or password. Please try again.");
            passwordField.clear();
        }
    }

    /**
     * Navigates to the sign-up screen when the user clicks "Sign up".
     */
    @FXML
    private void handleSignUp() {
        SceneManager.showSignUp();
    }

    /**
     * Opens the themed password recovery form using a saved single-use code.
     */
    @FXML
    private void handleForgotPassword() {
        AccountRecoveryDialogs.showReset(usernameField.getText().trim());
    }
}
