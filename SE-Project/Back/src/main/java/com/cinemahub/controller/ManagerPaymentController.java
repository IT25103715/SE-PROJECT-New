package com.cinemahub.controller;

import com.cinemahub.model.Payment;
import com.cinemahub.model.PaymentStatus;
import com.cinemahub.service.PaymentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Function 4: "Payment and Transaction Management" - admin side.
 * SYSTEM_ADMIN only: the Manager account no longer sees payments, refunds or revenue. The
 * "/manager/**" URL rule in SecurityConfig still lets MANAGER through to this path, so the
 * class-level @PreAuthorize below is what blocks them (403) - including direct links.
 * Covers the Read (branch-wise transaction log + revenue totals), Update
 * (refund / status correction), and Delete (soft-delete archiving)
 * sub-functions.
 */
@Controller
@RequestMapping("/manager/payments")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class ManagerPaymentController {

    private final PaymentService paymentService;

    public ManagerPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public String list(Model model) {
        Map<String, BigDecimal> revenueByBranch = paymentService.getRevenueByBranch();
        BigDecimal totalRevenue = revenueByBranch.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("payments", paymentService.findActive());
        model.addAttribute("revenueByBranch", revenueByBranch);
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("archivedView", false);
        return "payments/manage-list";
    }

    @GetMapping("/archived")
    public String archived(Model model) {
        model.addAttribute("payments", paymentService.findAll().stream().filter(Payment::isArchived).toList());
        model.addAttribute("archivedView", true);
        return "payments/manage-list";
    }

    /** Update sub-function: manual refund, in addition to the automatic one triggered by booking cancellation. */
    @PostMapping("/{id}/refund")
    public String refund(@PathVariable Long id) {
        paymentService.updateStatus(id, PaymentStatus.REFUNDED);
        return "redirect:/manager/payments";
    }

    /** Update sub-function: mark a stuck/incorrect transaction as failed. */
    @PostMapping("/{id}/fail")
    public String fail(@PathVariable Long id) {
        paymentService.updateStatus(id, PaymentStatus.FAILED);
        return "redirect:/manager/payments";
    }

    /** Delete sub-function: soft-delete via the "archived" flag - the row itself is never removed. */
    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id) {
        paymentService.archive(id);
        return "redirect:/manager/payments";
    }
}
