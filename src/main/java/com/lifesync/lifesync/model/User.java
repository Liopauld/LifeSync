package com.lifesync.lifesync.model;

import java.util.UUID;

/**
 * Represents a registered user of the LifeSync application.
 *
 * Stores personal identity data (name, email, credentials), anthropometric body
 * metrics (age, biological sex, height in cm, weight in kg), lifestyle activity levels,
 * dietary preferences, and computed nutrition targets.
 *
 * <h3>BMI & BMR Integration</h3>
 * Body Mass Index (BMI) and Basal Metabolic Rate (BMR) are calculated directly from
 * the user's height, weight, age, and sex. These metrics drive app-wide personalization:
 * <ul>
 *   <li>Personalized AI suggestions on the Home dashboard</li>
 *   <li>Calorie and macronutrient recommendations in Meals & Recipes</li>
 *   <li>Recommended fitness activities in the Activity tracker</li>
 * </ul>
 *
 * Persisted as JSON in {@code data/users.json} via {@link com.lifesync.lifesync.service.DataStore}.
 */
public class User {

    // ── Enums ──────────────────────────────────────────────────────────

    /** Physical activity level used in TDEE (Total Daily Energy Expenditure) calculation. */
    public enum ActivityLevel {
        SEDENTARY,          // Little or no exercise (desk job)
        LIGHTLY_ACTIVE,     // Light exercise/sports 1-3 days/week
        MODERATELY_ACTIVE,  // Moderate exercise/sports 3-5 days/week
        VERY_ACTIVE,        // Hard exercise/sports 6-7 days/week
        EXTRA_ACTIVE        // Very hard exercise + physical job
    }

    /** Biological sex, required for Mifflin-St Jeor BMR calculation. */
    public enum Gender {
        MALE, FEMALE
    }

    /** World Health Organization standard Body Mass Index classifications. */
    public enum BMICategory {
        UNDERWEIGHT("Underweight", "#42A5F5"),     // BMI < 18.5
        NORMAL("Normal Weight", "#4CAF50"),       // 18.5 <= BMI < 25.0
        OVERWEIGHT("Overweight", "#FFA726"),       // 25.0 <= BMI < 30.0
        OBESE("Obese", "#EF5350");                 // BMI >= 30.0

        private final String displayName;
        private final String colorHex;

        BMICategory(String displayName, String colorHex) {
            this.displayName = displayName;
            this.colorHex = colorHex;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorHex() {
            return colorHex;
        }
    }

    // ── Fields ─────────────────────────────────────────────────────────

    private String id;              // Unique identifier (UUID)
    private String username;        // Login username (unique)
    private String email;           // Email address (unique)
    private String passwordHash;    // SHA-256 hashed password

    private String firstName;       // Display first name
    private String lastName;        // Display last name
    private Gender gender;          // Biological sex for BMR calc
    private int age;                // Age in years
    private double heightCm;        // Height in centimeters (e.g. 175.0)
    private double weightKg;        // Weight in kilograms (e.g. 70.0)

    private ActivityLevel activityLevel;   // Physical activity tier
    private String dietaryPreferences;     // e.g. "Vegetarian", "High Protein", etc.
    private int dailyCalorieTarget;        // Computed or customized kcal goal
    private String createdDate;            // ISO-8601 date string of account creation

    // ── Constructors ───────────────────────────────────────────────────

    /** No-arg constructor required by Gson deserialization. */
    public User() {
        this.id = UUID.randomUUID().toString();
        this.gender = Gender.MALE;
        this.activityLevel = ActivityLevel.SEDENTARY;
        this.dailyCalorieTarget = 2000;
        this.createdDate = java.time.LocalDate.now().toString();
    }

    /**
     * Creates a new user with minimum sign-up fields (default metrics).
     */
    public User(String username, String email, String passwordHash,
                String firstName, String lastName) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = Gender.MALE;
        this.age = 20;
        this.heightCm = 170.0;
        this.weightKg = 65.0;
        this.activityLevel = ActivityLevel.MODERATELY_ACTIVE;
        this.dailyCalorieTarget = 2000;
        this.createdDate = java.time.LocalDate.now().toString();
    }

    /**
     * Creates a new user with full anthropometric and lifestyle profile upon sign-up.
     */
    public User(String username, String email, String passwordHash,
                String firstName, String lastName, Gender gender,
                int age, double heightCm, double weightKg, ActivityLevel activityLevel) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender != null ? gender : Gender.MALE;
        this.age = age > 0 ? age : 20;
        this.heightCm = heightCm > 0 ? heightCm : 170.0;
        this.weightKg = weightKg > 0 ? weightKg : 65.0;
        this.activityLevel = activityLevel != null ? activityLevel : ActivityLevel.MODERATELY_ACTIVE;
        this.createdDate = java.time.LocalDate.now().toString();
        // Calorie target computed automatically later
        this.dailyCalorieTarget = 2000;
    }

    // ── BMI Calculation Helpers ────────────────────────────────────────

    /**
     * Computes the Body Mass Index (BMI) using:
     * <pre>
     *   BMI = weight (kg) / (height (m))^2
     * </pre>
     *
     * @return BMI value rounded to 1 decimal place, or 0.0 if metrics are missing
     */
    public double getBmi() {
        if (heightCm <= 0 || weightKg <= 0) {
            return 0.0;
        }
        double heightM = heightCm / 100.0;
        double bmi = weightKg / (heightM * heightM);
        return Math.round(bmi * 10.0) / 10.0;
    }

    /**
     * Categorizes the user's BMI according to WHO international cut-offs.
     *
     * @return {@link BMICategory} classification
     */
    public BMICategory getBmiCategory() {
        double bmi = getBmi();
        if (bmi <= 0) return BMICategory.NORMAL;
        if (bmi < 18.5) return BMICategory.UNDERWEIGHT;
        if (bmi < 25.0) return BMICategory.NORMAL;
        if (bmi < 30.0) return BMICategory.OVERWEIGHT;
        return BMICategory.OBESE;
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public double getHeightCm() { return heightCm; }
    public void setHeightCm(double heightCm) { this.heightCm = heightCm; }

    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }

    public ActivityLevel getActivityLevel() { return activityLevel; }
    public void setActivityLevel(ActivityLevel activityLevel) { this.activityLevel = activityLevel; }

    public String getDietaryPreferences() { return dietaryPreferences; }
    public void setDietaryPreferences(String dietaryPreferences) { this.dietaryPreferences = dietaryPreferences; }

    public int getDailyCalorieTarget() { return dailyCalorieTarget; }
    public void setDailyCalorieTarget(int dailyCalorieTarget) { this.dailyCalorieTarget = dailyCalorieTarget; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    /** Convenience: returns "FirstName LastName". */
    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    @Override
    public String toString() {
        return "User{" + username + ", " + getFullName().trim() + ", BMI=" + getBmi() + "}";
    }
}
