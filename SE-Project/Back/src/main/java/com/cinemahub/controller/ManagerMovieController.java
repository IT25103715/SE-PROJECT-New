package com.cinemahub.controller;

import com.cinemahub.model.Movie;
import com.cinemahub.model.User;
import com.cinemahub.service.MovieService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MANAGER/SYSTEM_ADMIN-only CRUD over movies (Function 2, write side).
 * Restricted via the "/manager/**" rule in SecurityConfig. A MANAGER's
 * submissions need SYSTEM_ADMIN approval before customers can see them
 * (see AdminApprovalController) - SYSTEM_ADMIN's own submissions don't.
 */
@Controller
@RequestMapping("/manager/movies")
public class ManagerMovieController {

    private final MovieService movieService;
    private final UserService userService;

    public ManagerMovieController(MovieService movieService, UserService userService) {
        this.movieService = movieService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("movies", movieService.findAll());
        return "movies/manage-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("movie", new Movie());
        return "movies/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute Movie movie, BindingResult bindingResult, Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "movies/form";
        }
        movieService.save(movie, currentUser(authentication));
        return "redirect:/manager/movies";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("movie", movieService.findById(id));
        return "movies/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute Movie movie, BindingResult bindingResult,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "movies/form";
        }
        movieService.update(id, movie, currentUser(authentication));
        return "redirect:/manager/movies";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long id, Model model) {
        model.addAttribute("movie", movieService.findById(id));
        return "movies/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            movieService.delete(id);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/manager/movies";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
