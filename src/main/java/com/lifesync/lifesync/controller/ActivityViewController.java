package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.ActivityService;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.GeminiService;
import com.lifesync.lifesync.service.ThemeManager;
import com.lifesync.lifesync.service.UserService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Activity & Fitness view (activity-view.fxml).
 *
 * Displays a weekly workout summary with progress cards, a filterable list
 * of all activities, and a weekly breakdown panel showing day-by-day stats.
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>On initialization, loads all activities for the current user from
 *       {@link ActivityService}.</li>
 *   <li>Populates three summary cards: weekly progress, calories burned,
 *       and active days.</li>
 *   <li>Builds the activity list as styled cards with status indicators,
 *       action buttons (mark complete, cancel, delete).</li>
 *   <li>Filter combos let the user narrow by activity type and status.</li>
 *   <li>The weekly breakdown shows each day (Mon–Sun) with the number of
 *       activities scheduled and completed.</li>
 * </ol>
 */
public class ActivityViewController {

    // ── FXML-injected fields ───────────────────────────────────────────

    // Summary cards
    @FXML private Label weeklyProgressLabel;
    @FXML private ProgressBar weeklyProgressBar;
    @FXML private Label caloriesBurnedLabel;
    @FXML private ProgressBar caloriesBurnedBar;
    @FXML private Label activeDaysLabel;
    @FXML private ProgressBar activeDaysBar;

    // Filters
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;

    // Activity list
    @FXML private VBox activityListContainer;

    // Weekly breakdown
    @FXML private VBox weeklyBreakdownList;

    // Quick stats
    @FXML private Label totalWorkoutsLabel;
    @FXML private Label completedCountLabel;
    @FXML private Label upcomingCountLabel;
    @FXML private Label cancelledCountLabel;
    @FXML private Label completionRateLabel;

    // BMI Fitness Guidance
    @FXML private Label bmiCategoryPill;
    @FXML private Label bmiFitnessAdviceLabel;

    /** The currently logged-in user. */
    private User currentUser;

    /** All activities for the current user (unfiltered master list). */
    private List<Activity> allActivities;

    /**
     * Called automatically after FXML injection.
     * Sets up filters, loads data, and populates the view.
     */
    @FXML
    public void initialize() {
        currentUser = AuthService.getCurrentUser();
        if (currentUser == null) return;

        // Load all activities for this user
        allActivities = ActivityService.getAllActivities(currentUser.getId());

        // Set up filter dropdowns
        setupFilters();

        // Populate everything
        loadSummaryCards();
        loadActivityList();
        loadWeeklyBreakdown();
        loadQuickStats();
        loadBMIFitnessGuidance();
    }

    /**
     * Displays personalized workout advice customized to the user's BMI category,
     * powered by Google Gemini AI with instant fallback to the local BMI workout rules.
     */
    private void loadBMIFitnessGuidance() {
        if (bmiCategoryPill == null || bmiFitnessAdviceLabel == null) return;

        if (currentUser != null && currentUser.getBmi() > 0) {
            User.BMICategory cat = currentUser.getBmiCategory();
            bmiCategoryPill.setText(cat.getDisplayName());

            bmiCategoryPill.getStyleClass().removeAll("badge-underweight", "badge-normal", "badge-overweight", "badge-obese", "badge-pending");
            bmiCategoryPill.getStyleClass().add("badge-" + cat.name().toLowerCase());

            // Set immediate local fallback
            bmiFitnessAdviceLabel.setText(UserService.getBMIWorkoutAdvice(currentUser));

            // Enhance asynchronously via Gemini AI
            GeminiService.generateFitnessGuidance(currentUser, allActivities)
                    .thenAccept(guidance -> {
                        if (guidance != null && !guidance.isBlank()) {
                            Platform.runLater(() -> bmiFitnessAdviceLabel.setText(guidance));
                        }
                    });
        } else {
            bmiCategoryPill.setText("General");
            bmiCategoryPill.getStyleClass().removeAll("badge-underweight", "badge-normal", "badge-overweight", "badge-obese");
            bmiCategoryPill.getStyleClass().add("badge-pending");
            bmiFitnessAdviceLabel.setText("Consistency is key! Aim for 30 minutes of daily physical activity.");
        }
    }

    // ── Filter Setup ───────────────────────────────────────────────────

    /**
     * Initializes the type and status filter ComboBoxes.
     * "All Types" and "All Status" are the default (null-equivalent) options.
     * Changing a filter triggers a list reload.
     */
    private void setupFilters() {
        // Type filter: "All Types" + each ActivityType name
        List<String> typeOptions = new ArrayList<>();
        typeOptions.add("All Types");
        for (Activity.ActivityType type : Activity.ActivityType.values()) {
            // Capitalize nicely: WORKOUT -> Workout
            String name = type.name().charAt(0) + type.name().substring(1).toLowerCase();
            typeOptions.add(name);
        }
        typeFilterCombo.setItems(FXCollections.observableArrayList(typeOptions));
        typeFilterCombo.setValue("All Types");

        // Status filter: "All Status" + each Status name
        List<String> statusOptions = new ArrayList<>();
        statusOptions.add("All Status");
        for (Activity.Status status : Activity.Status.values()) {
            String name = status.name().charAt(0) + status.name().substring(1).toLowerCase();
            statusOptions.add(name);
        }
        statusFilterCombo.setItems(FXCollections.observableArrayList(statusOptions));
        statusFilterCombo.setValue("All Status");

        // Add listeners to reload the list when filters change
        typeFilterCombo.setOnAction(e -> loadActivityList());
        statusFilterCombo.setOnAction(e -> loadActivityList());
    }

    // ── Summary Cards ──────────────────────────────────────────────────

    /**
     * Populates the three summary cards with this week's workout stats.
     *
     * "This week" is defined as Monday through Sunday of the current week.
     */
    private void loadSummaryCards() {
        // Determine this week's date range (Monday to Sunday)
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        // Filter activities to this week only
        List<Activity> weekActivities = allActivities.stream()
                .filter(a -> {
                    LocalDate d = LocalDate.parse(a.getDate());
                    return !d.isBefore(monday) && !d.isAfter(sunday);
                })
                .toList();

        // -- Card 1: Weekly Workout Progress --
        // Count total workouts and completed workouts this week
        long totalWorkouts = weekActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT)
                .count();
        long completedWorkouts = weekActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT)
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED)
                .count();
        weeklyProgressLabel.setText(completedWorkouts + " / " + totalWorkouts + " workouts");
        weeklyProgressBar.setProgress(totalWorkouts > 0
                ? (double) completedWorkouts / totalWorkouts : 0);

        // -- Card 2: Total Calories Burned --
        // Sum up caloriesBurned from completed workouts this week
        int totalCalBurned = weekActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED)
                .mapToInt(Activity::getCaloriesBurned)
                .sum();
        caloriesBurnedLabel.setText(totalCalBurned + " kcal");
        // Normalize against a weekly goal of 3500 kcal (reasonable default)
        caloriesBurnedBar.setProgress(Math.min(1.0, totalCalBurned / 3500.0));

        // -- Card 3: Active Days --
        // Count distinct days that have at least one completed activity
        long activeDays = weekActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED)
                .map(Activity::getDate)
                .distinct()
                .count();
        activeDaysLabel.setText(activeDays + " / 7 days");
        activeDaysBar.setProgress(activeDays / 7.0);
    }

    // ── Activity List ──────────────────────────────────────────────────

    /**
     * Builds the filterable activity list.
     * Each activity is rendered as a styled card with time, type icon,
     * title, status pill, and action buttons.
     */
    private void loadActivityList() {
        activityListContainer.getChildren().clear();

        // Apply filters
        List<Activity> filtered = allActivities;

        // Type filter
        String selectedType = typeFilterCombo.getValue();
        if (selectedType != null && !selectedType.equals("All Types")) {
            Activity.ActivityType filterType = Activity.ActivityType.valueOf(
                    selectedType.toUpperCase());
            filtered = filtered.stream()
                    .filter(a -> a.getType() == filterType)
                    .collect(Collectors.toList());
        }

        // Status filter
        String selectedStatus = statusFilterCombo.getValue();
        if (selectedStatus != null && !selectedStatus.equals("All Status")) {
            Activity.Status filterStatus = Activity.Status.valueOf(
                    selectedStatus.toUpperCase());
            filtered = filtered.stream()
                    .filter(a -> a.getStatus() == filterStatus)
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            Label empty = new Label("No activities match your filters.");
            empty.getStyleClass().add("muted-text");
            empty.setPadding(new Insets(20));
            activityListContainer.getChildren().add(empty);
            return;
        }

        // Group activities by date for a cleaner display
        // LinkedHashMap preserves insertion order (we sort by date first)
        List<Activity> sortedFiltered = filtered.stream()
                .sorted(Comparator.comparing(Activity::getDate)
                        .thenComparing(Activity::getStartTime))
                .toList();

        String lastDate = "";
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

        for (Activity activity : sortedFiltered) {
            // Add a date header when the date changes
            if (!activity.getDate().equals(lastDate)) {
                lastDate = activity.getDate();
                LocalDate date = LocalDate.parse(lastDate);
                Label dateHeader = new Label(date.format(displayFmt));
                dateHeader.getStyleClass().add("activity-date-header");
                dateHeader.setPadding(new Insets(8, 0, 4, 4));
                activityListContainer.getChildren().add(dateHeader);
            }

            // Build the activity card
            HBox card = createActivityCard(activity);
            activityListContainer.getChildren().add(card);
        }
    }

    /**
     * Creates a single activity card for the activity list.
     *
     * Layout: [Type Icon] [Title + Subtitle] ... [Status Pill] [Action Buttons]
     *
     * @param activity the activity to display
     * @return styled HBox representing this activity
     */
    private HBox createActivityCard(Activity activity) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("activity-card");
        card.setPadding(new Insets(12, 16, 12, 16));

        // Type icon circle
        String icon = getTypeIcon(activity.getType());
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().addAll("activity-item-icon", "icon-badge",
                "icon-" + activity.getType().name().toLowerCase(java.util.Locale.ROOT));

        // Title + time subtitle
        VBox titleBox = new VBox(2);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Label titleLabel = new Label(activity.getTitle());
        titleLabel.getStyleClass().add("activity-item-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinWidth(0);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);

        String timeText = activity.getStartTime() + " - " + activity.getEndTime();
        if (activity.getDescription() != null && !activity.getDescription().isEmpty()) {
            timeText += "  •  " + activity.getDescription();
        }
        Label subtitleLabel = new Label(timeText);
        subtitleLabel.getStyleClass().add("activity-item-subtitle");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMinHeight(Region.USE_PREF_SIZE);

        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Calories burned badge (if > 0)
        HBox badgeBox = new HBox(6);
        badgeBox.setAlignment(Pos.CENTER);
        if (activity.getCaloriesBurned() > 0) {
            Label calBadge = new Label("🔥 " + activity.getCaloriesBurned() + " kcal");
            calBadge.getStyleClass().add("calorie-badge");
            badgeBox.getChildren().add(calBadge);
        }

        // Status pill
        Label statusPill = new Label(activity.getStatus() == Activity.Status.COMPLETED
                ? "✓ COMPLETED" : activity.getStatus().name());
        statusPill.getStyleClass().addAll("status-pill",
                "status-" + activity.getStatus().name().toLowerCase());

        // Action buttons
        HBox actionBox = new HBox(6);
        actionBox.setAlignment(Pos.CENTER);

        if (activity.getStatus() == Activity.Status.UPCOMING) {
            // Complete button
            Button completeBtn = new Button("✓");
            completeBtn.setTooltip(new Tooltip("Mark as completed"));
            completeBtn.getStyleClass().addAll("action-icon-btn", "action-complete-btn");
            completeBtn.setOnAction(e -> {
                ActivityService.markAsCompleted(activity.getId());
                refreshAll();
            });

            // Cancel button
            Button cancelBtn = new Button("✕");
            cancelBtn.setTooltip(new Tooltip("Cancel activity"));
            cancelBtn.getStyleClass().addAll("action-icon-btn", "action-cancel-btn");
            cancelBtn.setOnAction(e -> {
                ActivityService.markAsCancelled(activity.getId());
                refreshAll();
            });

            actionBox.getChildren().addAll(completeBtn, cancelBtn);
        }

        // Delete button (always available)
        Button deleteBtn = new Button("🗑");
        deleteBtn.setTooltip(new Tooltip("Delete activity"));
        deleteBtn.getStyleClass().addAll("action-icon-btn", "action-delete-btn");
        deleteBtn.setOnAction(e -> {
            // Confirm before deleting
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            ThemeManager.applyTheme(confirm.getDialogPane());
            confirm.setTitle("Delete Activity");
            confirm.setHeaderText("Are you sure?");
            confirm.setContentText("Delete \"" + activity.getTitle() + "\"? This cannot be undone.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                ActivityService.deleteActivity(activity.getId());
                refreshAll();
            }
        });
        actionBox.getChildren().add(deleteBtn);

        card.getChildren().addAll(iconLabel, titleBox, badgeBox, statusPill, actionBox);
        return card;
    }

    // ── Weekly Breakdown ───────────────────────────────────────────────

    /**
     * Populates the right-side weekly breakdown panel.
     * Shows each day of the current week with the count of scheduled
     * and completed activities.
     */
    private void loadWeeklyBreakdown() {
        weeklyBreakdownList.getChildren().clear();

        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            String dateStr = day.toString();
            String dayName = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            // Count activities on this day
            long total = allActivities.stream()
                    .filter(a -> a.getDate().equals(dateStr)).count();
            long completed = allActivities.stream()
                    .filter(a -> a.getDate().equals(dateStr))
                    .filter(a -> a.getStatus() == Activity.Status.COMPLETED).count();

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));
            row.getStyleClass().add("this-week-row");

            // Highlight today
            if (day.equals(today)) {
                row.getStyleClass().add("this-week-row-today");
            }

            // Day name label
            Label dayLabel = new Label(dayName);
            dayLabel.setMinWidth(40);
            dayLabel.getStyleClass().add(day.equals(today) ? "this-week-day-today" : "this-week-day");

            // Date label
            Label dateLabel = new Label(day.format(DateTimeFormatter.ofPattern("MMM d")));
            dateLabel.setMinWidth(50);
            dateLabel.getStyleClass().add("this-week-date");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Count display
            Label countLabel = new Label(completed + "/" + total);
            countLabel.getStyleClass().add("this-week-count");

            // Small progress indicator
            ProgressBar dayBar = new ProgressBar(total > 0
                    ? (double) completed / total : 0);
            dayBar.setPrefWidth(60);
            dayBar.setPrefHeight(6);

            row.getChildren().addAll(dayLabel, dateLabel, spacer, countLabel, dayBar);
            weeklyBreakdownList.getChildren().add(row);
        }
    }

    // ── Quick Stats ────────────────────────────────────────────────────

    /**
     * Populates the Quick Stats card with aggregate counts.
     */
    private void loadQuickStats() {
        long total = allActivities.size();
        long completed = allActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED).count();
        long upcoming = allActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.UPCOMING).count();
        long cancelled = allActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.CANCELLED).count();

        totalWorkoutsLabel.setText(String.valueOf(total));
        completedCountLabel.setText(String.valueOf(completed));
        upcomingCountLabel.setText(String.valueOf(upcoming));
        cancelledCountLabel.setText(String.valueOf(cancelled));

        // Completion rate = completed / (completed + cancelled) — excludes upcoming
        long finished = completed + cancelled;
        double rate = finished > 0 ? (double) completed / finished * 100 : 0;
        completionRateLabel.setText(String.format("%.0f%%", rate));
    }

    // ── Add Workout Dialog ─────────────────────────────────────────────

    /**
     * Opens a dialog to add a new workout/activity.
     * The dialog collects title, type, date, time range, estimated
     * calories burned, and an optional description.
     */
    @FXML
    private void handleAddWorkout() {
        Dialog<Activity> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Add Activity");
        dialog.setHeaderText("Schedule a new activity");

        // Form fields
        TextField titleField = new TextField();
        titleField.setPromptText("Activity title (e.g. Morning Run)");

        ComboBox<Activity.ActivityType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Activity.ActivityType.values());
        typeCombo.setValue(Activity.ActivityType.WORKOUT);

        DatePicker datePicker = new DatePicker(LocalDate.now());

        TextField startField = new TextField("07:00");
        startField.setPromptText("Start time (HH:mm)");

        TextField endField = new TextField("08:00");
        endField.setPromptText("End time (HH:mm, up to 24:00)");

        TextField caloriesField = new TextField("0");
        caloriesField.setPromptText("Est. calories burned");

        TextField descField = new TextField();
        descField.setPromptText("Description (optional)");

        // Layout in a GridPane
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Type:"), 0, 1);
        grid.add(typeCombo, 1, 1);
        grid.add(new Label("Date:"), 0, 2);
        grid.add(datePicker, 1, 2);
        grid.add(new Label("Start Time:"), 0, 3);
        grid.add(startField, 1, 3);
        grid.add(new Label("End Time:"), 0, 4);
        grid.add(endField, 1, 4);
        grid.add(new Label("Calories:"), 0, 5);
        grid.add(caloriesField, 1, 5);
        grid.add(new Label("Description:"), 0, 6);
        grid.add(descField, 1, 6);

        dialog.getDialogPane().setContent(grid);

        // OK / Cancel buttons
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Convert result to Activity when OK is pressed
        dialog.setResultConverter(buttonType -> {
            if (buttonType == addButtonType) {
                String title = titleField.getText().trim();
                if (title.isEmpty()) return null;

                Activity newActivity = new Activity(
                        currentUser.getId(),
                        title,
                        typeCombo.getValue(),
                        datePicker.getValue().toString(),
                        startField.getText().trim(),
                        endField.getText().trim()
                );

                // Set optional fields
                String desc = descField.getText().trim();
                if (!desc.isEmpty()) {
                    newActivity.setDescription(desc);
                }
                try {
                    newActivity.setCaloriesBurned(Integer.parseInt(caloriesField.getText().trim()));
                } catch (NumberFormatException ex) {
                    newActivity.setCaloriesBurned(0);
                }

                return newActivity;
            }
            return null;
        });

        // Show dialog and handle result
        Optional<Activity> result = dialog.showAndWait();
        result.ifPresent(activity -> {
            // Check for scheduling conflicts
            List<Activity> conflicts;
            try {
                conflicts = ActivityService.findConflicts(
                        currentUser.getId(), activity.getDate(),
                        activity.getStartTime(), activity.getEndTime(), null);
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                ThemeManager.applyTheme(alert.getDialogPane());
                alert.setTitle("Invalid activity time");
                alert.setHeaderText(ex.getMessage());
                alert.showAndWait();
                return;
            }

            if (!conflicts.isEmpty()) {
                // Apply the same strict conflict rule as the calendar and meal planner.
                String conflictNames = conflicts.stream()
                        .map(Activity::getTitle)
                        .collect(Collectors.joining(", "));
                Alert warn = new Alert(Alert.AlertType.ERROR);
                ThemeManager.applyTheme(warn.getDialogPane());
                warn.setTitle("Schedule Conflict");
                warn.setHeaderText("Time overlap detected");
                warn.setContentText("This overlaps with: " + conflictNames
                        + "\n\nChoose a non-overlapping time before adding this activity.");
                warn.showAndWait();
                return;
            }

            ActivityService.addActivity(activity);
            refreshAll();
        });
    }

    // ── Refresh ────────────────────────────────────────────────────────

    /**
     * Reloads all data from the service layer and refreshes every section.
     */
    private void refreshAll() {
        allActivities = ActivityService.getAllActivities(currentUser.getId());
        loadSummaryCards();
        loadActivityList();
        loadWeeklyBreakdown();
        loadQuickStats();
    }

    // ── Utility ────────────────────────────────────────────────────────

    /**
     * Returns an emoji icon for the given activity type.
     *
     * @param type the activity type
     * @return an emoji string representing the type
     */
    private String getTypeIcon(Activity.ActivityType type) {
        return switch (type) {
            case WORKOUT -> "💪";
            case MEAL -> "🍽️";
            case STUDY -> "📚";
            case REST -> "😴";
            case OTHER -> "📌";
        };
    }
}
