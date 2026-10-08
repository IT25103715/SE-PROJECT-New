package com.cinemahub.controller;

import com.cinemahub.service.ShowtimeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Public showtime listing (Function 2, read side). */
@Controller
@RequestMapping("/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("showtimes", showtimeService.findAllApproved());
        return "showtimes/list";
    }
}
