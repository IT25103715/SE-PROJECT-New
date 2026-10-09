package com.cinemahub.controller;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Ticket;
import com.cinemahub.service.TicketService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Cinema staff check tickets in at the door. The scan screen offers two ways to supply the
 * QR code value, both posting to the same endpoints below so the outcome is identical:
 * live camera scanning in the browser (html5-qrcode, see ticket/scan.html), or typing/pasting
 * the code by hand as a fallback (camera denied, no camera, poor lighting, damaged QR code).
 */
@Controller
@RequestMapping("/staff/tickets")
public class StaffTicketController {

    private final TicketService ticketService;

    public StaffTicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public String scanForm() {
        return "ticket/scan";
    }

    @PostMapping("/verify")
    public String verify(@RequestParam String qrCodeData, Model model) {
        try {
            Ticket ticket = ticketService.scanAndValidate(qrCodeData);
            model.addAttribute("ticket", ticket);
            model.addAttribute("resultMessage", "Ticket accepted - entry granted.");
        } catch (ResourceNotFoundException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "ticket/scan";
    }

    /** Delete: staff manually voids a ticket (e.g. suspected fraud/duplicate), without cancelling its booking. */
    @PostMapping("/void")
    public String voidTicket(@RequestParam String qrCodeData, Model model) {
        try {
            Ticket ticket = ticketService.voidTicket(qrCodeData);
            model.addAttribute("ticket", ticket);
            model.addAttribute("resultMessage", "Ticket voided - it can no longer be used for entry.");
        } catch (ResourceNotFoundException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "ticket/scan";
    }
}
