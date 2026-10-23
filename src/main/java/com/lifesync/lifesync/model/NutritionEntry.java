package com.lifesync.lifesync.model;

import java.util.UUID;

/**
 * Represents a single food intake record for nutrition tracking.
 *
 * Each entry logs what the user ate for a particular meal on a given day,
 * along with its macronutrient breakdown (calories, protein, carbs, fat).
 * Multiple entries per day are expected — one per food item or meal.
 *
 * The {@link com.lifesync.lifesync.service.NutritionService} aggregates
 * these entries to show daily totals and compare against the user's targets.
 */
public class NutritionEntry {

    // ── Enums ──────────────────────────────────────────────────────────

    /** Which meal of the day this entry belongs to. */
    public enum MealType {
        BREAKFAST,
        LUNCH,
        DINNER,
        SNACK
    }

    // ── Fields ─────────────────────────────────────────────────────────

    private String id;          // Unique identifier (UUID)
    private String userId;      // Owner of this entry (references User.id)
    private String date;        // ISO date string (yyyy-MM-dd)
    private MealType mealType;  // Meal category
    private String foodName;    // Description of what was eaten
    private double calories;    // Total kilocalories
    private double proteinG;    // Grams of protein
    private double carbsG;      // Grams of carbohydrates
    private double fatG;        // Grams of fat

    // ── Constructors ───────────────────────────────────────────────────

    /** No-arg constructor required by Gson deserialization. */
    public NutritionEntry() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Creates a complete nutrition entry.
     *
     * @param userId   owner's user ID
     * @param date     date of the meal (yyyy-MM-dd)
     * @param mealType which meal this belongs to
     * @param foodName description of the food
     * @param calories total kcal
     * @param proteinG grams of protein
     * @param carbsG   grams of carbs
     * @param fatG     grams of fat
     */
    public NutritionEntry(String userId, String date, MealType mealType,
                          String foodName, double calories,
                          double proteinG, double carbsG, double fatG) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.date = date;
        this.mealType = mealType;
        this.foodName = foodName;
        this.calories = calories;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public MealType getMealType() { return mealType; }
    public void setMealType(MealType mealType) { this.mealType = mealType; }

    public String getFoodName() { return foodName; }
    public void setFoodName(String foodName) { this.foodName = foodName; }

    public double getCalories() { return calories; }
    public void setCalories(double calories) { this.calories = calories; }

    public double getProteinG() { return proteinG; }
    public void setProteinG(double proteinG) { this.proteinG = proteinG; }

    public double getCarbsG() { return carbsG; }
    public void setCarbsG(double carbsG) { this.carbsG = carbsG; }

    public double getFatG() { return fatG; }
    public void setFatG(double fatG) { this.fatG = fatG; }

    @Override
    public String toString() {
        return "NutritionEntry{" + foodName + " (" + mealType + ") " + calories + " kcal}";
    }
}
