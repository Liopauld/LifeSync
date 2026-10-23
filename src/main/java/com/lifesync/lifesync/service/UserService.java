package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages user profile data and performs anthropometric, metabolic, and BMI-driven calculations.
 *
 * <h3>Key Health Calculations</h3>
 * <ul>
 *   <li><strong>BMI (Body Mass Index)</strong> — {@code weight_kg / (height_m)^2}.
 *       Used to categorize weight status (Underweight, Normal, Overweight, Obese)
 *       and dynamically personalize app recommendations.</li>
 *   <li><strong>BMR (Basal Metabolic Rate)</strong> — the baseline calories burned
 *       at complete rest, computed via the Mifflin-St Jeor equation.</li>
 *   <li><strong>TDEE (Total Daily Energy Expenditure)</strong> — BMR multiplied by the
 *       user's physical activity factor.</li>
 * </ul>
 *
 * <h3>BMI-Driven Recommendations</h3>
 * This service provides intelligent dietary and workout suggestions customized to the
 * user's current BMI category to help them progress safely toward balanced health.
 *
 * Profile updates are persisted to {@code data/users.json} via {@link DataStore}.
 */
public class UserService {

    /** JSON file where user accounts are stored. */
    private static final String USERS_FILE = "users.json";

    /** Gson type token for List<User>. */
    private static final Type USER_LIST_TYPE = new TypeToken<List<User>>() {}.getType();

    /**
     * Saves an updated user profile back to the JSON file.
     * Finds the existing record by ID and replaces it.
     *
     * @param updatedUser the user object with modified fields
     */
    public static void saveUser(User updatedUser) {
        List<User> users = DataStore.loadAll(USERS_FILE, USER_LIST_TYPE);

        boolean found = false;
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getId().equals(updatedUser.getId())) {
                users.set(i, updatedUser);
                found = true;
                break;
            }
        }

        if (!found) {
            users.add(updatedUser);
        }

        DataStore.saveAll(USERS_FILE, users);
    }

    // ── BMI Calculations ───────────────────────────────────────────────

    /**
     * Calculates Body Mass Index for a user.
     *
     * @param user user whose metrics are used
     * @return calculated BMI rounded to 1 decimal place, or 0.0 if missing data
     */
    public static double calculateBMI(User user) {
        if (user == null) return 0.0;
        return calculateBMI(user.getWeightKg(), user.getHeightCm());
    }

    /**
     * Calculates Body Mass Index given raw weight and height.
     *
     * @param weightKg weight in kilograms
     * @param heightCm height in centimeters
     * @return calculated BMI rounded to 1 decimal place
     */
    public static double calculateBMI(double weightKg, double heightCm) {
        if (weightKg <= 0 || heightCm <= 0) {
            return 0.0;
        }
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        return Math.round(bmi * 10.0) / 10.0;
    }

    /**
     * Classifies a BMI value according to World Health Organization standards.
     *
     * @param bmi numeric BMI value
     * @return {@link User.BMICategory} classification
     */
    public static User.BMICategory getBMICategory(double bmi) {
        if (bmi <= 0) return User.BMICategory.NORMAL;
        if (bmi < 18.5) return User.BMICategory.UNDERWEIGHT;
        if (bmi < 25.0) return User.BMICategory.NORMAL;
        if (bmi < 30.0) return User.BMICategory.OVERWEIGHT;
        return User.BMICategory.OBESE;
    }

    /**
     * Computes the healthy weight range (corresponding to normal BMI 18.5 – 24.9)
     * for a given height in centimeters.
     *
     * @param heightCm height in centimeters
     * @return string representation, e.g. "53.5 – 72.0 kg"
     */
    public static String getHealthyWeightRange(double heightCm) {
        if (heightCm <= 0) return "-- kg";
        double heightM = heightCm / 100.0;
        double minWeight = 18.5 * (heightM * heightM);
        double maxWeight = 24.9 * (heightM * heightM);
        return String.format("%.1f – %.1f kg", minWeight, maxWeight);
    }

    // ── BMI-Driven Recommendations ─────────────────────────────────────

    /**
     * Generates a primary AI/System health suggestion tailored to the user's BMI.
     *
     * @param user logged-in user
     * @return personalized lifestyle and nutrition tip
     */
    public static String getBMISuggestion(User user) {
        if (user == null || user.getWeightKg() <= 0 || user.getHeightCm() <= 0) {
            return "Set up your height and weight in your Profile to unlock personalized BMI-based recommendations!";
        }

        double bmi = user.getBmi();
        User.BMICategory category = user.getBmiCategory();

        String diet = (user.getDietaryPreferences() != null) ? user.getDietaryPreferences().toLowerCase().trim() : "";
        boolean isLactoseFree = diet.contains("lactose") || diet.contains("dairy") || diet.contains("vegan");
        boolean isVegetarian = diet.contains("veg");

        switch (category) {
            case UNDERWEIGHT:
                String surplusFoods = isLactoseFree
                        ? "dairy-free smoothies, avocados, peanut butter, nuts, and whole grain oats"
                        : "Greek yogurt, avocados, nuts, whole grain oats, and high-protein smoothies";
                return String.format(
                        "BMI: %.1f (Underweight). Focus on a wholesome calorie surplus. " +
                        "Add nutrient-dense calories with %s! (Diet: %s)",
                        bmi, surplusFoods, diet.isBlank() ? "Standard" : user.getDietaryPreferences().trim());
            case NORMAL:
                return String.format(
                        "BMI: %.1f (Normal Weight). Excellent work maintaining a healthy balance! " +
                        "Keep prioritizing wholesome balanced meals, colorful vegetables, and regular hydration. (Diet: %s)",
                        bmi, diet.isBlank() ? "Standard" : user.getDietaryPreferences().trim());
            case OVERWEIGHT:
                String proteinSources = isVegetarian
                        ? "tofu, lentils, and beans"
                        : "lean proteins (like Grilled Chicken and Salmon)";
                return String.format(
                        "BMI: %.1f (Overweight). Aim for a moderate, sustainable caloric deficit. " +
                        "Boost fiber and %s to stay satiated longer between meals. (Diet: %s)",
                        bmi, proteinSources, diet.isBlank() ? "Standard" : user.getDietaryPreferences().trim());
            case OBESE:
                return String.format(
                        "BMI: %.1f (Obese). Start with low-impact consistency: enjoy high-volume, " +
                        "low-calorie meals (leafy salads, clear broths) and incorporate gentle daily brisk walks. (Diet: %s)",
                        bmi, diet.isBlank() ? "Standard" : user.getDietaryPreferences().trim());
            default:
                return "Eat balanced meals and stay active today!";
        }
    }

    /**
     * Recommends specific recipe tag filters based on the user's BMI status.
     *
     * @param user logged-in user
     * @return list of priority tags to emphasize in recipe catalogs
     */
    public static List<String> getBMIRecipeTags(User user) {
        if (user == null) return new ArrayList<>(Arrays.asList("Healthy"));
        User.BMICategory category = user.getBmiCategory();

        List<String> tags = new ArrayList<>();
        switch (category) {
            case UNDERWEIGHT:
                tags.addAll(Arrays.asList("High Protein", "Breakfast", "Smoothie", "Healthy"));
                break;
            case OVERWEIGHT:
            case OBESE:
                tags.addAll(Arrays.asList("Low Carb", "High Protein", "Healthy"));
                break;
            case NORMAL:
            default:
                tags.addAll(Arrays.asList("Healthy", "High Protein", "Quick"));
                break;
        }

        if (user.getDietaryPreferences() != null && !user.getDietaryPreferences().isBlank()) {
            String diet = user.getDietaryPreferences().toLowerCase();
            if (diet.contains("lactose") || diet.contains("dairy")) {
                tags.add(0, "Lactose-Free");
            }
            if (diet.contains("vegan")) {
                tags.add(0, "Vegan");
            } else if (diet.contains("veg")) {
                tags.add(0, "Vegetarian");
            }
            if (diet.contains("gluten")) {
                tags.add(0, "Gluten Free");
            }
        }
        return tags;
    }

    /**
     * Returns tailored workout guidance based on the user's BMI category.
     *
     * @param user logged-in user
     * @return concise workout recommendation string
     */
    public static String getBMIWorkoutAdvice(User user) {
        if (user == null) return "Aim for 30 minutes of physical activity today.";
        User.BMICategory category = user.getBmiCategory();

        switch (category) {
            case UNDERWEIGHT:
                return "Focus on progressive resistance and strength training to build lean muscle mass. Keep intense cardio moderate.";
            case NORMAL:
                return "Maintain a well-rounded routine: 3-4 days of strength training coupled with 2 days of cardiovascular endurance.";
            case OVERWEIGHT:
                return "Combine full-body strength training with moderate-intensity cardio (brisk jogging, cycling, swimming) to maximize caloric expenditure.";
            case OBESE:
                return "Prioritize joint-friendly, low-impact movements: daily 20-30 min brisk walks, swimming, elliptical, and mobility yoga.";
            default:
                return "Stay consistent and celebrate every step of progress!";
        }
    }

    // ── BMR & TDEE Calculations ────────────────────────────────────────

    /**
     * Calculates Basal Metabolic Rate using the Mifflin-St Jeor equation:
     * <pre>
     *   Male:   BMR = (10 × weight_kg) + (6.25 × height_cm) − (5 × age) + 5
     *   Female: BMR = (10 × weight_kg) + (6.25 × height_cm) − (5 × age) − 161
     * </pre>
     *
     * @param user the user whose BMR to calculate
     * @return BMR in kilocalories/day, or 0 if required data is missing
     */
    public static double calculateBMR(User user) {
        if (user == null || user.getWeightKg() <= 0 || user.getHeightCm() <= 0 || user.getAge() <= 0) {
            return 0.0;
        }

        double bmr = (10 * user.getWeightKg())
                + (6.25 * user.getHeightCm())
                - (5 * user.getAge());

        if (user.getGender() == User.Gender.FEMALE) {
            bmr -= 161;
        } else {
            bmr += 5;
        }

        return bmr;
    }

    /**
     * Calculates Total Daily Energy Expenditure (TDEE).
     *
     * TDEE = BMR × activity multiplier.
     *
     * @param user the user whose TDEE to calculate
     * @return TDEE in kilocalories/day
     */
    public static double calculateTDEE(User user) {
        double bmr = calculateBMR(user);
        double multiplier = getActivityMultiplier(user.getActivityLevel());
        return bmr * multiplier;
    }

    /**
     * Returns the activity multiplier for TDEE calculation.
     */
    private static double getActivityMultiplier(User.ActivityLevel level) {
        if (level == null) return 1.2;

        return switch (level) {
            case SEDENTARY -> 1.2;
            case LIGHTLY_ACTIVE -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE -> 1.725;
            case EXTRA_ACTIVE -> 1.9;
        };
    }

    /**
     * Recalculates and updates the user's daily calorie target based on
     * their body metrics and activity level, adjusting for BMI goal if needed.
     *
     * @param user the user to update
     */
    public static void recalculateAndSave(User user) {
        if (user == null) return;
        int tdee = (int) Math.round(calculateTDEE(user));
        if (tdee > 0) {
            // Adaptive target adjustment based on BMI category:
            User.BMICategory category = user.getBmiCategory();
            if (category == User.BMICategory.UNDERWEIGHT) {
                // Slight surplus (+250 kcal) to support healthy weight gain
                user.setDailyCalorieTarget(tdee + 250);
            } else if (category == User.BMICategory.OVERWEIGHT || category == User.BMICategory.OBESE) {
                // Modest, safe deficit (-350 kcal) for gradual fat loss
                user.setDailyCalorieTarget(Math.max(1400, tdee - 350));
            } else {
                user.setDailyCalorieTarget(tdee);
            }
        }
        saveUser(user);
    }
}
