package com.cinemahub.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * The scannable ticket generated once a {@link Booking} is confirmed
 * (Function 6). qrCodeData is the raw string encoded into the QR image (a
 * unique token); the PNG itself is generated on demand by QrCodeService
 * rather than stored, to keep the table small.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER - the ticket-scan screen shows the movie/showtime behind the booking after the session has closed.
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(nullable = false, unique = true, length = 100)
    private String qrCodeData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    private LocalDateTime usedAt;

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

    public String getQrCodeData() {
        return qrCodeData;
    }

    public void setQrCodeData(String qrCodeData) {
        this.qrCodeData = qrCodeData;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
