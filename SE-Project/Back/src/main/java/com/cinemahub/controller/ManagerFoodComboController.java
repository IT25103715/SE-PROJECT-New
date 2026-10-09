package com.cinemahub.controller;

import com.cinemahub.model.FoodCombo;
import com.cinemahub.model.FoodComboItem;
import com.cinemahub.model.FoodItem;
import com.cinemahub.model.User;
import com.cinemahub.service.FoodComboService;
import com.cinemahub.service.FoodItemService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Food combo packs (/manager/food-combos) - MANAGER and SYSTEM_ADMIN, via the existing
 * "/manager/**" rule in SecurityConfig. Same list / form / deactivate pattern as
 * ManagerFoodController. The form lists the menu's food items with a quantity box each and shows
 * the individual total next to the combo price, so the saving is obvious.
 */
@Controller
@RequestMapping("/manager/food-combos")
public class ManagerFoodComboController {

    private final FoodComboService foodComboService;
    private final FoodItemService foodItemService;
    private final UserService userService;

    public ManagerFoodComboController(FoodComboService foodComboService, FoodItemService foodItemService,
                                      UserService userService) {
        this.foodComboService = foodComboService;
        this.foodItemService = foodItemService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("combos", foodComboService.findAll());
        return "food-combos/manage-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        FoodCombo combo = new FoodCombo();
        model.addAttribute("foodCombo", combo);
        addFormOptions(model, combo, Map.of());
        return "food-combos/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute FoodCombo foodCombo, BindingResult bindingResult,
                         @RequestParam(required = false) List<Long> itemId,
                         @RequestParam(required = false) List<Integer> itemQty,
                         Model model, Authentication authentication) {
        Map<Long, Integer> quantities = FoodComboService.toQuantities(itemId, itemQty);
        if (bindingResult.hasErrors()) {
            addFormOptions(model, foodCombo, quantities);
            return "food-combos/form";
        }
        try {
            foodComboService.create(foodCombo, quantities, currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addFormOptions(model, foodCombo, quantities);
            return "food-combos/form";
        }
        return "redirect:/manager/food-combos";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        FoodCombo combo = foodComboService.findById(id);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (FoodComboItem item : combo.getItems()) {
            quantities.merge(item.getFoodItem().getId(), item.getQuantity(), Integer::sum);
        }
        model.addAttribute("foodCombo", combo);
        addFormOptions(model, combo, quantities);
        return "food-combos/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute FoodCombo foodCombo, BindingResult bindingResult,
                         @RequestParam(required = false) List<Long> itemId,
                         @RequestParam(required = false) List<Integer> itemQty,
                         Model model, Authentication authentication) {
        Map<Long, Integer> quantities = FoodComboService.toQuantities(itemId, itemQty);
        foodCombo.setId(id);
        if (bindingResult.hasErrors()) {
            addFormOptions(model, foodCombo, quantities);
            return "food-combos/form";
        }
        try {
            foodComboService.update(id, foodCombo, quantities, currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addFormOptions(model, foodCombo, quantities);
            return "food-combos/form";
        }
        return "redirect:/manager/food-combos";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id) {
        foodComboService.deactivate(id);
        return "redirect:/manager/food-combos";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id) {
        foodComboService.reactivate(id);
        return "redirect:/manager/food-combos";
    }

    /**
     * The menu items the form offers (approved + available), plus any item already in this combo
     * that has since been taken off the menu (so the manager can see it and remove it), and the
     * quantity currently chosen for each.
     */
    private void addFormOptions(Model model, FoodCombo combo, Map<Long, Integer> quantities) {
        List<FoodItem> menuItems = new ArrayList<>(foodItemService.findOrderable());
        for (Long id : quantities.keySet()) {
            if (menuItems.stream().noneMatch(item -> item.getId().equals(id))) {
                try {
                    menuItems.add(foodItemService.findById(id));
                } catch (RuntimeException ignored) {
                    // item no longer exists - nothing to show
                }
            }
        }
        model.addAttribute("menuItems", menuItems);
        model.addAttribute("cardThemes", com.cinemahub.model.CardTheme.values());
        model.addAttribute("selectedQuantities", quantities);
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
