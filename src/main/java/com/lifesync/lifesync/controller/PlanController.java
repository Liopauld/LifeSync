package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.ActivityService;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.GeminiService;
import com.lifesync.lifesync.service.NutritionService;
import com.lifesync.lifesync.service.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for the Plan &amp; Calendar view (plan-view.fxml).
 *
 * Manages a custom mini calendar, a daily timeline view showing activities
 * as cards, an event detail panel, and quick action buttons for adding,
 * editing, and managing scheduled activities.
 *
 * <p>The calendar is built programmatically using a {@link GridPane} where
 * each date cell is a clickable {@link Label}. Selecting a date reloads
 * the timeline for that day.</p>
 *
 * <h3>Component Overview</h3>
 * <ul>
 *   <li><b>Mini Calendar</b> – A 7-column grid (Sun–Sat) with clickable
 *       date cells. Highlights today and the selected date.</li>
 *   <li><b>Day Timeline</b> – Hourly slots from 6 AM to 9 PM. Activities
 *       are rendered as coloured cards; empty slots show an "add" button.</li>
 *   <li><b>Event Detail Panel</b> – Slide-in panel that shows full details
 *       for a selected activity, including mark-as-done and edit actions.</li>
 *   <li><b>Goals Progress</b> – Three progress bars tracking nutrition,
 *       activity, and overall plan completion for the selected date.</li>
 *   <li><b>Upcoming Plans</b> – A sidebar list of the next 5 scheduled
 *       activities across all future dates.</li>
 * </ul>
 */
public class PlanController {

    // ── FXML-injected fields ───────────────────────────────────────────

    // Date navigation
    /** Displays the currently selected date in short format (e.g. "Apr 23, 2025"). */
    @FXML private Label currentDateLabel;

    /** Displays the full date heading inside the day timeline (e.g. "Wednesday, April 23, 2025"). */
    @FXML private Label dayViewDateLabel;

    /** Toggle button for the "Day" view mode. */
    @FXML private ToggleButton dayToggle;

    /** Toggle button for the "Week" view mode. */
    @FXML private ToggleButton weekToggle;

    /** Toggle button for the "Month" view mode. */
    @FXML private ToggleButton monthToggle;

    /** Toggle button for 24-hour mode. */
    @FXML private ToggleButton toggle24Hour;

    // Mini calendar
    /** Label showing the currently displayed month and year in the mini calendar. */
    @FXML private Label calendarMonthLabel;

    /** Grid pane used to render the mini calendar dates. */
    @FXML private GridPane calendarGrid;

    // Goals progress
    /** Progress bar for the nutrition (calorie) goal. */
    @FXML private ProgressBar nutritionGoalBar;

    /** Percentage label for the nutrition goal. */
    @FXML private Label nutritionGoalLabel;

    /** Progress bar for the activity (workout) goal. */
    @FXML private ProgressBar activityGoalBar;

    /** Percentage label for the activity goal. */
    @FXML private Label activityGoalLabel;

    /** Progress bar for the overall plan completion goal. */
    @FXML private ProgressBar planGoalBar;

    /** Percentage label for the plan goal. */
    @FXML private Label planGoalLabel;

    // Timeline
    /** Container for the hourly time-slot rows in the day timeline. */
    @FXML private VBox timelineContainer;

    // Event detail panel
    /** The detail panel container; hidden/shown via visible and managed properties. */
    @FXML private VBox eventDetailPanel;

    /** Title label in the event detail panel. */
    @FXML private Label detailTitle;

    /** Time range label in the event detail panel. */
    @FXML private Label detailTime;

    /** Status pill label in the event detail panel. */
    @FXML private Label detailStatus;

    /** Description label in the event detail panel. */
    @FXML private Label detailDescription;

    /** Checkbox to toggle the completion status of the selected activity. */
    @FXML private CheckBox markDoneCheckbox;

    // Upcoming plans
    /** Container for the list of upcoming plan rows. */
    @FXML private VBox upcomingPlansList;

    // Quick action buttons
    @FXML private Button btnViewCalendar;
    @FXML private Button btnReschedule;

    // ── Internal state ─────────────────────────────────────────────────

    /** View mode: Day, Week, or Month. */
    public enum ViewMode {
        DAY,
        WEEK,
        MONTH
    }

    private ViewMode currentViewMode = ViewMode.DAY;

    /** The date currently being viewed in the timeline. */
    private LocalDate selectedDate;

    /** The month currently displayed in the mini calendar. */
    private YearMonth displayedMonth;

    /** The activity currently shown in the detail panel (if any). */
    private Activity selectedActivity;

    /** The logged-in user. */
    private User currentUser;

    // ── Initialisation ─────────────────────────────────────────────────

    /**
     * Called automatically after FXML injection.
     * Initialises the calendar, timeline, and goals to today's date.
     */
    @FXML
    public void initialize() {
        currentUser = AuthService.getCurrentUser();
        selectedDate = LocalDate.now();
        displayedMonth = YearMonth.from(selectedDate);

        // Set up toggle group so only one view mode can be active at a time
        ToggleGroup viewGroup = new ToggleGroup();
        dayToggle.setToggleGroup(viewGroup);
        weekToggle.setToggleGroup(viewGroup);
        monthToggle.setToggleGroup(viewGroup);
        dayToggle.setSelected(true);

        if (toggle24Hour != null && LocalTime.now().getHour() >= 21) {
            toggle24Hour.setSelected(true);
        }

        // Listener for view mode toggles
        viewGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                // Keep the previous selection active if user attempts to deselect all
                if (oldVal != null) {
                    oldVal.setSelected(true);
                }
                return;
            }
            if (newVal == dayToggle) {
                currentViewMode = ViewMode.DAY;
            } else if (newVal == weekToggle) {
                currentViewMode = ViewMode.WEEK;
            } else if (newVal == monthToggle) {
                currentViewMode = ViewMode.MONTH;
            }
            refreshAll();
        });

        refreshAll();
    }

    // ── Date Navigation ────────────────────────────────────────────────

    /** Moves to the previous day/week/month and refreshes the view. */
    @FXML
    private void handlePrevDay() {
        if (currentViewMode == ViewMode.WEEK) {
            selectedDate = selectedDate.minusWeeks(1);
        } else if (currentViewMode == ViewMode.MONTH) {
            displayedMonth = displayedMonth.minusMonths(1);
            selectedDate = displayedMonth.atDay(Math.min(selectedDate.getDayOfMonth(), displayedMonth.lengthOfMonth()));
        } else {
            selectedDate = selectedDate.minusDays(1);
        }
        displayedMonth = YearMonth.from(selectedDate);
        refreshAll();
    }

    /** Moves to the next day/week/month and refreshes the view. */
    @FXML
    private void handleNextDay() {
        if (currentViewMode == ViewMode.WEEK) {
            selectedDate = selectedDate.plusWeeks(1);
        } else if (currentViewMode == ViewMode.MONTH) {
            displayedMonth = displayedMonth.plusMonths(1);
            selectedDate = displayedMonth.atDay(Math.min(selectedDate.getDayOfMonth(), displayedMonth.lengthOfMonth()));
        } else {
            selectedDate = selectedDate.plusDays(1);
        }
        displayedMonth = YearMonth.from(selectedDate);
        refreshAll();
    }

    /** Jumps to today and refreshes the view. */
    @FXML
    private void handleToday() {
        selectedDate = LocalDate.now();
        displayedMonth = YearMonth.from(selectedDate);
        refreshAll();
    }

    /** Toggles 24-hour mode on and off. */
    @FXML
    private void handleToggle24Hour() {
        refreshAll();
    }

    // ── Mini Calendar Navigation ───────────────────────────────────────

    /** Shows the previous month in the mini calendar. */
    @FXML
    private void handlePrevMonth() {
        displayedMonth = displayedMonth.minusMonths(1);
        buildCalendar();
    }

    /** Shows the next month in the mini calendar. */
    @FXML
    private void handleNextMonth() {
        displayedMonth = displayedMonth.plusMonths(1);
        buildCalendar();
    }

    // ── Core Refresh ───────────────────────────────────────────────────

    /**
     * Refreshes all components: date labels, calendar, timeline, goals,
     * and upcoming plans.
     */
    private void refreshAll() {
        updateDateLabels();
        buildCalendar();
        if (currentViewMode == ViewMode.DAY) {
            loadTimeline();
        } else if (currentViewMode == ViewMode.WEEK) {
            renderWeekView();
        } else if (currentViewMode == ViewMode.MONTH) {
            renderMonthView();
        }
        loadGoalsProgress();
        loadUpcomingPlans();
        hideDetailPanel();
    }

    /**
     * Updates the date labels in the navigation bar and timeline header.
     */
    private void updateDateLabels() {
        if (currentViewMode == ViewMode.WEEK) {
            LocalDate monday = selectedDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate sunday = monday.plusDays(6);
            DateTimeFormatter navFmt = DateTimeFormatter.ofPattern("MMM d");
            currentDateLabel.setText(monday.format(navFmt) + " - " + sunday.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
            dayViewDateLabel.setText("Week: " + monday.format(DateTimeFormatter.ofPattern("MMMM d")) + " – " + sunday.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        } else if (currentViewMode == ViewMode.MONTH) {
            currentDateLabel.setText(displayedMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + displayedMonth.getYear());
            dayViewDateLabel.setText(displayedMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + displayedMonth.getYear() + " — Monthly Overview");
        } else {
            DateTimeFormatter navFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
            currentDateLabel.setText(selectedDate.format(navFmt));
            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
            dayViewDateLabel.setText(selectedDate.format(dayFmt));
        }
    }

    // ── Mini Calendar Builder ──────────────────────────────────────────

    /**
     * Builds the mini calendar grid for the currently displayed month.
     *
     * <p>The calendar is a 7-column {@link GridPane}:</p>
     * <ul>
     *   <li>Row 0: Day-of-week headers (Sun–Sat)</li>
     *   <li>Rows 1–6: Date cells</li>
     * </ul>
     *
     * <p>Each date cell is a clickable {@link Label}. The selected date and
     * today receive special styling via CSS classes. Clicking a date updates
     * the timeline to show that day's activities.</p>
     */
    private void buildCalendar() {
        calendarGrid.getChildren().clear();
        calendarMonthLabel.setText(displayedMonth.getMonth().getDisplayName(
                TextStyle.FULL, Locale.ENGLISH) + " " + displayedMonth.getYear());

        // Day-of-week headers (row 0)
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int col = 0; col < 7; col++) {
            Label dayLabel = new Label(dayNames[col]);
            dayLabel.getStyleClass().add("calendar-day-header");
            dayLabel.setAlignment(Pos.CENTER);
            dayLabel.setPrefSize(34, 20);
            GridPane.setHalignment(dayLabel, HPos.CENTER);
            calendarGrid.add(dayLabel, col, 0);
        }

        // Determine which column the 1st of the month falls on (Sunday = 0)
        LocalDate firstOfMonth = displayedMonth.atDay(1);
        int startColumn = firstOfMonth.getDayOfWeek().getValue() % 7;
        int daysInMonth = displayedMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        int row = 1;
        int col = startColumn;

        // Populate date cells
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate thisDate = displayedMonth.atDay(day);
            Label dateLabel = new Label(String.valueOf(day));
            dateLabel.setAlignment(Pos.CENTER);
            dateLabel.setPrefSize(34, 30);
            dateLabel.getStyleClass().add("calendar-date");

            // Highlight today with a distinct style
            if (thisDate.equals(today)) {
                dateLabel.getStyleClass().add("calendar-today");
            }

            // Highlight the currently selected date
            if (thisDate.equals(selectedDate)) {
                dateLabel.getStyleClass().add("calendar-selected");
            }

            // Click handler: select this date and reload the timeline
            final LocalDate clickedDate = thisDate;
            dateLabel.setOnMouseClicked(e -> {
                selectedDate = clickedDate;
                refreshAll();
            });

            GridPane.setHalignment(dateLabel, HPos.CENTER);
            calendarGrid.add(dateLabel, col, row);

            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
    }

    // ── Timeline Builder ───────────────────────────────────────────────

    private int getHour(String timeStr) {
        if (timeStr == null || !timeStr.contains(":")) return 0;
        try {
            return Integer.parseInt(timeStr.split(":")[0]);
        } catch (Exception ex) {
            return 0;
        }
    }

    private int getMinute(String timeStr) {
        if (timeStr == null || !timeStr.contains(":")) return 0;
        try {
            return Integer.parseInt(timeStr.split(":")[1]);
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean activityCoversHour(Activity a, int hour) {
        int sh = getHour(a.getStartTime());
        int eh = getHour(a.getEndTime());
        int em = getMinute(a.getEndTime());
        return sh <= hour && (eh > hour || (eh == hour && em > 0));
    }

    /**
     * Builds the day timeline view with full support for 24-hour mode,
     * multi-hour time block coverage spanning, and nested sub-schedules.
     */
    private void loadTimeline() {
        timelineContainer.getChildren().clear();

        if (currentUser == null) return;

        List<Activity> allActivities = ActivityService.getActivitiesForDate(
                currentUser.getId(), selectedDate.toString());

        boolean isPast9pm = selectedDate.equals(LocalDate.now()) && LocalTime.now().getHour() >= 21;
        boolean hasOffHourActivities = allActivities.stream().anyMatch(a -> {
            int sh = getHour(a.getStartTime());
            int eh = getHour(a.getEndTime());
            int em = getMinute(a.getEndTime());
            return sh < 6 || sh >= 21 || eh > 21 || (eh == 21 && em > 0);
        });

        if (toggle24Hour != null) {
            if (isPast9pm || hasOffHourActivities) {
                if (!toggle24Hour.isSelected()) {
                    toggle24Hour.setSelected(true);
                }
            }
        }

        boolean is24HourMode = (toggle24Hour != null && toggle24Hour.isSelected())
                || isPast9pm || hasOffHourActivities;

        int startHour = is24HourMode ? 0 : 6;
        int endHour = is24HourMode ? 23 : 21;

        List<Activity> activeRoots = allActivities.stream()
                .filter(a -> a.getStatus() != Activity.Status.CANCELLED && a.getParentId() == null)
                .toList();

        for (int hour = startHour; hour <= endHour; hour++) {
            final int slotHour = hour;
            String displayTime = LocalTime.of(hour, 0)
                    .format(DateTimeFormatter.ofPattern("h:mm a"));

            HBox slot = new HBox(15);
            slot.setAlignment(Pos.TOP_LEFT);
            slot.setPadding(new Insets(5, 0, 5, 0));
            slot.getStyleClass().add("timeline-slot");

            Label timeLabel = new Label(displayTime);
            timeLabel.getStyleClass().add("timeline-time");
            timeLabel.setMinWidth(80);
            timeLabel.setAlignment(Pos.TOP_RIGHT);

            Region line = new Region();
            line.setPrefWidth(2);
            line.setMinWidth(2);
            line.setPrefHeight(60);
            line.getStyleClass().add("timeline-line");

            List<Activity> rootsStarting = activeRoots.stream()
                    .filter(a -> getHour(a.getStartTime()) == slotHour)
                    .toList();

            List<Activity> rootsOngoing = activeRoots.stream()
                    .filter(a -> getHour(a.getStartTime()) < slotHour && activityCoversHour(a, slotHour))
                    .toList();

            if (!rootsStarting.isEmpty()) {
                VBox cardStack = new VBox(8);
                HBox.setHgrow(cardStack, Priority.ALWAYS);
                for (Activity a : rootsStarting) {
                    cardStack.getChildren().add(createActivityCard(a));
                }
                slot.getChildren().addAll(timeLabel, line, cardStack);
            } else if (!rootsOngoing.isEmpty()) {
                Activity ongoingParent = rootsOngoing.get(0);
                VBox coveredBlock = createCoveredSlotCard(ongoingParent, slotHour);
                HBox.setHgrow(coveredBlock, Priority.ALWAYS);
                slot.getChildren().addAll(timeLabel, line, coveredBlock);
            } else {
                Label addBtn = new Label("+");
                addBtn.getStyleClass().add("timeline-add-btn");
                addBtn.setOnMouseClicked(e -> showAddEventDialog(slotHour, null));
                slot.getChildren().addAll(timeLabel, line, addBtn);
            }

            timelineContainer.getChildren().add(slot);
        }
    }

    /**
     * Creates a continuous spanning coverage card for an ongoing activity
     * that covers the specified slot hour (e.g. 8am to 12pm multi-hour block).
     */
    private VBox createCoveredSlotCard(Activity parent, int hour) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("timeline-activity-card", "timeline-covered-slot");
        card.setPadding(new Insets(8, 14, 8, 14));

        String typeClass = "card-" + parent.getType().name().toLowerCase();
        card.getStyleClass().add(typeClass);
        card.setStyle("-fx-border-style: dashed; -fx-border-width: 1.5; -fx-opacity: 0.92;");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label ongoingIcon = new Label("↳ " + getTypeIcon(parent.getType()));
        ongoingIcon.setStyle("-fx-font-size: 13px;");

        int sh = getHour(parent.getStartTime());
        int currentHourOfBlock = hour - sh + 1;
        Label parentTitle = new Label(parent.getTitle() + " (Covered • " + parent.getStartTime() + " - " + parent.getEndTime() + " • Hour " + currentHourOfBlock + ")");
        parentTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button addSubBtn = new Button("➕ Add Sub-schedule");
        addSubBtn.setStyle("-fx-font-size: 11px; -fx-background-color: transparent; -fx-border-color: #F5A623; -fx-border-radius: 4; -fx-padding: 2 6; -fx-cursor: hand;");
        addSubBtn.setOnAction(e -> showAddEventDialog(hour, parent));

        header.getChildren().addAll(ongoingIcon, parentTitle, sp, addSubBtn);
        card.getChildren().add(header);

        List<Activity> subs = ActivityService.getSubActivities(parent.getId()).stream()
                .filter(s -> s.getStatus() != Activity.Status.CANCELLED && getHour(s.getStartTime()) == hour)
                .toList();

        if (!subs.isEmpty()) {
            VBox subStack = new VBox(4);
            subStack.setPadding(new Insets(4, 0, 4, 16));
            for (Activity sub : subs) {
                HBox subRow = new HBox(8);
                subRow.setAlignment(Pos.CENTER_LEFT);
                Label dot = new Label(sub.getStatus() == Activity.Status.COMPLETED ? "✅" : "📌");
                Label subTitle = new Label(sub.getStartTime() + " - " + sub.getEndTime() + ": " + sub.getTitle());
                subTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");
                HBox.setHgrow(subTitle, Priority.ALWAYS);

                Button delSub = new Button("✕");
                delSub.getStyleClass().add("close-btn");
                delSub.setOnAction(ev -> {
                    ActivityService.deleteActivity(sub.getId());
                    refreshAll();
                });

                subRow.getChildren().addAll(dot, subTitle, delSub);
                subRow.setOnMouseClicked(ev -> showActivityDetail(sub));
                subStack.getChildren().add(subRow);
            }
            card.getChildren().add(subStack);
        }

        card.setOnMouseClicked(e -> {
            if (e.getTarget() != addSubBtn) {
                showActivityDetail(parent);
            }
        });

        return card;
    }

    /**
     * Renders the 7-day weekly schedule overview in the main plan container.
     */
    private void renderWeekView() {
        timelineContainer.getChildren().clear();
        if (currentUser == null) return;

        LocalDate monday = selectedDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        VBox weekContainer = new VBox(12);
        weekContainer.setPadding(new Insets(8));

        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            List<Activity> dayActs = ActivityService.getActivitiesForDate(currentUser.getId(), day.toString());

            VBox dayCard = new VBox(8);
            dayCard.getStyleClass().add("dashboard-card");
            dayCard.setPadding(new Insets(12, 16, 12, 16));

            if (day.equals(selectedDate)) {
                dayCard.setStyle("-fx-border-color: #F5A623; -fx-border-width: 2; -fx-border-radius: 12;");
            } else if (day.equals(LocalDate.now())) {
                dayCard.setStyle("-fx-border-color: #7C6FE4; -fx-border-width: 1.5; -fx-border-radius: 12;");
            }

            HBox dayHeader = new HBox(10);
            dayHeader.setAlignment(Pos.CENTER_LEFT);

            String dayTitle = day.format(DateTimeFormatter.ofPattern("EEEE, MMM d"));
            if (day.equals(LocalDate.now())) dayTitle += " (Today)";
            Label titleLabel = new Label(dayTitle);
            titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label countBadge = new Label(dayActs.size() + " event" + (dayActs.size() == 1 ? "" : "s"));
            countBadge.getStyleClass().add("category-tag");

            Button addBtn = new Button("➕");
            addBtn.getStyleClass().add("action-icon-btn");
            final LocalDate targetDay = day;
            addBtn.setOnAction(e -> {
                selectedDate = targetDay;
                showAddEventDialog(9);
            });

            dayHeader.getChildren().addAll(titleLabel, spacer, countBadge, addBtn);
            dayCard.getChildren().add(dayHeader);

            if (dayActs.isEmpty()) {
                Label empty = new Label("No activities scheduled.");
                empty.getStyleClass().add("muted-text");
                empty.setPadding(new Insets(4, 0, 4, 10));
                dayCard.getChildren().add(empty);
            } else {
                VBox eventsBox = new VBox(6);
                for (Activity a : dayActs) {
                    HBox row = new HBox(8);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("upcoming-row");
                    row.setStyle("-fx-cursor: hand;");
                    row.setOnMouseClicked(e -> showActivityDetail(a));

                    Label timeLabel = new Label(a.getStartTime() + " - " + a.getEndTime());
                    timeLabel.getStyleClass().add("upcoming-time");
                    timeLabel.setMinWidth(95);

                    Label iconLabel = new Label(getTypeIcon(a.getType()));
                    iconLabel.getStyleClass().add("card-icon");

                    Label actTitle = new Label(a.getTitle());
                    actTitle.getStyleClass().add("upcoming-title");

                    Region rSpacer = new Region();
                    HBox.setHgrow(rSpacer, Priority.ALWAYS);

                    Label statusPill = new Label(a.getStatus().name());
                    statusPill.getStyleClass().addAll("status-pill",
                            a.getStatus() == Activity.Status.COMPLETED ? "status-completed" : "status-upcoming");

                    row.getChildren().addAll(timeLabel, iconLabel, actTitle, rSpacer, statusPill);
                    eventsBox.getChildren().add(row);
                }
                dayCard.getChildren().add(eventsBox);
            }

            weekContainer.getChildren().add(dayCard);
        }

        timelineContainer.getChildren().add(weekContainer);
    }

    /**
     * Renders a full monthly calendar grid with daily event count pills and previews.
     */
    private void renderMonthView() {
        timelineContainer.getChildren().clear();
        if (currentUser == null) return;

        VBox monthContainer = new VBox(10);
        monthContainer.setPadding(new Insets(8));

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setAlignment(Pos.CENTER);

        for (int c = 0; c < 7; c++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7.0);
            col.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(col);
        }

        String[] days = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int col = 0; col < 7; col++) {
            Label header = new Label(days[col]);
            header.getStyleClass().add("calendar-day-header");
            header.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
            GridPane.setHalignment(header, HPos.CENTER);
            grid.add(header, col, 0);
        }

        LocalDate firstOfMonth = displayedMonth.atDay(1);
        int startCol = firstOfMonth.getDayOfWeek().getValue() % 7;
        int daysInMonth = displayedMonth.lengthOfMonth();
        LocalDate today = LocalDate.now();

        int row = 1;
        int col = startCol;

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate thisDate = displayedMonth.atDay(day);
            List<Activity> dayActs = ActivityService.getActivitiesForDate(currentUser.getId(), thisDate.toString());

            VBox cell = new VBox(4);
            cell.setMinHeight(75);
            cell.setPrefHeight(85);
            cell.setPadding(new Insets(6));
            cell.getStyleClass().add("dashboard-card");
            cell.setStyle("-fx-cursor: hand; -fx-background-radius: 8; -fx-border-radius: 8;");

            if (thisDate.equals(selectedDate)) {
                cell.setStyle("-fx-cursor: hand; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #F5A623; -fx-border-width: 2;");
            } else if (thisDate.equals(today)) {
                cell.setStyle("-fx-cursor: hand; -fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #7C6FE4; -fx-border-width: 1.5;");
            }

            HBox top = new HBox(4);
            top.setAlignment(Pos.CENTER_LEFT);
            Label numLabel = new Label(String.valueOf(day));
            numLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            top.getChildren().addAll(numLabel, sp);

            if (!dayActs.isEmpty()) {
                Label badge = new Label(String.valueOf(dayActs.size()));
                badge.setStyle("-fx-background-color: #F5A623; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 1 5;");
                top.getChildren().add(badge);
            }
            cell.getChildren().add(top);

            for (int aIdx = 0; aIdx < Math.min(dayActs.size(), 2); aIdx++) {
                Activity act = dayActs.get(aIdx);
                Label actLabel = new Label(getTypeIcon(act.getType()) + " " + act.getTitle());
                actLabel.setStyle("-fx-font-size: 10px; -fx-text-overrun: ellipsis;");
                actLabel.setMaxWidth(Double.MAX_VALUE);
                cell.getChildren().add(actLabel);
            }
            if (dayActs.size() > 2) {
                Label more = new Label("+" + (dayActs.size() - 2) + " more");
                more.getStyleClass().add("muted-text");
                more.setStyle("-fx-font-size: 9px;");
                cell.getChildren().add(more);
            }

            final LocalDate clickedDate = thisDate;
            cell.setOnMouseClicked(e -> {
                selectedDate = clickedDate;
                if (e.getClickCount() >= 2) {
                    dayToggle.setSelected(true);
                } else {
                    refreshAll();
                }
            });

            grid.add(cell, col, row);

            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }

        monthContainer.getChildren().add(grid);
        timelineContainer.getChildren().add(monthContainer);
    }

    /**
     * Creates a styled activity card for the timeline.
     *
     * <p>The card contains a header row (icon, title, status pill) and a
     * subtitle showing the time range and optional description. Clicking
     * the card opens the event detail panel.</p>
     *
     * @param activity the activity to display
     * @return a {@link VBox} styled as an activity card
     */
    private VBox createActivityCard(Activity activity) {
        VBox card = new VBox(6);
        card.getStyleClass().add("timeline-activity-card");
        card.setPadding(new Insets(10, 15, 10, 15));

        // Activity type determines the card colour class (e.g. "card-workout")
        String typeClass = "card-" + activity.getType().name().toLowerCase();
        card.getStyleClass().add(typeClass);

        // ── Header row: icon + title + spacer + duration badge + status pill + add sub-plan ──
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        // Type icon (emoji)
        String icon = getTypeIcon(activity.getType());
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("card-icon");

        // Title
        Label titleLabel = new Label(activity.getTitle());
        titleLabel.getStyleClass().add("card-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Duration Badge
        int sh = getHour(activity.getStartTime());
        int eh = getHour(activity.getEndTime());
        int durationHrs = Math.max(1, eh - sh);
        Label durationBadge = new Label("⏱ " + durationHrs + "h (" + activity.getStartTime() + " - " + activity.getEndTime() + ")");
        durationBadge.getStyleClass().add("category-tag");

        // Status pill (COMPLETED / UPCOMING / etc.)
        Label statusPill = new Label(activity.getStatus().name());
        statusPill.getStyleClass().addAll("status-pill",
                activity.getStatus() == Activity.Status.COMPLETED
                        ? "status-completed" : "status-upcoming");

        Button addSubBtn = new Button("➕ Sub-plan");
        addSubBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6; -fx-cursor: hand;");
        addSubBtn.setOnAction(e -> showAddEventDialog(sh, activity));

        header.getChildren().addAll(iconLabel, titleLabel, spacer, durationBadge, statusPill, addSubBtn);

        // ── Subtitle: time range + optional description ───────────
        String subtitle = activity.getStartTime() + " - " + activity.getEndTime();
        if (activity.getDescription() != null && !activity.getDescription().isEmpty()) {
            subtitle += " • " + activity.getDescription();
        }
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("card-subtitle");

        card.getChildren().addAll(header, subtitleLabel);

        // Display nested sub-schedules / subjects scheduled within this parent
        List<Activity> subs = ActivityService.getSubActivities(activity.getId()).stream()
                .filter(s -> s.getStatus() != Activity.Status.CANCELLED)
                .toList();

        if (!subs.isEmpty()) {
            VBox subBox = new VBox(4);
            subBox.setPadding(new Insets(4, 0, 2, 16));
            Label subHead = new Label("📋 Sub-schedules / Subjects inside " + activity.getTitle() + ":");
            subHead.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
            subBox.getChildren().add(subHead);

            for (Activity sub : subs) {
                HBox subRow = new HBox(6);
                subRow.setAlignment(Pos.CENTER_LEFT);
                Label subIcon = new Label(sub.getStatus() == Activity.Status.COMPLETED ? "✅" : "▫️");
                Label subLabel = new Label(sub.getStartTime() + " - " + sub.getEndTime() + ": " + sub.getTitle());
                subLabel.setStyle("-fx-font-size: 11px;");
                subRow.getChildren().addAll(subIcon, subLabel);
                subBox.getChildren().add(subRow);
            }
            card.getChildren().add(subBox);
        }

        // Click to show detail panel
        card.setOnMouseClicked(e -> {
            if (e.getTarget() != addSubBtn) {
                showActivityDetail(activity);
            }
        });

        return card;
    }

    /**
     * Returns an emoji icon appropriate for the given activity type.
     *
     * @param type the activity type
     * @return an emoji string
     */
    private String getTypeIcon(Activity.ActivityType type) {
        return switch (type) {
            case WORKOUT -> "💪";
            case MEAL    -> "🍽️";
            case STUDY   -> "📚";
            case REST    -> "😴";
            case OTHER   -> "📌";
        };
    }

    // ── Event Detail Panel ─────────────────────────────────────────────

    /**
     * Shows the detail panel for a selected activity, populating all
     * fields and making the panel visible.
     *
     * @param activity the activity to display details for
     */
    private void showActivityDetail(Activity activity) {
        selectedActivity = activity;
        eventDetailPanel.setVisible(true);
        eventDetailPanel.setManaged(true);

        if (activity.getParentId() != null) {
            detailTitle.setText(activity.getTitle() + " ↳ (Sub-schedule)");
        } else {
            detailTitle.setText(activity.getTitle());
        }
        detailTime.setText(activity.getStartTime() + " - " + activity.getEndTime());

        // Update status pill text and style
        detailStatus.setText(activity.getStatus().name());
        detailStatus.getStyleClass().removeAll(
                "status-completed", "status-upcoming", "status-cancelled");
        detailStatus.getStyleClass().add(
                "status-" + activity.getStatus().name().toLowerCase());

        detailDescription.setText(activity.getDescription() != null
                ? activity.getDescription() : "No description.");

        // Sync checkbox state
        markDoneCheckbox.setSelected(
                activity.getStatus() == Activity.Status.COMPLETED);
        markDoneCheckbox.setDisable(
                activity.getStatus() == Activity.Status.CANCELLED);
    }

    /** Hides the event detail panel (FXML action handler). */
    @FXML
    private void handleCloseDetail() {
        hideDetailPanel();
    }

    /** Hides the detail panel and clears the activity selection. */
    private void hideDetailPanel() {
        eventDetailPanel.setVisible(false);
        eventDetailPanel.setManaged(false);
        selectedActivity = null;
    }

    /**
     * Toggles the completion status of the selected activity.
     * If checked, marks the activity as COMPLETED; if unchecked, reverts to UPCOMING.
     */
    @FXML
    private void handleMarkDone() {
        if (selectedActivity == null) return;

        if (markDoneCheckbox.isSelected()) {
            ActivityService.markAsCompleted(selectedActivity.getId());
            selectedActivity.setStatus(Activity.Status.COMPLETED);
        } else {
            // Un-marking: set back to UPCOMING
            selectedActivity.setStatus(Activity.Status.UPCOMING);
            ActivityService.updateActivity(selectedActivity);
        }

        refreshAll();
        showActivityDetail(selectedActivity);
    }

    /**
     * Opens the edit dialog for the currently selected activity.
     * Delegates to {@link #showEditEventDialog(Activity)}.
     */
    @FXML
    private void handleEditEvent() {
        if (selectedActivity == null) return;
        showEditEventDialog(selectedActivity);
    }

    // ── Add / Edit Event Dialogs ───────────────────────────────────────

    /**
     * Shows a dialog to add a new top-level activity at the specified hour.
     */
    private void showAddEventDialog(int hour) {
        showAddEventDialog(hour, null);
    }

    /**
     * Shows a dialog to add a new activity or nested sub-schedule at the specified hour.
     *
     * @param hour           the starting hour (24-hour format)
     * @param parentActivity optional parent activity if this is a sub-schedule
     */
    private void showAddEventDialog(int hour, Activity parentActivity) {
        Dialog<Activity> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        if (parentActivity != null) {
            dialog.setTitle("Add Sub-schedule");
            dialog.setHeaderText("Add plan inside: " + parentActivity.getTitle()
                    + " (" + parentActivity.getStartTime() + " - " + parentActivity.getEndTime() + ")");
        } else {
            dialog.setTitle("Add Event");
            dialog.setHeaderText("Create a new activity");
        }

        // ── Form fields ───────────────────────────────────────────
        TextField titleField = new TextField();
        titleField.setPromptText(parentActivity != null ? "Sub-task or Subject title (e.g. Math, Chemistry)" : "Event title");

        TextField descField = new TextField();
        descField.setPromptText("Description (optional)");

        ComboBox<Activity.ActivityType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Activity.ActivityType.values());
        typeCombo.setValue(parentActivity != null ? parentActivity.getType() : Activity.ActivityType.OTHER);

        TextField startField = new TextField(String.format("%02d:00", hour));
        startField.setPromptText("Start time (HH:mm)");

        int defaultEndHour = hour + 1;
        if (parentActivity != null) {
            int pEnd = getHour(parentActivity.getEndTime());
            if (defaultEndHour > pEnd) defaultEndHour = pEnd;
        }
        TextField endField = new TextField(String.format("%02d:00", defaultEndHour));
        endField.setPromptText("End time (HH:mm)");

        // ── Layout ────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Type:"), 0, 2);
        grid.add(typeCombo, 1, 2);
        grid.add(new Label("Start Time:"), 0, 3);
        grid.add(startField, 1, 3);
        grid.add(new Label("End Time:"), 0, 4);
        grid.add(endField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // ── Buttons ───────────────────────────────────────────────
        ButtonType addButton = new ButtonType(parentActivity != null ? "Add Sub-schedule" : "Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == addButton) {
                String title = titleField.getText().trim();
                if (title.isEmpty()) return null;

                Activity newActivity = new Activity(
                        currentUser.getId(), title, typeCombo.getValue(),
                        parentActivity != null ? parentActivity.getDate() : selectedDate.toString(),
                        startField.getText().trim(), endField.getText().trim());
                newActivity.setDescription(descField.getText().trim());
                if (parentActivity != null) {
                    newActivity.setParentId(parentActivity.getId());
                }
                return newActivity;
            }
            return null;
        });

        Optional<Activity> result = dialog.showAndWait();
        result.ifPresent(activity -> {
            try {
                LocalTime st = LocalTime.parse(activity.getStartTime());
                LocalTime et = LocalTime.parse(activity.getEndTime());
                if (!st.isBefore(et)) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Invalid Time Range");
                    alert.setHeaderText("End time must be after start time.");
                    alert.setContentText("Please provide a valid time range where start time is before end time.");
                    alert.showAndWait();
                    return;
                }

                // If this is a sub-schedule, enforce bounds within parent activity
                if (parentActivity != null) {
                    LocalTime pStart = LocalTime.parse(parentActivity.getStartTime());
                    LocalTime pEnd = LocalTime.parse(parentActivity.getEndTime());
                    if (st.isBefore(pStart) || et.isAfter(pEnd)) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Sub-schedule Out of Bounds");
                        alert.setHeaderText("Sub-schedule must fall within " + parentActivity.getTitle()
                                + " (" + parentActivity.getStartTime() + " - " + parentActivity.getEndTime() + ").");
                        alert.showAndWait();
                        return;
                    }
                } else {
                    // Top-level activity: if outside standard 6am-9pm, automatically enable 24h mode
                    if (st.getHour() < 6 || et.getHour() > 21 || (et.getHour() == 21 && et.getMinute() > 0)) {
                        if (toggle24Hour != null) {
                            toggle24Hour.setSelected(true);
                        }
                    }
                }
            } catch (Exception ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Invalid Time Format");
                alert.setHeaderText("Please enter times in HH:mm format (e.g. 08:00, 14:30).");
                alert.showAndWait();
                return;
            }

            // Check for scheduling conflicts (sub-schedules do NOT conflict with parent)
            String parentId = parentActivity != null ? parentActivity.getId() : null;
            List<Activity> conflicts = ActivityService.findConflicts(
                    currentUser.getId(), activity.getDate(),
                    activity.getStartTime(), activity.getEndTime(), null, parentId);

            if (!conflicts.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Schedule Conflict Detected");
                alert.setHeaderText("Cannot schedule overlapping activities:");
                StringBuilder conflictList = new StringBuilder();
                for (Activity c : conflicts) {
                    conflictList.append("• ").append(c.getTitle())
                            .append(" (").append(c.getStartTime())
                            .append(" - ").append(c.getEndTime()).append(")\n");
                }
                alert.setContentText(conflictList + "\nOverlapping time slots are not allowed. Please select a non-overlapping time.");
                alert.showAndWait();
                return;
            }

            ActivityService.addActivity(activity);
            refreshAll();
        });
    }

    /**
     * Shows a dialog to edit an existing activity.
     * Pre-fills the form with the activity's current values and includes
     * a "Delete" button for removing the activity entirely.
     *
     * @param activity the activity to edit
     */
    private void showEditEventDialog(Activity activity) {
        Dialog<Activity> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Edit Event");
        dialog.setHeaderText("Modify activity details");

        // ── Pre-filled form fields ────────────────────────────────
        TextField titleField = new TextField(activity.getTitle());
        TextField descField = new TextField(
                activity.getDescription() != null ? activity.getDescription() : "");

        ComboBox<Activity.ActivityType> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Activity.ActivityType.values());
        typeCombo.setValue(activity.getType());

        TextField startField = new TextField(activity.getStartTime());
        TextField endField = new TextField(activity.getEndTime());

        // ── Layout ────────────────────────────────────────────────
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Description:"), 0, 1);
        grid.add(descField, 1, 1);
        grid.add(new Label("Type:"), 0, 2);
        grid.add(typeCombo, 1, 2);
        grid.add(new Label("Start Time:"), 0, 3);
        grid.add(startField, 1, 3);
        grid.add(new Label("End Time:"), 0, 4);
        grid.add(endField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // ── Buttons: Save, Delete, Cancel ─────────────────────────
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(
                saveButton, deleteButton, ButtonType.CANCEL);

        // Convert result: update the activity or return a delete sentinel
        dialog.setResultConverter(btn -> {
            if (btn == saveButton) {
                activity.setTitle(titleField.getText().trim());
                activity.setDescription(descField.getText().trim());
                activity.setType(typeCombo.getValue());
                activity.setStartTime(startField.getText().trim());
                activity.setEndTime(endField.getText().trim());
                return activity;
            } else if (btn == deleteButton) {
                // Return a sentinel object whose title is "DELETE"
                Activity sentinel = new Activity();
                sentinel.setId(activity.getId());
                sentinel.setTitle("DELETE");
                return sentinel;
            }
            return null;
        });

        Optional<Activity> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            // Handle delete sentinel
            if ("DELETE".equals(updated.getTitle())
                    && updated.getId().equals(activity.getId())) {
                ActivityService.deleteActivity(activity.getId());
            } else {
                try {
                    LocalTime st = LocalTime.parse(updated.getStartTime());
                    LocalTime et = LocalTime.parse(updated.getEndTime());
                    if (!st.isBefore(et)) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Invalid Time Range");
                        alert.setHeaderText("End time must be after start time.");
                        alert.showAndWait();
                        return;
                    }
                } catch (Exception ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Invalid Time Format");
                    alert.setHeaderText("Please enter times in HH:mm format.");
                    alert.showAndWait();
                    return;
                }

                // Check for conflicts (excluding self) - STRICT BLOCKING
                List<Activity> conflicts = ActivityService.findConflicts(
                        currentUser.getId(), selectedDate.toString(),
                        updated.getStartTime(), updated.getEndTime(),
                        updated.getId());

                if (!conflicts.isEmpty()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Schedule Conflict Detected");
                    alert.setHeaderText("This change conflicts with existing activities:");
                    StringBuilder conflictList = new StringBuilder();
                    for (Activity c : conflicts) {
                        conflictList.append("• ").append(c.getTitle())
                                .append(" (").append(c.getStartTime())
                                .append(" - ").append(c.getEndTime()).append(")\n");
                    }
                    alert.setContentText(conflictList + "\nOverlapping time slots are not allowed. Please choose a different time slot.");
                    alert.showAndWait();
                    return;
                }

                ActivityService.updateActivity(updated);
            }
            refreshAll();
        });
    }

    /**
     * Opens the add event dialog for approximately the current hour.
     * Clamps the hour to the visible timeline range.
     */
    @FXML
    private void handleAddEvent() {
        int currentHour = LocalTime.now().getHour();
        boolean isPast9pm = selectedDate.equals(LocalDate.now()) && LocalTime.now().getHour() >= 21;
        if (!isPast9pm) {
            if (currentHour < 6) currentHour = 6;
            if (currentHour > 20) currentHour = 20;
        } else {
            if (currentHour > 23) currentHour = 23;
        }
        showAddEventDialog(currentHour);
    }

    /**
     * Toggles/opens the full calendar overview by switching to the Month view.
     */
    @FXML
    private void handleViewCalendar() {
        currentViewMode = ViewMode.MONTH;
        monthToggle.setSelected(true);
        refreshAll();
    }

    /**
     * Opens a dialog allowing the user to select an existing upcoming activity
     * and reschedule its date and time slot with strict conflict validation.
     */
    @FXML
    private void handleReschedule() {
        if (currentUser == null) return;

        List<Activity> allUpcoming = ActivityService.getAllActivities(currentUser.getId()).stream()
                .filter(a -> a.getStatus() == Activity.Status.UPCOMING)
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .toList();

        if (allUpcoming.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Reschedule Event");
            alert.setHeaderText("No Upcoming Activities");
            alert.setContentText("You don't have any upcoming activities to reschedule. Add a new activity first!");
            alert.showAndWait();
            return;
        }

        Dialog<Activity> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Reschedule Activity");
        dialog.setHeaderText("Select an event and choose its new date and time");

        ComboBox<Activity> activityCombo = new ComboBox<>();
        activityCombo.getItems().addAll(allUpcoming);
        activityCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Activity item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle() + " (" + item.getDate() + " " + item.getStartTime() + " - " + item.getEndTime() + ")");
                }
            }
        });
        activityCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Activity item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle() + " (" + item.getDate() + " " + item.getStartTime() + " - " + item.getEndTime() + ")");
                }
            }
        });

        if (selectedActivity != null && allUpcoming.contains(selectedActivity)) {
            activityCombo.setValue(selectedActivity);
        } else {
            activityCombo.setValue(allUpcoming.get(0));
        }

        DatePicker newDatePicker = new DatePicker(selectedDate);
        TextField newStartField = new TextField(activityCombo.getValue().getStartTime());
        TextField newEndField = new TextField(activityCombo.getValue().getEndTime());

        activityCombo.valueProperty().addListener((obs, oldA, newA) -> {
            if (newA != null) {
                try {
                    newDatePicker.setValue(LocalDate.parse(newA.getDate()));
                } catch (Exception ignored) {}
                newStartField.setText(newA.getStartTime());
                newEndField.setText(newA.getEndTime());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Select Activity:"), 0, 0);
        grid.add(activityCombo, 1, 0);
        grid.add(new Label("New Date:"), 0, 1);
        grid.add(newDatePicker, 1, 1);
        grid.add(new Label("New Start Time:"), 0, 2);
        grid.add(newStartField, 1, 2);
        grid.add(new Label("New End Time:"), 0, 3);
        grid.add(newEndField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        ButtonType rescheduleBtn = new ButtonType("Reschedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(rescheduleBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == rescheduleBtn) {
                Activity act = activityCombo.getValue();
                if (act == null || newDatePicker.getValue() == null) return null;

                String newDate = newDatePicker.getValue().toString();
                String newStart = newStartField.getText().trim();
                String newEnd = newEndField.getText().trim();

                try {
                    LocalTime st = LocalTime.parse(newStart);
                    LocalTime et = LocalTime.parse(newEnd);
                    if (!st.isBefore(et)) {
                        Platform.runLater(() -> {
                            Alert a = new Alert(Alert.AlertType.ERROR);
                            a.setTitle("Invalid Time Range");
                            a.setHeaderText("End time must be after start time.");
                            a.showAndWait();
                        });
                        return null;
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Invalid Time Format");
                        a.setHeaderText("Times must be in HH:mm format (e.g. 09:00, 15:30).");
                        a.showAndWait();
                    });
                    return null;
                }

                // Check for conflicts on the new date (excluding the event itself)
                List<Activity> conflicts = ActivityService.findConflicts(
                        currentUser.getId(), newDate, newStart, newEnd, act.getId());
                if (!conflicts.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.ERROR);
                        a.setTitle("Schedule Conflict Detected");
                        a.setHeaderText("Cannot reschedule to an overlapping time slot:");
                        StringBuilder sb = new StringBuilder();
                        for (Activity c : conflicts) {
                            sb.append("• ").append(c.getTitle()).append(" (")
                              .append(c.getStartTime()).append(" - ").append(c.getEndTime()).append(")\n");
                        }
                        a.setContentText(sb + "\nOverlapping time slots are not allowed. Please select a non-overlapping time.");
                        a.showAndWait();
                    });
                    return null;
                }

                act.setDate(newDate);
                act.setStartTime(newStart);
                act.setEndTime(newEnd);
                return act;
            }
            return null;
        });

        Optional<Activity> res = dialog.showAndWait();
        res.ifPresent(updated -> {
            ActivityService.updateActivity(updated);
            selectedDate = LocalDate.parse(updated.getDate());
            displayedMonth = YearMonth.from(selectedDate);
            refreshAll();

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Rescheduled");
            success.setHeaderText("Activity Successfully Rescheduled");
            success.setContentText("\"" + updated.getTitle() + "\" was rescheduled to " + updated.getDate() + " (" + updated.getStartTime() + " - " + updated.getEndTime() + ").");
            success.showAndWait();
        });
    }

    /**
     * Checks for scheduling conflicts across all non-cancelled activities
     * on the selected date. Displays a summary alert listing any
     * overlapping pairs of activities.
     */
    @FXML
    private void handleCheckConflicts() {
        if (currentUser == null) return;

        List<Activity> activities = ActivityService.getActivitiesForDate(
                currentUser.getId(), selectedDate.toString());

        StringBuilder conflicts = new StringBuilder();
        int conflictCount = 0;

        // Compare each pair of activities for time overlaps
        for (int i = 0; i < activities.size(); i++) {
            Activity a = activities.get(i);
            if (a.getStatus() == Activity.Status.CANCELLED) continue;

            for (int j = i + 1; j < activities.size(); j++) {
                Activity b = activities.get(j);
                if (b.getStatus() == Activity.Status.CANCELLED) continue;

                // Parse times and check for overlap
                LocalTime aStart = LocalTime.parse(a.getStartTime());
                LocalTime aEnd   = LocalTime.parse(a.getEndTime());
                LocalTime bStart = LocalTime.parse(b.getStartTime());
                LocalTime bEnd   = LocalTime.parse(b.getEndTime());

                // Two intervals overlap if each starts before the other ends
                if (aStart.isBefore(bEnd) && bStart.isBefore(aEnd)) {
                    conflictCount++;
                    conflicts.append("• \"").append(a.getTitle()).append("\" (")
                            .append(a.getStartTime()).append("-").append(a.getEndTime())
                            .append(") overlaps with \"").append(b.getTitle()).append("\" (")
                            .append(b.getStartTime()).append("-").append(b.getEndTime())
                            .append(")\n");
                }
            }
        }

        // Show result alert
        Alert alert = new Alert(conflictCount > 0
                ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
        alert.setTitle("Conflict Check");
        alert.setHeaderText(conflictCount > 0
                ? conflictCount + " conflict(s) found!"
                : "No conflicts found!");
        alert.setContentText(conflictCount > 0
                ? conflicts.toString()
                : "All activities for " + selectedDate + " are conflict-free.");
        alert.showAndWait();
    }

    /**
     * Consults Google Gemini AI to analyze the user's daily schedule in relation
     * to their BMI category, energy demands, and meal/break timing.
     */
    @FXML
    private void handleAIScheduleAdvice() {
        if (currentUser == null) return;

        List<Activity> dayActivities = ActivityService.getActivitiesForDate(
                currentUser.getId(), selectedDate.toString());

        Dialog<Void> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("LifeSync Schedule Coach");
        dialog.setHeaderText("✨ Smart Schedule Optimization (Google Gemini)");

        VBox content = new VBox(14);
        content.setPrefWidth(500);
        content.setPadding(new Insets(15));

        // Badges: Date, Event count, BMI category
        HBox pills = new HBox(8);
        pills.setAlignment(Pos.CENTER_LEFT);

        Label datePill = new Label("📅 " + selectedDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        datePill.getStyleClass().add("plan-ai-pill-blue");

        Label countPill = new Label(dayActivities.size() + " scheduled events");
        countPill.getStyleClass().add("plan-ai-pill-purple");

        Label bmiPill = new Label("BMI " + String.format("%.1f", currentUser.getBmi()) + " (" +
                (currentUser.getBmiCategory() != null ? currentUser.getBmiCategory().getDisplayName() : "Normal") + ")");
        bmiPill.getStyleClass().add("plan-ai-pill-orange");

        pills.getChildren().addAll(datePill, countPill, bmiPill);

        Label adviceLabel = new Label("Analyzing your schedule balance with Google Gemini AI...");
        adviceLabel.setWrapText(true);
        adviceLabel.getStyleClass().add("plan-ai-advice-label");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(26, 26);
        HBox loadingBox = new HBox(10, spinner, new Label("Evaluating timeline, breaks & meal windows..."));
        loadingBox.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(pills, new Separator(), loadingBox, adviceLabel);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GeminiService.generateScheduleOptimization(currentUser, dayActivities)
                .thenAccept(advice -> {
                    Platform.runLater(() -> {
                        content.getChildren().remove(loadingBox);
                        adviceLabel.setText(advice);
                    });
                });

        dialog.show();
    }

    // ── Goals Progress ─────────────────────────────────────────────────

    /**
     * Loads progress data for the Goals Progress section.
     *
     * <ul>
     *   <li><b>Nutrition</b> – Calorie intake vs. the user's daily target.</li>
     *   <li><b>Activity</b> – Completed workouts vs. total workouts.</li>
     *   <li><b>Plan</b> – Completed tasks vs. total tasks for the day.</li>
     * </ul>
     */
    private void loadGoalsProgress() {
        if (currentUser == null) return;
        String userId = currentUser.getId();
        String date = selectedDate.toString();

        // ── Nutrition goal: calorie intake vs target ──────────────
        double totalCal = NutritionService.getTotalCalories(userId, date);
        int target = currentUser.getDailyCalorieTarget();
        double nutritionPct = target > 0 ? totalCal / target : 0;
        nutritionGoalBar.setProgress(Math.min(nutritionPct, 1.0));
        nutritionGoalLabel.setText(String.format("%.0f%%", nutritionPct * 100));

        // ── Activity goal: completed / total workouts ─────────────
        List<Activity> dayActivities =
                ActivityService.getActivitiesForDate(userId, date);
        long totalWorkouts = dayActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT).count();
        long completedWorkouts = dayActivities.stream()
                .filter(a -> a.getType() == Activity.ActivityType.WORKOUT)
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED).count();
        double activityPct = totalWorkouts > 0
                ? (double) completedWorkouts / totalWorkouts : 0;
        activityGoalBar.setProgress(activityPct);
        activityGoalLabel.setText(String.format("%.0f%%", activityPct * 100));

        // ── Plan goal: completed / total tasks ────────────────────
        long totalTasks = dayActivities.size();
        long completedTasks = dayActivities.stream()
                .filter(a -> a.getStatus() == Activity.Status.COMPLETED).count();
        double planPct = totalTasks > 0
                ? (double) completedTasks / totalTasks : 0;
        planGoalBar.setProgress(planPct);
        planGoalLabel.setText(String.format("%.0f%%", planPct * 100));
    }

    // ── Upcoming Plans ─────────────────────────────────────────────────

    /**
     * Loads the next 5 upcoming activities into the right sidebar panel.
     * Each row shows the date, start time, and title of the activity.
     */
    private void loadUpcomingPlans() {
        upcomingPlansList.getChildren().clear();
        if (currentUser == null) return;

        List<Activity> upcoming = ActivityService.getUpcomingActivities(
                currentUser.getId(), 5);

        if (upcoming.isEmpty()) {
            Label empty = new Label("No upcoming plans.");
            empty.getStyleClass().add("muted-text");
            upcomingPlansList.getChildren().add(empty);
            return;
        }

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d");
        for (Activity activity : upcoming) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("upcoming-row");

            // Date column
            LocalDate actDate = LocalDate.parse(activity.getDate());
            Label dateLabel = new Label(actDate.format(dateFmt));
            dateLabel.getStyleClass().add("upcoming-date");
            dateLabel.setMinWidth(45);

            // Time column
            Label timeLabel = new Label(activity.getStartTime());
            timeLabel.getStyleClass().add("upcoming-time");
            timeLabel.setMinWidth(45);

            // Title column
            Label titleLabel = new Label(activity.getTitle());
            titleLabel.getStyleClass().add("upcoming-title");

            row.getChildren().addAll(dateLabel, timeLabel, titleLabel);
            upcomingPlansList.getChildren().add(row);
        }
    }
}
