package com.cinemahub.dto;

import com.cinemahub.model.FeedbackType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FeedbackForm {

    private Long bookingId;

    /** The movie a review is about - required when a REVIEW has a rating (see FeedbackService#applyForm). */
    private Long movieId;

    @NotNull(message = "Please choose a type")
    private FeedbackType type;

    @NotBlank(message = "Message cannot be empty")
    private String message;

    @Min(1)
    @Max(5)
    private Integer rating;

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getMovieId() {
        return movieId;
    }

    public void setMovieId(Long movieId) {
        this.movieId = movieId;
    }

    public FeedbackType getType() {
        return type;
    }

    public void setType(FeedbackType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }
}
