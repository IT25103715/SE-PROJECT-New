package com.cinemahub.config;

import com.cinemahub.model.PaymentOption;
import com.cinemahub.model.PaymentOptionStatus;
import com.cinemahub.model.PaymentOptionType;
import com.cinemahub.repository.PaymentOptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Makes sure the seat map's "Payment method" dropdown actually offers PayPal once the real
 * PayPal Sandbox checkout is configured (see PayPalConfig / PayPalCheckoutController).
 *
 * DataSeeder only seeds payment options into an EMPTY database, so a database created before
 * the Payment Manager's options existed (or one where PayPal was never added) has no PayPal
 * option at all - and the dropdown only lists ACTIVE Payment Manager options.
 *
 * On startup, if PayPal Sandbox keys are set and no option named "PayPal" exists, this adds one
 * (type PAYPAL with the public Client ID, ACTIVE). If a PayPal option already exists it is left exactly as it is -
 * a Payment Manager who deliberately disabled it keeps that decision (reactivate it from the
 * Payment Manager dashboard instead).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class PayPalOptionInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PayPalOptionInitializer.class);
    static final String PAYPAL_OPTION_NAME = "PayPal";

    private final PayPalConfig payPalConfig;
    private final PaymentOptionRepository paymentOptionRepository;

    public PayPalOptionInitializer(PayPalConfig payPalConfig, PaymentOptionRepository paymentOptionRepository) {
        this.payPalConfig = payPalConfig;
        this.paymentOptionRepository = paymentOptionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!payPalConfig.isEnabled()) {
            return;
        }
        Optional<PaymentOption> existing = paymentOptionRepository.findAll().stream()
                .filter(option -> PAYPAL_OPTION_NAME.equalsIgnoreCase(option.getName().trim()))
                .findFirst();
        if (existing.isPresent()) {
            if (existing.get().getStatus() != PaymentOptionStatus.ACTIVE) {
                log.warn("PayPal payment option exists but is {} - customers won't see it until a Payment "
                        + "Manager reactivates it (/payment-manager/dashboard).", existing.get().getStatus());
            }
            return;
        }
        PaymentOption paypal = new PaymentOption();
        paypal.setName(PAYPAL_OPTION_NAME);
        paypal.setType(PaymentOptionType.PAYPAL);
        // Public Client ID only - the secret stays in config/application.properties.
        paypal.setClientId(payPalConfig.getClientId());
        paypal.setStatus(PaymentOptionStatus.ACTIVE);
        paymentOptionRepository.save(paypal);
        log.info("Added the missing \"PayPal\" payment option so the PayPal Sandbox checkout shows at checkout.");
    }
}
