package com.cinemahub.controller;

import com.cinemahub.model.FoodCategory;
import com.cinemahub.model.FoodItem;
import com.cinemahub.model.User;
import com.cinemahub.service.FoodItemService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * MANAGER/SYSTEM_ADMIN CRUD over the food menu customers can add at checkout. Restricted by the
 * existing "/manager/**" rule in SecurityConfig. A MANAGER's new/edited items need SYSTEM_ADMIN
 * approval (Approval Queue) before customers see them - same as Movies/Showtimes/Halls.
 */
@Controller
@RequestMapping("/manager/food")
public class ManagerFoodController {

    private final FoodItemService foodItemService;
    private final UserService userService;

    public ManagerFoodController(FoodItemService foodItemService, UserService userService) {
        this.foodItemService = foodItemService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("foodItems", foodItemService.findAll());
        return "food/manage-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("foodItem", new FoodItem());
        model.addAttribute("categories", FoodCategory.values());
        return "food/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute FoodItem foodItem, BindingResult bindingResult, Model model,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", FoodCategory.values());
            return "food/form";
        }
        foodItemService.create(foodItem, currentUser(authentication));
        return "redirect:/manager/food";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("foodItem", foodItemService.findById(id));
        model.addAttribute("categories", FoodCategory.values());
        return "food/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute FoodItem foodItem, BindingResult bindingResult,
                          Model model, Authentication authentication) {
        if (bindingResult.hasErrors()) {
            foodItem.setId(id);
            model.addAttribute("categories", FoodCategory.values());
            return "food/form";
        }
        foodItemService.update(id, foodItem, currentUser(authentication));
        return "redirect:/manager/food";
    }

    /** Soft delete - the item stays in past bookings, it just can't be ordered any more. */
    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id) {
        foodItemService.deactivate(id);
        return "redirect:/manager/food";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id) {
        foodItemService.reactivate(id);
        return "redirect:/manager/food";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
