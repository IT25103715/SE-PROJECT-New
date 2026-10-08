package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.Ticket;
import com.cinemahub.model.TicketStatus;
import com.cinemahub.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Function 6: issuing QR tickets after a booking is confirmed, and letting
 * cinema staff "scan" (look up by QR value) and validate them at the door.
 */
@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;

    public TicketService(TicketRepository ticketRepository, QrCodeService qrCodeService) {
        this.ticketRepository = ticketRepository;
        this.qrCodeService = qrCodeService;
    }

    @Transactional
    public Ticket issueTicket(Booking booking) {
        Ticket ticket = new Ticket();
        ticket.setBooking(booking);
        ticket.setQrCodeData(qrCodeService.generateTicketToken());
        ticket.setStatus(TicketStatus.VALID);
        return ticketRepository.save(ticket);
    }

    public Ticket findByQrCodeData(String qrCodeData) {
        return ticketRepository.findByQrCodeData(qrCodeData)
                .orElseThrow(() -> new ResourceNotFoundException("No ticket matches this code: " + qrCodeData));
    }

    public Ticket findByBookingId(Long bookingId) {
        return ticketRepository.findByBooking_Id(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No ticket for booking: " + bookingId));
    }

    /** Cinema staff scans a QR code at the entrance: valid tickets get marked USED once, on entry. */
    @Transactional
    public Ticket scanAndValidate(String qrCodeData) {
        Ticket ticket = findByQrCodeData(qrCodeData);
        if (ticket.getStatus() == TicketStatus.USED) {
            throw new IllegalStateException("This ticket has already been used");
        }
        if (ticket.getStatus() == TicketStatus.VOID) {
            throw new IllegalStateException("This ticket was cancelled and is no longer valid");
        }
        ticket.setStatus(TicketStatus.USED);
        ticket.setUsedAt(java.time.LocalDateTime.now());
        return ticketRepository.save(ticket);
    }

    /** Called when a booking is cancelled, so the ticket can no longer be scanned. */
    @Transactional
    public void voidTicketForBooking(Long bookingId) {
        ticketRepository.findByBooking_Id(bookingId).ifPresent(ticket -> {
            ticket.setStatus(TicketStatus.VOID);
            ticketRepository.save(ticket);
        });
    }

    /**
     * Delete: cinema staff manually invalidates a ticket by its QR code (e.g.
     * suspected fraud or a duplicate), independent of the booking it belongs
     * to ever being cancelled. Unlike {@link #voidTicketForBooking}, this is
     * a direct staff action reachable from the scan screen, not a side
     * effect of cancellation.
     */
    @Transactional
    public Ticket voidTicket(String qrCodeData) {
        Ticket ticket = findByQrCodeData(qrCodeData);
        if (ticket.getStatus() == TicketStatus.VOID) {
            throw new IllegalStateException("This ticket is already void");
        }
        ticket.setStatus(TicketStatus.VOID);
        return ticketRepository.save(ticket);
    }
}
