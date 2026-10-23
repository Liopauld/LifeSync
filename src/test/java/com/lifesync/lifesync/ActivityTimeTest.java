package com.lifesync.lifesync;

import com.lifesync.lifesync.model.ActivityTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActivityTimeTest {
    @Test
    void midnightEndRemainsAtTheEndOfTheDay() {
        ActivityTime lastHour = ActivityTime.parse("23:00", "24:00");
        assertEquals(1380, lastHour.startMinutes());
        assertEquals(1440, lastHour.endMinutes());
        assertEquals(1440, ActivityTime.parse("00:00", "24:00").endMinutes());
        assertEquals(1, ActivityTime.parse("23:59", "24:00").endMinutes()
                - ActivityTime.parse("23:59", "24:00").startMinutes());
    }

    @Test
    void lateNightOverlapsAndAdjacentRangesUseHalfOpenBoundaries() {
        ActivityTime lastHour = ActivityTime.parse("23:00", "24:00");
        assertTrue(lastHour.overlaps(ActivityTime.parse("23:59", "24:00")));
        assertFalse(lastHour.overlaps(ActivityTime.parse("22:00", "23:00")));
        assertFalse(lastHour.overlaps(ActivityTime.parse("00:00", "01:00")));
    }

    @Test
    void midnightParentContainsLateChildrenButRejectsOutsideBounds() {
        ActivityTime parent = ActivityTime.parse("23:00", "24:00");
        assertTrue(parent.contains(ActivityTime.parse("23:30", "24:00")));
        assertTrue(parent.contains(parent));
        assertFalse(parent.contains(ActivityTime.parse("22:59", "24:00")));
        assertFalse(ActivityTime.parse("22:00", "23:30").contains(parent));
    }

    @Test
    void malformedTimesAndMidnightStartsAreRejected() {
        for (String invalid : new String[]{null, "", "9:00", "23:60", "25:00", "24:01", "12:00:00", "2300"}) {
            assertThrows(IllegalArgumentException.class, () -> ActivityTime.parse(invalid, "24:00"));
            assertThrows(IllegalArgumentException.class, () -> ActivityTime.parse("00:00", invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> ActivityTime.parse("24:00", "24:00"));
    }

    @Test
    void zeroDurationAndReversedRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ActivityTime.parse("12:00", "12:00"));
        assertThrows(IllegalArgumentException.class, () -> ActivityTime.parse("23:00", "00:00"));
        assertThrows(IllegalArgumentException.class, () -> ActivityTime.parse("15:00", "14:00"));
    }
}
