package com.cinemahub.service;

import com.cinemahub.dto.FeedbackForm;
import com.cinemahub.dto.MovieRating;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.*;
import com.cinemahub.repository.BookingRepository;
import com.cinemahub.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Function 5: customers raise reviews/complaints; Customer Relations Executives triage them. */
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final BookingRepository bookingRepository;
    private final MovieService movieService;

    public FeedbackService(FeedbackRepository feedbackRepository, BookingRepository bookingRepository,
                           MovieService movieService) {
        this.feedbackRepository = feedbackRepository;
        this.bookingRepository = bookingRepository;
        this.movieService = movieService;
    }

    @Transactional
    public Feedback submit(User user, FeedbackForm form) {
        Feedback feedback = new Feedback();
        feedback.setUser(user);
        applyForm(feedback, form);
        return feedbackRepository.save(feedback);
    }

    /**
     * Update: the customer's own submission, message/rating/type/booking only -
     * locked once a CRE has marked it Resolved, so a finished complaint's record
     * can't be rewritten after the fact.
     */
    @Transactional
    public Feedback update(Long id, Long requestingUserId, FeedbackForm form) {
        Feedback feedback = findById(id);
        if (!feedback.getUser().getId().equals(requestingUserId)) {
            throw new IllegalStateException("You can only edit your own feedback");
        }
        if (feedback.isResolved()) {
            throw new IllegalStateException("This feedback has already been resolved and can no longer be edited");
        }
        applyForm(feedback, form);
        return feedbackRepository.save(feedback);
    }

    private void applyForm(Feedback feedback, FeedbackForm form) {
        feedback.setType(form.getType());
        feedback.setMessage(form.getMessage());
        feedback.setRating(form.getRating());

        Booking booking = null;
        if (form.getBookingId() != null) {
            booking = bookingRepository.findById(form.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + form.getBookingId()));
        }
        Movie movie = resolveMovie(form, booking);
        // A rated review is what a movie's average rating is built from, so it must say which movie it's for.
        if (form.getType() == FeedbackType.REVIEW && form.getRating() != null && movie == null) {
            throw new IllegalArgumentException("Please choose which movie you are reviewing");
        }
        feedback.setBooking(booking);
        feedback.setMovie(movie);
    }

    /**
     * The movie this feedback is about: a linked booking decides it (that booking's movie), otherwise
     * the movie the customer picked, otherwise none (e.g. a general complaint).
     */
    private Movie resolveMovie(FeedbackForm form, Booking booking) {
        if (booking != null) {
            Movie bookedMovie = booking.getShowtime().getMovie();
            if (form.getMovieId() != null && !form.getMovieId().equals(bookedMovie.getId())) {
                throw new IllegalArgumentException("The booking you picked is for \"" + bookedMovie.getTitle()
                        + "\" - choose that movie, or set the booking to none");
            }
            return bookedMovie;
        }
        return form.getMovieId() != null ? movieService.findById(form.getMovieId()) : null;
    }

    // ---------------------------------------------------------------------------------------
    // Movie ratings - averages of customers' rated reviews
    // ---------------------------------------------------------------------------------------

    /** One movie's average rating and review count ({@link MovieRating#NONE} when it has no rated reviews yet). */
    public MovieRating getRating(Long movieId) {
        List<Object[]> rows = feedbackRepository.ratingSummary(movieId, FeedbackType.REVIEW);
        if (rows.isEmpty()) {
            return MovieRating.NONE;
        }
        Object[] row = rows.get(0);
        long count = ((Number) row[1]).longValue();
        return count == 0 ? MovieRating.NONE : new MovieRating(((Number) row[0]).doubleValue(), count);
    }

    /**
     * Ratings for every movie that has at least one rated review, keyed by movie id - one query
     * for a whole page of movie cards. A movie missing from the map has no reviews yet.
     */
    public Map<Long, MovieRating> getRatingsByMovie() {
        Map<Long, MovieRating> ratings = new HashMap<>();
        for (Object[] row : feedbackRepository.ratingSummaries(FeedbackType.REVIEW)) {
            ratings.put(((Number) row[0]).longValue(),
                    new MovieRating(((Number) row[1]).doubleValue(), ((Number) row[2]).longValue()));
        }
        return ratings;
    }

    /** A movie's written reviews, newest first, for its public detail page. */
    public List<Feedback> findReviewsForMovie(Long movieId) {
        return feedbackRepository.findByMovie_IdAndTypeOrderByCreatedAtDesc(movieId, FeedbackType.REVIEW);
    }

    public List<Feedback> findByUser(Long userId) {
        return feedbackRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    public List<Feedback> findAll() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    public Feedback findById(Long id) {
        return feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found: " + id));
    }

    @Transactional
    public void markResolved(Long id) {
        Feedback feedback = findById(id);
        feedback.setResolved(true);
        feedbackRepository.save(feedback);
    }

    /** CRE's admin-side delete - kept as its own method (separate from {@link #deleteOwn}) in case it moves to a soft-delete later. */
    @Transactional
    public void delete(Long id) {
        if (!feedbackRepository.existsById(id)) {
            throw new ResourceNotFoundException("Feedback not found: " + id);
        }
        feedbackRepository.deleteById(id);
    }

    /**
     * Delete: the customer's own submission, hard-deleted since it's their own
     * content being withdrawn - distinct from CRE's admin-side {@link #delete}
     * above, which stays as its own separate action/method.
     */
    @Transactional
    public void deleteOwn(Long id, Long requestingUserId) {
        Feedback feedback = findById(id);
        if (!feedback.getUser().getId().equals(requestingUserId)) {
            throw new IllegalStateException("You can only delete your own feedback");
        }
        feedbackRepository.deleteById(id);
    }
}
