package com.cinemahub.model;

/**
 * What part of a booking a Percentage / Fixed Amount promotion discounts.
 * BOOKING_TOTAL is the original behaviour (seats + food + parking); FOOD_ONLY discounts only the
 * food subtotal (food items and food combos), e.g. "20% off food when you book 2+ seats".
 */
public enum DiscountScope {
    BOOKING_TOTAL("Whole booking (seats + food + parking)"),
    FOOD_ONLY("Food only");

    private final String displayLabel;

    DiscountScope(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }
}
