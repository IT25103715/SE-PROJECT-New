package com.cinemahub.service.discount;

import com.cinemahub.model.DiscountScope;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * discountValue is a percentage, e.g. 20 means "take 20% off" - of the whole booking, or of the
 * food subtotal only when the promotion's scope is FOOD_ONLY. A minimum-seats rule, when set,
 * must be met or the discount is zero (see ScopedDiscountStrategy).
 */
public class PercentageDiscountStrategy extends ScopedDiscountStrategy {

    /** Whole booking, no conditions - the original behaviour. */
    public PercentageDiscountStrategy() {
        this(DiscountScope.BOOKING_TOTAL, null, null);
    }

    public PercentageDiscountStrategy(DiscountScope scope, Integer minSeats, ComboOrder order) {
        super(scope, minSeats, order);
    }

    @Override
    protected BigDecimal discountOn(BigDecimal base, BigDecimal discountValue) {
        // A percentage discount can never exceed the amount itself (the base class also caps it,
        // keeping the total safe if a future rule ever allows values over 100).
        return base.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
