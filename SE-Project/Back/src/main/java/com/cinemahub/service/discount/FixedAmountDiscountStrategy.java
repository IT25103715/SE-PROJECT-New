package com.cinemahub.service.discount;

import com.cinemahub.model.DiscountScope;

import java.math.BigDecimal;

/**
 * discountValue is a flat currency amount, e.g. 500 means "Rs. 500 off" - off the whole booking,
 * or off the food subtotal only when the promotion's scope is FOOD_ONLY (never more than that
 * subtotal). A minimum-seats rule, when set, must be met or the discount is zero.
 */
public class FixedAmountDiscountStrategy extends ScopedDiscountStrategy {

    /** Whole booking, no conditions - the original behaviour. */
    public FixedAmountDiscountStrategy() {
        this(DiscountScope.BOOKING_TOTAL, null, null);
    }

    public FixedAmountDiscountStrategy(DiscountScope scope, Integer minSeats, ComboOrder order) {
        super(scope, minSeats, order);
    }

    @Override
    protected BigDecimal discountOn(BigDecimal base, BigDecimal discountValue) {
        // Never discount more than the amount itself, so the final total can't go negative.
        return discountValue.min(base);
    }
}
