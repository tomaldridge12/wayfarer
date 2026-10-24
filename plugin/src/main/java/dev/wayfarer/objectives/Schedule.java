package dev.wayfarer.objectives;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * When objective windows open: the daily at a time of day, the weekly on a day at that time, and weekend events from
 * one day and time to another, all in one time zone (summer time included). Plain java.time, no server types.
 */
public record Schedule(ZoneId zone, LocalTime dailyReset, DayOfWeek weeklyDay, DayOfWeek eventStartDay, LocalTime eventStart,
        DayOfWeek eventEndDay, LocalTime eventEnd) {
    public static Schedule defaults() {
        return new Schedule(ZoneId.of("Europe/London"), LocalTime.of(6, 0), DayOfWeek.MONDAY, DayOfWeek.FRIDAY,
                LocalTime.of(18, 0), DayOfWeek.MONDAY, LocalTime.MIDNIGHT);
    }

    /** When the daily window holding this moment opened. */
    public Instant dailyStart(Instant now) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime today = local.toLocalDate().atTime(dailyReset).atZone(zone);
        return (local.isBefore(today) ? today.minusDays(1) : today).toInstant();
    }

    /** When the next daily window opens. */
    public Instant nextDaily(Instant now) {
        return dailyStart(now).atZone(zone).plusDays(1).toLocalDate().atTime(dailyReset).atZone(zone).toInstant();
    }

    /** The daily window's number (days since 1970 by the window's opening date), for streaks. */
    public long day(Instant now) {
        return dailyStart(now).atZone(zone).toLocalDate().toEpochDay();
    }

    /** When the weekly window holding this moment opened: the weekly day at the daily reset time. */
    public Instant weeklyStart(Instant now) {
        ZonedDateTime local = now.atZone(zone);
        LocalDate day = local.toLocalDate().with(TemporalAdjusters.previousOrSame(weeklyDay));
        ZonedDateTime start = day.atTime(dailyReset).atZone(zone);
        return (local.isBefore(start) ? start.minusWeeks(1) : start).toInstant();
    }

    public Instant nextWeekly(Instant now) {
        return weeklyStart(now).atZone(zone).plusWeeks(1).toLocalDate().atTime(dailyReset).atZone(zone).toInstant();
    }

    /** The weekend event window holding this moment, as [start, end), or empty between events. */
    public Optional<Instant[]> event(Instant now) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime start = local.toLocalDate().with(TemporalAdjusters.previousOrSame(eventStartDay)).atTime(eventStart).atZone(zone);
        if (local.isBefore(start)) {
            start = start.minusWeeks(1);
        }
        ZonedDateTime end = start.toLocalDate().with(TemporalAdjusters.next(eventEndDay)).atTime(eventEnd).atZone(zone);
        if (eventEndDay == eventStartDay && !eventEnd.isBefore(eventStart)) {
            end = start.toLocalDate().atTime(eventEnd).atZone(zone);
        }
        return local.isBefore(end) ? Optional.of(new Instant[] {start.toInstant(), end.toInstant()}) : Optional.empty();
    }

    /** "5h 12m", "3d 4h", "12m": how long until a moment. */
    public static String until(Instant now, Instant then) {
        long minutes = Math.max(0, (then.getEpochSecond() - now.getEpochSecond() + 59) / 60);
        if (minutes >= 24 * 60) {
            return (minutes / (24 * 60)) + "d " + (minutes % (24 * 60)) / 60 + "h";
        }
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }
}
