package com.cinemahub.service.notification;

import com.cinemahub.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates a booking-confirmation email. There is no real mail server
 * configured for this university project, so "sending" means logging the
 * message - swapping in JavaMailSender later would only change this class.
 */
public class BookingConfirmationNotification implements Notification {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationNotification.class);

    @Override
    public void send(User recipient, String message) {
        log.info("[EMAIL to {}] Booking confirmed: {}", recipient.getEmail(), message);
    }
}
