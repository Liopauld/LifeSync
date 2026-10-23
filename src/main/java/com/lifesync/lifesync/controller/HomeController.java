package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.ActivityService;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.GeminiService;
import com.lifesync.lifesync.service.NutritionService;
import com.lifesync.lifesync.service.PantryService;
import com.lifesync.lifesync.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Controller for the Home Dashboard (home-view.fxml).
 *
 * Loads and displays a summary of the user's day: planned activities,
 * nutrition progress, upcoming events, and recent activity updates.
 * All data is pulled from the service layer on each load so the
 * dashboard always reflects the latest state.
 */
public class HomeController {

    // ── FXML-injected fields ───────────────────────────────────────────

    /** Dynamic greeting label – text set based on time of day and user's name. */
    @FXML private Label greetingLabel;

    // ── Summary cards ──────────────────────────────────────────────────

    /** Count label for Today's Plan card (e.g. "3 / 5 tasks"). */
    @FXML private Label planCountLabel;
    /** Progress bar showing task completion ratio. */
    @FXML private ProgressBar planProgressBar;

    /** Count label for Meals card (e.g. "2 / 3 meals"). */
    @FXML private Label mealsCountLabel;
    /** Progress bar showing meal logging progress. */
    @FXML private ProgressBar mealsProgressBar;

    /** Count label for Activity card (e.g. "1 / 2 activities"). */
    @FXML private Label activityCountLabel;
    /** Progress bar showing workout completion ratio. */
    @FXML private ProgressBar activityProgressBar;

    /** Count label for Pantry card (e.g. "0 items low"). */
    @FXML private Label pantryCountLabel;
    /** Progress bar showing pantry stock status. */
    @FXML private ProgressBar pantryProgressBar;

    // ── Today's plan ───────────────────────────────────────────────────

    /** Displays the current date in a human-friendly format. */
    @FXML private Label todayDateLabel;
    /** Container for dynamically-built activity rows. */
    @FXML private VBox todayPlanList;

    // ── Nutrition overview ─────────────────────────────────────────────

    /** Large calorie count (e.g. "1,250"). */
    @FXML private Label calorieCountLabel;
    /** Target label below calorie count (e.g. "of 2,000"). */
    @FXML private Label calorieTargetLabel;

    /** Protein grams label (e.g. "85 g"). */
    @FXML private Label proteinGramsLabel;
    /** Protein percentage label (e.g. "35%"). */
    @FXML private Label proteinPercentLabel;
    /** Protein macro progress bar. */
    @FXML private ProgressBar proteinProgressBar;

    /** Carbs grams label (e.g. "120 g"). */
    @FXML private Label carbsGramsLabel;
    /** Carbs percentage label (e.g. "45%"). */
    @FXML private Label carbsPercentLabel;
    /** Carbs macro progress bar. */
    @FXML private ProgressBar carbsProgressBar;

    /** Fat grams label (e.g. "40 g"). */
    @FXML private Label fatGramsLabel;
    /** Fat percentage label (e.g. "20%"). */
    @FXML private Label fatPercentLabel;
    /** Fat macro progress bar. */
    @FXML private ProgressBar fatProgressBar;

    // ── AI suggestion ──────────────────────────────────────────────────

    /** Label displaying the AI-generated suggestion text. */
    @FXML private Label aiSuggestionLabel;

    // ── Upcoming plans & recent updates ────────────────────────────────

    /** Container for upcoming activity rows. */
    @FXML private VBox upcomingPlansList;
    /** Container for recent update rows. */
    @FXML private VBox recentUpdatesList;

    // ── Instance state ─────────────────────────────────────────────────

    /** The currently logged-in user. */
    private User currentUser;

    /** Today's date string in yyyy-MM-dd format. */
    private String today;

    // ═══════════════════════════════════════════════════════════════════
    //  Initialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called automatically after FXML injection.
     * Loads all dashboard data from the service layer.
     */
    @FXML
    public void initialize() {
        currentUser = AuthService.getCurrentUser();
        today = LocalDate.now().toString();

        loadGreeting();
        loadSummaryCards();
        loadTodaysPlan();
        loadNutritionOverview();
        loadAISuggestion();
        loadUpcomingPlans();
        loadRecentUpdates();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Dashboard section loaders
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Sets the greeting based on time of day and user's first name.
     * Morning (before 12), afternoon (12–17), or evening (after 17).
     */
    private void loadGreeting() {
        int hour = LocalTime.now().getHour();
        String timeGreeting;
        if (hour < 12) {
            timeGreeting = "Good morning";
        } else if (hour < 17) {
            timeGreeting = "Good afternoon";
        } else {
            timeGreeting = "Good evening";
        }

        String name = currentUser != null ? currentUser.getFirstName() : "User";
        greetingLabel.setText("☀ " + timeGreeting + ", " + (name != null ? name : "User") + "!");
    }

    /**
     * Populates the 4 summary cards with today's progress counts.
     * Each card shows a ratio (completed / total) and a matching progress bar.
     */
    private void loadSummaryCards() {
        if (currentUser == null) return;
        String userId = currentUser.getId();

        // ── Today's Plan card ──
        List<Activity> todayActivities = ActivityService.getActivitiesForDate(userId, today);
        long totalTasks = todayActivities.size();
        long completedTasks = todayActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED).count();
        planCountLabel.setText(completedTasks + " / " + totalTasks + " tasks");
        planProgressBar.setProgress(totalTasks > 0 ? (double) completedTasks / totalTasks : 0);

        // ── Meals card ──
        long mealsLogged = NutritionService.countMealsLogged(userId, today);
        mealsCountLabel.setText(mealsLogged + " / 3 meals");
        mealsProgressBar.setProgress(mealsLogged / 3.0);

        // ── Activity card (workouts only) ──
        long totalWorkouts = todayActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT).count();
        long completedWorkouts = todayActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT)
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED).count();
        activityCountLabel.setText(completedWorkouts + " / " + totalWorkouts + " activities");
        activityProgressBar.setProgress(totalWorkouts > 0 ? (double) completedWorkouts / totalWorkouts : 0);

        // ── Pantry card – real count from PantryService ──
        List<PantryItem> pantryItems = PantryService.getPantryItems(userId);
        long lowItems = (pantryItems == null) ? 0 : pantryItems.stream().filter(PantryItem::isLow).count();
        long totalItems = (pantryItems == null) ? 0 : pantryItems.size();
        pantryCountLabel.setText(totalItems + " items" + (lowItems > 0 ? " (" + lowItems + " low)" : ""));
        pantryProgressBar.setProgress(totalItems > 0 ? Math.max(0.05, 1.0 - (double) lowItems / totalItems) : 0.05);
    }

    /**
     * Builds the timeline list of today's activities.
     * Each activity is shown as a styled HBox with time, status indicator, and title.
     */
    private void loadTodaysPlan() {
        // Set today's date display (e.g. "Tue, Oct 22, 2024")
        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
        todayDateLabel.setText(now.format(fmt));

        if (currentUser == null) return;

        List<Activity> activities = ActivityService.getActivitiesForDate(
                currentUser.getId(), today);
        todayPlanList.getChildren().clear();

        // Show placeholder when no activities exist
        if (activities.isEmpty()) {
            Label empty = new Label("No activities planned for today. Add one!");
            empty.getStyleClass().add("muted-text");
            todayPlanList.getChildren().add(empty);
            return;
        }

        // Build a row for each activity
        for (Activity activity : activities) {
            HBox row = createActivityRow(activity);
            todayPlanList.getChildren().add(row);
        }
    }

    /**
     * Creates a single activity row for the Today's Plan list.
     *
     * Layout: [Time] [Status Circle] [Title]  ────  [Status Pill]
     *
     * @param activity the activity to display
     * @return styled HBox representing this activity
     */
    private HBox createActivityRow(Activity activity) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("activity-row");

        // Time label (fixed width for alignment)
        Label timeLabel = new Label(activity.getStartTime());
        timeLabel.getStyleClass().add("activity-time");
        timeLabel.setMinWidth(60);

        // Status indicator circle (colour via CSS class)
        Circle statusDot = new Circle(5);
        statusDot.getStyleClass().add(
                activity.getStatus() == Activity.Status.COMPLETED
                        ? "status-dot-completed" : "status-dot-upcoming");

        // Activity title
        Label titleLabel = new Label(activity.getTitle());
        titleLabel.getStyleClass().add("activity-title");

        // Spacer pushes status pill to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // Status pill badge
        Label statusPill = new Label(activity.getStatus().name());
        statusPill.getStyleClass().addAll("status-pill",
                activity.getStatus() == Activity.Status.COMPLETED
                        ? "status-completed" : "status-upcoming");

        row.getChildren().addAll(timeLabel, statusDot, titleLabel, spacer, statusPill);
        return row;
    }

    /**
     * Loads nutrition data for today and updates the calorie and macro displays.
     * Macro percentages are calculated as a share of total macro-derived calories.
     */
    private void loadNutritionOverview() {
        if (currentUser == null) return;
        String userId = currentUser.getId();

        // Fetch today's totals from the nutrition service
        double totalCal = NutritionService.getTotalCalories(userId, today);
        double totalProtein = NutritionService.getTotalProtein(userId, today);
        double totalCarbs = NutritionService.getTotalCarbs(userId, today);
        double totalFat = NutritionService.getTotalFat(userId, today);
        int target = currentUser.getDailyCalorieTarget();

        // ── Calorie display ──
        calorieCountLabel.setText(String.format("%,.0f", totalCal));
        calorieTargetLabel.setText("of " + String.format("%,d", target));

        // ── Macro calculations (percentage of total macro-derived calories) ──
        // Protein & carbs = 4 kcal/g, fat = 9 kcal/g
        double totalMacroCal = (totalProtein * 4) + (totalCarbs * 4) + (totalFat * 9);
        double proteinPct = totalMacroCal > 0 ? (totalProtein * 4 / totalMacroCal) * 100 : 0;
        double carbsPct = totalMacroCal > 0 ? (totalCarbs * 4 / totalMacroCal) * 100 : 0;
        double fatPct = totalMacroCal > 0 ? (totalFat * 9 / totalMacroCal) * 100 : 0;

        // Protein bar
        proteinGramsLabel.setText(String.format("%.0f g", totalProtein));
        proteinPercentLabel.setText(String.format("%.0f%%", proteinPct));
        proteinProgressBar.setProgress(proteinPct / 100.0);

        // Carbs bar
        carbsGramsLabel.setText(String.format("%.0f g", totalCarbs));
        carbsPercentLabel.setText(String.format("%.0f%%", carbsPct));
        carbsProgressBar.setProgress(carbsPct / 100.0);

        // Fat bar
        fatGramsLabel.setText(String.format("%.0f g", totalFat));
        fatPercentLabel.setText(String.format("%.0f%%", fatPct));
        fatProgressBar.setProgress(fatPct / 100.0);
    }

    /**
     * Loads intelligent health and nutrition suggestions customized to the
     * user's current BMI, activity status, and available pantry stock using
     * Google Gemini AI, with instant fallback to the local BMI rules engine.
     */
    private void loadAISuggestion() {
        if (currentUser == null) {
            aiSuggestionLabel.setText("Welcome to LifeSync! Plan your day and nourish your body.");
            return;
        }

        // 1. Show immediate local suggestion so UI is instantly populated
        if (currentUser.getBmi() > 0) {
            aiSuggestionLabel.setText(UserService.getBMISuggestion(currentUser));
        } else {
            aiSuggestionLabel.setText(
                "Welcome to LifeSync! Set up your height & weight in your profile to unlock personalized BMI-tailored recommendations."
            );
            return;
        }

        // 2. Query Gemini AI asynchronously with today's activities & pantry stock
        List<Activity> todayActivities = ActivityService.getActivitiesForDate(currentUser.getId(), today);
        List<PantryItem> pantryItems = PantryService.getPantryItems(currentUser.getId());

        GeminiService.generateDailyLifestyleSuggestion(currentUser, todayActivities, pantryItems)
                .thenAccept(suggestion -> {
                    if (suggestion != null && !suggestion.isBlank()) {
                        Platform.runLater(() -> {
                            aiSuggestionLabel.setText(suggestion);
                        });
                    }
                });
    }

    /**
     * Loads upcoming activities (next 7 days) into the upcoming plans section.
     * Each row shows the date, time, title, and a category tag.
     */
    private void loadUpcomingPlans() {
        if (currentUser == null) return;

        List<Activity> upcoming = ActivityService.getUpcomingActivities(
                currentUser.getId(), 5);
        upcomingPlansList.getChildren().clear();

        // Show placeholder when nothing is upcoming
        if (upcoming.isEmpty()) {
            Label empty = new Label("No upcoming plans.");
            empty.getStyleClass().add("muted-text");
            upcomingPlansList.getChildren().add(empty);
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d");
        for (Activity activity : upcoming) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("upcoming-row");

            // Date label (e.g. "Oct 23")
            LocalDate actDate = LocalDate.parse(activity.getDate());
            Label dateLabel = new Label(actDate.format(dateFmt));
            dateLabel.getStyleClass().add("upcoming-date");
            dateLabel.setMinWidth(50);

            // Time label
            Label timeLabel = new Label(activity.getStartTime());
            timeLabel.getStyleClass().add("upcoming-time");
            timeLabel.setMinWidth(50);

            // Activity title
            Label titleLabel = new Label(activity.getTitle());
            titleLabel.getStyleClass().add("upcoming-title");

            // Spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            // Category tag (title-cased type name)
            Label categoryTag = new Label(
                    activity.getType().name().substring(0, 1)
                    + activity.getType().name().substring(1).toLowerCase());
            categoryTag.getStyleClass().addAll("category-tag",
                    "tag-" + activity.getType().name().toLowerCase());

            row.getChildren().addAll(dateLabel, timeLabel, titleLabel, spacer, categoryTag);
            upcomingPlansList.getChildren().add(row);
        }
    }

    /**
     * Loads recent activity updates (completed/cancelled items).
     * Shows the last few status changes as a simple feed with icons.
     */
    private void loadRecentUpdates() {
        if (currentUser == null) return;

        List<Activity> allActivities = ActivityService.getAllActivities(currentUser.getId());
        recentUpdatesList.getChildren().clear();

        // Filter to completed or cancelled activities, take last 4
        List<Activity> recent = allActivities.stream()
                .filter(a -> a.getStatus() != Activity.Status.UPCOMING)
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .limit(4)
                .toList();

        // Show placeholder when no updates exist
        if (recent.isEmpty()) {
            Label empty = new Label("No recent updates yet.");
            empty.getStyleClass().add("muted-text");
            recentUpdatesList.getChildren().add(empty);
            return;
        }

        for (Activity activity : recent) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("update-row");

            // Status icon (✅ for completed, ❌ for cancelled)
            String icon = activity.getStatus() == Activity.Status.COMPLETED ? "✅" : "❌";
            Label iconLabel = new Label(icon);

            // Description text
            String verb = activity.getStatus() == Activity.Status.COMPLETED
                    ? "completed" : "cancelled";
            Label descLabel = new Label("You " + verb + " \"" + activity.getTitle() + "\"");
            descLabel.getStyleClass().add("update-text");
            descLabel.setWrapText(true);

            row.getChildren().addAll(iconLabel, descLabel);
            recentUpdatesList.getChildren().add(row);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Interactive Dashboard Navigation Handlers
    // ═══════════════════════════════════════════════════════════════════

    @FXML
    private void handleGoToPlan() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().navigateToPlan();
        }
    }

    @FXML
    private void handleGoToMeals() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().navigateToMeals();
        }
    }

    @FXML
    private void handleGoToActivity() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().navigateToActivity();
        }
    }

    @FXML
    private void handleGoToPantry() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().navigateToPantry();
        }
    }
}
