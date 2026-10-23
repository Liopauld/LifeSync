package com.lifesync.lifesync.model;

import java.util.regex.Pattern;

/**
 * A same-day, half-open activity interval measured in minutes since midnight.
 * Unlike LocalTime, the end boundary can represent 24:00 without wrapping to 00:00.
 * JSON continues to store the original HH:mm strings on Activity.
 */
public record ActivityTime(int startMinutes, int endMinutes) {
    private static final Pattern CLOCK_TIME = Pattern.compile("(?:[01][0-9]|2[0-3]):[0-5][0-9]");

    public ActivityTime {
        if (startMinutes < 0 || startMinutes >= 1440 || endMinutes > 1440 || endMinutes <= startMinutes) {
            throw new IllegalArgumentException("End time must be after start time on the same day. Use 24:00 to end at midnight.");
        }
    }

    public static ActivityTime parse(String start, String end) {
        return new ActivityTime(minutes(start, false), minutes(end, true));
    }

    private static int minutes(String time, boolean end) {
        if (end && "24:00".equals(time)) return 1440;
        if (time == null || !CLOCK_TIME.matcher(time).matches()) {
            throw new IllegalArgumentException("Enter times in HH:mm format: starts 00:00–23:59; ends may also use 24:00.");
        }
        return Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3));
    }

    public boolean overlaps(ActivityTime other) {
        return startMinutes < other.endMinutes && other.startMinutes < endMinutes;
    }

    public boolean contains(ActivityTime other) {
        return startMinutes <= other.startMinutes && endMinutes >= other.endMinutes;
    }
}
