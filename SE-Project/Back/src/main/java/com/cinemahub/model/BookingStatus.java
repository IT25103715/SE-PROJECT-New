package com.cinemahub.model;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    // Payment was not completed before Booking#expiresAt (10 minutes for Apple Pay / PayPal,
    // 24 hours for a Bank Transfer with no receipt uploaded). Set by BookingExpiryScheduler -
    // the seats are released the same way a cancellation releases them, and nothing is refunded
    // because no payment was ever completed.
    EXPIRED
}
