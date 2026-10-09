package com.cinemahub.controller;

import com.cinemahub.dto.ShowtimeForm;
import com.cinemahub.model.CinemaHall;
import com.cinemahub.model.Movie;
import com.cinemahub.model.Showtime;
import com.cinemahub.model.User;
import com.cinemahub.service.CinemaHallService;
import com.cinemahub.service.MovieService;
import com.cinemahub.service.ShowtimeService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MANAGER/SYSTEM_ADMIN-only CRUD over showtimes (Function 2, write side). A
 * MANAGER's submissions need SYSTEM_ADMIN approval before customers can see
 * them (see AdminApprovalController) - SYSTEM_ADMIN's own submissions don't.
 */
@Controller
@RequestMapping("/manager/showtimes")
public class ManagerShowtimeController {

    private final ShowtimeService showtimeService;
    private final MovieService movieService;
    private final CinemaHallService cinemaHallService;
    private final UserService userService;

    public ManagerShowtimeController(ShowtimeService showtimeService, MovieService movieService,
                                      CinemaHallService cinemaHallService, UserService userService) {
        this.showtimeService = showtimeService;
        this.movieService = movieService;
        this.cinemaHallService = cinemaHallService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("showtimes", showtimeService.findAll());
        return "showtimes/manage-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("showtimeForm", new ShowtimeForm());
        addReferenceData(model);
        return "showtimes/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute ShowtimeForm showtimeForm, BindingResult bindingResult, Model model,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "showtimes/form";
        }
        try {
            showtimeService.save(toEntity(showtimeForm), currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addReferenceData(model);
            return "showtimes/form";
        }
        return "redirect:/manager/showtimes";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Showtime showtime = showtimeService.findById(id);
        ShowtimeForm form = new ShowtimeForm();
        form.setId(showtime.getId());
        form.setMovieId(showtime.getMovie().getId());
        form.setCinemaHallId(showtime.getCinemaHall().getId());
        form.setDateTime(showtime.getDateTime());
        form.setPrice(showtime.getPrice());
        model.addAttribute("showtimeForm", form);
        addReferenceData(model);
        return "showtimes/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute ShowtimeForm showtimeForm,
                          BindingResult bindingResult, Model model, Authentication authentication) {
        if (bindingResult.hasErrors()) {
            addReferenceData(model);
            return "showtimes/form";
        }
        try {
            showtimeService.update(id, toEntity(showtimeForm), currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addReferenceData(model);
            return "showtimes/form";
        }
        return "redirect:/manager/showtimes";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long id, Model model) {
        model.addAttribute("showtime", showtimeService.findById(id));
        return "showtimes/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            showtimeService.delete(id);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/manager/showtimes";
    }

    private Showtime toEntity(ShowtimeForm form) {
        Movie movie = movieService.findById(form.getMovieId());
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        CinemaHall hall = cinemaHallService.findApprovedById(form.getCinemaHallId());

        Showtime showtime = new Showtime();
        showtime.setMovie(movie);
        showtime.setCinemaHall(hall);
        showtime.setDateTime(form.getDateTime());
        showtime.setPrice(form.getPrice());
        return showtime;
    }

    private void addReferenceData(Model model) {
        model.addAttribute("movies", movieService.findAll());
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        model.addAttribute("halls", cinemaHallService.findAllApproved());
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
