package com.cinemahub.exception;

/**
 * Thrown when a customer tries to pay for (or upload a receipt for) a PENDING booking whose
 * payment deadline has already passed - its seats have been, or are about to be, released.
 * Extends IllegalStateException so every existing "show the message and go back" catch block
 * keeps handling it.
 */
public class BookingExpiredException extends IllegalStateException {

    public static final String MESSAGE = "Your reserved seats have been released. Please start a new booking.";

    public BookingExpiredException() {
        super(MESSAGE);
    }
}
