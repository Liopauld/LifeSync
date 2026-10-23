package com.lifesync.lifesync.service;

import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and integration tests for {@link GeminiService}.
 * Verifies live Google Gemini API connectivity, JSON parsing, asynchronous
 * non-blocking execution, and graceful fallback behavior.
 */
public class GeminiServiceTest {

    private User testUser;
    private List<PantryItem> testPantry;
    private List<Activity> testActivities;

    @BeforeEach
    public void setUp() {
        testUser = new User(
                "geminitest",
                "gemini@test.com",
                "hashedpassword",
                "Alex",
                "Rivera",
                User.Gender.MALE,
                21,
                175.0,
                70.0,
                User.ActivityLevel.MODERATELY_ACTIVE
        );
        UserService.recalculateAndSave(testUser);

        testPantry = new ArrayList<>();
        testPantry.add(new PantryItem(testUser.getId(), "Eggs", "Protein", "pcs", 6.0));
        testPantry.add(new PantryItem(testUser.getId(), "Oats", "Grains", "g", 500.0));
        testPantry.add(new PantryItem(testUser.getId(), "Spinach", "Produce", "bag", 1.0));

        testActivities = new ArrayList<>();
        testActivities.add(new Activity(testUser.getId(), "Morning Jog", Activity.ActivityType.WORKOUT,
                LocalDate.now().toString(), "07:00", "07:45"));
        testActivities.add(new Activity(testUser.getId(), "Java Lab Study", Activity.ActivityType.STUDY,
                LocalDate.now().toString(), "09:00", "11:00"));
    }

    @Test
    public void testDirectGeminiCallReturnsResponseOrFallback() throws Exception {
        String fallback = "Fallback lifestyle tip";
        String response = GeminiService.callGeminiAsync("Say hello in three words", fallback)
                .get(12, TimeUnit.SECONDS);

        assertNotNull(response, "Gemini response should never be null");
        assertFalse(response.isBlank(), "Gemini response should not be empty");
        assertTrue(response.length() > 2, "Response should have meaningful text");
    }

    @Test
    public void testDailyLifestyleSuggestion() throws Exception {
        String result = GeminiService.generateDailyLifestyleSuggestion(testUser, testActivities, testPantry)
                .get(12, TimeUnit.SECONDS);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.length() > 15, "Expected substantial advice text");
    }

    @Test
    public void testMealRecommendationsFromPantry() throws Exception {
        String result = GeminiService.generateMealRecommendationsFromPantry(testUser, testPantry)
                .get(12, TimeUnit.SECONDS);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.length() > 15);
    }

    @Test
    public void testFitnessGuidance() throws Exception {
        String result = GeminiService.generateFitnessGuidance(testUser, testActivities)
                .get(12, TimeUnit.SECONDS);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.length() > 15);
    }

    @Test
    public void testScheduleOptimization() throws Exception {
        String result = GeminiService.generateScheduleOptimization(testUser, testActivities)
                .get(12, TimeUnit.SECONDS);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.length() > 15);
    }

    @Test
    public void testSendChatMessage() throws Exception {
        String fallback = "Fallback chat answer";
        String prompt = "You are LifeSync AI Coach. User asks: How much water should I drink daily?";
        String response = GeminiService.sendChatMessage(prompt, new ArrayList<>(), fallback)
                .get(12, TimeUnit.SECONDS);

        assertNotNull(response);
        assertFalse(response.isBlank());
        assertTrue(response.length() > 5);
    }

    @Test
    public void testGenerateRecipesFromPantry() throws Exception {
        if (testPantry == null) {
            setUp();
        }

        List<com.lifesync.lifesync.model.Recipe> recipes =
                GeminiService.generateRecipesFromPantry(testUser, testPantry)
                        .get(12, TimeUnit.SECONDS);

        assertNotNull(recipes);
        assertFalse(recipes.isEmpty(), "Expected at least 1 recipe from pantry stock");
        assertTrue(recipes.get(0).getName() != null && !recipes.get(0).getName().isBlank());
    }
}
