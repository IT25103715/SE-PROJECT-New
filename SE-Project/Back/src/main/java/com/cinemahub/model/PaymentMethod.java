package com.cinemahub.model;

/**
 * Simulated payment methods only - see "Payment (Simulated)" under System
 * Limitations & Constraints in the proposal. No real payment gateway is
 * ever contacted; this is purely a dropdown recorded on the Payment row.
 */
public enum PaymentMethod {
    CARD,
    CASH,
    SIMULATED_GATEWAY;

    /** Cosmetic label only - the constant name (and DB value) stays SIMULATED_GATEWAY; no real PayPal integration exists. */
    public String getDisplayLabel() {
        return switch (this) {
            case CARD -> "Card";
            case CASH -> "Cash";
            case SIMULATED_GATEWAY -> "PayPal";
        };
    }
}
