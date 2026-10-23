package com.lifesync.lifesync.service;

import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.Recipe;
import com.lifesync.lifesync.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the RecipeService business logic:
 * retrieving all recipes, recommended recipes, filtering, and favorite toggles.
 */
public class RecipeServiceTest {

    @BeforeEach
    public void setUp() {
        // Ensure recipes are loaded
        RecipeService.getAllRecipes();
    }

    @Test
    public void testGetAllRecipesNotEmpty() {
        List<Recipe> recipes = RecipeService.getAllRecipes();
        assertNotNull(recipes, "Recipes list should not be null");
        assertTrue(recipes.size() >= 12, "Should contain at least 12 recipes matching the UI mockup");
    }

    @Test
    public void testGetRecommendedRecipes() {
        List<Recipe> recommended = RecipeService.getRecommendedRecipes();
        assertNotNull(recommended, "Recommended recipes should not be null");
        assertFalse(recommended.isEmpty(), "Recommended recipes should have items");

        boolean hasChickenBowl = recommended.stream().anyMatch(r -> r.getName().contains("Grilled Chicken Bowl"));
        boolean hasGarlicPasta = recommended.stream().anyMatch(r -> r.getName().contains("Garlic Butter Pasta"));
        boolean hasSmoothie = recommended.stream().anyMatch(r -> r.getName().contains("Berry Smoothie Bowl"));

        assertTrue(hasChickenBowl, "Should recommend Grilled Chicken Bowl");
        assertTrue(hasGarlicPasta, "Should recommend Garlic Butter Pasta");
        assertTrue(hasSmoothie, "Should recommend Berry Smoothie Bowl");
    }

    @Test
    public void testFilterByMealType() {
        List<Recipe> breakfasts = RecipeService.filterAndSort("", "Breakfast", null, "All", "Most Popular");
        assertFalse(breakfasts.isEmpty(), "Should find breakfast meals");
        for (Recipe r : breakfasts) {
            boolean isBreakfast = r.getMealType().equalsIgnoreCase("Breakfast")
                    || (r.getTags() != null && r.getTags().stream().anyMatch(t -> t.equalsIgnoreCase("Breakfast")));
            assertTrue(isBreakfast, "All returned recipes should be for Breakfast");
        }
    }

    @Test
    public void testFilterByDietaryPreference() {
        List<Recipe> vegetarian = RecipeService.filterAndSort("", "All", Arrays.asList("Vegetarian"), "All", "Most Popular");
        assertFalse(vegetarian.isEmpty(), "Should find vegetarian recipes");
        for (Recipe r : vegetarian) {
            assertTrue(r.getTags().contains("Vegetarian"), "Recipe should have Vegetarian tag: " + r.getName());
        }
    }

    @Test
    public void testFilterByPrepTime() {
        List<Recipe> quickRecipes = RecipeService.filterAndSort("", "All", null, "< 15 mins", "Prep Time: Quickest");
        assertFalse(quickRecipes.isEmpty(), "Should find quick recipes");
        for (Recipe r : quickRecipes) {
            assertTrue(r.getPrepTimeMinutes() < 15, "Prep time should be < 15 min");
        }
    }

    @Test
    public void testToggleFavorite() {
        List<Recipe> all = RecipeService.getAllRecipes();
        Recipe target = all.get(0);
        boolean initial = target.isFavorite();

        RecipeService.toggleFavorite(target.getId());
        List<Recipe> reloaded = RecipeService.getAllRecipes();
        Recipe updated = reloaded.stream().filter(r -> r.getId().equals(target.getId())).findFirst().orElseThrow();
        assertEquals(!initial, updated.isFavorite(), "Favorite status should be toggled");

        // Toggle back to restore
        RecipeService.toggleFavorite(target.getId());
    }

    @Test
    public void testGenerateLocalPantryRecipesWithEggAndBread() {
        User user = new User("tester", "test@test.com", "hash", "Test", "User");
        user.setDietaryPreferences("lactose intolerent");

        List<PantryItem> pantry = Arrays.asList(
                new PantryItem(user.getId(), "Egg", "Dairy & Eggs", "pieces", 10),
                new PantryItem(user.getId(), "Bread", "Grains", "pieces", 10)
        );

        List<Recipe> recipes = RecipeService.generateLocalPantryRecipes(user, pantry);
        assertNotNull(recipes, "Generated recipes should not be null");
        assertFalse(recipes.isEmpty(), "Should generate recipes from egg and bread");
        assertEquals(4, recipes.size(), "Should produce 4 egg & bread recipe variations");

        for (Recipe r : recipes) {
            assertTrue(r.getTags().contains("Lactose-Free"), "Recipe must be tagged Lactose-Free");
            assertTrue(r.getTags().contains("Pantry Made"), "Recipe must be tagged Pantry Made");
            for (String ing : r.getIngredients()) {
                assertFalse(ing.toLowerCase().contains("milk"), "Must not contain milk");
                assertFalse(ing.toLowerCase().contains("cheese"), "Must not contain cheese");
                assertFalse(ing.toLowerCase().contains("butter"), "Must not contain dairy butter");
            }
        }
    }

    @Test
    public void testGenerateLocalPantryRecipesEmptyPantry() {
        User user = new User("tester", "test@test.com", "hash", "Test", "User");
        List<Recipe> recipes = RecipeService.generateLocalPantryRecipes(user, new ArrayList<>());
        assertNotNull(recipes);
        assertTrue(recipes.isEmpty(), "Empty pantry should produce empty list");
    }
}
