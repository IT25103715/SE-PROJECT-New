package com.cinemahub.controller;

import com.cinemahub.dto.RegistrationDto;
import com.cinemahub.model.Movie;
import com.cinemahub.service.ShowtimeService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login page rendering + public self-registration (Function 1).
 * Actual credential checking on login is handled entirely by Spring
 * Security (see SecurityConfig#filterChain) - this controller only shows
 * the login form and processes registration.
 */
@Controller
public class AuthController {

    /** How many posters the login page's backdrop collage shows. */
    private static final int LOGIN_POSTER_LIMIT = 6;

    private final UserService userService;
    private final ShowtimeService showtimeService;

    public AuthController(UserService userService, ShowtimeService showtimeService) {
        this.userService = userService;
        this.showtimeService = showtimeService;
    }

    /**
     * The left-hand visual is built from REAL data: posters of the films that have upcoming
     * approved showtimes, and how many such films there are (no invented statistics).
     */
    @GetMapping("/auth/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        // Marks both fields aria-invalid after a failed attempt (Thymeleaf can't read request
        // parameters inside th:attr, so the template gets a plain flag).
        model.addAttribute("loginFailed", error != null);
        Map<Long, Movie> nowShowing = new LinkedHashMap<>();
        showtimeService.findUpcomingApproved().forEach(st -> nowShowing.putIfAbsent(st.getMovie().getId(), st.getMovie()));
        List<String> posters = nowShowing.values().stream()
                .map(Movie::getPosterUrl)
                .filter(url -> url != null && !url.isBlank())
                .limit(LOGIN_POSTER_LIMIT)
                .toList();
        model.addAttribute("loginPosters", posters);
        model.addAttribute("nowShowingCount", nowShowing.size());
        return "auth/login";
    }

    @GetMapping("/auth/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationDto", new RegistrationDto());
        return "auth/register";
    }

    @PostMapping("/auth/register")
    public String register(@Valid @ModelAttribute RegistrationDto registrationDto,
                            BindingResult bindingResult,
                            Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerCustomer(registrationDto);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }
        return "redirect:/auth/login?registered";
    }
}
