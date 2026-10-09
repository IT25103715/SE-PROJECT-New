package com.cinemahub.controller;

import com.cinemahub.service.MovieService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Cross-submission "Movies Overview" for MANAGER/SYSTEM_ADMIN - every movie
 * in the system regardless of who submitted it or its approval status.
 * Reached via the navbar logo for those two roles (see fragments/layout.html
 * for the role-conditional brand link). Restricted via the existing
 * "/manager/**" rule in SecurityConfig, which already grants both roles;
 * the approve/reject actions below are narrowed further to SYSTEM_ADMIN only
 * via @PreAuthorize, since a MANAGER may view this list but not decide it.
 * Approve/Reject call the exact same MovieService methods as the general
 * Admin Approval Queue (AdminApprovalController) - this is a second entry
 * point into the same approval data, not a second approval mechanism.
 */
@Controller
@RequestMapping("/manager/movies-overview")
public class ManagerMovieOverviewController {

    private final MovieService movieService;

    public ManagerMovieOverviewController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("movies", movieService.findAllForOverview());
        return "movies/overview";
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id) {
        movieService.approve(id);
        return "redirect:/manager/movies-overview";
    }

    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        movieService.reject(id, reason);
        return "redirect:/manager/movies-overview";
    }
}
