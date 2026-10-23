package com.lifesync.lifesync.service;

import com.lifesync.lifesync.model.Activity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActivityService, verifying strict conflict prevention,
 * overlap detection algorithms, and lifecycle status management.
 */
class ActivityServiceTest {

    @TempDir Path directory;
    private String previousDirectory;

    private final String testUserId = "test-user-" + System.currentTimeMillis();
    private final String testDate = LocalDate.now().toString();

    @BeforeEach
    void setUp() {
        previousDirectory = System.getProperty("lifesync.data.dir");
        System.setProperty("lifesync.data.dir", directory.toString());
    }

    @AfterEach
    void restoreDirectory() {
        if (previousDirectory == null) System.clearProperty("lifesync.data.dir");
        else System.setProperty("lifesync.data.dir", previousDirectory);
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

    @Test
    void legacyEntriesWithoutParentIdSurviveUpdateAndReload() throws Exception {
        Files.writeString(directory.resolve("activities.json"), """
                [{"id":"legacy","userId":"legacy-user","title":"Old plan",
                  "type":"STUDY","date":"2026-10-24","startTime":"08:00","endTime":"12:00"}]
                """);
        Activity legacy = ActivityService.getAllActivities("legacy-user").getFirst();
        assertNull(legacy.getParentId());
        assertEquals(Activity.Status.UPCOMING, legacy.getStatus());
        legacy.setTitle("Renamed plan");
        ActivityService.updateActivity(legacy);
        Activity reloaded = ActivityService.getAllActivities("legacy-user").getFirst();
        assertNull(reloaded.getParentId());
        assertEquals("legacy", reloaded.getId());
        assertEquals("Renamed plan", reloaded.getTitle());
    }

    @Test
    void nestedPlansRoundTripThroughDiskAndRemainLinkedAfterUpdate() throws Exception {
        Activity parent = activity("School", "08:00", "12:00", null);
        Activity child = activity("Class", "09:00", "10:00", parent.getId());
        Activity grandchild = activity("Quiz", "09:15", "09:30", child.getId());
        assertTrue(Files.readString(directory.resolve("activities.json")).contains("\"parentId\""));
        Activity reloaded = ActivityService.getSubActivities(parent.getId()).getFirst();
        assertEquals(child.getId(), reloaded.getId());
        assertEquals(parent.getId(), reloaded.getParentId());
        reloaded.setTitle("Updated class");
        ActivityService.updateActivity(reloaded);
        assertEquals("Updated class", ActivityService.getSubActivities(parent.getId()).getFirst().getTitle());
        assertEquals(grandchild.getId(), ActivityService.getSubActivities(child.getId()).getFirst().getId());
        assertTrue(ActivityService.findConflicts(testUserId, testDate,
                "09:00", "10:00", child.getId(), parent.getId()).isEmpty());
    }

    @Test
    void deletingParentRemovesAllDescendantsAndPreservesOtherPlans() {
        Activity parent = activity("School", "08:00", "12:00", null);
        Activity child = activity("Class", "09:00", "10:00", parent.getId());
        activity("Quiz", "09:15", "09:30", child.getId());
        Activity unrelated = activity("Dinner", "18:00", "19:00", null);
        ActivityService.deleteActivity(parent.getId());
        assertEquals(List.of(unrelated.getId()), ActivityService.getAllActivities(testUserId)
                .stream().map(Activity::getId).toList());
    }

    @Test
    void deletingChildPreservesItsParentAndSibling() {
        Activity parent = activity("School", "08:00", "12:00", null);
        Activity child = activity("Class", "09:00", "10:00", parent.getId());
        activity("Quiz", "09:15", "09:30", child.getId());
        Activity sibling = activity("Lunch", "11:00", "12:00", parent.getId());
        ActivityService.deleteActivity(child.getId());
        assertEquals(2, ActivityService.getAllActivities(testUserId).size());
        assertEquals(sibling.getId(), ActivityService.getSubActivities(parent.getId()).getFirst().getId());
    }

    private Activity activity(String title, String start, String end, String parentId) {
        Activity result = new Activity(testUserId, title, Activity.ActivityType.STUDY, testDate, start, end);
        result.setParentId(parentId);
        ActivityService.addActivity(result);
        return result;
    }

    @Test
    void proposedMidnightEndConflictsWithLateEveningActivity() {
        Activity late = activity("Late study", "23:30", "23:45", null);
        var conflicts = ActivityService.findConflicts(testUserId, testDate, "23:00", "24:00", null);
        assertEquals(List.of(late.getId()), conflicts.stream().map(Activity::getId).toList());
    }

    @Test
    void persistedMidnightEndConflictsUntilEndOfDayButNotOnNextDate() {
        Activity late = activity("Late study", "23:00", "24:00", null);
        var conflicts = ActivityService.findConflicts(testUserId, testDate, "23:30", "23:59", null);
        assertEquals(List.of(late.getId()), conflicts.stream().map(Activity::getId).toList());
        assertTrue(ActivityService.findConflicts(testUserId, testDate, "22:00", "23:00", null).isEmpty());
        assertTrue(ActivityService.findConflicts(testUserId, LocalDate.parse(testDate).plusDays(1).toString(),
                "00:00", "01:00", null).isEmpty());
        assertEquals("24:00", ActivityService.getAllActivities(testUserId).getFirst().getEndTime());
    }

    @Test
    void midnightIsAnEndBoundaryNotAStartTime() {
        assertThrows(IllegalArgumentException.class,
                () -> ActivityService.findConflicts(testUserId, testDate, "24:00", "24:00", null));
        assertThrows(IllegalArgumentException.class,
                () -> ActivityService.findConflicts(testUserId, testDate, "23:00", "24:01", null));
    }

    @Test
    void lateNightSubPlansIgnoreParentAndStillDetectSiblingConflicts() {
        Activity parent = activity("Night block", "22:00", "24:00", null);
        Activity child = activity("Late study", "23:00", "24:00", parent.getId());
        assertTrue(ActivityService.findConflicts(testUserId, testDate, "22:30", "23:00", null,
                parent.getId()).isEmpty());
        assertEquals(List.of(child.getId()), ActivityService.findConflicts(testUserId, testDate,
                "23:30", "24:00", null, parent.getId()).stream().map(Activity::getId).toList());
        assertTrue(ActivityService.findConflicts(testUserId, testDate, "23:00", "24:00",
                child.getId(), parent.getId()).isEmpty());
    }

    @Test
    void generatedPlanCommitsAdjacentMidnightActivitiesTogether() {
        Activity first = new Activity(testUserId, "Reading", Activity.ActivityType.STUDY, testDate, "22:00", "23:00");
        Activity last = new Activity(testUserId, "Rest", Activity.ActivityType.REST, testDate, "23:00", "24:00");
        ActivityService.addActivitiesWithoutConflicts(List.of(first, last));
        assertEquals(List.of(first.getId(), last.getId()), ActivityService.getAllActivities(testUserId)
                .stream().map(Activity::getId).toList());
    }

    @Test
    void generatedPlanConflictWithExistingActivityRejectsWholeBatch() {
        Activity existing = activity("Existing", "12:00", "13:00", null);
        Activity valid = new Activity(testUserId, "Valid", Activity.ActivityType.STUDY, testDate, "10:00", "11:00");
        Activity conflict = new Activity(testUserId, "Conflict", Activity.ActivityType.STUDY, testDate, "12:30", "13:30");
        assertThrows(IllegalArgumentException.class,
                () -> ActivityService.addActivitiesWithoutConflicts(List.of(valid, conflict)));
        assertEquals(List.of(existing.getId()), ActivityService.getAllActivities(testUserId).stream().map(Activity::getId).toList());
    }

    @Test
    void generatedPlanConflictsWithinBatchDoNotPersistPartialPlans() {
        Activity first = new Activity(testUserId, "First", Activity.ActivityType.STUDY, testDate, "12:00", "13:00");
        Activity second = new Activity(testUserId, "Second", Activity.ActivityType.STUDY, testDate, "12:30", "13:30");
        assertThrows(IllegalArgumentException.class,
                () -> ActivityService.addActivitiesWithoutConflicts(List.of(first, second)));
        assertTrue(ActivityService.getAllActivities(testUserId).isEmpty());
    }

    @Test
    void malformedGeneratedTimeDoesNotPersistEarlierValidItems() {
        Activity first = new Activity(testUserId, "First", Activity.ActivityType.STUDY, testDate, "12:00", "13:00");
        Activity malformed = new Activity(testUserId, "Malformed", Activity.ActivityType.STUDY, testDate, "23:00", "24:01");
        assertThrows(IllegalArgumentException.class,
                () -> ActivityService.addActivitiesWithoutConflicts(List.of(first, malformed)));
        assertTrue(ActivityService.getAllActivities(testUserId).isEmpty());
    }

    @Test
    void generatedPlansCannotDuplicateAnExistingItem() {
        Activity existing = activity("Existing", "12:00", "13:00", null);
        assertThrows(IllegalArgumentException.class,
                () -> ActivityService.addActivitiesWithoutConflicts(List.of(existing)));
        assertEquals(1, ActivityService.getAllActivities(testUserId).size());
    }
}
