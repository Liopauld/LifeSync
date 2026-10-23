package com.lifesync.lifesync.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a recipe in the LifeSync recipe database.
 *
 * A recipe contains a list of required ingredients, preparation/cooking
 * instructions, nutritional information, and tags for categorization.
 * The recipe matching system (Phase 2) will compare these ingredients
 * against the user's pantry to find cookable meals.
 *
 * Users can mark recipes as favorites, which feeds into the
 * AI-assisted recommendation context.
 */
public class Recipe {

    // ── Fields ─────────────────────────────────────────────────────────

    private String id;                   // Unique identifier (UUID)
    private String name;                 // Recipe name, e.g. "Teriyaki Chicken Bowl"
    private String description;          // Brief description of the dish
    private List<String> ingredients;    // List of required ingredients with amounts
    private String instructions;         // Step-by-step cooking instructions
    private String cookingMethod;        // e.g. "Stir-fry", "Bake", "Grill"
    private int prepTimeMinutes;         // Preparation time in minutes
    private int cookTimeMinutes;         // Cooking time in minutes

    // Nutritional information per serving
    private double calories;             // kcal per serving
    private double proteinG;             // grams of protein per serving
    private double carbsG;               // grams of carbs per serving
    private double fatG;                 // grams of fat per serving

    private List<String> tags;           // Categorical tags, e.g. "High Protein", "Quick"
    private boolean favorite;            // Whether the user has marked this as a favorite
    private String mealType;             // e.g. "Breakfast", "Lunch", "Dinner", "Snacks"
    private String imagePath;            // Resource path to recipe image, e.g. "/com/lifesync/lifesync/images/grilled_chicken_bowl.jpg"
    private int popularity;              // Popularity rating for sorting (e.g. 1-100)

    // ── Constructors ───────────────────────────────────────────────────

    /** No-arg constructor required by Gson deserialization. */
    public Recipe() {
        this.id = UUID.randomUUID().toString();
        this.ingredients = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.mealType = "Lunch";
        this.popularity = 80;
    }

    /**
     * Creates a recipe with the basic information.
     *
     * @param name        recipe display name
     * @param description brief summary
     * @param ingredients list of ingredients with amounts
     * @param instructions cooking directions
     */
    public Recipe(String name, String description,
                  List<String> ingredients, String instructions) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.description = description;
        this.ingredients = ingredients != null ? ingredients : new ArrayList<>();
        this.instructions = instructions;
        this.tags = new ArrayList<>();
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getIngredients() { return ingredients; }
    public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getCookingMethod() { return cookingMethod; }
    public void setCookingMethod(String cookingMethod) { this.cookingMethod = cookingMethod; }

    public int getPrepTimeMinutes() { return prepTimeMinutes; }
    public void setPrepTimeMinutes(int prepTimeMinutes) { this.prepTimeMinutes = prepTimeMinutes; }

    public int getCookTimeMinutes() { return cookTimeMinutes; }
    public void setCookTimeMinutes(int cookTimeMinutes) { this.cookTimeMinutes = cookTimeMinutes; }

    public double getCalories() { return calories; }
    public void setCalories(double calories) { this.calories = calories; }

    public double getProteinG() { return proteinG; }
    public void setProteinG(double proteinG) { this.proteinG = proteinG; }

    public double getCarbsG() { return carbsG; }
    public void setCarbsG(double carbsG) { this.carbsG = carbsG; }

    public double getFatG() { return fatG; }
    public void setFatG(double fatG) { this.fatG = fatG; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    public String getMealType() { return mealType != null ? mealType : "Lunch"; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public int getPopularity() { return popularity; }
    public void setPopularity(int popularity) { this.popularity = popularity; }

    /** Total time = preparation + cooking. */
    public int getTotalTimeMinutes() {
        return prepTimeMinutes + cookTimeMinutes;
    }

    @Override
    public String toString() {
        return "Recipe{" + name + " (" + getTotalTimeMinutes() + " min)}";
    }
}
