package com.lifesync.lifesync.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic JSON-based persistence layer for LifeSync.
 *
 * All application data (users, activities, nutrition entries, pantry items,
 * recipes) is stored as JSON files inside a {@code data/} directory located
 * next to the running application.  Each entity type gets its own file
 * (e.g., {@code users.json}, {@code activities.json}).
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>{@link #loadAll(String, Type)} reads a JSON array from disk and
 *       deserializes it into a {@code List<T>} using Gson.</li>
 *   <li>{@link #saveAll(String, List)} serializes a {@code List<T>} to
 *       pretty-printed JSON and writes it to disk, overwriting the file.</li>
 *   <li>If the data directory or file doesn't exist yet, it's created
 *       automatically on first write.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * This class is <em>not</em> thread-safe.  For a single-user desktop app
 * running on the JavaFX Application Thread, that's perfectly fine.
 */
public class DataStore {

    /** Directory where all JSON data files are stored. */
    private static final String DATA_DIR = "data";

    /** Gson instance configured for pretty-printing (human-readable files). */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * Loads all items of a given type from a JSON file.
     *
     * @param filename the JSON file name (e.g., "users.json")
     * @param type     the Gson Type representing {@code List<T>},
     *                 created via {@code new TypeToken<List<MyClass>>(){}.getType()}
     * @param <T>      the element type
     * @return a mutable list of deserialized objects, or an empty list if
     *         the file doesn't exist or is empty
     */
    public static <T> List<T> loadAll(String filename, Type type) {
        Path filePath = Paths.get(DATA_DIR, filename);

        // If the file doesn't exist yet, return an empty list — no crash
        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }

        try (Reader reader = new FileReader(filePath.toFile())) {
            List<T> result = GSON.fromJson(reader, type);
            return result != null ? result : new ArrayList<>();
        } catch (IOException e) {
            // Log the error and return empty rather than crashing the app
            System.err.println("[DataStore] Error reading " + filename + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Saves all items to a JSON file, overwriting any existing content.
     *
     * @param filename the JSON file name (e.g., "activities.json")
     * @param items    the list of objects to serialize
     * @param <T>      the element type
     */
    public static <T> void saveAll(String filename, List<T> items) {
        Path dirPath = Paths.get(DATA_DIR);

        try {
            // Create the data directory if it doesn't exist
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path filePath = dirPath.resolve(filename);

            try (Writer writer = new FileWriter(filePath.toFile())) {
                GSON.toJson(items, writer);
            }
        } catch (IOException e) {
            System.err.println("[DataStore] Error writing " + filename + ": " + e.getMessage());
        }
    }
}
