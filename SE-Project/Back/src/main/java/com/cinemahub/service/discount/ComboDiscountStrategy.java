package com.cinemahub.service.discount;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * COMBO promotions (e.g. "2+ seats + Large Popcorn + Parking -> Rs. 500 off"): the discount is a
 * flat Rs. amount (discountValue), but ONLY when the booking being priced meets every condition
 * the promotion sets - minimum seats, a required food item in the order, parking added.
 * If any condition isn't met, {@link #calculateDiscount} returns zero, so the booking simply goes
 * through at full price (same "an unmet/invalid promo never blocks the booking" rule as the other
 * strategies). Created by DiscountStrategyFactory like the Percentage/Fixed strategies.
 */
public class ComboDiscountStrategy implements ConditionalDiscountStrategy {

    private final Integer minSeats;
    private final Long requiredFoodItemId;
    private final String requiredFoodItemName;
    private final boolean requiresParking;
    private final ComboOrder order;

    /**
     * @param order the booking's contents; null only for a plain validity preview with no booking
     *              (e.g. "Try a Coupon" / the help assistant), where the conditions can't be checked
     */
    public ComboDiscountStrategy(Integer minSeats, Long requiredFoodItemId, String requiredFoodItemName,
                                 boolean requiresParking, ComboOrder order) {
        this.minSeats = minSeats;
        this.requiredFoodItemId = requiredFoodItemId;
        this.requiredFoodItemName = requiredFoodItemName;
        this.requiresParking = requiresParking;
        this.order = order;
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal originalPrice, BigDecimal discountValue) {
        if (order != null && !unmetConditions().isEmpty()) {
            return BigDecimal.ZERO;
        }
        // Like FixedAmountDiscountStrategy: never more than the price itself.
        return discountValue.min(originalPrice);
    }

    /** Human-readable list of what the booking is missing (empty when every condition is met). */
    @Override
    public List<String> unmetConditions() {
        List<String> missing = new ArrayList<>();
        if (order == null) {
            return missing;
        }
        if (minSeats != null && minSeats > 0 && order.seatCount() < minSeats) {
            missing.add("at least " + minSeats + " seats");
        }
        if (requiredFoodItemId != null && !order.contains(requiredFoodItemId)) {
            missing.add(requiredFoodItemName != null ? requiredFoodItemName : "the required food item");
        }
        if (requiresParking && !order.parking()) {
            missing.add("parking");
        }
        return missing;
    }
}
