package com.cinemahub.controller;

import com.cinemahub.dto.PromotionTestForm;
import com.cinemahub.model.DiscountType;
import com.cinemahub.model.Promotion;
import com.cinemahub.model.PromotionStatus;
import com.cinemahub.model.User;
import com.cinemahub.service.BookingService;
import com.cinemahub.service.FoodItemService;
import com.cinemahub.service.PromotionService;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PROMOTION_MANAGER-only CRUD over coupons/campaigns (Function 6) - this
 * role's entire scope on the site. Restricted via the
 * "/promotion-manager/**" rule in SecurityConfig, which (deliberately,
 * unlike the old shared "/promotions/manage/**") does NOT grant
 * SYSTEM_ADMIN access - the admin's own site-control panel is separate.
 * Every promotion created here needs SYSTEM_ADMIN approval before it's
 * redeemable by customers (see AdminApprovalController).
 */
@Controller
@RequestMapping("/promotion-manager")
public class PromotionController {

    private final PromotionService promotionService;
    private final BookingService bookingService;
    private final UserService userService;
    private final FoodItemService foodItemService;

    public PromotionController(PromotionService promotionService, BookingService bookingService,
                                UserService userService, FoodItemService foodItemService) {
        this.promotionService = promotionService;
        this.bookingService = bookingService;
        this.userService = userService;
        this.foodItemService = foodItemService;
    }

    /** Form dropdowns: discount types, discount scopes, and the food items a COMBO can require (approved, available ones). */
    private void addFormOptions(Model model) {
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("discountScopes", com.cinemahub.model.DiscountScope.values());
        model.addAttribute("cardThemes", com.cinemahub.model.CardTheme.values());
        model.addAttribute("comboFoodItems", foodItemService.findOrderable());
    }

    /** The COMBO's "Required Food Item" comes in as a plain id (requiredFoodItemId), blank = none. */
    private void applyRequiredFoodItem(Promotion promotion, Long requiredFoodItemId) {
        promotion.setRequiredFoodItem(requiredFoodItemId == null ? null : foodItemService.findById(requiredFoodItemId));
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Promotion> promotions = promotionService.findAll();
        model.addAttribute("promotions", promotions);

        // Performance tracking (US-35): simple aggregate stats above the table.
        long activeCount = promotions.stream().filter(p -> p.getStatus() == PromotionStatus.ACTIVE).count();
        int totalRedemptions = promotions.stream().mapToInt(Promotion::getUsageCount).sum();
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("totalRedemptions", totalRedemptions);
        model.addAttribute("discountThisMonth", bookingService.getDiscountGivenThisMonth());

        return "promotion-manager/dashboard";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("promotion", new Promotion());
        addFormOptions(model);
        return "promotion-manager/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute Promotion promotion, BindingResult bindingResult,
                          @RequestParam(required = false) Long requiredFoodItemId, Model model,
                          Authentication authentication) {
        applyRequiredFoodItem(promotion, requiredFoodItemId);
        if (bindingResult.hasErrors()) {
            addFormOptions(model);
            return "promotion-manager/form";
        }
        try {
            promotionService.create(promotion, currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            addFormOptions(model);
            return "promotion-manager/form";
        }
        return "redirect:/promotion-manager/dashboard";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("promotion", promotionService.findById(id));
        addFormOptions(model);
        return "promotion-manager/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute Promotion promotion,
                          BindingResult bindingResult, @RequestParam(required = false) Long requiredFoodItemId,
                          Model model, Authentication authentication) {
        applyRequiredFoodItem(promotion, requiredFoodItemId);
        if (bindingResult.hasErrors()) {
            promotion.setId(id);
            addFormOptions(model);
            return "promotion-manager/form";
        }
        try {
            promotionService.update(id, promotion, currentUser(authentication));
        } catch (IllegalArgumentException ex) {
            promotion.setId(id);
            model.addAttribute("errorMessage", ex.getMessage());
            addFormOptions(model);
            return "promotion-manager/form";
        }
        return "redirect:/promotion-manager/dashboard";
    }

    /** The module's explicit "Delete" action - flips the coupon to DISABLED instead of removing it. */
    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id) {
        promotionService.deactivate(id);
        return "redirect:/promotion-manager/dashboard";
    }

    @PostMapping("/{id}/expire")
    public String expire(@PathVariable Long id) {
        promotionService.expire(id);
        return "redirect:/promotion-manager/dashboard";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id) {
        promotionService.reactivate(id);
        return "redirect:/promotion-manager/dashboard";
    }

    /** Lets a Promotion Manager try a code against an amount and see the Strategy pattern's result. */
    @GetMapping("/test")
    public String testForm(Model model) {
        model.addAttribute("promotionTestForm", new PromotionTestForm());
        return "promotion-manager/test";
    }

    @PostMapping("/test")
    public String test(@Valid @ModelAttribute PromotionTestForm promotionTestForm,
                        BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "promotion-manager/test";
        }
        try {
            model.addAttribute("result",
                    promotionService.previewDiscount(promotionTestForm.getCode(), promotionTestForm.getAmount()));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "promotion-manager/test";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
