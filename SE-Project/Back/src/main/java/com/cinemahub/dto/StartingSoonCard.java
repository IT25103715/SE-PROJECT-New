package com.cinemahub.dto;

import com.cinemahub.model.Showtime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One card in the homepage "Starting Soon" row: a showtime starting within the next 2 hours
 * (ShowtimeService#findStartingSoon) plus how long until it starts. The page counts down from
 * {@link #getSecondsUntilStart()} in the browser, so the label stays right without reloading.
 */
public class StartingSoonCard {

    private final Showtime showtime;
    private final long secondsUntilStart;

    public StartingSoonCard(Showtime showtime, LocalDateTime now) {
        this.showtime = showtime;
        this.secondsUntilStart = Math.max(0, Duration.between(now, showtime.getDateTime()).getSeconds());
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public long getSecondsUntilStart() {
        return secondsUntilStart;
    }

    /** "Starts in 45 min" / "Starts in 1 h 20 min" - same wording the page's countdown script uses. */
    public String getStartsInText() {
        long minutes = Math.max(1, (secondsUntilStart + 59) / 60);
        if (minutes < 60) {
            return "Starts in " + minutes + " min";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        return "Starts in " + hours + " h" + (rest > 0 ? " " + rest + " min" : "");
    }
}
