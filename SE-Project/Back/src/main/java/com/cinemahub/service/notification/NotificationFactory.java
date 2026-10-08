package com.cinemahub.service.notification;

import org.springframework.mail.javamail.JavaMailSender;

/**
 * Factory pattern: callers ask for a {@link NotificationType} and get back
 * the right {@link Notification} implementation, without needing to know
 * about (or import) the concrete classes themselves. Adding a new
 * notification kind later - e.g. an SMS reminder - only means adding one
 * enum value and one case here.
 */
public class NotificationFactory {

    private NotificationFactory() {
    }

    public static Notification create(NotificationType type) {
        return switch (type) {
            case BOOKING_CONFIRMATION -> new BookingConfirmationNotification();
            case BOOKING_CANCELLATION -> new BookingCancellationNotification();
            case MEMBERSHIP_UPGRADE -> new MembershipUpgradeNotification();
            case NEW_MOVIE_EMAIL, PENDING_APPROVAL_EMAIL -> throw new IllegalArgumentException(
                    type + " sends a real email - use createMovieEmail(...)");
        };
    }

    /**
     * The real-email notifications about a movie need the mail sender plus the movie's title and a
     * link (detail page for customers, approval queue for admins), so they get their own factory method.
     */
    public static Notification createMovieEmail(NotificationType type, JavaMailSender mailSender,
                                                String fromAddress, String movieTitle, String link) {
        return switch (type) {
            case NEW_MOVIE_EMAIL -> new NewMovieEmailNotification(mailSender, fromAddress, movieTitle, link);
            case PENDING_APPROVAL_EMAIL -> new PendingApprovalEmailNotification(mailSender, fromAddress, movieTitle, link);
            default -> throw new IllegalArgumentException(type + " is not a movie email - use create(...)");
        };
    }
}
