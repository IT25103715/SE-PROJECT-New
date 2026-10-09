package com.cinemahub.repository;

import com.cinemahub.model.Feedback;
import com.cinemahub.model.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Feedback> findAllByOrderByCreatedAtDesc();

    /** A movie's reviews, newest first (shown on the movie detail page). */
    List<Feedback> findByMovie_IdAndTypeOrderByCreatedAtDesc(Long movieId, FeedbackType type);

    /**
     * Average star rating + number of rated reviews for every movie that has at least one:
     * rows of [movieId (Long), average (Double), count (Long)]. Only REVIEW rows with a rating
     * count - complaints and unrated reviews never affect a movie's score.
     */
    @Query("SELECT f.movie.id, AVG(f.rating), COUNT(f) FROM Feedback f "
            + "WHERE f.type = :type AND f.rating IS NOT NULL AND f.movie IS NOT NULL GROUP BY f.movie.id")
    List<Object[]> ratingSummaries(@Param("type") FeedbackType type);

    /** Same summary for one movie: a single [average (Double), count (Long)] row (average is null when count is 0). */
    @Query("SELECT AVG(f.rating), COUNT(f) FROM Feedback f "
            + "WHERE f.movie.id = :movieId AND f.type = :type AND f.rating IS NOT NULL")
    List<Object[]> ratingSummary(@Param("movieId") Long movieId, @Param("type") FeedbackType type);
}
