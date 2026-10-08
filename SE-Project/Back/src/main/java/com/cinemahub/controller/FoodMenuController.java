package com.cinemahub.controller;

import com.cinemahub.service.FoodComboService;
import com.cinemahub.service.FoodItemService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public, read-only food & drinks menu (/menu) - where the homepage Promotions tab's food combo
 * cards ("View Menu") lead. Shows the approved, available combo packs and food items; customers
 * add them at checkout after picking their seats.
 */
@Controller
public class FoodMenuController {

    private final FoodComboService foodComboService;
    private final FoodItemService foodItemService;

    public FoodMenuController(FoodComboService foodComboService, FoodItemService foodItemService) {
        this.foodComboService = foodComboService;
        this.foodItemService = foodItemService;
    }

    @GetMapping("/menu")
    public String menu(Model model) {
        model.addAttribute("foodCombos", foodComboService.findOrderable());
        model.addAttribute("foodItems", foodItemService.findOrderable());
        return "menu";
    }
}
