package com.cinemahub.service.notification;

import com.cinemahub.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simulates a cancellation-notice email (see BookingConfirmationNotification for why it's just a log line). */
public class BookingCancellationNotification implements Notification {

    private static final Logger log = LoggerFactory.getLogger(BookingCancellationNotification.class);

    @Override
    public void send(User recipient, String message) {
        log.info("[EMAIL to {}] Booking cancelled: {}", recipient.getEmail(), message);
    }
}
