package com.cinemahub.dto;

import com.cinemahub.model.Movie;

/**
 * One card in the homepage "Top Rated" tab: a movie, its average customer rating, and (if it is
 * screening) the id of its next upcoming showtime for the "Buy Tickets" button - null otherwise.
 */
public record RatedMovieCard(Movie movie, MovieRating rating, Long nextShowtimeId) {
}
