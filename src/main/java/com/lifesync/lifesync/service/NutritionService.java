package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.NutritionEntry;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates and analyzes daily nutrition intake data.
 *
 * <h3>What this service does</h3>
 * <ul>
 *   <li>CRUD operations for {@link NutritionEntry} records.</li>
 *   <li>Calculates daily totals (calories, protein, carbs, fat).</li>
 *   <li>Computes progress percentages vs. the user's daily targets.</li>
 *   <li>Counts meals logged per day.</li>
 * </ul>
 *
 * Data is persisted to {@code data/nutrition.json} via {@link DataStore}.
 */
public class NutritionService {

    /** JSON file for nutrition entry persistence. */
    private static final String NUTRITION_FILE = "nutrition.json";

    /** Gson type token for List<NutritionEntry>. */
    private static final Type ENTRY_LIST_TYPE =
            new TypeToken<List<NutritionEntry>>() {}.getType();

    /**
     * Adds a new nutrition entry and saves it.
     *
     * @param entry the nutrition entry to add
     */
    public static void addEntry(NutritionEntry entry) {
        List<NutritionEntry> all = DataStore.loadAll(NUTRITION_FILE, ENTRY_LIST_TYPE);
        all.add(entry);
        DataStore.saveAll(NUTRITION_FILE, all);
    }

    /**
     * Returns all nutrition entries for a user on a specific date.
     *
     * @param userId the user's ID
     * @param date   the date to filter (yyyy-MM-dd)
     * @return list of entries for that day
     */
    public static List<NutritionEntry> getEntriesForDate(String userId, String date) {
        List<NutritionEntry> all = DataStore.loadAll(NUTRITION_FILE, ENTRY_LIST_TYPE);
        return all.stream()
                .filter(e -> e.getUserId().equals(userId))
                .filter(e -> e.getDate().equals(date))
                .collect(Collectors.toList());
    }

    /**
     * Calculates total calories consumed on a given date.
     *
     * @param userId the user's ID
     * @param date   the date to total (yyyy-MM-dd)
     * @return total kilocalories
     */
    public static double getTotalCalories(String userId, String date) {
        return getEntriesForDate(userId, date).stream()
                .mapToDouble(NutritionEntry::getCalories)
                .sum();
    }

    /**
     * Calculates total protein consumed on a given date.
     *
     * @param userId the user's ID
     * @param date   the date (yyyy-MM-dd)
     * @return total grams of protein
     */
    public static double getTotalProtein(String userId, String date) {
        return getEntriesForDate(userId, date).stream()
                .mapToDouble(NutritionEntry::getProteinG)
                .sum();
    }

    /**
     * Calculates total carbohydrates consumed on a given date.
     *
     * @param userId the user's ID
     * @param date   the date (yyyy-MM-dd)
     * @return total grams of carbs
     */
    public static double getTotalCarbs(String userId, String date) {
        return getEntriesForDate(userId, date).stream()
                .mapToDouble(NutritionEntry::getCarbsG)
                .sum();
    }

    /**
     * Calculates total fat consumed on a given date.
     *
     * @param userId the user's ID
     * @param date   the date (yyyy-MM-dd)
     * @return total grams of fat
     */
    public static double getTotalFat(String userId, String date) {
        return getEntriesForDate(userId, date).stream()
                .mapToDouble(NutritionEntry::getFatG)
                .sum();
    }

    /**
     * Counts how many distinct meals have been logged for a date.
     * Groups by meal type (BREAKFAST, LUNCH, DINNER, SNACK).
     *
     * @param userId the user's ID
     * @param date   the date (yyyy-MM-dd)
     * @return number of distinct meal types logged (0-4)
     */
    public static long countMealsLogged(String userId, String date) {
        return getEntriesForDate(userId, date).stream()
                .map(NutritionEntry::getMealType)
                .distinct()
                .count();
    }

    /**
     * Calculates what percentage of a daily calorie target has been consumed.
     *
     * @param consumed the calories consumed so far
     * @param target   the daily calorie target
     * @return percentage (0.0 to 100.0+), capped at 100 for display purposes
     */
    public static double calorieProgressPercent(double consumed, int target) {
        if (target <= 0) return 0;
        return Math.min((consumed / target) * 100.0, 100.0);
    }
}
