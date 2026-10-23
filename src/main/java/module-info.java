/**
 * Module descriptor for the LifeSync application.
 *
 * Declares all required JavaFX and third-party modules, and opens
 * internal packages to the FXML loader and Gson reflection so that
 * controllers and model classes can be instantiated at runtime.
 *
 * Unused template libraries (controlsfx, formsfx, validatorfx,
 * bootstrapfx, tilesfx, javafx-web, javafx-swing) have been removed
 * to avoid module resolution errors on JDK 27+.
 */
module com.lifesync.lifesync {
    // --- JavaFX core modules ---
    requires javafx.controls;
    requires javafx.fxml;

    // --- Icon packs ---
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    // --- JSON persistence ---
    requires com.google.gson;

    // --- HTTP Client for Gemini AI API ---
    requires java.net.http;

    // --- XML processing ---
    requires java.xml;

    // --- Open packages for reflection (FXML + Gson) ---
    opens com.lifesync.lifesync to javafx.fxml;
    opens com.lifesync.lifesync.controller to javafx.fxml;
    opens com.lifesync.lifesync.model to com.google.gson, javafx.base;
    opens com.lifesync.lifesync.service to com.google.gson;

    // --- Export the root package so the launcher can access it ---
    exports com.lifesync.lifesync;
}
