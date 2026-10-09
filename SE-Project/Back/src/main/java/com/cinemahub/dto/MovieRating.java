package com.cinemahub.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A movie's average star rating, calculated from customer reviews (Feedback rows with
 * type = REVIEW and a rating). See FeedbackService#getRating / #getRatingsByMovie.
 * A movie with no rated reviews has {@link #NONE}: count 0, shown as "No reviews yet".
 */
public record MovieRating(double average, long count) {

    public static final MovieRating NONE = new MovieRating(0, 0);

    public boolean hasReviews() {
        return count > 0;
    }

    /** Average rounded to one decimal place, e.g. "4.2". */
    public String getAverageText() {
        return BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    /** Width of the filled part of the 5-star bar, as a CSS percentage, e.g. "84%". */
    public String getFillPercent() {
        double percent = Math.max(0, Math.min(5, average)) / 5 * 100;
        return BigDecimal.valueOf(percent).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** "1 review" / "23 reviews". */
    public String getCountText() {
        return count + (count == 1 ? " review" : " reviews");
    }
}
