package com.cinemahub.model;

/**
 * Which {@link com.cinemahub.service.discount.DiscountStrategy} a
 * {@link Promotion} should be calculated with.
 * COMBO = flat Rs. amount off, only when the booking meets the promotion's combo conditions
 * (minSeats / requiredFoodItem / requiresParking) - see ComboDiscountStrategy.
 */
public enum DiscountType {
    PERCENTAGE,
    FIXED_AMOUNT,
    COMBO
}
