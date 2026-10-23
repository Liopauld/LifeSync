package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataStoreTest {
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
    void utf8DataSurvivesReplacementWithoutTemporaryFiles() throws Exception {
        var type = new TypeToken<List<String>>() { }.getType();
        DataStore.saveAll("values.json", List.of("old"));
        DataStore.saveAll("values.json", List.of("健康", "José", "🌱"));
        assertEquals(List.of("健康", "José", "🌱"), DataStore.loadAll("values.json", type));
        try (var paths = Files.list(directory)) {
            assertEquals(List.of("values.json"), paths.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void writeFailuresAreReportedInsteadOfClaimingSuccess() throws Exception {
        Path occupied = directory.resolve("occupied");
        Files.writeString(occupied, "keep this data");
        System.setProperty("lifesync.data.dir", occupied.toString());
        assertThrows(UncheckedIOException.class, () -> DataStore.saveAll("values.json", List.of("new")));
        assertEquals("keep this data", Files.readString(occupied));
    }

    @Test
    void unreadableStoreIsNotTreatedAsAnEmptyAccountList() throws Exception {
        Files.createDirectory(directory.resolve("users.json"));
        assertThrows(RuntimeException.class, () -> DataStore.loadAll("users.json",
                new TypeToken<List<String>>() { }.getType()));
    }
}
