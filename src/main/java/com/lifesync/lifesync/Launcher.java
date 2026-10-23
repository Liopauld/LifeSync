package com.lifesync.lifesync;

import javafx.application.Application;

/**
 * Launcher class that serves as the true entry point of the application.
 *
 * <h3>Why a separate Launcher?</h3>
 * JavaFX applications packaged with the module system require the main class
 * to NOT extend {@link Application} directly when launched from a standard
 * {@code public static void main}.  This workaround is the standard pattern:
 * the Launcher delegates to {@link LifeSyncApplication} via
 * {@link Application#launch(Class, String...)}.
 */
public class Launcher {

    /**
     * Program entry point.
     * Delegates to the JavaFX Application lifecycle.
     *
     * @param args command-line arguments (passed through to JavaFX)
     */
    public static void main(String[] args) {
        Application.launch(LifeSyncApplication.class, args);
    }
}
