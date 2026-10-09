package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.CinemaHall;
import com.cinemahub.model.Role;
import com.cinemahub.model.Seat;
import com.cinemahub.model.User;
import com.cinemahub.repository.CinemaHallRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Manages screening rooms. Creating a hall also generates its physical
 * {@link Seat} grid once (e.g. a 5x8 hall becomes seats A1..A8, B1..B8, ...)
 * so every showtime held there shares the same seat map.
 */
@Service
public class CinemaHallService {

    private final CinemaHallRepository cinemaHallRepository;

    public CinemaHallService(CinemaHallRepository cinemaHallRepository) {
        this.cinemaHallRepository = cinemaHallRepository;
    }

    public List<CinemaHall> findAll() {
        return cinemaHallRepository.findAll();
    }

    /** Halls selectable when scheduling a showtime - only ones SYSTEM_ADMIN has approved. */
    public List<CinemaHall> findAllApproved() {
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        return cinemaHallRepository.findByApprovalStatus(ApprovalStatus.APPROVED);
    }

    public CinemaHall findById(Long id) {
        return cinemaHallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema hall not found: " + id));
    }

    /**
     * Same lookup as {@link #findById}, but rejects a hall that isn't
     * APPROVED yet - used when scheduling a showtime so a hall can't be
     * assigned by directly posting its id, bypassing the (already-filtered)
     * dropdown on the showtime form.
     */
    public CinemaHall findApprovedById(Long id) {
        CinemaHall hall = findById(id);
        if (hall.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalArgumentException("Cinema hall '" + hall.getName() + "' is awaiting admin approval and can't be used for a showtime yet");
        }
        return hall;
    }

    public List<CinemaHall> findPending() {
        return cinemaHallRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }

    @Transactional
    public CinemaHall save(CinemaHall hall, User creator) {
        hall.setSeats(generateSeats(hall));
        hall.setCreatedBy(creator);
        applyApprovalStatus(hall, creator);
        return cinemaHallRepository.save(hall);
    }

    /**
     * Renaming only - totalRows/seatsPerRow can't be edited after creation
     * because the physical {@link Seat} grid (and any bookings pointing at
     * those seat ids) is generated once, up front, in {@link #save}.
     */
    @Transactional
    public CinemaHall update(Long id, String newName, User editor) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Hall name is required");
        }
        CinemaHall hall = findById(id);
        hall.setName(newName);
        applyApprovalStatus(hall, editor);
        return cinemaHallRepository.save(hall);
    }

    @Transactional
    public void approve(Long id) {
        CinemaHall hall = findById(id);
        hall.setApprovalStatus(ApprovalStatus.APPROVED);
        hall.setRejectionReason(null);
        cinemaHallRepository.save(hall);
    }

    @Transactional
    public void reject(Long id, String reason) {
        CinemaHall hall = findById(id);
        hall.setApprovalStatus(ApprovalStatus.REJECTED);
        hall.setRejectionReason(reason);
        cinemaHallRepository.save(hall);
    }

    @Transactional
    public void delete(Long id) {
        if (!cinemaHallRepository.existsById(id)) {
            throw new ResourceNotFoundException("Cinema hall not found: " + id);
        }
        try {
            cinemaHallRepository.deleteById(id);
            cinemaHallRepository.flush();   // force the DELETE now so a foreign-key violation surfaces here
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("This hall can't be deleted because showtimes still use it. Delete or move those showtimes first.", ex);
        }
    }

    private List<Seat> generateSeats(CinemaHall hall) {
        List<Seat> seats = new ArrayList<>();
        for (int row = 0; row < hall.getTotalRows(); row++) {
            String rowLabel = rowLabel(row);
            for (int number = 1; number <= hall.getSeatsPerRow(); number++) {
                Seat seat = new Seat();
                seat.setCinemaHall(hall);
                seat.setRowLabel(rowLabel);
                seat.setSeatNumber(number);
                seat.setSeatCode(rowLabel + number);
                seats.add(seat);
            }
        }
        return seats;
    }

    /**
     * Spreadsheet-style row label (A, B, ..., Z, AA, AB, ...). Plain
     * {@code 'A' + row} broke past 25 rows: it wrapped into lowercase
     * letters, which MySQL's case-insensitive collation treats as equal to
     * the earlier uppercase ones, tripping the (cinema_hall_id, seat_code)
     * unique constraint and crashing hall creation for any hall with more
     * than ~32 rows.
     */
    private String rowLabel(int row) {
        StringBuilder label = new StringBuilder();
        int n = row;
        do {
            label.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return label.toString();
    }

    // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
    private void applyApprovalStatus(CinemaHall hall, User actingUser) {
        hall.setApprovalStatus(actingUser.getRole() == Role.SYSTEM_ADMIN
                ? ApprovalStatus.APPROVED : ApprovalStatus.PENDING);
        hall.setRejectionReason(null);
    }
}
