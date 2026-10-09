package com.cinemahub.controller;

import com.cinemahub.model.ParkingOption;
import com.cinemahub.model.User;
import com.cinemahub.service.ParkingOptionService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * MANAGER/SYSTEM_ADMIN CRUD over car parking options ("/manager/**" rule in SecurityConfig).
 * Same approval and soft-delete rules as the food menu.
 */
@Controller
@RequestMapping("/manager/parking")
public class ManagerParkingController {

    private final ParkingOptionService parkingOptionService;
    private final UserService userService;

    public ManagerParkingController(ParkingOptionService parkingOptionService, UserService userService) {
        this.parkingOptionService = parkingOptionService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("parkingOptions", parkingOptionService.findAll());
        return "parking/manage-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("parkingOption", new ParkingOption());
        return "parking/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute ParkingOption parkingOption, BindingResult bindingResult,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "parking/form";
        }
        parkingOptionService.create(parkingOption, currentUser(authentication));
        return "redirect:/manager/parking";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("parkingOption", parkingOptionService.findById(id));
        return "parking/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute ParkingOption parkingOption,
                          BindingResult bindingResult, Authentication authentication) {
        if (bindingResult.hasErrors()) {
            parkingOption.setId(id);
            return "parking/form";
        }
        parkingOptionService.update(id, parkingOption, currentUser(authentication));
        return "redirect:/manager/parking";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id) {
        parkingOptionService.deactivate(id);
        return "redirect:/manager/parking";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id) {
        parkingOptionService.reactivate(id);
        return "redirect:/manager/parking";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
