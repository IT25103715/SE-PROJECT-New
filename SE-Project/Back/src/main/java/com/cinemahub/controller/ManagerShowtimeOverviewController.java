package com.cinemahub.controller;

import com.cinemahub.service.ShowtimeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Cross-submission "Showtimes Overview" for MANAGER/SYSTEM_ADMIN - every
 * showtime in the system regardless of who submitted it or its approval
 * status. Mirrors {@link ManagerMovieOverviewController} exactly: restricted
 * via the existing "/manager/**" rule in SecurityConfig, which already grants
 * both roles; the approve/reject actions below are narrowed further to
 * SYSTEM_ADMIN only via @PreAuthorize, since a MANAGER may view this list but
 * not decide it. Approve/Reject call the exact same ShowtimeService methods
 * as the general Admin Approval Queue (AdminApprovalController) - this is a
 * second entry point into the same approval data, not a second approval
 * mechanism.
 */
@Controller
@RequestMapping("/manager/showtimes-overview")
public class ManagerShowtimeOverviewController {

    private final ShowtimeService showtimeService;

    public ManagerShowtimeOverviewController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("showtimes", showtimeService.findAllForOverview());
        return "showtimes/overview";
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id) {
        showtimeService.approve(id);
        return "redirect:/manager/showtimes-overview";
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        showtimeService.reject(id, reason);
        return "redirect:/manager/showtimes-overview";
    }
}
