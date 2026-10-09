package com.cinemahub.dto;

/** JSON body of POST /api/tickets/scan - {@code { "qrData": "..." }}, the raw value read from the ticket's QR code. */
public record TicketScanRequest(String qrData) {
}
