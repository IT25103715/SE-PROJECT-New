package com.cinemahub.service.notification;

import com.cinemahub.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simulates the "you're now a CinemaX member" email sent when a customer's confirmed bookings
 * reach the membership threshold (see MembershipService). Like BookingConfirmationNotification,
 * there is no mail server in this project, so "sending" means logging the message.
 */
public class MembershipUpgradeNotification implements Notification {

    private static final Logger log = LoggerFactory.getLogger(MembershipUpgradeNotification.class);

    @Override
    public void send(User recipient, String message) {
        log.info("[EMAIL to {}] Welcome to CinemaX membership: {}", recipient.getEmail(), message);
    }
}
