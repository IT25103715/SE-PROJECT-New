package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.Role;
import com.cinemahub.model.Showtime;
import com.cinemahub.model.User;
import com.cinemahub.repository.ShowtimeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.format.DateTimeFormatter;

@Service
public class ShowtimeService {

    /** How far ahead the homepage "Starting Soon" row looks for last-minute showtimes. */
    public static final Duration STARTING_SOON_WINDOW = Duration.ofHours(2);

    private final ShowtimeRepository showtimeRepository;

    public ShowtimeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }

    public List<Showtime> findAll() {
        return showtimeRepository.findAllByOrderByDateTimeAsc();
    }

    /** Public showtime search (Function 2, read side) - approved showtimes only. */
    public List<Showtime> findAllApproved() {
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        return showtimeRepository.findByApprovalStatusOrderByDateTimeAsc(ApprovalStatus.APPROVED).stream()
                .filter(showtime -> showtime.getMovie().getApprovalStatus() == ApprovalStatus.APPROVED)
                .toList();
    }

    /** Approved showtimes that haven't started yet, earliest first - what "Now Showing" means on the homepage. */
    public List<Showtime> findUpcomingApproved() {
        LocalDateTime now = LocalDateTime.now();
        return findAllApproved().stream()
                .filter(showtime -> showtime.getDateTime().isAfter(now))
                .toList();
    }

    /**
     * "Starting Soon" (homepage rush row): approved showtimes of approved movies, across all movies,
     * that start within the next {@link #STARTING_SOON_WINDOW} (2 hours), soonest first.
     * Built on findAllApproved, so the same approval rules as everywhere else apply.
     */
    public List<Showtime> findStartingSoon() {
        return findStartingSoon(LocalDateTime.now());
    }

    /** Same as {@link #findStartingSoon()}, measured from the given moment (makes the 2-hour window testable). */
    public List<Showtime> findStartingSoon(LocalDateTime now) {
        LocalDateTime until = now.plus(STARTING_SOON_WINDOW);
        return findAllApproved().stream()
                .filter(showtime -> showtime.getDateTime().isAfter(now) && !showtime.getDateTime().isAfter(until))
                .toList();
    }

    /**
     * One movie's upcoming approved showtimes grouped by calendar day, earliest first - drives the
     * "Select a Showtime" picker (fragments/showtime-picker.html) on the movie page and seat map.
     * Built on findUpcomingApproved, so the approved-showtime AND approved-movie rules still apply.
     */
    public Map<LocalDate, List<Showtime>> findUpcomingApprovedByMovieGroupedByDate(Long movieId) {
        return findUpcomingApproved().stream()
                .filter(showtime -> showtime.getMovie().getId().equals(movieId))
                .collect(Collectors.groupingBy(showtime -> showtime.getDateTime().toLocalDate(),
                        LinkedHashMap::new, Collectors.toList()));
    }

    public List<Showtime> findByMovie(Long movieId) {
        return showtimeRepository.findByMovie_IdOrderByDateTimeAsc(movieId);
    }

    /** Showtimes for a movie's public detail page - approved only, and only reachable if the movie itself is approved. */
    public List<Showtime> findApprovedByMovie(Long movieId) {
        return showtimeRepository.findByMovie_IdAndApprovalStatusOrderByDateTimeAsc(movieId, ApprovalStatus.APPROVED);
    }

    public Showtime findById(Long id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + id));
    }

    /** Same lookup as {@link #findById}, but 404s a showtime (or its movie) customers aren't allowed to see/book yet. */
    public Showtime findApprovedById(Long id) {
        Showtime showtime = findById(id);
        if (showtime.getApprovalStatus() != ApprovalStatus.APPROVED
                || showtime.getMovie().getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new ResourceNotFoundException("Showtime not found: " + id);
        }
        return showtime;
    }

    public List<Showtime> findPending() {
        return showtimeRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    /**
     * Every showtime regardless of who submitted it or its approval status,
     * for the MANAGER/SYSTEM_ADMIN "Showtimes Overview" screen. PENDING
     * showtimes come first (newest first) so they're the first thing an
     * admin sees; the rest follow, newest first - same rule as
     * {@link com.cinemahub.service.MovieService#findAllForOverview()}.
     */
    public List<Showtime> findAllForOverview() {
        return showtimeRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Showtime showtime) -> showtime.getApprovalStatus() == ApprovalStatus.PENDING ? 0 : 1)
                        .thenComparing(Showtime::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional
    public Showtime save(Showtime showtime, User creator) {
        requireHallFree(showtime, null);
        showtime.setCreatedBy(creator);
        applyApprovalStatus(showtime, creator);
        return showtimeRepository.save(showtime);
    }

    @Transactional
    public Showtime update(Long id, Showtime updated, User editor) {
        requireHallFree(updated, id);
        Showtime showtime = findById(id);
        showtime.setMovie(updated.getMovie());
        showtime.setCinemaHall(updated.getCinemaHall());
        showtime.setDateTime(updated.getDateTime());
        showtime.setPrice(updated.getPrice());
        applyApprovalStatus(showtime, editor);
        return showtimeRepository.save(showtime);
    }

    @Transactional
    public void approve(Long id) {
        Showtime showtime = findById(id);
        showtime.setApprovalStatus(ApprovalStatus.APPROVED);
        showtime.setRejectionReason(null);
        showtimeRepository.save(showtime);
    }

    @Transactional
    public void reject(Long id, String reason) {
        Showtime showtime = findById(id);
        showtime.setApprovalStatus(ApprovalStatus.REJECTED);
        showtime.setRejectionReason(reason);
        showtimeRepository.save(showtime);
    }

    @Transactional
    public void delete(Long id) {
        if (!showtimeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Showtime not found: " + id);
        }
        try {
            showtimeRepository.deleteById(id);
            showtimeRepository.flush();   // force the DELETE now so a foreign-key violation surfaces here
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("This showtime can't be deleted because customers have bookings for it.", ex);
        }
    }

    /**
     * A hall can only screen one film at a time: the new showtime's run (start + the movie's
     * runtime) must not overlap any other non-rejected showtime in the same hall. {@code ignoreId}
     * is the showtime being edited, so it doesn't clash with its own old slot.
     */
    private void requireHallFree(Showtime candidate, Long ignoreId) {
        LocalDateTime start = candidate.getDateTime();
        LocalDateTime end = start.plusMinutes(runtimeOf(candidate));
        for (Showtime other : showtimeRepository.findByCinemaHall_Id(candidate.getCinemaHall().getId())) {
            if (other.getId().equals(ignoreId) || other.getApprovalStatus() == ApprovalStatus.REJECTED) {
                continue;
            }
            LocalDateTime otherStart = other.getDateTime();
            LocalDateTime otherEnd = otherStart.plusMinutes(runtimeOf(other));
            if (start.isBefore(otherEnd) && otherStart.isBefore(end)) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
                throw new IllegalArgumentException(candidate.getCinemaHall().getName() + " is already showing \""
                        + other.getMovie().getTitle() + "\" from " + otherStart.format(fmt) + " to " + otherEnd.format(fmt)
                        + " - pick a different time or hall.");
            }
        }
    }

    private long runtimeOf(Showtime showtime) {
        Integer minutes = showtime.getMovie().getDurationMinutes();
        return minutes == null ? 0 : minutes;
    }

    // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(Showtime showtime, User actingUser) {
        showtime.setApprovalStatus(actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        showtime.setRejectionReason(null);
    }
}
