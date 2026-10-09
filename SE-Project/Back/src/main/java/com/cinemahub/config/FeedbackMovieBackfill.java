package com.cinemahub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Movie ratings: links feedback written before feedback.movie_id existed to its movie, so those
 * older reviews count towards the movie's average rating. Only rows that are linked to a booking
 * can be matched (the booking's showtime says which movie it was); feedback with no booking and no
 * movie can't be attributed and is left alone. Safe to run on every start - it only touches rows
 * whose movie_id is still empty. Plain SQL that works on both MySQL and H2.
 */
@Component
public class FeedbackMovieBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FeedbackMovieBackfill.class);

    private final JdbcTemplate jdbcTemplate;

    public FeedbackMovieBackfill(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int linked = jdbcTemplate.update(
                    "UPDATE feedback SET movie_id = (SELECT s.movie_id FROM bookings b "
                            + "JOIN showtimes s ON s.id = b.showtime_id WHERE b.id = feedback.booking_id) "
                            + "WHERE movie_id IS NULL AND booking_id IS NOT NULL");
            if (linked > 0) {
                log.info("Linked {} older feedback row(s) to their movie via their booking", linked);
            }
        } catch (Exception ex) {
            log.warn("Feedback movie backfill skipped: {}", ex.getMessage());
        }
    }
}
