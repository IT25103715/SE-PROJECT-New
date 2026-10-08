package com.cinemahub.model;

import jakarta.persistence.*;

/**
 * Join row linking a {@link Booking} to one {@link Seat}. A booking for 3
 * seats produces 3 rows here. Whether a given seat is already taken for a
 * showtime is checked by looking for a BookingSeat whose booking has the
 * same showtime, the same seat, and a status of PENDING or CONFIRMED
 * (see BookingService#isSeatAvailable).
 */
@Entity
@Table(name = "booking_seats")
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // EAGER - booking confirmation/history views list each seat's code after the session has closed.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }
}
