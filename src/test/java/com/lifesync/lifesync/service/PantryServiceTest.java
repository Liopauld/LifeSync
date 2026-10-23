package com.lifesync.lifesync.service;

import com.lifesync.lifesync.model.PantryItem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PantryServiceTest {
    @TempDir Path directory;
    private String previousDirectory;

    @BeforeEach
    void setUp() {
        previousDirectory = System.getProperty("lifesync.data.dir");
        System.setProperty("lifesync.data.dir", directory.toString());
    }

    @AfterEach
    void tearDown() {
        if (previousDirectory == null) System.clearProperty("lifesync.data.dir");
        else System.setProperty("lifesync.data.dir", previousDirectory);
    }

    @Test
    void validStockAndZeroThresholdSurviveDiskReload() {
        PantryItem item = new PantryItem("user", "Oats", "Grains", "g", 0);
        item.setLowThreshold(0);
        PantryService.addItem(item);
        PantryItem reloaded = PantryService.getAllItems("user").getFirst();
        assertEquals(0, reloaded.getQuantity());
        assertEquals(0, reloaded.getLowThreshold());
        assertEquals(item.getId(), reloaded.getId());
    }

    @Test
    void invalidQuantitiesAreRejectedWithoutPersistingAnything() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            PantryItem item = new PantryItem("user", "Oats", "Grains", "g", invalid);
            assertThrows(IllegalArgumentException.class, () -> PantryService.addItem(item));
        }
        assertTrue(PantryService.getAllItems("user").isEmpty());
    }

    @Test
    void invalidThresholdsAreRejected() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            PantryItem item = new PantryItem("user", "Oats", "Grains", "g", 100);
            item.setLowThreshold(invalid);
            assertThrows(IllegalArgumentException.class, () -> PantryService.addItem(item));
        }
        assertTrue(PantryService.getAllItems("user").isEmpty());
    }

    @Test
    void invalidEditPreservesExistingStockOnDisk() {
        PantryItem item = new PantryItem("user", "Oats", "Grains", "g", 100);
        PantryService.addItem(item);
        item.setQuantity(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> PantryService.updateItem(item));
        assertEquals(100, PantryService.getAllItems("user").getFirst().getQuantity());
    }

    @Test
    void unnamedIngredientsAreRejected() {
        PantryItem item = new PantryItem("user", "  ", "Grains", "g", 100);
        assertThrows(IllegalArgumentException.class, () -> PantryService.addItem(item));
        assertThrows(IllegalArgumentException.class, () -> PantryService.addItem(null));
    }
}
