package com.lifesync.lifesync.service;

import com.lifesync.lifesync.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActivityService, verifying strict conflict prevention,
 * overlap detection algorithms, and lifecycle status management.
 */
class ActivityServiceTest {

    private final String testUserId = "test-user-" + System.currentTimeMillis();
    private final String testDate = LocalDate.now().toString();

    @BeforeEach
    void setUp() {
        // Clean up activities for test user
        List<Activity> existing = ActivityService.getActivitiesForDate(testUserId, testDate);
        for (Activity a : existing) {
            ActivityService.deleteActivity(a.getId());
        }
    }

    @Test
    @DisplayName("Adjacent time slots (e.g. 07:00-08:00 and 08:00-09:00) do NOT conflict")
    void testAdjacentSlotsDoNotConflict() {
        Activity act1 = new Activity(testUserId, "Morning Workout", Activity.ActivityType.WORKOUT,
                testDate, "07:00", "08:00");
        ActivityService.addActivity(act1);

        List<Activity> conflicts = ActivityService.findConflicts(testUserId, testDate, "08:00", "09:00", null);
        assertTrue(conflicts.isEmpty(), "Adjacent event starting at 08:00 should not conflict with event ending at 08:00");
    }

    @Test
    @DisplayName("Overlapping time slots (e.g. 07:30-08:30 and 08:00-09:00) detect conflict")
    void testOverlappingSlotsConflict() {
        Activity act1 = new Activity(testUserId, "Breakfast", Activity.ActivityType.MEAL,
                testDate, "07:30", "08:30");
        ActivityService.addActivity(act1);

        List<Activity> conflicts = ActivityService.findConflicts(testUserId, testDate, "08:00", "09:00", null);
        assertEquals(1, conflicts.size(), "Event overlapping from 08:00 to 08:30 must be reported as a conflict");
        assertEquals(act1.getId(), conflicts.get(0).getId());
    }

    @Test
    @DisplayName("Editing an activity excludes itself from conflict check")
    void testEditingExcludesSelf() {
        Activity act = new Activity(testUserId, "Study Session", Activity.ActivityType.STUDY,
                testDate, "09:00", "11:00");
        ActivityService.addActivity(act);

        // Check conflict for same window, passing act.getId() as excludeId
        List<Activity> conflicts = ActivityService.findConflicts(testUserId, testDate, "09:00", "11:00", act.getId());
        assertTrue(conflicts.isEmpty(), "Editing an event with its own time should not flag a conflict with itself");
    }

    @Test
    @DisplayName("Cancelled activities do not cause scheduling conflicts")
    void testCancelledActivitiesIgnoredInConflicts() {
        Activity act = new Activity(testUserId, "Cancelled Meeting", Activity.ActivityType.OTHER,
                testDate, "14:00", "15:00");
        act.setStatus(Activity.Status.CANCELLED);
        ActivityService.addActivity(act);

        List<Activity> conflicts = ActivityService.findConflicts(testUserId, testDate, "14:00", "15:00", null);
        assertTrue(conflicts.isEmpty(), "Cancelled activities must be ignored in conflict checks");
    }

    @Test
    @DisplayName("Contained sub-intervals are flagged as conflicts")
    void testContainedSubIntervalConflicts() {
        Activity act = new Activity(testUserId, "Long Seminar", Activity.ActivityType.STUDY,
                testDate, "10:00", "14:00");
        ActivityService.addActivity(act);

        // Smaller interval inside 10:00-14:00
        List<Activity> conflicts = ActivityService.findConflicts(testUserId, testDate, "11:00", "12:00", null);
        assertFalse(conflicts.isEmpty(), "An interval completely inside an existing activity must be flagged as conflict");
    }

    @Test
    @DisplayName("Sub-schedule does not conflict with its parent block")
    void testSubScheduleDoesNotConflictWithParent() {
        Activity parent = new Activity(testUserId, "School Schedule", Activity.ActivityType.STUDY,
                testDate, "08:00", "12:00");
        ActivityService.addActivity(parent);

        // Sub-schedule "Math" from 08:00-09:00 with parentId
        List<Activity> conflicts = ActivityService.findConflicts(
                testUserId, testDate, "08:00", "09:00", null, parent.getId());
        assertTrue(conflicts.isEmpty(), "A sub-schedule within its parent block must not conflict with the parent");
    }

    @Test
    @DisplayName("Overlapping sibling sub-schedules within the same parent flag a conflict")
    void testOverlappingSiblingSubSchedulesConflict() {
        Activity parent = new Activity(testUserId, "School Schedule", Activity.ActivityType.STUDY,
                testDate, "08:00", "12:00");
        ActivityService.addActivity(parent);

        Activity sub1 = new Activity(testUserId, "Math Class", Activity.ActivityType.STUDY,
                testDate, "08:00", "09:30");
        sub1.setParentId(parent.getId());
        ActivityService.addActivity(sub1);

        // Sub2 "Physics Class" from 09:00-10:00 overlaps with Math 08:00-09:30
        List<Activity> conflicts = ActivityService.findConflicts(
                testUserId, testDate, "09:00", "10:00", null, parent.getId());
        assertEquals(1, conflicts.size(), "Overlapping sibling sub-schedules must detect conflict");
        assertEquals(sub1.getId(), conflicts.get(0).getId());
    }

    @Test
    @DisplayName("Deleting a parent activity cascades to delete all sub-schedules")
    void testDeletingParentCascadesToSubSchedules() {
        Activity parent = new Activity(testUserId, "Gym Block", Activity.ActivityType.WORKOUT,
                testDate, "17:00", "19:00");
        ActivityService.addActivity(parent);

        Activity sub1 = new Activity(testUserId, "Warmup Jog", Activity.ActivityType.WORKOUT,
                testDate, "17:00", "17:30");
        sub1.setParentId(parent.getId());
        ActivityService.addActivity(sub1);

        Activity sub2 = new Activity(testUserId, "Bench Press", Activity.ActivityType.WORKOUT,
                testDate, "17:30", "18:30");
        sub2.setParentId(parent.getId());
        ActivityService.addActivity(sub2);

        assertEquals(2, ActivityService.getSubActivities(parent.getId()).size());

        // Delete parent
        ActivityService.deleteActivity(parent.getId());

        // Sub-activities must also be gone
        assertTrue(ActivityService.getSubActivities(parent.getId()).isEmpty(),
                "Sub-activities should be deleted when parent activity is deleted");
    }
}
