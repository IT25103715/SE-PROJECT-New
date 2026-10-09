package com.cinemahub.controller;

import com.cinemahub.model.CinemaHall;
import com.cinemahub.model.User;
import com.cinemahub.service.CinemaHallService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MANAGER/SYSTEM_ADMIN CRUD over cinema halls - a small supporting piece of
 * Function 2 needed before showtimes can be scheduled (a showtime always
 * happens in a hall). A MANAGER's new hall needs SYSTEM_ADMIN approval
 * before it can be assigned to a showtime (see AdminApprovalController) -
 * SYSTEM_ADMIN's own submissions don't.
 */
@Controller
@RequestMapping("/manager/halls")
public class ManagerHallController {

    private final CinemaHallService cinemaHallService;
    private final UserService userService;

    public ManagerHallController(CinemaHallService cinemaHallService, UserService userService) {
        this.cinemaHallService = cinemaHallService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("halls", cinemaHallService.findAll());
        return "showtimes/halls-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("hall", new CinemaHall());
        return "showtimes/halls-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("hall") CinemaHall hall, BindingResult bindingResult,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "showtimes/halls-form";
        }
        cinemaHallService.save(hall, currentUser(authentication));
        return "redirect:/manager/halls";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("hall", cinemaHallService.findById(id));
        return "showtimes/halls-form";
    }

    /**
     * Renaming only - see {@link CinemaHallService#update} for why
     * totalRows/seatsPerRow can't change once the hall has a seat grid.
     */
    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @RequestParam String name, Model model, Authentication authentication) {
        try {
            cinemaHallService.update(id, name, currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("hall", cinemaHallService.findById(id));
            model.addAttribute("errorMessage", ex.getMessage());
            return "showtimes/halls-form";
        }
        return "redirect:/manager/halls";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            cinemaHallService.delete(id);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/manager/halls";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
