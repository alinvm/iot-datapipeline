package org.example.iot.agg.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

public class TimeUtil {
    private static final ZoneId UTC = ZoneId.of("UTC");

    public static long floorToMinuteStartMs(long timestamp) {
        return Instant.ofEpochMilli(timestamp).truncatedTo(MINUTES).toEpochMilli();
    }

    public static long floorToHourStartMs(long timestamp) {
        return Instant.ofEpochMilli(timestamp).truncatedTo(HOURS).toEpochMilli();
    }

    public static int bucketMonth(long timestamp) {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), UTC);
        return zonedDateTime.getYear() * 100 + zonedDateTime.getMonthValue(); // e.g., 202511
    }
}
