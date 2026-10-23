package com.lifesync.lifesync.service;

import com.lifesync.lifesync.model.NutritionEntry;
import com.lifesync.lifesync.model.Recipe;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Regression evidence for the Java requirements supplied in the proposal screenshots. */
class RequirementCoverageTest {
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

    private void entry(String user, String date, double calories, double protein, double carbs, double fat) {
        NutritionService.addEntry(new NutritionEntry(user, date, NutritionEntry.MealType.LUNCH,
                "Test food", calories, protein, carbs, fat));
    }

    @Test
    void matrixPreservesAllFourNutrientColumnsAcrossRows() {
        entry("user", "2026-10-24", 420, 35, 40, 12);
        entry("user", "2026-10-24", 180, 10, 20, 8);
        double[][] matrix = NutritionService.getDailyNutritionMatrix("user", "2026-10-24");
        assertEquals(2, matrix.length);
        assertArrayEquals(new double[]{420, 35, 40, 12}, matrix[0]);
        assertArrayEquals(new double[]{180, 10, 20, 8}, matrix[1]);
    }

    @Test
    void matrixIsScopedToTheRequestedAccountAndDay() {
        entry("user", "2026-10-24", 420, 35, 40, 12);
        entry("other", "2026-10-24", 999, 99, 99, 99);
        entry("user", "2026-10-25", 999, 99, 99, 99);
        assertEquals(1, NutritionService.getDailyNutritionMatrix("user", "2026-10-24").length);
        assertEquals(0, NutritionService.getDailyNutritionMatrix("user", "2026-10-26").length);
    }

    @Test
    void matrixColumnAggregationAgreesWithExistingTotals() {
        entry("user", "2026-10-24", 420, 35, 40, 12);
        entry("user", "2026-10-24", 180, 10, 20, 8);
        double[] totals = new double[NutritionService.NUTRIENT_COLUMNS];
        for (double[] row : NutritionService.getDailyNutritionMatrix("user", "2026-10-24")) {
            for (int column = 0; column < totals.length; column++) totals[column] += row[column];
        }
        assertEquals(NutritionService.getTotalCalories("user", "2026-10-24"), totals[NutritionService.CALORIES_COLUMN]);
        assertEquals(NutritionService.getTotalProtein("user", "2026-10-24"), totals[NutritionService.PROTEIN_COLUMN]);
        assertEquals(NutritionService.getTotalCarbs("user", "2026-10-24"), totals[NutritionService.CARBS_COLUMN]);
        assertEquals(NutritionService.getTotalFat("user", "2026-10-24"), totals[NutritionService.FAT_COLUMN]);
    }

    @Test
    void changingReturnedMatrixCannotModifySavedNutrition() {
        entry("user", "2026-10-24", 420, 35, 40, 12);
        double[][] matrix = NutritionService.getDailyNutritionMatrix("user", "2026-10-24");
        matrix[0][0] = 0;
        assertEquals(420, NutritionService.getTotalCalories("user", "2026-10-24"));
    }

    @Test
    void generatedRecipeFavoriteSurvivesCatalogReload() {
        Recipe generated = new Recipe("Generated oats", "Pantry recipe", List.of("Oats"), "Cook oats.");
        assertTrue(RecipeService.toggleFavorite(generated));
        assertTrue(RecipeService.getFavorites().stream().anyMatch(r -> r.getId().equals(generated.getId())));
    }

    @Test
    void generatedRecipeCanBeUnfavoritedWithoutDuplicatingIt() {
        Recipe generated = new Recipe("Generated oats", "Pantry recipe", List.of("Oats"), "Cook oats.");
        RecipeService.toggleFavorite(generated);
        Recipe loaded = RecipeService.getFavorites().stream().filter(r -> r.getId().equals(generated.getId())).findFirst().orElseThrow();
        assertFalse(RecipeService.toggleFavorite(loaded));
        assertFalse(RecipeService.getFavorites().stream().anyMatch(r -> r.getId().equals(generated.getId())));
        assertEquals(1, RecipeService.getAllRecipes().stream().filter(r -> r.getId().equals(generated.getId())).count());
    }

    @Test
    void catalogRecipeToggleRetainsOtherRecipes() {
        List<Recipe> before = RecipeService.getAllRecipes();
        Recipe recipe = before.getFirst();
        boolean previous = recipe.isFavorite();
        assertEquals(!previous, RecipeService.toggleFavorite(recipe));
        assertEquals(before.size(), RecipeService.getAllRecipes().size());
    }
}
