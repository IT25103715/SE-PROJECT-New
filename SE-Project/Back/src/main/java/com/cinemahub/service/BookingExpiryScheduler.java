package com.cinemahub.service;

import com.cinemahub.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment timeout: every minute, finds PENDING bookings whose payment deadline
 * ({@code Booking.expiresAt}) has passed and expires them one by one through
 * {@link BookingService#expireIfOverdue} - each in its own transaction with the booking row
 * locked, so a payment completing at the same moment always wins cleanly.
 *
 * <ul>
 *   <li>Seat holds (every payment method, from the pay step): 10 minutes ({@link BookingService#INSTANT_PAYMENT_WINDOW}).</li>
 *   <li>Bank Transfer: 24 hours ({@link BookingService#BANK_TRANSFER_WINDOW}), and only while no
 *       receipt has been uploaded - once one is, the deadline is cleared and the admin decides.</li>
 * </ul>
 * No refund logic ever runs here - an expired booking was never paid.
 */
@Component
public class BookingExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public BookingExpiryScheduler(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void expireOverdueBookings() {
        List<Long> overdue = bookingRepository.findOverduePendingIds(LocalDateTime.now());
        int expired = 0;
        for (Long bookingId : overdue) {
            try {
                if (bookingService.expireIfOverdue(bookingId)) {
                    expired++;
                }
            } catch (RuntimeException ex) {
                // One bad row must never stop the others from being released.
                log.warn("Could not expire booking #{}: {}", bookingId, ex.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Payment timeout: {} booking(s) expired and their seats released", expired);
        }
    }
}
