package com.cinemahub.controller;

import com.cinemahub.model.PaymentOption;
import com.cinemahub.model.PaymentOptionType;
import com.cinemahub.service.PaymentOptionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * PAYMENT_MANAGER-only CRUD over which payment options the system accepts -
 * this role's entire scope on the site. Restricted via the
 * "/payment-manager/**" rule in SecurityConfig (SYSTEM_ADMIN also allowed,
 * for oversight). Distinct from PaymentController/ManagerPaymentController,
 * which handle individual customer transactions (Function 4).
 *
 * The form asks for different provider fields depending on the selected type (see
 * {@link #validateTypeFields}): Apple Pay, PayPal (public Client ID only - never the secret)
 * or Bank Transfer (full account details).
 */
@Controller
@RequestMapping("/payment-manager")
public class PaymentManagerController {

    private final PaymentOptionService paymentOptionService;

    public PaymentManagerController(PaymentOptionService paymentOptionService) {
        this.paymentOptionService = paymentOptionService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("paymentOptions", paymentOptionService.findAll());
        return "payment-manager/dashboard";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        PaymentOption paymentOption = new PaymentOption();
        paymentOption.setType(PaymentOptionType.APPLE_PAY);
        model.addAttribute("paymentOption", paymentOption);
        model.addAttribute("types", PaymentOptionType.values());
        return "payment-manager/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute PaymentOption paymentOption, BindingResult bindingResult, Model model) {
        validateTypeFields(paymentOption, bindingResult);
        if (bindingResult.hasErrors()) {
            model.addAttribute("types", PaymentOptionType.values());
            return "payment-manager/form";
        }
        try {
            paymentOptionService.create(paymentOption);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("types", PaymentOptionType.values());
            return "payment-manager/form";
        }
        return "redirect:/payment-manager/dashboard";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("paymentOption", paymentOptionService.findById(id));
        model.addAttribute("types", PaymentOptionType.values());
        return "payment-manager/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute PaymentOption paymentOption,
                          BindingResult bindingResult, Model model) {
        validateTypeFields(paymentOption, bindingResult);
        if (bindingResult.hasErrors()) {
            paymentOption.setId(id);
            model.addAttribute("types", PaymentOptionType.values());
            return "payment-manager/form";
        }
        try {
            paymentOptionService.update(id, paymentOption);
        } catch (IllegalArgumentException ex) {
            paymentOption.setId(id);
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("types", PaymentOptionType.values());
            return "payment-manager/form";
        }
        return "redirect:/payment-manager/dashboard";
    }

    /** The module's explicit "Delete" action - flips the payment option to DISABLED instead of removing it. */
    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id) {
        paymentOptionService.deactivate(id);
        return "redirect:/payment-manager/dashboard";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id) {
        paymentOptionService.reactivate(id);
        return "redirect:/payment-manager/dashboard";
    }

    /**
     * Server-side check of the type-specific fields (the form's show/hide is only for convenience):
     * Apple Pay needs Merchant ID + Display Name, PayPal needs the public Client ID, Bank Transfer
     * needs every bank field except the optional SWIFT code. Fields of other types are ignored and
     * cleared on save (PaymentOptionService#keepOnlyFieldsForType).
     */
    private void validateTypeFields(PaymentOption option, BindingResult result) {
        PaymentOptionType type = option.getType();
        if (type == null) {
            return; // already reported by @NotNull on the entity
        }
        switch (type) {
            case APPLE_PAY -> {
                require(option.getMerchantId(), "merchantId", "Merchant ID is required for Apple Pay", result);
                require(option.getMerchantDisplayName(), "merchantDisplayName", "Merchant Display Name is required for Apple Pay", result);
            }
            case PAYPAL -> {
                require(option.getClientId(), "clientId", "Client ID is required for PayPal", result);
                if (option.getClientId() != null && option.getClientId().trim().contains(" ")) {
                    result.rejectValue("clientId", "invalid", "A PayPal Client ID has no spaces - paste it exactly as shown on developer.paypal.com");
                }
            }
            case BANK_TRANSFER -> {
                require(option.getBankName(), "bankName", "Bank name is required", result);
                require(option.getBranchName(), "branchName", "Branch name is required", result);
                require(option.getAccountHolderName(), "accountHolderName", "Account holder name is required", result);
                require(option.getAccountNumber(), "accountNumber", "Account number is required", result);
                require(option.getBranchCode(), "branchCode", "Branch code is required", result);
                require(option.getPaymentReferenceInstructions(), "paymentReferenceInstructions",
                        "Payment reference instructions are required", result);
                String swift = option.getSwiftCode();
                if (swift != null && !swift.isBlank()
                        && !swift.trim().toUpperCase().matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?")) {
                    result.rejectValue("swiftCode", "invalid", "SWIFT code must be 8 or 11 letters/digits, e.g. CCEYLKLX - or leave it blank");
                }
            }
            case DIGITAL_WALLET, CARD_NETWORK -> {
                // Original generic types - only the optional free-text details field.
            }
        }
    }

    private static void require(String value, String field, String message, BindingResult result) {
        if (value == null || value.isBlank()) {
            result.rejectValue(field, "required", message);
        }
    }
}
