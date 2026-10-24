package com.raporx.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeUtil() {
    }

    public static String clock(long millis) {
        return CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZONE));
    }

    public static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis).atZone(ZONE));
    }

    public static String full(long millis) {
        return FULL.format(Instant.ofEpochMilli(millis).atZone(ZONE));
    }

    public static String ago(long millis) {
        long diff = Math.max(0, System.currentTimeMillis() - millis);
        long sec = diff / 1000;
        if (sec < 60) {
            return "az önce";
        }
        long min = sec / 60;
        if (min < 60) {
            return min + " dk önce";
        }
        long hours = min / 60;
        if (hours < 24) {
            return hours + " sa önce";
        }
        return (hours / 24) + " gün önce";
    }
}
