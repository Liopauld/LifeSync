package com.lifesync.lifesync.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ThemeManager}.
 * Verifies theme state toggling, listener callbacks, and persistence.
 */
class ThemeManagerTest {

    private boolean originalState;

    @BeforeEach
    void setUp() {
        originalState = ThemeManager.isDarkMode();
    }

    @AfterEach
    void tearDown() {
        ThemeManager.setDarkMode(originalState);
    }

    @Test
    @DisplayName("setDarkMode updates state correctly")
    void testSetDarkMode() {
        ThemeManager.setDarkMode(true);
        assertTrue(ThemeManager.isDarkMode(), "ThemeManager should report dark mode as true");

        ThemeManager.setDarkMode(false);
        assertFalse(ThemeManager.isDarkMode(), "ThemeManager should report dark mode as false");
    }

    @Test
    @DisplayName("toggleTheme inverts theme state")
    void testToggleTheme() {
        ThemeManager.setDarkMode(false);
        assertFalse(ThemeManager.isDarkMode());

        ThemeManager.toggleTheme();
        assertTrue(ThemeManager.isDarkMode(), "ThemeManager should be dark after toggle");

        ThemeManager.toggleTheme();
        assertFalse(ThemeManager.isDarkMode(), "ThemeManager should be light after second toggle");
    }

    @Test
    @DisplayName("ThemeChangeListener is notified on theme changes")
    void testListenerNotification() {
        AtomicBoolean receivedState = new AtomicBoolean(false);
        AtomicInteger callCount = new AtomicInteger(0);

        ThemeManager.ThemeChangeListener listener = isDark -> {
            receivedState.set(isDark);
            callCount.incrementAndGet();
        };

        ThemeManager.addListener(listener);

        try {
            ThemeManager.setDarkMode(true);
            assertEquals(1, callCount.get(), "Listener should be called once");
            assertTrue(receivedState.get(), "Listener should receive true");

            ThemeManager.toggleTheme();
            assertEquals(2, callCount.get(), "Listener should be called twice");
            assertFalse(receivedState.get(), "Listener should receive false");
        } finally {
            ThemeManager.removeListener(listener);
        }

        // After removal, listener should no longer receive calls
        ThemeManager.setDarkMode(true);
        assertEquals(2, callCount.get(), "Removed listener should not be called again");
    }

    @Test
    @DisplayName("Theme preference is saved to disk")
    void testPersistence() {
        ThemeManager.setDarkMode(true);
        File file = DataStore.dataDirectory().resolve("theme.json").toFile();
        assertTrue(file.exists(), "theme.json file should exist after setting dark mode");

        ThemeManager.setDarkMode(false);
        assertTrue(file.exists(), "theme.json file should still exist");
    }
}
