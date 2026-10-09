package com.cinemahub.model;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    REFUNDED,
    FAILED,
    // Bank Transfer only: a receipt was uploaded (or is expected) and is waiting on
    // SYSTEM_ADMIN review at /admin/receipts - see BookingService#createBooking and
    // PaymentService#initiateBankTransferPayment. The booking stays PENDING and no
    // ticket exists yet while a payment sits in this state.
    AWAITING_RECEIPT,
    // Bank Transfer only: an admin rejected the uploaded receipt. The booking behind
    // it is cancelled (see BookingService#rejectBankTransferBooking) and its seats
    // freed the same way a customer cancellation frees them.
    RECEIPT_REJECTED
}
