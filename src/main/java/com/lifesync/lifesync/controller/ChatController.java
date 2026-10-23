package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.ActivityService;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.ChatScopeValidator;
import com.lifesync.lifesync.service.GeminiService;
import com.lifesync.lifesync.service.PantryService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controller for the LifeSync AI Coach chatbot view (chat-view.fxml).
 *
 * <p>Provides an in-app conversational interface powered by Google Gemini AI,
 * strictly scoped to LifeSync-relevant health and wellness topics. The chatbot
 * maintains persistent conversation history across tab navigation and seamlessly
 * integrates AI-generated workout and meal plans into the user's schedule.</p>
 *
 * @author LifeSync Development Team
 * @version 2.0 (Tab Persistence & Schedule Integration)
 */
public class ChatController {

    // ── Static Persistent History (persists across tab switches) ───────────

    /**
     * Represents a single recorded message with sender, timestamp, and optional plan activities.
     */
    public static class ChatMessageRecord {
        public final String sender; // "user" or "model"
        public final String text;
        public final String timeStr;
        public boolean planAdded;
        public final List<Activity> extractedActivities;

        public ChatMessageRecord(String sender, String text, String timeStr, List<Activity> extractedActivities) {
            this.sender = sender;
            this.text = text;
            this.timeStr = timeStr;
            this.planAdded = false;
            this.extractedActivities = extractedActivities != null ? extractedActivities : new ArrayList<>();
        }
    }

    /** Static message history rendered on UI. */
    private static final List<ChatMessageRecord> persistentMessages = new ArrayList<>();

    /** Static conversation turns for multi-turn Gemini API context. */
    private static final List<String[]> persistentConversationHistory = new ArrayList<>();

    // ── Regex Pattern for Machine-Parseable Plan Events ─────────────────────

    private static final Pattern EVENT_PATTERN = Pattern.compile(
            "\\[EVENT:\\s*([0-9]{4}-[0-9]{2}-[0-9]{2})\\s*\\|\\s*([^|]+)\\s*\\|\\s*([0-9]{1,2}:[0-9]{2})\\s*\\|\\s*([0-9]{1,2}:[0-9]{2})\\s*\\|\\s*([A-Za-z]+)\\s*\\|\\s*([^\\]]+)\\]"
    );

    // ── FXML-injected fields ───────────────────────────────────────────────

    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesBox;
    @FXML private TextField chatInputField;
    @FXML private Button sendButton;
    @FXML private HBox typingIndicatorBox;
    @FXML private Label statusPill;

    // ── Internal state ─────────────────────────────────────────────────────

    private User currentUser;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    // ── Initialization ─────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        currentUser = AuthService.getCurrentUser();

        if (persistentMessages.isEmpty()) {
            showWelcomeMessage();
        } else {
            // Restore previous messages from persistent history
            chatMessagesBox.getChildren().clear();
            for (ChatMessageRecord msg : persistentMessages) {
                if ("user".equals(msg.sender)) {
                    renderUserBubble(msg.text, msg.timeStr);
                } else {
                    renderBotBubble(msg);
                }
            }
            scrollToBottom();
        }
    }

    /**
     * Displays the initial greeting bubble from the AI Coach if no history exists.
     */
    private void showWelcomeMessage() {
        String name = (currentUser != null && currentUser.getFirstName() != null)
                ? currentUser.getFirstName()
                : "there";

        String bmiContext = "";
        if (currentUser != null && currentUser.getBmi() > 0) {
            bmiContext = String.format(" I can see your BMI is **%.1f** (%s) and your daily calorie target is **%d kcal**.",
                    currentUser.getBmi(),
                    currentUser.getBmiCategory() != null ? currentUser.getBmiCategory().getDisplayName() : "Normal",
                    currentUser.getDailyCalorieTarget());
        }

        String dietCtx = "";
        if (currentUser != null && currentUser.getDietaryPreferences() != null
                && !currentUser.getDietaryPreferences().isBlank()) {
            dietCtx = " I'll respect your **" + currentUser.getDietaryPreferences().trim() + "** dietary preference in all my suggestions.";
        }

        String welcome = "👋 Hi " + name + "! I'm your **LifeSync AI Coach** — here to help you live healthier and happier! 🌿"
                + bmiContext + dietCtx
                + "\n\nI can help you with:"
                + "\n• 🍽 **Meal ideas** from your pantry"
                + "\n• 💪 **Workout & fitness** plans for the day or week"
                + "\n• ⚖️ **BMI & nutrition** guidance"
                + "\n• 📅 **Daily scheduling** that adds events straight into your calendar"
                + "\n• 🥫 **Pantry** ingredient questions"
                + "\n\nTry asking: *\"create me a whole week workout plan\"* or *\"give me some meals to plan for the day\"*!";

        ChatMessageRecord rec = new ChatMessageRecord("model", welcome, LocalTime.now().format(TIME_FMT), null);
        persistentMessages.add(rec);
        renderBotBubble(rec);
    }

    // ── Message Handling ───────────────────────────────────────────────────

    @FXML
    private void handleSendMessage() {
        String userMessage = chatInputField.getText().trim();
        if (userMessage.isEmpty()) return;

        chatInputField.clear();

        String timeNow = LocalTime.now().format(TIME_FMT);
        ChatMessageRecord userRec = new ChatMessageRecord("user", userMessage, timeNow, null);
        persistentMessages.add(userRec);
        renderUserBubble(userMessage, timeNow);

        // Record user turn in Gemini context
        persistentConversationHistory.add(new String[]{"user", userMessage});

        // Fast-fail guardrail: reject inquiries outside LifeSync scope immediately
        if (ChatScopeValidator.isOffTopic(userMessage)) {
            String refusal = ChatScopeValidator.getRefusalMessage();
            persistentConversationHistory.add(new String[]{"model", refusal});
            ChatMessageRecord botRec = new ChatMessageRecord("model", refusal, LocalTime.now().format(TIME_FMT), null);
            persistentMessages.add(botRec);
            renderBotBubble(botRec);
            scrollToBottom();
            return;
        }

        // Show typing indicator
        showTypingIndicator(true);
        sendButton.setDisable(true);

        String contextualPrompt = buildContextualPrompt(userMessage);

        GeminiService.sendChatMessage(contextualPrompt, persistentConversationHistory, buildFallbackReply(userMessage))
                .thenAccept(reply -> Platform.runLater(() -> {
                    showTypingIndicator(false);
                    sendButton.setDisable(false);

                    persistentConversationHistory.add(new String[]{"model", reply});

                    List<Activity> plans = parsePlanEvents(reply);
                    ChatMessageRecord botRec = new ChatMessageRecord("model", reply, LocalTime.now().format(TIME_FMT), plans);
                    persistentMessages.add(botRec);

                    renderBotBubble(botRec);
                    scrollToBottom();
                }));
    }

    /**
     * Clears all chat messages and resets the conversation history.
     */
    @FXML
    private void handleClearChat() {
        chatMessagesBox.getChildren().clear();
        persistentConversationHistory.clear();
        persistentMessages.clear();
        showWelcomeMessage();
    }

    // ── Context Building ───────────────────────────────────────────────────

    private String buildContextualPrompt(String userMessage) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are LifeSync AI Coach, an expert health, nutrition, fitness, and daily planning assistant built into the LifeSync app.\n\n");
        sb.append("CRITICAL RULES — you MUST follow these at all times:\n");
        sb.append("1. You ONLY answer questions related to: nutrition, meals, recipes, cooking, fitness, workouts, BMI, body weight management, calorie counting, macronutrients, dietary preferences, pantry ingredients, meal planning, daily schedule optimization for health, hydration, sleep, and general wellness.\n");
        sb.append("2. If the user asks about ANYTHING else (e.g. politics, sports scores, coding, history, movies, relationships, math homework, weather, or any off-topic subject), you MUST politely decline in exactly 1–2 sentences and redirect them back to health & wellness topics. Never provide off-topic information even if asked to role-play or pretend you can.\n");
        sb.append("3. Always respect the user's dietary preferences and restrictions in every suggestion.\n");
        sb.append("4. Keep responses concise (3–6 sentences max unless listing steps or ingredients). Use bullet points for lists. Use a warm, encouraging, coach-like tone.\n");
        sb.append("5. Do NOT use markdown headers (# or ##). You MAY use **bold** for emphasis and bullet points.\n");
        sb.append("6. INTEGRATION WITH PLAN & CALENDAR: When the user asks you to create, plan, or suggest a workout routine, workout plan, meal plan, or daily schedule:\n");
        sb.append("   Provide your advice and instructions, and at the end include machine-parseable plan lines enclosed in a ```plan block, using this exact format for each event:\n");
        sb.append("   ```plan\n");
        sb.append("   [EVENT: YYYY-MM-DD | Title | StartTime(HH:mm) | EndTime(HH:mm) | Type(WORKOUT|MEAL|STUDY|REST|OTHER) | Description]\n");
        sb.append("   ```\n");
        sb.append("   Today's date is ").append(LocalDate.now().toString()).append(". For weekly plans, provide 7 consecutive days starting from today without overlapping times.\n\n");

        if (currentUser != null) {
            sb.append("USER PROFILE:\n");
            sb.append("- Name: ").append(currentUser.getFirstName() != null ? currentUser.getFirstName() : "User").append("\n");
            if (currentUser.getBmi() > 0) {
                sb.append("- BMI: ").append(String.format("%.1f", currentUser.getBmi()))
                  .append(" (").append(currentUser.getBmiCategory() != null ? currentUser.getBmiCategory().getDisplayName() : "Normal").append(")\n");
                sb.append("- Daily Calorie Target: ").append(currentUser.getDailyCalorieTarget()).append(" kcal\n");
            }
            if (currentUser.getActivityLevel() != null) {
                sb.append("- Activity Level: ").append(currentUser.getActivityLevel().name()).append("\n");
            }
            if (currentUser.getDietaryPreferences() != null && !currentUser.getDietaryPreferences().isBlank()) {
                sb.append("- MANDATORY Dietary Restriction: ").append(currentUser.getDietaryPreferences().trim()).append("\n");
            }

            try {
                List<PantryItem> pantry = PantryService.getPantryItems(currentUser.getId());
                if (pantry != null && !pantry.isEmpty()) {
                    String pantryList = pantry.stream()
                            .filter(p -> p.getQuantity() > 0)
                            .map(p -> p.getName() + " (" + p.getQuantity() + " " + p.getUnit() + ")")
                            .limit(10)
                            .collect(Collectors.joining(", "));
                    if (!pantryList.isBlank()) {
                        sb.append("- Pantry Ingredients in Stock: ").append(pantryList).append("\n");
                    }
                }
            } catch (Exception ignored) {}
            sb.append("\n");
        }

        int historyStart = Math.max(0, persistentConversationHistory.size() - 6);
        if (historyStart < persistentConversationHistory.size()) {
            sb.append("CONVERSATION SO FAR:\n");
            for (int i = historyStart; i < persistentConversationHistory.size(); i++) {
                String[] turn = persistentConversationHistory.get(i);
                sb.append(turn[0].equals("user") ? "User: " : "Coach: ").append(turn[1]).append("\n");
            }
            sb.append("\n");
        }

        sb.append("User: ").append(userMessage).append("\nCoach:");
        return sb.toString();
    }

    /**
     * Generates a sensible fallback reply when Gemini is unavailable.
     * Generates actionable structured workout and meal plans if requested!
     */
    private String buildFallbackReply(String userMessage) {
        if (ChatScopeValidator.isOffTopic(userMessage)) {
            return ChatScopeValidator.getRefusalMessage();
        }

        String lower = userMessage.toLowerCase();
        LocalDate today = LocalDate.now();

        // 1. Whole week workout plan
        if (lower.contains("workout") && (lower.contains("week") || lower.contains("plan") || lower.contains("routine"))) {
            StringBuilder sb = new StringBuilder();
            sb.append("Here is your tailored **7-Day Workout Plan** calibrated to your fitness goals! 💪\n\n");
            sb.append("• **Day 1**: Upper Body Push (Chest, Shoulders & Triceps)\n");
            sb.append("• **Day 2**: Core & Aerobic Cardio Interval Training\n");
            sb.append("• **Day 3**: Active Mobility & Flexibility Yoga\n");
            sb.append("• **Day 4**: Lower Body Power (Squats, Lunges & Calves)\n");
            sb.append("• **Day 5**: Full Body Functional HIIT Conditioning\n");
            sb.append("• **Day 6**: Outdoor Endurance Jog / Power Walk\n");
            sb.append("• **Day 7**: Rest, Hydration & Muscle Recovery\n\n");
            sb.append("Click the button below to add all 7 sessions directly into your calendar!\n\n");
            sb.append("```plan\n");
            for (int i = 0; i < 7; i++) {
                LocalDate d = today.plusDays(i);
                String dayTitle;
                String desc;
                Activity.ActivityType type = Activity.ActivityType.WORKOUT;
                if (i == 0) { dayTitle = "Upper Body Strength"; desc = "Push-ups, dumbbell presses, tricep dips"; }
                else if (i == 1) { dayTitle = "Core & HIIT Circuit"; desc = "Planks, bicycle crunches, jumping jacks"; }
                else if (i == 2) { dayTitle = "Mobility & Yoga"; desc = "Dynamic stretching and light yoga"; type = Activity.ActivityType.REST; }
                else if (i == 3) { dayTitle = "Lower Body Strength"; desc = "Bodyweight squats, lunges, calf raises"; }
                else if (i == 4) { dayTitle = "Full Body Conditioning"; desc = "Burpees, kettlebell swings, mountain climbers"; }
                else if (i == 5) { dayTitle = "Cardio & Endurance"; desc = "Outdoor 5K brisk jog or fast walk"; }
                else { dayTitle = "Mindful Rest & Recovery"; desc = "Hydration, relaxing walk, sleep replenishment"; type = Activity.ActivityType.REST; }
                sb.append(String.format("[EVENT: %s | %s | 07:00 | 07:45 | %s | %s]\n", d, dayTitle, type.name(), desc));
            }
            sb.append("```");
            return sb.toString();
        }

        // 2. Meal plan for the day
        if (lower.contains("meal") && (lower.contains("plan") || lower.contains("day") || lower.contains("give"))) {
            StringBuilder sb = new StringBuilder();
            sb.append("Here is a balanced **Daily Meal Plan** crafted for your dietary needs and calorie targets! 🍽️\n\n");
            sb.append("• **Breakfast (08:00)**: Oatmeal with berries, chia seeds, and protein yogurt\n");
            sb.append("• **Lunch (12:00)**: Grilled chicken or tofu quinoa salad with avocado\n");
            sb.append("• **Snack (15:30)**: Apple slices with almond butter and a handful of walnuts\n");
            sb.append("• **Dinner (18:30)**: Baked salmon or roasted tempeh with steamed broccoli and brown rice\n\n");
            sb.append("Click below to integrate these meals into your daily schedule!\n\n");
            sb.append("```plan\n");
            sb.append(String.format("[EVENT: %s | High-Protein Breakfast | 08:00 | 08:30 | MEAL | Oatmeal with fresh berries & Greek yogurt]\n", today));
            sb.append(String.format("[EVENT: %s | Balanced Lunch | 12:00 | 12:45 | MEAL | Quinoa salad with chicken/tofu & greens]\n", today));
            sb.append(String.format("[EVENT: %s | Afternoon Energy Snack | 15:30 | 16:00 | MEAL | Apple slices with almond butter & walnuts]\n", today));
            sb.append(String.format("[EVENT: %s | Nutritious Dinner | 18:30 | 19:15 | MEAL | Salmon or tempeh with roasted vegetables and rice]\n", today));
            sb.append("```");
            return sb.toString();
        }

        if (currentUser != null && currentUser.getBmi() > 0) {
            return "I'm your LifeSync AI Coach. Based on your profile (BMI "
                    + String.format("%.1f", currentUser.getBmi())
                    + "), I recommend focusing on consistent workouts and balanced nutrition today! Ask me to 'create a whole week workout plan' or 'give me some meals to plan for the day' to add them to your schedule.";
        }
        return "I'm ready to help you plan! Ask me to 'create a whole week workout plan' or 'give me meals to plan for the day', and I will craft them and add them directly to your calendar! 💪🌿";
    }

    // ── Plan Parser ────────────────────────────────────────────────────────

    private List<Activity> parsePlanEvents(String text) {
        List<Activity> list = new ArrayList<>();
        if (text == null) return list;
        Matcher m = EVENT_PATTERN.matcher(text);
        while (m.find()) {
            try {
                String date = m.group(1).trim();
                String title = m.group(2).trim();
                String start = normalizeTime(m.group(3).trim());
                String end = normalizeTime(m.group(4).trim());
                String typeStr = m.group(5).trim().toUpperCase();
                String desc = m.group(6).trim();

                Activity.ActivityType type;
                try {
                    type = Activity.ActivityType.valueOf(typeStr);
                } catch (Exception ex) {
                    type = Activity.ActivityType.OTHER;
                }

                Activity act = new Activity(
                        currentUser != null ? currentUser.getId() : "default_user",
                        title, type, date, start, end
                );
                act.setDescription(desc);
                list.add(act);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private static String normalizeTime(String t) {
        if (t.length() == 4 && t.charAt(1) == ':') {
            return "0" + t;
        }
        return t;
    }

    // ── UI Bubble Builders ─────────────────────────────────────────────────

    private void renderUserBubble(String text, String timeStr) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add("chat-user-bubble");
        bubble.setMaxWidth(480);
        bubble.setPadding(new Insets(10, 14, 10, 14));

        Label msgLabel = new Label(text);
        msgLabel.getStyleClass().add("chat-user-text");
        msgLabel.setWrapText(true);

        Label timeLabel = new Label(timeStr != null ? timeStr : LocalTime.now().format(TIME_FMT));
        timeLabel.getStyleClass().add("chat-user-time");
        timeLabel.setAlignment(Pos.CENTER_RIGHT);
        timeLabel.setMaxWidth(Double.MAX_VALUE);
        timeLabel.setTextAlignment(TextAlignment.RIGHT);

        bubble.getChildren().addAll(msgLabel, timeLabel);
        row.getChildren().add(bubble);
        chatMessagesBox.getChildren().add(row);
    }

    private void renderBotBubble(ChatMessageRecord record) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Node avatar;
        try {
            var resource = getClass().getResource("/com/lifesync/lifesync/images/chatbot.png");
            if (resource != null) {
                ImageView botIcon = new ImageView(new Image(resource.toExternalForm()));
                botIcon.setFitWidth(30);
                botIcon.setFitHeight(30);
                botIcon.setPreserveRatio(true);
                avatar = botIcon;
            } else {
                Label fallback = new Label("🤖");
                fallback.getStyleClass().add("chat-avatar-fallback");
                avatar = fallback;
            }
        } catch (Exception e) {
            Label fallback = new Label("🤖");
            fallback.getStyleClass().add("chat-avatar-fallback");
            avatar = fallback;
        }

        VBox bubble = new VBox(6);
        bubble.getStyleClass().add("chat-bot-bubble");
        bubble.setMaxWidth(520);
        bubble.setPadding(new Insets(12, 16, 12, 16));

        // Filter out raw ```plan block from display so user gets clean prose
        String displayText = record.text;
        if (displayText.contains("```plan")) {
            displayText = displayText.replaceAll("```plan[\\s\\S]*?```", "").trim();
        }

        String[] lines = displayText.split("\n");
        for (String line : lines) {
            if (line.isBlank()) {
                Region gap = new Region();
                gap.setPrefHeight(4);
                bubble.getChildren().add(gap);
                continue;
            }
            Label lineLabel = new Label(stripMarkdownBold(line));
            lineLabel.setWrapText(true);
            if (line.contains("**")) {
                lineLabel.getStyleClass().add("chat-bot-text-bold");
            } else {
                lineLabel.getStyleClass().add("chat-bot-text");
            }
            bubble.getChildren().add(lineLabel);
        }

        // Actionable Plan Card Integration
        if (record.extractedActivities != null && !record.extractedActivities.isEmpty()) {
            VBox planCard = new VBox(8);
            planCard.setStyle("-fx-background-color: rgba(245, 166, 35, 0.08); -fx-border-color: #F5A623; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12;");

            HBox cardHeader = new HBox(8);
            cardHeader.setAlignment(Pos.CENTER_LEFT);
            Label planIcon = new Label("📅");
            Label planTitle = new Label("AI Actionable Plan (" + record.extractedActivities.size() + " scheduled items)");
            planTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            cardHeader.getChildren().addAll(planIcon, planTitle);

            VBox previewList = new VBox(3);
            for (Activity a : record.extractedActivities) {
                Label item = new Label("• " + a.getDate() + " (" + a.getStartTime() + " - " + a.getEndTime() + "): " + a.getTitle());
                item.setStyle("-fx-font-size: 11px;");
                previewList.getChildren().add(item);
            }

            Button addBtn = new Button(record.planAdded ? "✅ Plan Added to Calendar" : "➕ Add All to My Schedule & Plan");
            addBtn.setStyle("-fx-background-color: #F5A623; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 6 12;");
            addBtn.setDisable(record.planAdded);

            addBtn.setOnAction(e -> {
                String userId = currentUser != null ? currentUser.getId() : "default_user";
                int addedCount = 0;
                for (Activity a : record.extractedActivities) {
                    a.setUserId(userId);
                    ActivityService.addActivity(a);
                    addedCount++;
                }
                record.planAdded = true;
                addBtn.setText("✅ Added to Calendar (" + addedCount + " items)");
                addBtn.setDisable(true);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Plan Integrated");
                alert.setHeaderText("Activities Successfully Scheduled!");
                alert.setContentText(addedCount + " items from the AI Coach have been added to your calendar and will adapt based on your needs.");
                alert.showAndWait();
            });

            planCard.getChildren().addAll(cardHeader, previewList, addBtn);
            bubble.getChildren().add(planCard);
        }

        Label timeLabel = new Label("LifeSync AI • " + record.timeStr);
        timeLabel.getStyleClass().add("chat-bot-time");

        bubble.getChildren().add(timeLabel);
        row.getChildren().addAll(avatar, bubble);
        chatMessagesBox.getChildren().add(row);
    }

    private String stripMarkdownBold(String line) {
        return line.replace("**", "");
    }

    private void showTypingIndicator(boolean thinking) {
        typingIndicatorBox.setVisible(thinking);
        typingIndicatorBox.setManaged(thinking);
        if (statusPill != null) {
            statusPill.setText(thinking ? "● Thinking…" : "● Online");
            statusPill.getStyleClass().removeAll("chat-status-thinking", "chat-status-online");
            statusPill.getStyleClass().add(thinking ? "chat-status-thinking" : "chat-status-online");
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }
}
