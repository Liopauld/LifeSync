package com.lifesync.lifesync.model;

import java.util.UUID;

/**
 * Represents a scheduled activity in the user's plan.
 *
 * An activity can be a workout, a meal event, a study session, a rest period,
 * or any other task the user wants to track.  Activities have a date, time
 * range, and a status that changes as the user completes or cancels them.
 *
 * The {@link com.lifesync.lifesync.service.ActivityService} manages CRUD
 * operations and conflict detection for activities.
 */
public class Activity {

    // ── Enums ──────────────────────────────────────────────────────────

    /**
     * Categorizes the type of activity.
     * Used for icon selection, color coding, and calorie calculations.
     */
    public enum ActivityType {
        WORKOUT,   // Physical exercise (gym, jogging, basketball, etc.)
        MEAL,      // Eating event (breakfast, lunch, dinner, snack)
        STUDY,     // Academic or focus session
        REST,      // Rest or recovery period
        OTHER      // Anything else (errands, appointments, etc.)
    }

    /**
     * Tracks the current state of the activity.
     * Status changes drive the adaptive planning workflow.
     */
    public enum Status {
        UPCOMING,   // Scheduled but not yet started
        COMPLETED,  // User marked as done
        CANCELLED   // User cancelled this activity
    }

    // ── Fields ─────────────────────────────────────────────────────────

    private String id;           // Unique identifier (UUID)
    private String userId;       // Owner of this activity (references User.id)
    private String title;        // Short display name, e.g. "Morning Workout"
    private String description;  // Optional longer description
    private ActivityType type;   // Category of the activity
    private String date;         // ISO date string (yyyy-MM-dd)
    private String startTime;    // Start time string (HH:mm), e.g. "07:00"
    private String endTime;      // End time string (HH:mm), e.g. "08:00"
    private Status status;       // Current lifecycle status
    private int caloriesBurned;  // Estimated calories burned (for workouts)
    private String parentId;     // Optional parent activity ID for nested sub-schedules

    // ── Constructors ───────────────────────────────────────────────────

    /** No-arg constructor required by Gson deserialization. */
    public Activity() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.UPCOMING;
    }

    /**
     * Creates a fully specified activity.
     *
     * @param userId    owner's user ID
     * @param title     display name
     * @param type      category
     * @param date      date string (yyyy-MM-dd)
     * @param startTime start time (HH:mm)
     * @param endTime   end time (HH:mm)
     */
    public Activity(String userId, String title, ActivityType type,
                    String date, String startTime, String endTime) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.title = title;
        this.type = type;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = Status.UPCOMING;
        this.caloriesBurned = 0;
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ActivityType getType() { return type; }
    public void setType(ActivityType type) { this.type = type; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getCaloriesBurned() { return caloriesBurned; }
    public void setCaloriesBurned(int caloriesBurned) { this.caloriesBurned = caloriesBurned; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    @Override
    public String toString() {
        return "Activity{" + title + " @ " + date + " " + startTime + "-" + endTime
                + " [" + status + "]}";
    }
}
