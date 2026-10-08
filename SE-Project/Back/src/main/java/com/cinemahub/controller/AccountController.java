package com.cinemahub.controller;

import com.cinemahub.dto.AccountForm;
import com.cinemahub.model.Role;
import com.cinemahub.model.User;
import com.cinemahub.model.UserStatus;
import com.cinemahub.service.MembershipService;
import com.cinemahub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Basic self-service "my account" pages (Function 1) - view/update contact
 * details, or deactivate/delete the account. This used to be its own module
 * (Function 4, "Customer Profile Management") but that function was
 * repurposed to Payment & Transaction Management - see
 * {@link PaymentController} for "My Payment History" - so this now lives as
 * a thin controller directly over {@link User}, with no separate Profile
 * entity behind it.
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private final UserService userService;
    private final MembershipService membershipService;

    public AccountController(UserService userService, MembershipService membershipService) {
        this.userService = userService;
        this.membershipService = membershipService;
    }

    @GetMapping
    public String view(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        // Membership program panel - customers only (staff accounts don't book for themselves).
        if (user.getRole() == Role.CUSTOMER) {
            // Catch-up for customers who already had enough bookings before membership existed.
            if (!user.isMember() && membershipService.recordConfirmedBooking(user)) {
                user = currentUser(authentication);
                model.addAttribute("user", user);
            }
            long confirmed = membershipService.countConfirmedBookings(user.getId());
            model.addAttribute("showMembership", true);
            model.addAttribute("membershipThreshold", MembershipService.BOOKINGS_FOR_MEMBERSHIP);
            model.addAttribute("confirmedBookings", confirmed);
            model.addAttribute("bookingsToMembership", membershipService.bookingsUntilMembership(user));
        }
        return "account/view";
    }

    @GetMapping("/edit")
    public String editForm(Authentication authentication, Model model) {
        User user = currentUser(authentication);

        AccountForm form = new AccountForm();
        form.setName(user.getName());
        form.setPhoneNumber(user.getPhoneNumber());
        form.setPhoneNumberAlt(user.getPhoneNumberAlt());
        form.setAddress(user.getAddress());

        model.addAttribute("accountForm", form);
        return "account/edit";
    }

    /**
     * Saving the account page enforces the "at least one phone number" rule (PhoneNumberRule, run by
     * @Valid). Customers whose account has no phone yet are only asked for one here, when they save -
     * nothing blocks them from logging in or using the site before that.
     */
    @PostMapping("/edit")
    public String update(@Valid @ModelAttribute AccountForm accountForm, BindingResult bindingResult,
                         Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "account/edit";
        }
        User user = currentUser(authentication);
        try {
            userService.updateContactInfo(user.getId(), accountForm);
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("phoneNumber", "phone.atLeastOne", ex.getMessage());
            return "account/edit";
        }
        return "redirect:/account";
    }

    @PostMapping("/deactivate")
    public String deactivate(Authentication authentication, HttpServletRequest request) throws jakarta.servlet.ServletException {
        User user = currentUser(authentication);
        userService.updateStatus(user.getId(), UserStatus.SUSPENDED);
        request.logout();
        return "redirect:/?deactivated";
    }

    @PostMapping("/delete")
    public String delete(Authentication authentication, HttpServletRequest request,
                          RedirectAttributes redirectAttributes) throws jakarta.servlet.ServletException {
        User user = currentUser(authentication);
        try {
            userService.deleteUser(user.getId());
        } catch (IllegalStateException ex) {
            // Booking/payment history can't be erased - stay signed in and explain, instead of logging out first.
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/account";
        }
        request.logout();
        return "redirect:/?accountDeleted";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
