package com.cinemahub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Used by the manager showtime form instead of binding directly to the
 * {@link com.cinemahub.model.Showtime} entity, since a plain HTML
 * <select> can only submit an id (a String) - not a Movie/CinemaHall object.
 * The controller looks up the real entities from movieId/cinemaHallId
 * before saving.
 */
public class ShowtimeForm {

    private Long id;

    @NotNull(message = "Please choose a movie")
    private Long movieId;

    @NotNull(message = "Please choose a hall")
    private Long cinemaHallId;

    @NotNull(message = "Date and time are required")
    private LocalDateTime dateTime;

    @NotNull
    @Positive(message = "Price must be greater than zero")
    private BigDecimal price;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMovieId() {
        return movieId;
    }

    public void setMovieId(Long movieId) {
        this.movieId = movieId;
    }

    public Long getCinemaHallId() {
        return cinemaHallId;
    }

    public void setCinemaHallId(Long cinemaHallId) {
        this.cinemaHallId = cinemaHallId;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
