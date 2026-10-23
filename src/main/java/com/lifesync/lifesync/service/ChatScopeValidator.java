package com.lifesync.lifesync.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates whether user queries to the LifeSync AI Coach fall within the
 * application's defined functional scope: personal health, nutrition, recipes,
 * pantry ingredients, fitness, BMI and body metrics, and daily lifestyle planning.
 *
 * <p>Implements client-side guardrails to ensure the AI Coach politely declines
 * questions unrelated to the core mission of LifeSync (e.g. software programming,
 * political commentary, financial trading, pop culture entertainment, or general
 * academic homework).</p>
 *
 * @author LifeSync Development Team
 * @version 1.0 (Phase 3 – Chatbot Scope Enforcement)
 */
public class ChatScopeValidator {

    /**
     * Regex pattern matching unambiguous off-topic domains including programming/coding,
     * politics, pop culture entertainment, financial speculation, general academic homework,
     * and non-health topics.
     */
    private static final Pattern OFF_TOPIC_PATTERN = Pattern.compile(
            "\\b("
            // Coding & Tech
            + "python|java|c\\+\\+|javascript|typescript|html|css|sql|github|programming|coding|debugger|"
            + "compiler|regex|algorithm|write a function|write code|write a script|unit test|pull request|"
            // Politics & World Affairs
            + "politics|politician|president|election|democrat|republican|parliament|congress|senate|"
            + "legislation|geopolitics|war in|military coup|"
            // Entertainment, Movies, Gaming
            + "movie|film|box office|hollywood|netflix|actor|actress|video game|playstation|xbox|nintendo|"
            + "fortnite|minecraft|pokemon|anime|manga|celebrity gossip|"
            // Finance & Speculation
            + "stock market|stock price|stock prices|stocks|crypto|bitcoin|ethereum|forex|day trading|mutual fund|mortgage interest|dividend yield|share price|shares|"
            // Academic homework / math (unrelated to nutrition/BMI)
            + "calculus|derivative|integral|solve for x|quadratic equation|quantum physics|thermodynamics equation|"
            // Creative writing unrelated to health
            + "write a poem about love|write a romance story|write a song about cars"
            + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Regex pattern matching common conversational greetings, identity queries,
     * and system capability inquiries that should always be answered.
     */
    private static final Pattern GREETINGS_AND_CAPABILITIES = Pattern.compile(
            "^\\s*(hi|hello|hey|good morning|good afternoon|good evening|howdy|sup|who are you|"
            + "what can you do|help|what are your features|commands|about you|menu|capabilities)\\s*[?!.]*\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Regex pattern matching core domain terms belonging to LifeSync's scope.
     */
    private static final Pattern ON_TOPIC_PATTERN = Pattern.compile(
            "\\b("
            // Nutrition & Meals
            + "food|eat|meal|meals|recipe|recipes|cook|cooking|ingredient|ingredients|pantry|pantry stock|in stock|"
            + "diet|diets|dietary|calorie|calories|kcal|macro|macros|protein|carb|carbs|fat|fats|"
            + "fiber|sugar|sodium|vitamin|nutrition|snack|breakfast|lunch|dinner|"
            + "lactose|dairy|vegan|vegetarian|gluten|keto|allerg|allergy|water|hydration|"
            // Anthropometrics & BMI
            + "bmi|bmr|tdee|weight|weigh|height|underweight|overweight|obese|obesity|body fat|"
            + "lean mass|scale|gain weight|lose weight|fat loss|maintenance calories|"
            // Fitness & Activity
            + "workout|workouts|exercise|exercises|fitness|gym|cardio|running|jogging|walking|steps|"
            + "stretch|stretching|yoga|pushup|squat|lift|lifting|strength|training|recovery|heart rate|"
            // Planning & Habits
            + "plan|plans|planning|schedule|calendar|routine|morning routine|sleep|rest|energy|habit|habits|health|healthy|wellness|goal|goals"
            + ")\\b",
            Pattern.CASE_INSENSITIVE
    );

    /** Standard polite refusal response when a query is determined to be off-topic. */
    public static final String REFUSAL_MESSAGE =
            "I'm your **LifeSync AI Coach**, dedicated exclusively to your personal health, nutrition, "
            + "fitness, and daily lifestyle planning! 🌿\n\n"
            + "I cannot assist with topics outside of health and wellness (such as general knowledge, coding, politics, or entertainment).\n\n"
            + "Feel free to ask me about:\n"
            + "• 🍽 **Recipes** from your pantry stock\n"
            + "• ⚖️ **BMI & weight** management advice\n"
            + "• 💪 **Workout & fitness** routines\n"
            + "• 📅 **Daily schedule** and meal planning";

    private ChatScopeValidator() {
        // Private constructor for static utility class
    }

    /**
     * Determines whether the given query is out of scope for LifeSync.
     *
     * <p>A query is considered out of scope if:
     * <ol>
     *   <li>It matches explicit off-topic patterns (e.g. coding, politics, finance).</li>
     *   <li>It is not a recognised greeting/capability question AND contains no health,
     *       nutrition, fitness, pantry, or planning keywords.</li>
     * </ol>
     * </p>
     *
     * @param query the text submitted by the user
     * @return {@code true} if the query is unrelated to LifeSync's purpose; {@code false} otherwise
     */
    public static boolean isOffTopic(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        String trimmed = query.trim().toLowerCase(Locale.ROOT);

        // 1. Greetings and capability queries are always on-topic
        if (GREETINGS_AND_CAPABILITIES.matcher(trimmed).matches()) {
            return false;
        }

        // 2. Explicit off-topic matches (coding, politics, entertainment, etc.)
        if (OFF_TOPIC_PATTERN.matcher(trimmed).find()) {
            // Check if it's asking to write code about health, e.g. "write a python script for BMI" -> still off-topic
            return true;
        }

        // 3. If it contains known on-topic keywords, allow it
        if (ON_TOPIC_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        // 4. For very short queries (< 4 words) that are generic conversational terms, allow to Gemini
        String[] words = trimmed.split("\\s+");
        if (words.length <= 3) {
            return false;
        }

        // 5. If query contains no health/nutrition/fitness/planning terms and is long, flag as off-topic
        return true;
    }

    /**
     * Returns a personalized refusal message reminding the user of the chatbot's scope.
     *
     * @return the refusal message formatted with markdown
     */
    public static String getRefusalMessage() {
        return REFUSAL_MESSAGE;
    }
}
