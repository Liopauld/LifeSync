package com.lifesync.lifesync.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.Recipe;
import com.lifesync.lifesync.model.User;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service providing Google Gemini AI-driven personalized lifestyle recommendations
 * for the LifeSync desktop application.
 *
 * <p>The service synthesizes multi-dimensional user context — including anthropometric
 * metrics (BMI, BMR, TDEE), on-hand pantry ingredient stock, daily scheduled activities,
 * and nutrition targets — to generate context-aware, motivational recommendations.</p>
 *
 * <p>All network calls are executed asynchronously using {@link java.net.http.HttpClient}
 * and return {@link CompletableFuture} to ensure the JavaFX Application Thread remains
 * 100% fluid and responsive. If network latency, invalid keys, or connectivity failures
 * occur, the service seamlessly falls back to the deterministic local rules engine in
 * {@link UserService} so the application remains fully functional offline.</p>
 *
 * @author LifeSync Development Team
 * @version 3.0 (Phase 3 - Gemini AI Integration)
 */
public class GeminiService {

    /**
     * Google Gemini API key provided for the LifeSync university team project.
     */
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    /**
     * Primary Google Gemini model deployed for low-latency desktop AI interactions.
     */
    private static final String MODEL_NAME = "gemini-3.5-flash-lite";

    /**
     * REST endpoint URL for generating content with the configured Gemini model.
     */
    private static final String ENDPOINT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + API_KEY;

    /**
     * Shared HTTP client configured with a connection timeout.
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    /**
     * Shared Gson instance for serialization and deserialization.
     */
    private static final Gson GSON = new Gson();

    /**
     * Maximum duration to await an AI response before gracefully falling back.
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Private constructor to prevent instantiation of this static utility service.
     */
    private GeminiService() {
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Asynchronous Network Invocation
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Asynchronously sends a prompt to the Google Gemini API and extracts the generated text response.
     *
     * <p>If any exception occurs (e.g. network timeout, DNS failure, non-200 HTTP response,
     * or malformed JSON), this method catches the error, logs a diagnostic message to standard err,
     * and returns the specified {@code fallbackText} without failing the calling thread.</p>
     *
     * @param prompt       the prompt text to send to Gemini
     * @param fallbackText the safe default text to return if the API is unreachable
     * @return a {@link CompletableFuture} containing the AI text response or fallback text
     */
    public static CompletableFuture<String> callGeminiAsync(String prompt, String fallbackText) {
        try {
            // Build the JSON payload: {"contents":[{"parts":[{"text":"..."}]}]}
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);

            JsonArray partsArray = new JsonArray();
            partsArray.add(textPart);

            JsonObject contentObj = new JsonObject();
            contentObj.add("parts", partsArray);

            JsonArray contentsArray = new JsonArray();
            contentsArray.add(contentObj);

            JsonObject requestBody = new JsonObject();
            requestBody.add("contents", contentsArray);

            String jsonPayload = GSON.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            String parsed = extractCandidateText(response.body());
                            if (parsed != null && !parsed.isBlank()) {
                                return parsed.trim();
                            }
                        } else {
                            System.err.println("[LifeSync GeminiService] Non-200 status code: " + response.statusCode()
                                    + " body: " + response.body());
                        }
                        return fallbackText;
                    })
                    .exceptionally(throwable -> {
                        System.err.println("[LifeSync GeminiService] Request error: " + throwable.getMessage());
                        return fallbackText;
                    });
        } catch (Exception e) {
            System.err.println("[LifeSync GeminiService] Failed to dispatch request: " + e.getMessage());
            return CompletableFuture.completedFuture(fallbackText);
        }
    }

    /**
     * Extracts the generated response text from the Gemini JSON response payload.
     *
     * @param responseJson the raw JSON response body
     * @return the extracted candidate text, or {@code null} if parsing fails
     */
    private static String extractCandidateText(String responseJson) {
        try {
            JsonObject root = JsonParser.parseString(responseJson).getAsJsonObject();
            if (root.has("candidates")) {
                JsonArray candidates = root.getAsJsonArray("candidates");
                if (candidates.size() > 0) {
                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                    if (firstCandidate.has("content")) {
                        JsonObject content = firstCandidate.getAsJsonObject("content");
                        if (content.has("parts")) {
                            JsonArray parts = content.getAsJsonArray("parts");
                            if (parts.size() > 0) {
                                JsonElement textElem = parts.get(0).getAsJsonObject().get("text");
                                if (textElem != null) {
                                    return textElem.getAsString();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[LifeSync GeminiService] JSON parse exception: " + ex.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Domain-Specific Lifestyle Recommendations
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generates a holistic daily lifestyle tip for the Home Dashboard combining the user's
     * current BMI category, today's schedule, calorie target, and on-hand pantry ingredients.
     *
     * @param user            the currently active user
     * @param todayActivities the list of activities scheduled for today
     * @param pantryItems     the list of ingredients currently in the user's pantry
     * @return a {@link CompletableFuture} with the customized suggestion
     */
    public static CompletableFuture<String> generateDailyLifestyleSuggestion(User user,
                                                                              List<Activity> todayActivities,
                                                                              List<PantryItem> pantryItems) {
        String defaultFallback = (user != null && user.getBmi() > 0)
                ? UserService.getBMISuggestion(user)
                : "Plan your meals and workouts around your goals for maximum daily energy!";

        if (user == null) {
            return CompletableFuture.completedFuture(defaultFallback);
        }

        // Aggregate pantry names
        String pantryStock = (pantryItems == null || pantryItems.isEmpty())
                ? "basic pantry staples"
                : pantryItems.stream().map(PantryItem::getName).limit(8).collect(Collectors.joining(", "));

        // Aggregate today's activities
        String scheduleSummary = (todayActivities == null || todayActivities.isEmpty())
                ? "No specific tasks scheduled yet."
                : todayActivities.stream()
                .map(a -> a.getTitle() + " (" + a.getType().name() + " at " + a.getStartTime() + ")")
                .limit(5)
                .collect(Collectors.joining(", "));

        // Resolve dietary preferences for prompt context
        String dietaryPref = (user.getDietaryPreferences() != null && !user.getDietaryPreferences().isBlank())
                ? user.getDietaryPreferences()
                : "None specified";

        String prompt = String.format(
                "You are LifeSync, an uplifting and highly knowledgeable personal health and wellness coach for students and busy adults.\n\n" +
                "User Profile:\n" +
                "- Name: %s\n" +
                "- BMI: %.1f (%s category)\n" +
                "- Daily Calorie Goal: %d kcal\n" +
                "- Activity Level: %s\n" +
                "- MANDATORY DIETARY PREFERENCE / RESTRICTION: %s\n" +
                "- Ingredients in Pantry: %s\n" +
                "- Today's Schedule: %s\n\n" +
                "Task: Provide a concise, highly personalized 2-3 sentence lifestyle recommendation for today. " +
                "Integrate their BMI focus (weight management/energy balance), make smart use of their available pantry items for meals, " +
                "and strictly comply with their dietary preference: '%s'. " +
                "CRITICAL: If the user is lactose intolerant or dairy-free, you must NEVER recommend Greek yogurt, cow's milk, cheese, butter, or dairy products! " +
                "If vegetarian, never recommend meat or poultry. " +
                "Harmonize with their scheduled activities. Keep the tone warm, practical, and motivating. Do not use markdown headers.",
                user.getFirstName() != null ? user.getFirstName() : "there",
                user.getBmi(),
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Healthy",
                user.getDailyCalorieTarget(),
                user.getActivityLevel() != null ? user.getActivityLevel().name() : "MODERATE",
                dietaryPref,
                pantryStock,
                scheduleSummary,
                dietaryPref
        );

        return callGeminiAsync(prompt, defaultFallback);
    }

    /**
     * Generates personalized meal recommendations specifically matching available pantry ingredients
     * and aligning with the user's BMI and nutritional requirements.
     *
     * @param user        the active user
     * @param pantryItems the ingredients currently in stock
     * @return a {@link CompletableFuture} containing tailored recipe suggestions
     */
    public static CompletableFuture<String> generateMealRecommendationsFromPantry(User user,
                                                                                   List<PantryItem> pantryItems) {
        String fallback = (user != null && user.getBmi() > 0)
                ? "Based on your " + user.getBmiCategory().getDisplayName() + " profile (" + user.getBmi() +
                "), prioritize whole grains, lean proteins, and colorful vegetables from your pantry."
                : "Explore nutrient-dense dishes from our popular recipe catalog to nourish your body today!";

        if (user == null) {
            return CompletableFuture.completedFuture(fallback);
        }

        String dietaryPref = (user.getDietaryPreferences() != null && !user.getDietaryPreferences().isBlank())
                ? user.getDietaryPreferences().trim()
                : "None specified";

        String fallbackIngredients = (dietaryPref.toLowerCase().contains("lactose") || dietaryPref.toLowerCase().contains("dairy"))
                ? "Eggs, Bread, Oats, Olive Oil, Rice, Mixed Vegetables"
                : "Eggs, Milk, Oats, Chicken Breast, Olive Oil, Mixed Vegetables";

        String availableIngredients = (pantryItems == null || pantryItems.isEmpty())
                ? fallbackIngredients
                : pantryItems.stream()
                .filter(item -> !item.isLow())
                .map(item -> item.getName() + " (" + item.getQuantity() + " " + item.getUnit() + ")")
                .limit(12)
                .collect(Collectors.joining(", "));

        String prompt = String.format(
                "You are LifeSync Nutrition AI, an expert culinary and sports dietitian.\n\n" +
                "User Details:\n" +
                "- BMI: %.1f (%s)\n" +
                "- Daily Calorie Goal: %d kcal\n" +
                "- MANDATORY DIETARY PREFERENCES / RESTRICTIONS: %s\n" +
                "- Ingredients available in Pantry: %s\n\n" +
                "Task: Recommend 2 delicious, wholesome meal or snack concepts that specifically utilize these available pantry ingredients. " +
                "MANDATORY RULE: You MUST strictly comply with the user's dietary restriction: '%s'. " +
                "If lactose intolerant or dairy-free, you must NEVER recommend cow's milk, cheese, butter, yogurt, or cream! Use dairy-free alternatives or plant oils. " +
                "If vegetarian, do NOT suggest any meat, poultry, or fish. If vegan, exclude all animal products. " +
                "If they have allergies, exclude those ingredients entirely. " +
                "Explain in 2-3 short, clear sentences how these choices support their %s BMI target (e.g. satiety, lean muscle gain, or calorie control). " +
                "Be appetizing, practical, and concise. Avoid markdown titles.",
                user.getBmi(),
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal",
                user.getDailyCalorieTarget(),
                dietaryPref,
                availableIngredients,
                dietaryPref,
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal"
        );

        return callGeminiAsync(prompt, fallback);
    }

    /**
     * Generates smart fitness and workout advice customized to the user's BMI category,
     * recent activity completion, and energy levels.
     *
     * @param user             the active user
     * @param recentActivities the user's scheduled/completed workouts
     * @return a {@link CompletableFuture} containing the workout guidance
     */
    public static CompletableFuture<String> generateFitnessGuidance(User user, List<Activity> recentActivities) {
        String fallback = (user != null && user.getBmi() > 0)
                ? UserService.getBMIWorkoutAdvice(user)
                : "Consistency is key! Aim for at least 30 minutes of moderate activity each day.";

        if (user == null) {
            return CompletableFuture.completedFuture(fallback);
        }

        long completedWorkouts = (recentActivities == null) ? 0 : recentActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT && a.getStatus() == Activity.Status.COMPLETED)
                .count();

        String prompt = String.format(
                "You are LifeSync Fitness AI, an experienced personal trainer and exercise physiologist.\n\n" +
                "Client Profile:\n" +
                "- BMI: %.1f (%s category)\n" +
                "- Baseline Activity Level: %s\n" +
                "- Recent Workouts Completed: %d\n\n" +
                "Task: Provide actionable, scientifically grounded workout and recovery advice in 2-3 sentences. " +
                "Directly cater to their %s BMI category (e.g. low-impact cardio and joint safety for overweight/obese, " +
                "progressive resistance training for underweight, or balanced conditioning for normal). " +
                "Keep the tone encouraging and energetic.",
                user.getBmi(),
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal",
                user.getActivityLevel() != null ? user.getActivityLevel().name() : "MODERATE",
                completedWorkouts,
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal"
        );

        return callGeminiAsync(prompt, fallback);
    }

    /**
     * Generates scheduling optimization and time-management suggestions based on the user's
     * planned daily calendar and energy requirements.
     *
     * @param user           the active user
     * @param dayActivities  the list of activities planned for the given day
     * @return a {@link CompletableFuture} with schedule optimization guidance
     */
    public static CompletableFuture<String> generateScheduleOptimization(User user, List<Activity> dayActivities) {
        String fallback = "Keep a steady rhythm between focused sessions, hydration, balanced meals, and active recovery.";

        if (user == null || dayActivities == null || dayActivities.isEmpty()) {
            return CompletableFuture.completedFuture(fallback);
        }

        String schedule = dayActivities.stream()
                .map(a -> a.getStartTime() + " - " + a.getEndTime() + ": " + a.getTitle() + " [" + a.getType() + "]")
                .collect(Collectors.joining("; "));

        String prompt = String.format(
                "You are LifeSync Smart Planner AI.\n\n" +
                "Client: BMI %.1f (%s)\n" +
                "Today's Schedule: %s\n\n" +
                "Task: Review the schedule for productivity and wellness balance. In 2 concise sentences, " +
                "point out any optimal break, hydration, or pre/post-workout meal timing window based on their plan.",
                user.getBmi(),
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal",
                schedule
        );

        return callGeminiAsync(prompt, fallback);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // AI Coach Chat (Scoped Conversational Interface)
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sends a user message to the Gemini AI for the in-app chat interface.
     *
     * <p>The prompt already contains the system role definition, user profile context,
     * conversation history, and the current user message — assembled by {@code ChatController}.
     * This method simply dispatches it and returns the raw text response.</p>
     *
     * <p>The chatbot is scoped to health, nutrition, fitness, BMI, meal planning, pantry,
     * and daily scheduling topics via system-level instructions embedded in the prompt.
     * Off-topic queries are handled by the fallback string supplied by the caller.</p>
     *
     * @param fullPrompt        the fully-assembled prompt including system instructions,
     *                          user profile, conversation history, and current message
     * @param conversationHistory the running list of [role, text] pairs (unused here —
     *                           history is embedded in fullPrompt for simplicity)
     * @param fallbackReply     the safe reply to use if Gemini is unreachable or errors
     * @return a {@link CompletableFuture} containing the AI text response or fallbackReply
     */
    public static CompletableFuture<String> sendChatMessage(String fullPrompt,
                                                            List<String[]> conversationHistory,
                                                            String fallbackReply) {
        return callGeminiAsync(fullPrompt, fallbackReply);
    }

    /**
     * Dynamically generates complete, structured recipe objects using Google Gemini AI
     * crafted exclusively from the ingredients currently in the user's pantry, strictly
     * honoring the user's dietary preferences (e.g. lactose intolerance, vegetarian, etc.)
     * and calibrated to their BMI and calorie target.
     *
     * @param user        the active user profile
     * @param pantryItems the items currently in stock in the user's pantry
     * @return a CompletableFuture containing a list of customized AI-generated Recipe objects
     */
    public static CompletableFuture<List<Recipe>> generateRecipesFromPantry(User user, List<PantryItem> pantryItems) {
        List<Recipe> fallbackRecipes = RecipeService.generateLocalPantryRecipes(user, pantryItems);

        if (user == null || pantryItems == null || pantryItems.isEmpty()) {
            return CompletableFuture.completedFuture(fallbackRecipes);
        }

        String availableIngredients = pantryItems.stream()
                .filter(item -> !item.isLow())
                .map(item -> item.getName() + " (" + item.getQuantity() + " " + item.getUnit() + ")")
                .collect(Collectors.joining(", "));

        if (availableIngredients.isBlank()) {
            availableIngredients = pantryItems.stream()
                    .map(item -> item.getName() + " (" + item.getQuantity() + " " + item.getUnit() + ")")
                    .collect(Collectors.joining(", "));
        }

        String dietaryPref = (user.getDietaryPreferences() != null && !user.getDietaryPreferences().isBlank())
                ? user.getDietaryPreferences().trim()
                : "None specified";

        String prompt = String.format(
                "You are LifeSync AI Chef, an expert personal chef and clinical culinary nutritionist.\n\n" +
                "User Nutritional Profile:\n" +
                "- Name: %s\n" +
                "- BMI: %.1f (%s category)\n" +
                "- Daily Calorie Goal: %d kcal\n" +
                "- MANDATORY DIETARY PREFERENCES / RESTRICTIONS: %s\n" +
                "- Available Pantry Ingredients: %s\n\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. You MUST generate 3 to 4 creative, practical, and appetizing recipes that specifically utilize these available pantry ingredients.\n" +
                "2. STRICTLY ENFORCE the dietary preference: '%s'. " +
                "For example: if lactose intolerant or dairy-free, NEVER use milk, cheese, cream, butter, or yogurt. " +
                "If vegetarian, NEVER use meat, poultry, or fish. If vegan, zero animal products.\n" +
                "3. Do NOT invent exotic ingredients not present in the pantry, except for basic kitchen staples (water, cooking oil, salt, pepper).\n" +
                "4. Output MUST be ONLY a valid JSON array of recipe objects. Do NOT include any markdown code fences, headers, or conversational text. Output raw JSON array starting with '[' and ending with ']'.\n\n" +
                "JSON Schema per object:\n" +
                "[\n" +
                "  {\n" +
                "    \"name\": \"Recipe Name\",\n" +
                "    \"description\": \"1-2 sentence description explaining how this fits their pantry and dietary profile\",\n" +
                "    \"ingredients\": [\"ingredient 1 with amount\", \"ingredient 2 with amount\"],\n" +
                "    \"instructions\": \"Step 1...\\nStep 2...\\nStep 3...\",\n" +
                "    \"cookingMethod\": \"Pan-fry / Toast / Simmer / etc.\",\n" +
                "    \"prepTimeMinutes\": 5,\n" +
                "    \"cookTimeMinutes\": 10,\n" +
                "    \"calories\": 350.0,\n" +
                "    \"proteinG\": 18.0,\n" +
                "    \"carbsG\": 30.0,\n" +
                "    \"fatG\": 14.0,\n" +
                "    \"mealType\": \"Breakfast\",\n" +
                "    \"tags\": [\"Pantry Made\", \"AI Chef\", \"%s\"]\n" +
                "  }\n" +
                "]",
                user.getFirstName() != null ? user.getFirstName() : "User",
                user.getBmi(),
                user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal",
                user.getDailyCalorieTarget(),
                dietaryPref,
                availableIngredients,
                dietaryPref,
                dietaryPref
        );

        return callGeminiAsync(prompt, "")
                .thenApply(rawResponse -> {
                    if (rawResponse == null || rawResponse.isBlank()) {
                        return fallbackRecipes;
                    }
                    try {
                        String cleanJson = rawResponse.trim();
                        if (cleanJson.startsWith("```json")) {
                            cleanJson = cleanJson.substring(7);
                        } else if (cleanJson.startsWith("```")) {
                            cleanJson = cleanJson.substring(3);
                        }
                        if (cleanJson.endsWith("```")) {
                            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                        }
                        cleanJson = cleanJson.trim();

                        int startIdx = cleanJson.indexOf('[');
                        int endIdx = cleanJson.lastIndexOf(']');
                        if (startIdx >= 0 && endIdx > startIdx) {
                            cleanJson = cleanJson.substring(startIdx, endIdx + 1);
                        }

                        Type recipeListType = new TypeToken<List<Recipe>>() {}.getType();
                        List<Recipe> parsed = GSON.fromJson(cleanJson, recipeListType);
                        if (parsed != null && !parsed.isEmpty()) {
                            // Ensure valid IDs and tags
                            for (Recipe r : parsed) {
                                if (r.getId() == null || r.getId().isBlank()) {
                                    r.setId(UUID.randomUUID().toString());
                                }
                                if (r.getTags() == null) {
                                    r.setTags(new ArrayList<>());
                                }
                                if (!r.getTags().contains("AI Chef")) {
                                    r.getTags().add("AI Chef");
                                }
                                if (!r.getTags().contains("Pantry Made")) {
                                    r.getTags().add("Pantry Made");
                                }
                            }
                            return parsed;
                        }
                    } catch (Exception ex) {
                        System.err.println("[LifeSync GeminiService] Failed to parse generated recipes JSON: " + ex.getMessage());
                    }
                    return fallbackRecipes;
                });
    }
}
