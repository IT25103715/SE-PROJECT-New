package com.cinemahub.controller;

import com.cinemahub.dto.UserForm;
import com.cinemahub.model.Role;
import com.cinemahub.model.UserStatus;
import com.cinemahub.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * SYSTEM_ADMIN-only account management (Function 1). Full CRUD over
 * {@link com.cinemahub.model.User}: list, create, edit (role/status/reset
 * password), delete - plus the one-click suspend/activate shortcuts asked
 * for in the spec.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users-list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("userForm", new UserForm());
        model.addAttribute("roles", Role.values());
        model.addAttribute("statuses", UserStatus.values());
        return "admin/users-form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute UserForm userForm, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("statuses", UserStatus.values());
            return "admin/users-form";
        }
        try {
            userService.createUser(userForm);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("roles", Role.values());
            model.addAttribute("statuses", UserStatus.values());
            return "admin/users-form";
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var user = userService.findById(id);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setName(user.getName());
        form.setEmail(user.getEmail());
        form.setRole(user.getRole());
        form.setStatus(user.getStatus());
        // Older accounts may have no phone numbers yet - the page still opens; the "at least one
        // phone number" rule is only checked when the admin saves.
        form.setPhoneNumber(user.getPhoneNumber());
        form.setPhoneNumberAlt(user.getPhoneNumberAlt());
        model.addAttribute("userForm", form);
        model.addAttribute("roles", Role.values());
        model.addAttribute("statuses", UserStatus.values());
        return "admin/users-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute UserForm userForm,
                          BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("statuses", UserStatus.values());
            return "admin/users-form";
        }
        try {
            userService.updateUser(id, userForm);
        } catch (IllegalArgumentException ex) {
            bindingResult.rejectValue("phoneNumber", "phone.atLeastOne", ex.getMessage());
            model.addAttribute("roles", Role.values());
            model.addAttribute("statuses", UserStatus.values());
            return "admin/users-form";
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/suspend")
    public String suspend(@PathVariable Long id) {
        userService.updateStatus(id, UserStatus.SUSPENDED);
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id) {
        userService.updateStatus(id, UserStatus.ACTIVE);
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(@PathVariable Long id, Model model) {
        model.addAttribute("user", userService.findById(id));
        return "admin/users-delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }
}
