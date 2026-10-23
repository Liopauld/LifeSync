package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.SceneManager;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.ThemeManager;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Themed account recovery screens shared by login, onboarding, and profile. */
final class AccountRecoveryDialogs {
    private AccountRecoveryDialogs() { }

    static void showReset(String identity) {
        Dialog<ButtonType> dialog = dialog("Reset password", "Recover your LifeSync account");
        TextField account = new TextField(identity);
        PasswordField code = new PasswordField();
        PasswordField password = new PasswordField();
        PasswordField confirmation = new PasswordField();
        Label error = errorLabel();
        VBox form = new VBox(8,
                note("Enter the recovery code you saved when creating your account or from My Profile. "
                        + "LifeSync stores accounts on this device and does not send reset emails."),
                label("Username or email", account), account,
                label("Recovery code", code), code,
                label("New password (at least 6 characters)", password), password,
                label("Confirm new password", confirmation), confirmation,
                note("No saved code? If you can still sign in, generate one in My Profile. "
                        + "An account without a saved code cannot be reset from this screen."), error);
        dialog.getDialogPane().setContent(form);
        ButtonType reset = new ButtonType("Reset password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(reset, ButtonType.CANCEL);
        Button button = (Button) dialog.getDialogPane().lookupButton(reset);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            String message = null;
            if (account.getText().isBlank() || code.getText().isBlank()) {
                message = "Enter your username or email and recovery code.";
            } else if (password.getText().isBlank() || password.getText().length() < 6) {
                message = "Password must be at least 6 characters long.";
            } else if (!password.getText().equals(confirmation.getText())) {
                message = "Passwords do not match.";
            } else {
                try {
                    if (!AuthService.resetPassword(account.getText(), code.getText(), password.getText())) {
                        message = "The account or recovery code is incorrect, unavailable, or already used.";
                    }
                } catch (RuntimeException e) {
                    message = "Could not save your password. Please check that your data folder is writable and try again.";
                }
            }
            if (message != null) {
                error.setText(message);
                event.consume();
            }
        });
        dialog.showAndWait().filter(reset::equals).ifPresent(result -> {
            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Password updated");
            success.setHeaderText("You can now sign in with your new password.");
            success.setContentText("Your recovery code has been used. Generate a new one in My Profile after signing in.");
            configure(success);
            success.showAndWait();
        });
        code.clear();
        password.clear();
        confirmation.clear();
    }

    static void showGenerateCode(String identity) {
        Dialog<ButtonType> dialog = dialog("Account recovery", "Save a recovery code before you need it");
        PasswordField password = new PasswordField();
        Label error = errorLabel();
        dialog.getDialogPane().setContent(new VBox(10,
                note("Confirm your current password to generate a code. Any previous recovery code will stop working."),
                label("Current password", password), password, error));
        ButtonType generate = new ButtonType("Generate code", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(generate, ButtonType.CANCEL);
        String[] generated = new String[1];
        dialog.getDialogPane().lookupButton(generate).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                generated[0] = AuthService.issueRecoveryCode(identity, password.getText());
                if (generated[0] == null) {
                    error.setText("Your current password is incorrect.");
                    event.consume();
                }
            } catch (RuntimeException e) {
                error.setText("Could not save the recovery code. Please check that your data folder is writable and try again.");
                event.consume();
            }
        });
        dialog.showAndWait().filter(generate::equals).ifPresent(result -> showCode(generated[0]));
        password.clear();
    }

    static void showCode(String code) {
        Dialog<ButtonType> dialog = dialog("Save your recovery code", "Keep this code somewhere private");
        TextField field = new TextField(code);
        field.setEditable(false);
        field.setAccessibleText("Your single-use recovery code. Select and copy it to a safe place.");
        dialog.getDialogPane().setContent(new VBox(12,
                note("Copy this code into a password manager or another safe place. "
                        + "You will need it if you forget your password. It is shown only now and works once."),
                field));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setOnShown(event -> { field.requestFocus(); field.selectAll(); });
        dialog.showAndWait();
        field.clear();
    }

    private static Dialog<ButtonType> dialog(String title, String heading) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(heading);
        configure(dialog);
        return dialog;
    }

    private static void configure(Dialog<?> dialog) {
        if (SceneManager.getPrimaryStage() != null) dialog.initOwner(SceneManager.getPrimaryStage());
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(480);
        ThemeManager.applyTheme(dialog.getDialogPane());
    }

    private static Label label(String text, Control field) {
        Label label = new Label(text);
        label.setLabelFor(field);
        return label;
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(440);
        return label;
    }

    private static Label errorLabel() {
        Label label = note("");
        label.getStyleClass().add("error-label");
        label.setAccessibleRole(javafx.scene.AccessibleRole.TEXT);
        return label;
    }
}
