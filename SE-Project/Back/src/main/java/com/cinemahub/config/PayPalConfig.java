package com.cinemahub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * PayPal Sandbox settings, read from the git-ignored local file config/application.properties:
 * <pre>
 * paypal.client-id=...
 * paypal.client-secret=...
 * paypal.mode=sandbox
 * </pre>
 * Optional: {@code paypal.currency} (default USD) and {@code paypal.lkr-per-usd} (default 300) -
 * our prices are in LKR, which PayPal doesn't support, so the booking total is converted before
 * the PayPal order is created.
 *
 * The secret is only ever handed to {@link com.cinemahub.service.PayPalClient} for the OAuth call.
 * It is never logged, never sent to the browser, and toString() deliberately leaves it out.
 * Only "sandbox" mode is accepted - live payments are not part of this project.
 */
@Component
public class PayPalConfig {

    private static final String SANDBOX_API = "https://api-m.sandbox.paypal.com";

    private final String clientId;
    private final String clientSecret;
    private final String mode;
    private final String currency;
    private final BigDecimal lkrPerUsd;

    public PayPalConfig(@Value("${paypal.client-id:}") String clientId,
                        @Value("${paypal.client-secret:}") String clientSecret,
                        @Value("${paypal.mode:sandbox}") String mode,
                        @Value("${paypal.currency:USD}") String currency,
                        @Value("${paypal.lkr-per-usd:300}") BigDecimal lkrPerUsd) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.mode = mode == null ? "sandbox" : mode.trim().toLowerCase();
        this.currency = currency == null ? "USD" : currency.trim().toUpperCase();
        this.lkrPerUsd = lkrPerUsd;
    }

    /** True only when both Sandbox credentials are filled in and mode is "sandbox". */
    public boolean isEnabled() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && "sandbox".equals(mode);
    }

    public String getApiBaseUrl() {
        return SANDBOX_API;
    }

    /** Public value - PayPal's JS SDK needs it in the page. */
    public String getClientId() {
        return clientId;
    }

    /** Only PayPalClient reads this (for the OAuth token call) - never log it or put it in a model. */
    public String getClientSecret() {
        return clientSecret;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getLkrPerUsd() {
        return lkrPerUsd;
    }

    /** Converts an LKR booking total to the PayPal charge amount (2 decimals, never below 0.01). */
    public BigDecimal toPayPalAmount(BigDecimal lkrAmount) {
        BigDecimal converted = lkrAmount.divide(lkrPerUsd, 2, RoundingMode.HALF_UP);
        return converted.compareTo(new BigDecimal("0.01")) < 0 ? new BigDecimal("0.01") : converted;
    }

    @Override
    public String toString() {
        return "PayPalConfig{mode=" + mode + ", currency=" + currency + ", enabled=" + isEnabled() + "}";
    }
}
