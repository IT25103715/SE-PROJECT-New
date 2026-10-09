package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.Language;
import com.cinemahub.model.Movie;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.repository.MovieRepository;
import com.cinemahub.service.notification.MovieApprovalEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MovieService(MovieRepository movieRepository, ApplicationEventPublisher eventPublisher) {
        this.movieRepository = movieRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    /**
     * Public catalogue browsing, filtered by language and/or genre category
     * (US-03). The catalogue is small enough that filtering the already-loaded
     * list in Java is simpler to follow than building a dynamic query -
     * either filter is skipped when null/blank.
     */
    public List<Movie> search(Language language, String genre) {
        return movieRepository.findAll().stream()
                // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
                .filter(movie -> movie.getApprovalStatus() == ApprovalStatus.APPROVED)
                .filter(movie -> language == null || movie.getLanguage() == language)
                .filter(movie -> genre == null || genre.isBlank()
                        || movie.getGenre().toLowerCase().contains(genre.toLowerCase()))
                .toList();
    }

    /**
     * Homepage "Coming Soon" tab: approved movies whose release date is still in the
     * future, soonest first. The caller drops any that already have upcoming showtimes
     * (those are "Now Showing" - an advance screening beats the release date).
     */
    public List<Movie> findComingSoon() {
        LocalDate today = LocalDate.now();
        return movieRepository.findAll().stream()
                .filter(movie -> movie.getApprovalStatus() == ApprovalStatus.APPROVED)
                .filter(movie -> movie.getReleaseDate() != null && movie.getReleaseDate().isAfter(today))
                .sorted(Comparator.comparing(Movie::getReleaseDate))
                .toList();
    }

    public Movie findById(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found: " + id));
    }

    /** Same lookup as {@link #findById}, but 404s a movie customers aren't allowed to see yet. */
    public Movie findApprovedById(Long id) {
        Movie movie = findById(id);
        if (movie.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ResourceNotFoundException("Movie not found: " + id);
        }
        return movie;
    }

    public List<Movie> findPending() {
        return movieRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    /**
     * Every movie regardless of who submitted it or its approval status, for
     * the MANAGER/SYSTEM_ADMIN "Movies Overview" screen. PENDING movies come
     * first (newest first) so they're the first thing an admin sees; the
     * rest follow, newest first.
     */
    public List<Movie> findAllForOverview() {
        return movieRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Movie movie) -> movie.getApprovalStatus() == ApprovalStatus.PENDING ? 0 : 1)
                        .thenComparing(Movie::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public Movie save(Movie movie, User creator) {
        movie.setCreatedBy(creator);
        applyApprovalStatus(movie, creator);
        Movie saved = movieRepository.save(movie);
        publishApprovalEvent(null, saved, creator);
        return saved;
    }

    @Transactional
    public Movie update(Long id, Movie updated, User editor) {
        Movie movie = findById(id);
        movie.setTitle(updated.getTitle());
        movie.setGenre(updated.getGenre());
        movie.setLanguage(updated.getLanguage());
        movie.setDescription(updated.getDescription());
        movie.setDurationMinutes(updated.getDurationMinutes());
        movie.setPosterUrl(updated.getPosterUrl());
        movie.setReleaseDate(updated.getReleaseDate());
        ApprovalStatus before = movie.getApprovalStatus();
        applyApprovalStatus(movie, editor);
        Movie saved = movieRepository.save(movie);
        publishApprovalEvent(before, saved, editor);
        return saved;
    }

    @Transactional
    public void approve(Long id) {
        Movie movie = findById(id);
        ApprovalStatus before = movie.getApprovalStatus();
        movie.setApprovalStatus(ApprovalStatus.APPROVED);
        movie.setRejectionReason(null);
        movieRepository.save(movie);
        publishApprovalEvent(before, movie, null);
    }

    @Transactional
    public void reject(Long id, String reason) {
        Movie movie = findById(id);
        movie.setApprovalStatus(ApprovalStatus.REJECTED);
        movie.setRejectionReason(reason);
        movieRepository.save(movie);
    }

    @Transactional
    public void delete(Long id) {
        if (!movieRepository.existsById(id)) {
            throw new ResourceNotFoundException("Movie not found: " + id);
        }
        try {
            movieRepository.deleteById(id);
            movieRepository.flush();   // force the DELETE now so a foreign-key violation surfaces here
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("This movie can't be deleted because showtimes or other records still refer to it. Delete its showtimes first.", ex);
        }
    }

    /**
     * Emails go out only when the status actually CHANGES: to APPROVED (members hear about the new film)
     * or to PENDING (admins hear about the new submission). The listener (MovieEmailNotifier) runs after
     * this transaction commits, so a rolled-back save never sends an email.
     */
    private void publishApprovalEvent(ApprovalStatus before, Movie movie, User actingUser) {
        ApprovalStatus now = movie.getApprovalStatus();
        if (now == before) {
            return;
        }
        MovieApprovalEvent.Kind kind;
        if (now == ApprovalStatus.APPROVED) {
            kind = MovieApprovalEvent.Kind.APPROVED;
        } else if (now == ApprovalStatus.PENDING) {
            kind = MovieApprovalEvent.Kind.SUBMITTED_FOR_APPROVAL;
        } else {
            return;
        }
        String submittedBy = actingUser == null ? "A manager" : actingUser.getName() + " (" + actingUser.getEmail() + ")";
        eventPublisher.publishEvent(new MovieApprovalEvent(kind, movie.getId(), movie.getTitle(), movie.getGenre(), submittedBy));
    }

    // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(Movie movie, User actingUser) {
        movie.setApprovalStatus(actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        movie.setRejectionReason(null);
    }
}
