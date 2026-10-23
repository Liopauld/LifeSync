package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.ActivityTime;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of scheduled activities (CRUD + conflict detection).
 *
 * <h3>What this service does</h3>
 * <ul>
 *   <li>Creates, reads, updates, and deletes activities.</li>
 *   <li>Filters activities by date and by user.</li>
 *   <li>Detects time-range overlaps (conflicts) when adding/moving activities.</li>
 *   <li>Marks activities as completed or cancelled.</li>
 *   <li>Returns upcoming activities sorted chronologically.</li>
 * </ul>
 *
 * All data is persisted to {@code data/activities.json} via {@link DataStore}.
 */
public class ActivityService {

    /** JSON file for activity persistence. */
    private static final String ACTIVITIES_FILE = "activities.json";

    /** Gson type token for List<Activity>. */
    private static final Type ACTIVITY_LIST_TYPE =
            new TypeToken<List<Activity>>() {}.getType();

    /**
     * Returns all activities for a given user, sorted by date then start time.
     *
     * @param userId the user's ID
     * @return sorted list of all activities belonging to the user
     */
    public static List<Activity> getAllActivities(String userId) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        return all.stream()
                .filter(a -> a.getUserId().equals(userId))
                .sorted(Comparator.comparing(Activity::getDate)
                        .thenComparing(Activity::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Returns activities for a specific user on a specific date.
     *
     * @param userId the user's ID
     * @param date   the date to filter by (yyyy-MM-dd)
     * @return list of activities on that date, sorted by start time
     */
    public static List<Activity> getActivitiesForDate(String userId, String date) {
        return getAllActivities(userId).stream()
                .filter(a -> a.getDate().equals(date))
                .collect(Collectors.toList());
    }

    /**
     * Returns upcoming (non-completed, non-cancelled) activities from today onward.
     *
     * @param userId the user's ID
     * @param limit  maximum number of activities to return
     * @return upcoming activities sorted chronologically
     */
    public static List<Activity> getUpcomingActivities(String userId, int limit) {
        String today = LocalDate.now().toString();
        return getAllActivities(userId).stream()
                .filter(a -> a.getStatus() == Activity.Status.UPCOMING)
                .filter(a -> a.getDate().compareTo(today) >= 0)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Adds a new activity and persists it.
     *
     * @param activity the activity to add
     */
    public static void addActivity(Activity activity) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        all.add(activity);
        DataStore.saveAll(ACTIVITIES_FILE, all);
    }

    /**
     * Commits a generated plan as one JSON write after checking every interval.
     * A conflict with existing events or within the proposed plan rejects the whole batch.
     */
    public static void addActivitiesWithoutConflicts(List<Activity> proposed) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        List<Activity> merged = new ArrayList<>(all);
        Set<String> ids = all.stream().map(Activity::getId).collect(Collectors.toSet());
        for (Activity activity : proposed) {
            if (activity.getUserId() == null || activity.getUserId().isBlank()
                    || activity.getTitle() == null || activity.getTitle().isBlank()) {
                throw new IllegalArgumentException("Each plan item needs an account and a title.");
            }
            LocalDate.parse(activity.getDate());
            ActivityTime time = ActivityTime.parse(activity.getStartTime(), activity.getEndTime());
            if (!ids.add(activity.getId())) {
                throw new IllegalArgumentException("This plan contains an item already on your calendar.");
            }
            for (Activity existing : merged) {
                if (existing.getStatus() == Activity.Status.CANCELLED
                        || !activity.getUserId().equals(existing.getUserId())
                        || !activity.getDate().equals(existing.getDate())
                        || !java.util.Objects.equals(activity.getParentId(), existing.getParentId())) continue;
                if (time.overlaps(ActivityTime.parse(existing.getStartTime(), existing.getEndTime()))) {
                    throw new IllegalArgumentException("\"" + activity.getTitle() + "\" overlaps with \""
                            + existing.getTitle() + "\" on " + activity.getDate() + ".");
                }
            }
            merged.add(activity);
        }
        DataStore.saveAll(ACTIVITIES_FILE, merged);
    }

    /**
     * Updates an existing activity (finds by ID and replaces).
     *
     * @param updated the activity with modified fields
     */
    public static void updateActivity(Activity updated) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(updated.getId())) {
                all.set(i, updated);
                break;
            }
        }
        DataStore.saveAll(ACTIVITIES_FILE, all);
    }

    /**
     * Deletes an activity by ID, cascading to delete any nested sub-schedules.
     *
     * @param activityId the ID of the activity to remove
     */
    public static void deleteActivity(String activityId) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        // Expand the deletion set until every descendant is included; cycles terminate too.
        Set<String> removedIds = new HashSet<>();
        removedIds.add(activityId);
        boolean changed;
        do {
            changed = false;
            for (Activity activity : all) {
                if (activity.getParentId() != null && removedIds.contains(activity.getParentId())) {
                    changed |= removedIds.add(activity.getId());
                }
            }
        } while (changed);
        all.removeIf(a -> removedIds.contains(a.getId()));
        DataStore.saveAll(ACTIVITIES_FILE, all);
    }

    /**
     * Retrieves all sub-activities belonging to a specific parent activity.
     *
     * @param parentId the ID of the parent activity
     * @return sorted list of sub-activities
     */
    public static List<Activity> getSubActivities(String parentId) {
        if (parentId == null || parentId.isBlank()) return java.util.Collections.emptyList();
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        return all.stream()
                .filter(a -> parentId.equals(a.getParentId()))
                .sorted(Comparator.comparing(Activity::getStartTime))
                .collect(Collectors.toList());
    }

    /**
     * Marks an activity as completed.
     *
     * @param activityId the ID of the activity to complete
     */
    public static void markAsCompleted(String activityId) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        for (Activity a : all) {
            if (a.getId().equals(activityId)) {
                a.setStatus(Activity.Status.COMPLETED);
                break;
            }
        }
        DataStore.saveAll(ACTIVITIES_FILE, all);
    }

    /**
     * Marks an activity as cancelled.
     *
     * @param activityId the ID of the activity to cancel
     */
    public static void markAsCancelled(String activityId) {
        List<Activity> all = DataStore.loadAll(ACTIVITIES_FILE, ACTIVITY_LIST_TYPE);
        for (Activity a : all) {
            if (a.getId().equals(activityId)) {
                a.setStatus(Activity.Status.CANCELLED);
                break;
            }
        }
        DataStore.saveAll(ACTIVITIES_FILE, all);
    }

    /**
     * Checks if a proposed activity would conflict with any existing
     * UPCOMING activity on the same date for the same user.
     *
     * @param userId    the user's ID
     * @param date      the date to check (yyyy-MM-dd)
     * @param startTime proposed start time (HH:mm)
     * @param endTime   proposed end time (HH:mm)
     * @param excludeId activity ID to exclude from the check (for edits),
     *                  or null for new activities
     * @return list of conflicting activities (empty if no conflicts)
     */
    public static List<Activity> findConflicts(String userId, String date,
                                               String startTime, String endTime,
                                               String excludeId) {
        return findConflicts(userId, date, startTime, endTime, excludeId, null);
    }

    /**
     * Overloaded conflict checker supporting nested sub-schedules.
     * When parentId is provided, the sub-schedule does NOT conflict with the
     * parent, but is verified against other sub-schedules of the same parent.
     *
     * @param userId    the user's ID
     * @param date      the date to check (yyyy-MM-dd)
     * @param startTime proposed start time (HH:mm)
     * @param endTime   proposed end time (HH:mm)
     * @param excludeId activity ID to exclude from the check (for edits)
     * @param parentId  optional parent ID if creating a nested sub-schedule
     * @return list of conflicting activities
     */
    public static List<Activity> findConflicts(String userId, String date,
                                               String startTime, String endTime,
                                               String excludeId, String parentId) {
        ActivityTime proposed = ActivityTime.parse(startTime, endTime);

        List<Activity> dayActivities = getActivitiesForDate(userId, date);
        List<Activity> conflicts = new ArrayList<>();

        for (Activity existing : dayActivities) {
            // Skip cancelled activities and the activity being edited
            if (existing.getStatus() == Activity.Status.CANCELLED) continue;
            if (excludeId != null && existing.getId().equals(excludeId)) continue;

            if (parentId != null) {
                // When scheduling inside a parent, it does NOT conflict with the parent
                if (existing.getId().equals(parentId)) continue;
                // Only compare against sibling sub-schedules of the same parent
                if (!parentId.equals(existing.getParentId())) continue;
            } else {
                // Top-level activities only conflict with other top-level activities
                if (existing.getParentId() != null) continue;
            }

            ActivityTime existingTime = ActivityTime.parse(existing.getStartTime(), existing.getEndTime());

            // Overlap check: A.start < B.end AND B.start < A.end
            if (proposed.overlaps(existingTime)) {
                conflicts.add(existing);
            }
        }
        return conflicts;
    }

    /**
     * Counts activities with a given status for a user on a specific date.
     *
     * @param userId the user's ID
     * @param date   the date to count (yyyy-MM-dd)
     * @param status the status to filter by
     * @return number of matching activities
     */
    public static long countByStatus(String userId, String date, Activity.Status status) {
        return getActivitiesForDate(userId, date).stream()
                .filter(a -> a.getStatus() == status)
                .count();
    }
}
