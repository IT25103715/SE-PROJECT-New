package com.cinemahub.dto;

import java.util.List;

/**
 * JSON result of POST /api/tickets/scan, for the Android QR scanner app.
 *
 * @param valid            true only when the ticket was VALID and has now been marked USED (entry approved)
 * @param status           VALID (accepted now) | USED (already scanned before) | VOIDED (cancelled) | NOT_FOUND
 * @param movieTitle       null when NOT_FOUND
 * @param showtimeDateTime ISO-8601 local date-time, e.g. "2026-10-02T18:30:00"; null when NOT_FOUND
 * @param seatNumbers      e.g. ["A1", "A2"]; empty when NOT_FOUND
 * @param customerName     null when NOT_FOUND
 * @param message          human-readable result to show the door staff
 */
public record TicketScanResponse(boolean valid,
                                 String status,
                                 String movieTitle,
                                 String showtimeDateTime,
                                 List<String> seatNumbers,
                                 String customerName,
                                 String message) {

    public static TicketScanResponse notFound(String message) {
        return new TicketScanResponse(false, "NOT_FOUND", null, null, List.of(), null, message);
    }
}
