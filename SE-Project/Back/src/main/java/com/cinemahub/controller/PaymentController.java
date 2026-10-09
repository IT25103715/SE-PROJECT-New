package com.cinemahub.controller;

import com.cinemahub.model.User;
import com.cinemahub.service.PaymentService;
import com.cinemahub.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * Function 4: "Payment and Transaction Management" - customer-facing side.
 * "/payments" is the customer's own read-only payment history; the
 * manager/admin transaction log and revenue reporting live in
 * {@link ManagerPaymentController} under "/manager/payments", matching how
 * the other manager-only views (movies/showtimes/halls) are split out.
 */
@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    public PaymentController(PaymentService paymentService, UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    /** Read: My Payment History. */
    @GetMapping
    public String history(Authentication authentication, Model model) {
        User user = userService.findByEmail(authentication.getName());
        model.addAttribute("payments", paymentService.findByUser(user.getId()));
        return "payments/history";
    }
}
