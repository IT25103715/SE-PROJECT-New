package com.cinemahub.service.notification;

public enum NotificationType {
    BOOKING_CONFIRMATION,
    BOOKING_CANCELLATION,
    MEMBERSHIP_UPGRADE,
    // Real emails (EmailNotification) - created with NotificationFactory.createMovieEmail
    NEW_MOVIE_EMAIL,
    PENDING_APPROVAL_EMAIL
}
