package com.cinemahub.dto;

import com.cinemahub.model.Movie;

/**
 * One card in the homepage "Now Showing" row: the movie plus the id of its next
 * upcoming showtime, which the card's "Buy Tickets" button goes straight to
 * (the seat-selection page for that showtime).
 */
public class NowShowingCard {

    private final Movie movie;
    private final Long nextShowtimeId;

    public NowShowingCard(Movie movie, Long nextShowtimeId) {
        this.movie = movie;
        this.nextShowtimeId = nextShowtimeId;
    }

    public Movie getMovie() {
        return movie;
    }

    public Long getNextShowtimeId() {
        return nextShowtimeId;
    }
}
