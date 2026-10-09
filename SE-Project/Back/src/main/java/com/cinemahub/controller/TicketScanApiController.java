package com.cinemahub.controller;

import com.cinemahub.dto.TicketScanRequest;
import com.cinemahub.dto.TicketScanResponse;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.BookingSeat;
import com.cinemahub.model.Ticket;
import com.cinemahub.model.TicketStatus;
import com.cinemahub.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * JSON API for the Android QR scanner app: {@code POST /api/tickets/scan}.
 *
 * Separate from {@link StaffTicketController} (the browser scanner, which returns HTML and is
 * unchanged). Validation is NOT re-implemented here - it calls the exact same
 * {@link TicketService#scanAndValidate(String)} the web scanner uses, so the rules stay in one
 * place: a VALID ticket is marked USED (entry approved); USED and VOID tickets are refused;
 * an unknown code is NOT_FOUND.
 *
 * Secured in SecurityConfig#apiFilterChain: HTTP Basic auth (staff email + password), roles
 * CINEMA_STAFF / PAYMENT_MANAGER / SYSTEM_ADMIN - the same roles allowed on the web scanner.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketScanApiController {

    private final TicketService ticketService;
    private final TransactionTemplate readOnlyTransaction;

    public TicketScanApiController(TicketService ticketService, PlatformTransactionManager transactionManager) {
        this.ticketService = ticketService;
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @PostMapping("/scan")
    public ResponseEntity<TicketScanResponse> scan(@RequestBody(required = false) TicketScanRequest request) {
        String qrData = request == null || request.qrData() == null ? "" : request.qrData().trim();
        if (qrData.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(TicketScanResponse.notFound("No QR data was sent - scan the ticket again."));
        }
        try {
            // The one and only validation path (same call as the web scanner).
            Ticket ticket = ticketService.scanAndValidate(qrData);
            return ResponseEntity.ok(describe(ticket.getQrCodeData(), true, "VALID",
                    "Ticket valid - entry approved"));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(TicketScanResponse.notFound("Ticket not found - this QR code is not a CinemaHub ticket"));
        } catch (IllegalStateException ex) {
            // scanAndValidate refused it (already used or voided). Look the ticket up read-only
            // just to report which case it is and whose ticket it is - nothing is changed.
            return ResponseEntity.ok(describeRefused(qrData, ex.getMessage()));
        }
    }

    private TicketScanResponse describeRefused(String qrData, String serviceMessage) {
        TicketStatus status = ticketService.findByQrCodeData(qrData).getStatus();
        if (status == TicketStatus.USED) {
            return describe(qrData, false, "USED", "Already used - " + serviceMessage);
        }
        if (status == TicketStatus.VOID) {
            return describe(qrData, false, "VOIDED", "Ticket voided - " + serviceMessage);
        }
        return describe(qrData, false, status.name(), serviceMessage);
    }

    /**
     * Builds the response inside a short read-only transaction, because the booking's customer is
     * lazy-loaded and spring.jpa.open-in-view is off.
     */
    private TicketScanResponse describe(String qrData, boolean valid, String status, String message) {
        return readOnlyTransaction.execute(tx -> {
            Ticket ticket = ticketService.findByQrCodeData(qrData);
            Booking booking = ticket.getBooking();
            List<String> seats = booking.getBookingSeats().stream()
                    .map(BookingSeat::getSeat)
                    .map(seat -> seat.getSeatCode())
                    .sorted(Comparator.naturalOrder())
                    .toList();
            return new TicketScanResponse(
                    valid,
                    status,
                    booking.getShowtime().getMovie().getTitle(),
                    booking.getShowtime().getDateTime().toString(),
                    seats,
                    booking.getUser() != null ? booking.getUser().getName() : null,
                    message);
        });
    }
}
