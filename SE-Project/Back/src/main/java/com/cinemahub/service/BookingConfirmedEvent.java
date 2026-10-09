package com.cinemahub.service;

/**
 * Published when a booking's status becomes CONFIRMED, however that happened (card / Apple Pay /
 * PayPal payment, or an admin approving a bank-transfer receipt). Raised by
 * {@link com.cinemahub.model.BookingStatusListener}; handled by MembershipService once the
 * confirming transaction has committed.
 */
public record BookingConfirmedEvent(Long bookingId, Long userId) {
}
