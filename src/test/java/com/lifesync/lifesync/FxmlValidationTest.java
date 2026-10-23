package com.lifesync.lifesync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all FXML files in the resources directory are well-formed XML
 * and contain no syntax, character encoding, or tag hierarchy errors.
 */
class FxmlValidationTest {

    private static final String FXML_DIR = "src/main/resources/com/lifesync/lifesync/fxml";

    @Test
    @DisplayName("All FXML views parse successfully as well-formed XML")
    void testAllFxmlFilesParse() throws Exception {
        File dir = new File(FXML_DIR);
        assertTrue(dir.exists() && dir.isDirectory(), "FXML directory must exist");

        File[] fxmlFiles = dir.listFiles((d, name) -> name.endsWith(".fxml"));
        assertNotNull(fxmlFiles, "FXML file list should not be null");
        assertTrue(fxmlFiles.length > 0, "There should be at least one FXML file");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        for (File fxmlFile : fxmlFiles) {
            try (InputStream in = new FileInputStream(fxmlFile)) {
                assertDoesNotThrow(() -> builder.parse(in),
                        "FXML file " + fxmlFile.getName() + " should be valid XML");
            }
        }
    }
}
