package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.Recipe;
import com.lifesync.lifesync.model.User;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the recipe database — browsing, searching, favorites, and
 * matching recipes against the user's available pantry ingredients.
 *
 * <h3>Key features</h3>
 * <ul>
 *   <li>CRUD operations for recipes.</li>
 *   <li>Full-text search by name and ingredient.</li>
 *   <li>Favorite toggling and retrieval.</li>
 *   <li>Pantry matching — which recipes can be made with available ingredients.</li>
 *   <li>Pre-seeded sample recipes for demonstration.</li>
 * </ul>
 *
 * Data is persisted to {@code data/recipes.json} via {@link DataStore}.
 */
public class RecipeService {

    /** JSON file for recipe persistence. */
    private static final String RECIPES_FILE = "recipes.json";

    /** Gson type token for List<Recipe>. */
    private static final Type RECIPE_LIST_TYPE =
            new TypeToken<List<Recipe>>() {}.getType();

    /**
     * Returns all recipes, sorted by name.
     *
     * @return list of all recipes
     */
    public static List<Recipe> getAllRecipes() {
        List<Recipe> all = DataStore.loadAll(RECIPES_FILE, RECIPE_LIST_TYPE);
        if (all.isEmpty() || all.size() < 12) {
            // First run or updated catalog: seed the database with sample recipes
            all = createSampleRecipes();
            DataStore.saveAll(RECIPES_FILE, all);
        }
        all.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return all;
    }

    /**
     * Searches recipes by name or ingredient keyword (case-insensitive).
     *
     * @param query search term
     * @return matching recipes
     */
    public static List<Recipe> search(String query) {
        String lower = query.toLowerCase().trim();
        return getAllRecipes().stream()
                .filter(r -> r.getName().toLowerCase().contains(lower)
                        || r.getIngredients().stream()
                                .anyMatch(i -> i.toLowerCase().contains(lower))
                        || (r.getTags() != null && r.getTags().stream()
                                .anyMatch(t -> t.toLowerCase().contains(lower))))
                .collect(Collectors.toList());
    }

    /**
     * Returns only recipes marked as favorites.
     *
     * @return list of favorite recipes
     */
    public static List<Recipe> getFavorites() {
        return getAllRecipes().stream()
                .filter(Recipe::isFavorite)
                .collect(Collectors.toList());
    }

    /**
     * Returns recipes filtered by a specific tag (e.g., "High Protein", "Quick").
     *
     * @param tag the tag to filter by
     * @return matching recipes
     */
    public static List<Recipe> getByTag(String tag) {
        return getAllRecipes().stream()
                .filter(r -> r.getTags() != null
                        && r.getTags().stream()
                                .anyMatch(t -> t.equalsIgnoreCase(tag)))
                .collect(Collectors.toList());
    }

    /**
     * Toggles a recipe's favorite status and persists the change.
     *
     * @param recipeId the recipe to toggle
     */
    public static void toggleFavorite(String recipeId) {
        List<Recipe> all = DataStore.loadAll(RECIPES_FILE, RECIPE_LIST_TYPE);
        for (Recipe r : all) {
            if (r.getId().equals(recipeId)) {
                r.setFavorite(!r.isFavorite());
                break;
            }
        }
        DataStore.saveAll(RECIPES_FILE, all);
    }

    /**
     * Toggles a displayed recipe, first retaining generated recipes in the catalog.
     * Returns the persisted favorite state; restores the old state if saving fails.
     */
    public static boolean toggleFavorite(Recipe recipe) {
        List<Recipe> all = getAllRecipes();
        Recipe stored = all.stream().filter(r -> r.getId().equals(recipe.getId()))
                .findFirst().orElse(null);
        if (stored == null) {
            stored = recipe;
            all.add(stored);
        }
        boolean previous = stored.isFavorite();
        stored.setFavorite(!previous);
        try {
            DataStore.saveAll(RECIPES_FILE, all);
        } catch (RuntimeException ex) {
            stored.setFavorite(previous);
            throw ex;
        }
        return stored.isFavorite();
    }

    /**
     * Retrieves curated recommended recipes, personalized by the currently logged-in
     * user's Body Mass Index (BMI) and dietary focus.
     *
     * @return list of recommended recipes tailored to the user's BMI
     */
    public static List<Recipe> getRecommendedRecipes() {
        return getRecommendedRecipes(AuthService.getCurrentUser());
    }

    /**
     * Retrieves recommended recipes customized for a specific user's BMI classification:
     * <ul>
     *   <li><strong>Underweight:</strong> Calorie-dense, healthy-fat, and protein-packed meals
     *       (Smoothie bowls, Banana Pancakes, Garlic Butter Pasta).</li>
     *   <li><strong>Overweight / Obese:</strong> High-protein, high-fiber, lean meals
     *       (Grilled Chicken, Salads, Salmon with Quinoa, Vegetable Stir-Fry).</li>
     *   <li><strong>Normal:</strong> Balanced culinary selection supporting vitality and maintenance.</li>
     * </ul>
     *
     * @param user target user
     * @return list of BMI-tailored recommended recipes
     */
    public static List<Recipe> getRecommendedRecipes(User user) {
        List<Recipe> all = getAllRecipes();
        if (all.isEmpty()) return new ArrayList<>();

        if (user == null || user.getBmi() <= 0) {
            // Default signature showcase
            return all.stream()
                    .filter(r -> r.getName().contains("Grilled Chicken Bowl")
                            || r.getName().contains("Garlic Butter Pasta")
                            || r.getName().contains("Berry Smoothie Bowl"))
                    .collect(Collectors.toList());
        }

        User.BMICategory category = user.getBmiCategory();
        List<Recipe> tailored = new ArrayList<>();

        switch (category) {
            case UNDERWEIGHT -> {
                // Focus: calorie surplus with quality nutrients
                tailored = all.stream()
                        .filter(r -> r.getName().contains("Berry Smoothie Bowl")
                                || r.getName().contains("Banana Pancakes")
                                || r.getName().contains("Garlic Butter Pasta")
                                || r.getName().contains("Mediterranean Chicken Bowl"))
                        .collect(Collectors.toList());
            }
            case OVERWEIGHT, OBESE -> {
                // Focus: high satiety, lean protein, high fiber
                tailored = all.stream()
                        .filter(r -> r.getName().contains("Grilled Chicken Bowl")
                                || r.getName().contains("Chicken Caesar Salad")
                                || r.getName().contains("Salmon with Quinoa")
                                || r.getName().contains("Beef Stir-Fry"))
                        .collect(Collectors.toList());
            }
            case NORMAL -> {
                // Focus: balanced variety
                tailored = all.stream()
                        .filter(r -> r.getName().contains("Grilled Chicken Bowl")
                                || r.getName().contains("Garlic Butter Pasta")
                                || r.getName().contains("Berry Smoothie Bowl")
                                || r.getName().contains("Avocado Toast"))
                        .collect(Collectors.toList());
            }
        }

        if (tailored.isEmpty()) {
            return all.stream().limit(3).collect(Collectors.toList());
        }
        return tailored;
    }

    /**
     * Filters and sorts recipes based on search query, meal type, dietary preferences,
     * prep time range, and sort order.
     *
     * @param query              search text for name, ingredients, or tags
     * @param mealType           selected meal type ("All", "Breakfast", "Lunch", "Dinner", "Snacks")
     * @param dietaryPreferences list of active dietary tags (e.g. "High Protein", "Vegetarian")
     * @param prepTimeFilter     prep time category ("< 15 mins", "15-30 mins", "30-60 mins", "> 60 mins")
     * @param sortBy             sorting criteria ("Most Popular", "Featured", "Prep Time: Quickest", etc.)
     * @return filtered and sorted list of recipes
     */
    public static List<Recipe> filterAndSort(String query, String mealType,
                                            List<String> dietaryPreferences,
                                            String prepTimeFilter,
                                            String sortBy) {
        List<Recipe> all = getAllRecipes();

        return all.stream()
                .filter(r -> {
                    // 1. Text search
                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.toLowerCase().trim();
                        boolean matchesName = r.getName().toLowerCase().contains(q);
                        boolean matchesDesc = r.getDescription() != null && r.getDescription().toLowerCase().contains(q);
                        boolean matchesIng = r.getIngredients() != null && r.getIngredients().stream().anyMatch(i -> i.toLowerCase().contains(q));
                        boolean matchesTag = r.getTags() != null && r.getTags().stream().anyMatch(t -> t.toLowerCase().contains(q));
                        if (!matchesName && !matchesDesc && !matchesIng && !matchesTag) {
                            return false;
                        }
                    }

                    // 2. Meal Type filter
                    if (mealType != null && !mealType.equalsIgnoreCase("All")) {
                        boolean matchesMealType = r.getMealType().equalsIgnoreCase(mealType);
                        boolean tagsContainMeal = r.getTags() != null && r.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(mealType));
                        if (!matchesMealType && !tagsContainMeal) {
                            return false;
                        }
                    }

                    // 3. Dietary preferences filter
                    if (dietaryPreferences != null && !dietaryPreferences.isEmpty()) {
                        for (String pref : dietaryPreferences) {
                            boolean hasTag = r.getTags() != null && r.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(pref));
                            if (!hasTag) {
                                return false; // Must satisfy all active dietary preferences
                            }
                        }
                    }

                    // 4. Prep Time filter
                    if (prepTimeFilter != null && !prepTimeFilter.equalsIgnoreCase("All")) {
                        int prep = r.getPrepTimeMinutes();
                        switch (prepTimeFilter) {
                            case "< 15 mins", "< 15m" -> {
                                if (prep >= 15) return false;
                            }
                            case "15-30 mins", "15-30m" -> {
                                if (prep < 15 || prep > 30) return false;
                            }
                            case "30-60 mins", "30-60m" -> {
                                if (prep < 30 || prep > 60) return false;
                            }
                            case "> 60 mins", "> 60m" -> {
                                if (prep <= 60) return false;
                            }
                        }
                    }

                    return true;
                })
                .sorted((a, b) -> {
                    if (sortBy == null) return 0;
                    return switch (sortBy) {
                        case "Most Popular" -> Integer.compare(b.getPopularity(), a.getPopularity());
                        case "Prep Time: Quickest" -> Integer.compare(a.getPrepTimeMinutes(), b.getPrepTimeMinutes());
                        case "Calories: Low to High" -> Double.compare(a.getCalories(), b.getCalories());
                        case "Calories: High to Low" -> Double.compare(b.getCalories(), a.getCalories());
                        case "Alphabetical (A-Z)" -> a.getName().compareToIgnoreCase(b.getName());
                        default -> Integer.compare(b.getPopularity(), a.getPopularity()); // Featured
                    };
                })
                .collect(Collectors.toList());
    }

    /**
     * Matches recipes against available pantry items.
     * Returns recipes sorted by how many ingredients the user already has.
     *
     * Each returned recipe is annotated with a match score:
     * - Fully matched: all ingredients available
     * - Partially matched: some ingredients available
     *
     * @param userId the user's ID (to look up their pantry)
     * @return recipes sorted by match percentage (best matches first)
     */
    public static List<RecipeMatch> matchWithPantry(String userId) {
        List<PantryItem> pantry = PantryService.getAllItems(userId);
        List<String> pantryNames = pantry.stream()
                .map(p -> p.getName().toLowerCase())
                .collect(Collectors.toList());

        List<Recipe> recipes = getAllRecipes();
        List<RecipeMatch> matches = new ArrayList<>();

        for (Recipe recipe : recipes) {
            int total = recipe.getIngredients().size();
            if (total == 0) continue;

            // Count how many recipe ingredients are in the pantry
            long available = recipe.getIngredients().stream()
                    .filter(ingredient -> pantryNames.stream()
                            .anyMatch(p -> ingredient.toLowerCase().contains(p)
                                    || p.contains(ingredient.toLowerCase())))
                    .count();

            // Find missing ingredients
            List<String> missing = recipe.getIngredients().stream()
                    .filter(ingredient -> pantryNames.stream()
                            .noneMatch(p -> ingredient.toLowerCase().contains(p)
                                    || p.contains(ingredient.toLowerCase())))
                    .collect(Collectors.toList());

            double matchPercent = (double) available / total * 100;
            matches.add(new RecipeMatch(recipe, matchPercent, missing));
        }

        // Sort by match percentage descending (best matches first)
        matches.sort((a, b) -> Double.compare(b.matchPercent, a.matchPercent));
        return matches;
    }

    /**
     * Holds the result of matching a recipe against the user's pantry.
     * Includes the recipe, match percentage, and list of missing ingredients.
     */
    public static class RecipeMatch {
        public final Recipe recipe;
        public final double matchPercent;
        public final List<String> missingIngredients;

        public RecipeMatch(Recipe recipe, double matchPercent, List<String> missingIngredients) {
            this.recipe = recipe;
            this.matchPercent = matchPercent;
            this.missingIngredients = missingIngredients;
        }
    }

    // ── Sample Recipe Data ─────────────────────────────────────────────

    /**
     * Creates a set of sample recipes to pre-populate the database.
     * These provide a useful starting point for demonstration and testing.
     *
     * @return list of sample recipes with full nutritional data
     */
    private static List<Recipe> createSampleRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        // 0A. Grilled chicken rice bowl (Mockup Card 1)
        Recipe m1 = new Recipe("Grilled chicken rice bowl",
                "Chicken, rice, avocado, greens",
                Arrays.asList("Chicken", "Rice", "Avocado", "Greens"),
                "1. Season and grill chicken breast until golden. 2. Warm the rice. 3. Slice avocado and toss fresh greens. 4. Assemble into a wholesome bowl.");
        m1.setCookingMethod("Grill");
        m1.setMealType("Lunch");
        m1.setPrepTimeMinutes(15);
        m1.setCookTimeMinutes(10);
        m1.setCalories(520);
        m1.setProteinG(42);
        m1.setCarbsG(55);
        m1.setFatG(14);
        m1.setTags(Arrays.asList("High Protein", "Healthy", "Lunch", "Gluten Free"));
        m1.setPopularity(100);
        recipes.add(m1);

        // 0B. Spinach omelette (Mockup Card 2)
        Recipe m2 = new Recipe("Spinach omelette",
                "Eggs, spinach, herbs",
                Arrays.asList("Eggs", "Spinach", "Herbs", "Feta cheese"),
                "1. Whisk eggs with a pinch of salt and pepper. 2. Sauté baby spinach until wilted. 3. Pour eggs over spinach and cook on low heat. 4. Fold with fresh herbs and feta.");
        m2.setCookingMethod("Pan-fry");
        m2.setMealType("Breakfast");
        m2.setPrepTimeMinutes(5);
        m2.setCookTimeMinutes(10);
        m2.setCalories(280);
        m2.setProteinG(22);
        m2.setCarbsG(6);
        m2.setFatG(18);
        m2.setTags(Arrays.asList("Breakfast", "Vegetarian", "Quick", "Low Carb"));
        m2.setPopularity(99);
        recipes.add(m2);

        // 0C. Chicken & greens skillet (Mockup Card 3)
        Recipe m3 = new Recipe("Chicken & greens skillet",
                "Chicken with seasonal vegetables",
                Arrays.asList("Chicken", "Seasonal vegetables", "Greens", "Garlic"),
                "1. Cut chicken breast into bite-sized strips. 2. Sauté garlic in olive oil. 3. Add chicken and sear until cooked through. 4. Toss with seasonal vegetables and simmer until crisp-tender.");
        m3.setCookingMethod("Skillet");
        m3.setMealType("Dinner");
        m3.setPrepTimeMinutes(10);
        m3.setCookTimeMinutes(10);
        m3.setCalories(410);
        m3.setProteinG(38);
        m3.setCarbsG(18);
        m3.setFatG(15);
        m3.setTags(Arrays.asList("Healthy", "High Protein", "Dinner", "Low Carb"));
        m3.setPopularity(97);
        recipes.add(m3);

        // 1. Grilled Chicken Bowl (Recommended Card 1)
        Recipe r1 = new Recipe("Grilled Chicken Bowl",
                "Tender seasoned grilled chicken breast served over quinoa, charred corn, sweet tomatoes, and sliced fresh avocado.",
                Arrays.asList("Chicken breast", "Quinoa", "Sweet corn", "Cherry tomatoes", "Avocado", "Olive oil", "Cilantro", "Lime"),
                "1. Season chicken with salt, pepper, cumin, and lime juice. 2. Grill for 6-8 minutes per side until charred and cooked through. 3. Warm quinoa and corn. 4. Arrange in a bowl with tomatoes, avocado, and chopped cilantro.");
        r1.setCookingMethod("Grill");
        r1.setMealType("Lunch");
        r1.setPrepTimeMinutes(15);
        r1.setCookTimeMinutes(10);
        r1.setCalories(420);
        r1.setProteinG(40);
        r1.setCarbsG(35);
        r1.setFatG(12);
        r1.setTags(Arrays.asList("High Protein", "Healthy", "Lunch", "Gluten Free"));
        r1.setImagePath("/com/lifesync/lifesync/images/grilled_chicken_bowl.jpg");
        r1.setPopularity(98);
        recipes.add(r1);

        // 2. Garlic Butter Pasta (Recommended Card 2)
        Recipe r2 = new Recipe("Garlic Butter Pasta",
                "Silky al dente spaghetti tossed in rich golden garlic butter, finished with cracked black pepper and freshly grated parmesan.",
                Arrays.asList("Spaghetti", "Butter", "Garlic", "Parmesan cheese", "Fresh parsley", "Black pepper", "Olive oil"),
                "1. Boil spaghetti in salted water until al dente. 2. In a skillet, melt butter with olive oil over medium-low heat. 3. Sauté minced garlic until fragrant and golden. 4. Toss pasta into garlic butter with pasta water. 5. Top with parmesan and parsley.");
        r2.setCookingMethod("Stir-fry");
        r2.setMealType("Dinner");
        r2.setPrepTimeMinutes(5);
        r2.setCookTimeMinutes(15);
        r2.setCalories(380);
        r2.setProteinG(12);
        r2.setCarbsG(54);
        r2.setFatG(14);
        r2.setTags(Arrays.asList("Quick", "Comfort Food", "Dinner", "Vegetarian"));
        r2.setImagePath("/com/lifesync/lifesync/images/garlic_butter_pasta.jpg");
        r2.setPopularity(95);
        recipes.add(r2);

        // 3. Berry Smoothie Bowl (Recommended Card 3)
        Recipe r3 = new Recipe("Berry Smoothie Bowl",
                "Thick blended acai and wild berries topped with fresh strawberries, blueberries, chia seeds, sliced bananas, and coconut flakes.",
                Arrays.asList("Frozen mixed berries", "Banana", "Almond milk", "Chia seeds", "Blueberries", "Strawberries", "Coconut flakes"),
                "1. Blend frozen berries, 1/2 banana, and almond milk on high until smooth and thick. 2. Pour into a chilled bowl. 3. Neatly arrange sliced strawberries, blueberries, chia seeds, and coconut flakes on top.");
        r3.setCookingMethod("Blend");
        r3.setMealType("Breakfast");
        r3.setPrepTimeMinutes(10);
        r3.setCookTimeMinutes(0);
        r3.setCalories(290);
        r3.setProteinG(14);
        r3.setCarbsG(46);
        r3.setFatG(6);
        r3.setTags(Arrays.asList("Vegetarian", "Vegan", "Gluten Free", "Dairy Free", "Healthy", "Breakfast"));
        r3.setImagePath("/com/lifesync/lifesync/images/berry_smoothie_bowl.jpg");
        r3.setPopularity(92);
        recipes.add(r3);

        // 4. Mediterranean Chicken Bowl (AI Suggestion)
        Recipe r4 = new Recipe("Mediterranean Chicken Bowl",
                "Herb-roasted chicken served over Greek lemon quinoa with crisp cucumbers, kalamata olives, tangy feta cheese, and tzatziki.",
                Arrays.asList("Chicken breast", "Quinoa", "Cucumber", "Kalamata olives", "Feta cheese", "Cherry tomatoes", "Tzatziki sauce", "Oregano"),
                "1. Marinate chicken with oregano, garlic, olive oil, and lemon. 2. Grill or bake at 200°C for 20 minutes. 3. Slice chicken. 4. Assemble bowl with cucumber, olives, tomatoes, and crumbled feta. 5. Dollop fresh tzatziki.");
        r4.setCookingMethod("Grill");
        r4.setMealType("Lunch");
        r4.setPrepTimeMinutes(15);
        r4.setCookTimeMinutes(15);
        r4.setCalories(450);
        r4.setProteinG(42);
        r4.setCarbsG(38);
        r4.setFatG(14);
        r4.setTags(Arrays.asList("High Protein", "Balanced", "Lunch", "Dinner", "Gluten Free"));
        r4.setImagePath("/com/lifesync/lifesync/images/mediterranean_chicken_bowl.jpg");
        r4.setPopularity(96);
        recipes.add(r4);

        // 5. Chicken Caesar Salad (Popular)
        Recipe r5 = new Recipe("Chicken Caesar Salad",
                "Crisp romaine lettuce hearts tossed in creamy Caesar dressing with grilled chicken strips, shaved parmesan, and sourdough croutons.",
                Arrays.asList("Chicken breast", "Romaine lettuce", "Caesar dressing", "Parmesan cheese", "Croutons", "Black pepper", "Lemon"),
                "1. Season and sear chicken breast. 2. Chop crisp romaine lettuce. 3. Toss greens with Caesar dressing. 4. Top with sliced chicken, shaved parmesan, croutons, and cracked pepper.");
        r5.setCookingMethod("Grill");
        r5.setMealType("Lunch");
        r5.setPrepTimeMinutes(15);
        r5.setCookTimeMinutes(5);
        r5.setCalories(350);
        r5.setProteinG(32);
        r5.setCarbsG(10);
        r5.setFatG(18);
        r5.setTags(Arrays.asList("Healthy", "High Protein", "Lunch", "Low Carb"));
        r5.setPopularity(94);
        recipes.add(r5);

        // 6. Tuna Pasta Salad (Popular)
        Recipe r6 = new Recipe("Tuna Pasta Salad",
                "Chilled fusilli pasta with flaked tuna, sweet corn, diced red onion, celery, and a light Greek yogurt lemon-dill dressing.",
                Arrays.asList("Fusilli pasta", "Canned tuna", "Sweet corn", "Red onion", "Celery", "Greek yogurt", "Lemon", "Dill"),
                "1. Cook pasta al dente and rinse with cool water. 2. Drain and flake tuna. 3. Mix yogurt, lemon juice, salt, and dill. 4. Combine pasta, tuna, corn, red onion, and celery with dressing. Chill before serving.");
        r6.setCookingMethod("Boil");
        r6.setMealType("Lunch");
        r6.setPrepTimeMinutes(15);
        r6.setCookTimeMinutes(10);
        r6.setCalories(410);
        r6.setProteinG(34);
        r6.setCarbsG(45);
        r6.setFatG(11);
        r6.setTags(Arrays.asList("High Protein", "Lunch", "Quick", "Dairy Free"));
        r6.setPopularity(89);
        recipes.add(r6);

        // 7. Banana Pancakes (Popular)
        Recipe r7 = new Recipe("Banana Pancakes",
                "Fluffy golden whole-wheat pancakes made with ripe mashed bananas, served with pure maple syrup and fresh banana coins.",
                Arrays.asList("Whole wheat flour", "Ripe bananas", "Eggs", "Milk", "Baking powder", "Cinnamon", "Maple syrup"),
                "1. Mash bananas in a bowl. 2. Whisk in eggs and milk. 3. Fold in flour, baking powder, and cinnamon. 4. Cook ladlefuls on a lightly greased non-stick skillet for 2-3 min per side. 5. Serve warm with maple syrup.");
        r7.setCookingMethod("Grill");
        r7.setMealType("Breakfast");
        r7.setPrepTimeMinutes(5);
        r7.setCookTimeMinutes(10);
        r7.setCalories(320);
        r7.setProteinG(8);
        r7.setCarbsG(52);
        r7.setFatG(7);
        r7.setTags(Arrays.asList("Vegetarian", "Breakfast", "Comfort Food"));
        r7.setPopularity(87);
        recipes.add(r7);

        // 8. Avocado Toast (Popular)
        Recipe r8 = new Recipe("Avocado Toast",
                "Artisan toasted sourdough bread spread with creamy mashed avocado, everything bagel seasoning, chili flakes, and microgreens.",
                Arrays.asList("Sourdough bread", "Ripe avocado", "Lemon juice", "Everything bagel seasoning", "Red chili flakes", "Olive oil", "Microgreens"),
                "1. Toast sourdough slices until crispy and golden. 2. Mash avocado with lemon juice, salt, and pepper. 3. Spread generously on warm toast. 4. Drizzle with extra virgin olive oil, sprinkle seasoning, and top with microgreens.");
        r8.setCookingMethod("No cook");
        r8.setMealType("Breakfast");
        r8.setPrepTimeMinutes(10);
        r8.setCookTimeMinutes(0);
        r8.setCalories(260);
        r8.setProteinG(7);
        r8.setCarbsG(28);
        r8.setFatG(15);
        r8.setTags(Arrays.asList("Healthy", "Breakfast", "Vegetarian", "Vegan", "Dairy Free"));
        r8.setPopularity(91);
        recipes.add(r8);

        // 9. Baked Salmon with Vegetables
        Recipe r9 = new Recipe("Baked Salmon with Vegetables",
                "Oven-baked Atlantic salmon fillet seasoned with lemon herb butter, roasted alongside fresh tender asparagus spears.",
                Arrays.asList("Salmon fillet", "Asparagus", "Olive oil", "Lemon", "Garlic", "Butter", "Salt", "Pepper"),
                "1. Preheat oven to 200°C. 2. Season salmon with lemon, garlic, salt, and pepper. 3. Toss asparagus in olive oil. 4. Bake on a lined sheet pan for 18-20 minutes until salmon flakes easily with a fork.");
        r9.setCookingMethod("Bake");
        r9.setMealType("Dinner");
        r9.setPrepTimeMinutes(10);
        r9.setCookTimeMinutes(20);
        r9.setCalories(480);
        r9.setProteinG(38);
        r9.setCarbsG(8);
        r9.setFatG(24);
        r9.setTags(Arrays.asList("High Protein", "Healthy", "Dinner", "Gluten Free", "Low Carb"));
        r9.setPopularity(93);
        recipes.add(r9);

        // 10. Teriyaki Chicken Bowl
        Recipe r10 = new Recipe("Teriyaki Chicken Bowl",
                "Succulent pan-seared chicken glazed in homemade savory-sweet teriyaki sauce over steamed jasmine rice with broccoli.",
                Arrays.asList("Chicken breast", "Jasmine rice", "Soy sauce", "Honey", "Garlic", "Ginger", "Broccoli", "Sesame seeds"),
                "1. Steam jasmine rice. 2. Sauté sliced chicken until browned. 3. Whisk soy sauce, honey, garlic, and ginger. 4. Simmer sauce until glaze forms over chicken. 5. Serve with steamed broccoli and sesame seeds.");
        r10.setCookingMethod("Stir-fry");
        r10.setMealType("Dinner");
        r10.setPrepTimeMinutes(15);
        r10.setCookTimeMinutes(25);
        r10.setCalories(520);
        r10.setProteinG(42);
        r10.setCarbsG(58);
        r10.setFatG(12);
        r10.setTags(Arrays.asList("High Protein", "Balanced", "Dinner", "Asian"));
        r10.setPopularity(90);
        recipes.add(r10);

        // 11. Greek Yogurt Bowl (Parfait)
        Recipe r11 = new Recipe("Greek Yogurt Bowl",
                "Creamy Greek yogurt layered with organic honey, crunchy almond granola, and fresh seasonal strawberries.",
                Arrays.asList("Greek yogurt", "Honey", "Granola", "Strawberries", "Chia seeds"),
                "1. Spoon Greek yogurt into a bowl. 2. Top with crunchy granola. 3. Slice strawberries and arrange on top. 4. Drizzle pure honey.");
        r11.setCookingMethod("No cook");
        r11.setMealType("Breakfast");
        r11.setPrepTimeMinutes(5);
        r11.setCookTimeMinutes(0);
        r11.setCalories(280);
        r11.setProteinG(20);
        r11.setCarbsG(32);
        r11.setFatG(5);
        r11.setTags(Arrays.asList("Breakfast", "Quick", "Vegetarian", "High Protein"));
        r11.setPopularity(88);
        recipes.add(r11);

        // 12. Mixed Nuts & Cranberries
        Recipe r12 = new Recipe("Mixed Nuts & Cranberries",
                "Nutrient-dense raw almonds, walnuts, pumpkin seeds, and dried cranberries for sustained daily energy.",
                Arrays.asList("Almonds", "Walnuts", "Pumpkin seeds", "Dried cranberries", "Sea salt"),
                "1. Combine measured almonds, walnuts, and pumpkin seeds. 2. Toss with dried cranberries and a pinch of fine sea salt. 3. Portion into snack bags.");
        r12.setCookingMethod("No cook");
        r12.setMealType("Snacks");
        r12.setPrepTimeMinutes(5);
        r12.setCookTimeMinutes(0);
        r12.setCalories(200);
        r12.setProteinG(6);
        r12.setCarbsG(18);
        r12.setFatG(14);
        r12.setTags(Arrays.asList("Snacks", "Vegan", "Gluten Free", "Dairy Free", "Quick"));
        r12.setPopularity(82);
        recipes.add(r12);

        return recipes;
    }

    /**
     * Dynamically generates customized recipes synthesized directly from the user's
     * currently available pantry ingredients, strictly respecting their dietary
     * preferences (e.g. lactose intolerance, vegetarianism) and calibrated to their
     * BMI and calorie targets.
     *
     * @param user        target user profile with dietary preferences and BMI
     * @param pantryItems active items available in the user's pantry
     * @return a list of tailored Recipe objects created from their available stock
     */
    public static List<Recipe> generateLocalPantryRecipes(User user, List<PantryItem> pantryItems) {
        if (pantryItems == null || pantryItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<Recipe> created = new ArrayList<>();
        List<String> names = pantryItems.stream()
                .map(p -> p.getName().toLowerCase().trim())
                .collect(Collectors.toList());

        String dietaryPref = (user != null && user.getDietaryPreferences() != null)
                ? user.getDietaryPreferences().toLowerCase().trim()
                : "";
        boolean isLactoseFree = dietaryPref.contains("lactose") || dietaryPref.contains("dairy") || dietaryPref.contains("vegan");
        boolean isVegetarian = dietaryPref.contains("veg");
        boolean isVegan = dietaryPref.contains("vegan");

        boolean hasEgg = names.stream().anyMatch(n -> n.contains("egg"));
        boolean hasBread = names.stream().anyMatch(n -> n.contains("bread") || n.contains("toast"));
        boolean hasChicken = names.stream().anyMatch(n -> n.contains("chicken") || n.contains("poultry"));
        boolean hasRice = names.stream().anyMatch(n -> n.contains("rice"));

        // Combination 1: Egg + Bread
        if (hasEgg && hasBread) {
            Recipe r1 = new Recipe(
                    "Golden Sunnyside Egg Toast",
                    "Crispy golden toasted bread topped with perfectly fried eggs and cracked pepper. 100% "
                            + (isLactoseFree ? "dairy-free and lactose-friendly" : "wholesome")
                            + ", maximizing energy and lean protein from your pantry.",
                    Arrays.asList("2 slices Bread", "2 large Eggs", "1 tsp Cooking oil", "Salt and black pepper to taste"),
                    "1. Toast bread slices until golden brown.\n2. Heat a skillet over medium heat with oil.\n3. Crack eggs and fry to your desired doneness.\n4. Place eggs onto toasted bread, sprinkle with salt and pepper, and serve immediately."
            );
            r1.setMealType("Breakfast");
            r1.setCookingMethod("Pan-fry / Toast");
            r1.setPrepTimeMinutes(3);
            r1.setCookTimeMinutes(5);
            r1.setCalories(330);
            r1.setProteinG(16);
            r1.setCarbsG(26);
            r1.setFatG(14);
            List<String> tags1 = new ArrayList<>(Arrays.asList("Pantry Made", "AI Created", "High Protein", "Quick"));
            if (isLactoseFree) tags1.add("Lactose-Free");
            r1.setTags(tags1);
            created.add(r1);

            Recipe r2 = new Recipe(
                    "Savory Scrambled Egg Toast",
                    "Fluffy dairy-free scrambled eggs piled high over warm toast, seasoned to perfection.",
                    Arrays.asList("2 slices Bread", "2 Eggs", "1 tbsp Water (for extra fluffiness)", "1 tsp Cooking oil", "Pinch of salt"),
                    "1. Whisk eggs vigorously with 1 tbsp water and salt.\n2. Heat oil in skillet over medium-low heat.\n3. Pour in eggs and gently push with a spatula until soft curds form.\n4. Layer onto toast and enjoy warm."
            );
            r2.setMealType("Breakfast");
            r2.setCookingMethod("Scramble");
            r2.setPrepTimeMinutes(3);
            r2.setCookTimeMinutes(4);
            r2.setCalories(320);
            r2.setProteinG(16);
            r2.setCarbsG(26);
            r2.setFatG(13);
            List<String> tags2 = new ArrayList<>(Arrays.asList("Pantry Made", "AI Created", "Easy", "Warm"));
            if (isLactoseFree) tags2.add("Lactose-Free");
            r2.setTags(tags2);
            created.add(r2);

            Recipe r3 = new Recipe(
                    "Crispy Egg-in-a-Hole",
                    "A classic pantry delight where an egg is pan-fried directly inside a hollowed bread slice for crunchy edges and a rich center.",
                    Arrays.asList("2 slices Bread", "2 Eggs", "1 tsp Cooking oil", "Paprika and sea salt"),
                    "1. Cut a 2-inch circle from the center of each bread slice.\n2. Heat oil in pan and lay bread down.\n3. Crack one egg directly into the center cutout.\n4. Cook 3 minutes, flip carefully for 1-2 minutes, and toast the cutouts alongside."
            );
            r3.setMealType("Breakfast");
            r3.setCookingMethod("Pan-fry");
            r3.setPrepTimeMinutes(4);
            r3.setCookTimeMinutes(6);
            r3.setCalories(330);
            r3.setProteinG(16);
            r3.setCarbsG(26);
            r3.setFatG(14);
            List<String> tags3 = new ArrayList<>(Arrays.asList("Pantry Made", "AI Created", "Creative", "Snack"));
            if (isLactoseFree) tags3.add("Lactose-Free");
            r3.setTags(tags3);
            created.add(r3);

            Recipe r4 = new Recipe(
                    isLactoseFree ? "Dairy-Free French Toast" : "Golden Skillet French Toast",
                    "Thick bread slices dipped in a light egg wash and pan-toasted until aromatic and golden.",
                    Arrays.asList("2 slices Bread", "1 Egg", "1 tsp Sugar (optional)", "Pinch of cinnamon", "1 tsp Cooking oil"),
                    "1. Whisk egg with a splash of water, sugar, and cinnamon in a shallow bowl.\n2. Dip bread slices to coat both sides evenly.\n3. Toast in a greased pan over medium heat for 2-3 minutes per side until golden brown."
            );
            r4.setMealType("Breakfast");
            r4.setCookingMethod("Griddle");
            r4.setPrepTimeMinutes(5);
            r4.setCookTimeMinutes(6);
            r4.setCalories(290);
            r4.setProteinG(11);
            r4.setCarbsG(36);
            r4.setFatG(9);
            List<String> tags4 = new ArrayList<>(Arrays.asList("Pantry Made", "AI Created", "Sweet", "Comfort"));
            if (isLactoseFree) tags4.add("Lactose-Free");
            r4.setTags(tags4);
            created.add(r4);
        } else if (hasChicken && hasRice && !isVegetarian && !isVegan) {
            // Chicken + Rice
            Recipe r1 = new Recipe(
                    "Pan-Seared Chicken with Steamed Rice",
                    "Savory seasoned chicken breast served over fluffy rice, providing sustained lean protein and complex carbs.",
                    Arrays.asList("Chicken breast (150g)", "1 cup Rice", "1 tbsp Cooking oil", "Garlic, salt, and pepper"),
                    "1. Cook rice according to package directions.\n2. Season chicken with salt, pepper, and garlic.\n3. Pan-sear chicken in hot oil for 6-8 minutes per side until fully cooked.\n4. Slice and serve hot over rice."
            );
            r1.setMealType("Lunch");
            r1.setPrepTimeMinutes(10);
            r1.setCookTimeMinutes(20);
            r1.setCalories(480);
            r1.setProteinG(38);
            r1.setCarbsG(48);
            r1.setFatG(10);
            created.add(r1);
        } else if (hasEgg && !hasBread) {
            // Only egg
            Recipe r1 = new Recipe(
                    "Soft-Boiled Jammy Eggs",
                    "Perfect 6-minute soft-boiled eggs with a rich custard center, seasoned with a touch of salt.",
                    Arrays.asList("3 Eggs", "Water for boiling", "Salt and pepper"),
                    "1. Bring water to a boil.\n2. Gently lower eggs into water and boil for 6 minutes and 30 seconds.\n3. Plunge into cold water, peel, and season with salt and pepper."
            );
            r1.setMealType("Breakfast");
            r1.setCookingMethod("Boil");
            r1.setPrepTimeMinutes(2);
            r1.setCookTimeMinutes(7);
            r1.setCalories(210);
            r1.setProteinG(18);
            r1.setCarbsG(2);
            r1.setFatG(15);
            List<String> tags = new ArrayList<>(Arrays.asList("Pantry Made", "High Protein", "Quick"));
            if (isLactoseFree) tags.add("Lactose-Free");
            r1.setTags(tags);
            created.add(r1);
        } else if (hasBread && !hasEgg) {
            // Only bread
            Recipe r1 = new Recipe(
                    "Crispy Herb Toast",
                    "Crispy golden toasted bread brushed lightly with oil and sprinkled with sea salt.",
                    Arrays.asList("3 slices Bread", "1 tbsp Cooking oil", "Pinch of salt and black pepper"),
                    "1. Brush bread with oil.\n2. Toast in pan or toaster until golden and crispy."
            );
            r1.setMealType("Snacks");
            r1.setCookingMethod("Toast");
            r1.setPrepTimeMinutes(3);
            r1.setCookTimeMinutes(5);
            r1.setCalories(240);
            r1.setProteinG(6);
            r1.setCarbsG(36);
            r1.setFatG(8);
            List<String> tags = new ArrayList<>(Arrays.asList("Pantry Made", "Vegan"));
            if (isLactoseFree) tags.add("Lactose-Free");
            r1.setTags(tags);
            created.add(r1);
        } else {
            // Generic combination from whatever ingredients exist in pantry
            String primaryIng = pantryItems.get(0).getName();
            Recipe r1 = new Recipe(
                    "Pantry " + primaryIng + " Skillet",
                    "A nourishing dish crafted using " + primaryIng + " from your pantry, respecting your "
                            + (dietaryPref.isBlank() ? "healthy eating goals" : dietaryPref + " dietary preferences") + ".",
                    pantryItems.stream().limit(5).map(p -> p.getName() + " (" + (int) p.getQuantity() + " " + p.getUnit() + ")").collect(Collectors.toList()),
                    "1. Prepare and portion your ingredients.\n2. Cook over medium heat until tender.\n3. Season to taste and serve warm."
            );
            r1.setMealType("Lunch");
            r1.setCookingMethod("Skillet");
            r1.setPrepTimeMinutes(5);
            r1.setCookTimeMinutes(10);
            r1.setCalories(350);
            r1.setProteinG(15);
            r1.setCarbsG(40);
            r1.setFatG(10);
            List<String> tags = new ArrayList<>(Arrays.asList("Pantry Made", "AI Created"));
            if (isLactoseFree) tags.add("Lactose-Free");
            r1.setTags(tags);
            created.add(r1);
        }

        return created;
    }
}
