package com.cinemahub.controller;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.Payment;
import com.cinemahub.service.BookingService;
import com.cinemahub.service.PaymentService;
import com.cinemahub.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SYSTEM_ADMIN-only review queue for Bank Transfer receipts (covered by the existing
 * "/admin/**" -> SYSTEM_ADMIN rule in SecurityConfig, no new security rule needed). Kept as
 * its own controller rather than folded into AdminApprovalController: that one approves
 * *content* submitted by MANAGER/PROMOTION_MANAGER (movies, showtimes, halls, promotions)
 * before it's visible to customers at all, whereas this approves whether a *payment* that
 * already happened outside the system actually happened - a different domain, with a
 * heavier per-item review (viewing the uploaded file) than a plain approve/reject toggle.
 */
@Controller
@RequestMapping("/admin/receipts")
public class AdminReceiptController {

    private final PaymentService paymentService;
    private final BookingService bookingService;
    private final UserService userService;

    public AdminReceiptController(PaymentService paymentService, BookingService bookingService, UserService userService) {
        this.paymentService = paymentService;
        this.bookingService = bookingService;
        this.userService = userService;
    }

    @GetMapping
    public String queue(Model model) {
        List<Payment> payments = paymentService.findAwaitingReceipt();

        // Payment.booking and Booking.user are both LAZY, and spring.jpa.open-in-view=false
        // closes the session before the view renders - dereferencing payment.booking.user.name
        // straight in the template would throw LazyInitializationException (the same bug class
        // already fixed elsewhere in this project, see BookingService's javadoc history).
        // Resolved here instead: Payment.getBookingId()/booking.getUser().getId() only ever
        // read an already-known foreign-key id off a proxy (never triggers a query by itself),
        // then userService.findById() does one real, cheap lookup per row for the display name.
        Map<Long, String> customerNameByBookingId = new LinkedHashMap<>();
        for (Payment payment : payments) {
            Long bookingId = payment.getBookingId();
            Booking booking = bookingService.findById(bookingId);
            customerNameByBookingId.put(bookingId, userService.findById(booking.getUser().getId()).getName());
        }

        model.addAttribute("payments", payments);
        model.addAttribute("customerNameByBookingId", customerNameByBookingId);
        return "admin/receipts";
    }

    /** Streams the uploaded receipt file back (image inline, PDF opens/downloads in a new tab). */
    @GetMapping("/{paymentId}/file")
    @ResponseBody
    public ResponseEntity<Resource> viewReceipt(@PathVariable Long paymentId) throws MalformedURLException {
        Payment payment = paymentService.findById(paymentId);
        if (!payment.hasReceipt()) {
            throw new ResourceNotFoundException("No receipt uploaded yet for payment: " + paymentId);
        }
        Path path = Paths.get(payment.getReceiptFilePath());
        Resource resource = new UrlResource(path.toUri());
        MediaType contentType = payment.getReceiptContentType() != null
                ? MediaType.parseMediaType(payment.getReceiptContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        String filename = payment.getReceiptOriginalFilename() != null ? payment.getReceiptOriginalFilename() : "receipt";
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/{paymentId}/approve")
    public String approve(@PathVariable Long paymentId, RedirectAttributes redirectAttributes) {
        Payment payment = paymentService.findById(paymentId);
        try {
            bookingService.confirmBankTransferBooking(payment.getBookingId());
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/receipts";
    }

    @PostMapping("/{paymentId}/reject")
    public String reject(@PathVariable Long paymentId, RedirectAttributes redirectAttributes) {
        Payment payment = paymentService.findById(paymentId);
        try {
            bookingService.rejectBankTransferBooking(payment.getBookingId());
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/receipts";
    }
}
