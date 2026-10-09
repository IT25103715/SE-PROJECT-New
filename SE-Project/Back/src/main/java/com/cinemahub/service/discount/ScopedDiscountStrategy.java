package com.cinemahub.service.discount;

import com.cinemahub.model.DiscountScope;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared rules for the Percentage and Fixed Amount strategies:
 * <ul>
 *   <li><b>Scope</b> - BOOKING_TOTAL discounts the whole booking (as before); FOOD_ONLY discounts only
 *       the food subtotal, ignoring ticket and parking prices.</li>
 *   <li><b>Minimum seats</b> - if set, the discount only applies when the booking has at least that
 *       many seats; otherwise it is zero.</li>
 * </ul>
 * With the no-argument constructors of the subclasses (whole booking, no seat rule, no order) the
 * result is exactly the original behaviour.
 */
public abstract class ScopedDiscountStrategy implements ConditionalDiscountStrategy {

    private final DiscountScope scope;
    private final Integer minSeats;
    private final ComboOrder order;

    /**
     * @param order the booking being priced; null for a plain preview with no booking ("Try a
     *              Coupon", help assistant) - the conditions can't be checked then, and the amount
     *              given is treated as the amount the discount applies to
     */
    protected ScopedDiscountStrategy(DiscountScope scope, Integer minSeats, ComboOrder order) {
        this.scope = scope == null ? DiscountScope.BOOKING_TOTAL : scope;
        this.minSeats = minSeats;
        this.order = order;
    }

    @Override
    public final BigDecimal calculateDiscount(BigDecimal originalPrice, BigDecimal discountValue) {
        if (!unmetConditions().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal base = discountBase(originalPrice);
        if (base.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        // Never more than the part being discounted, so the total can't go negative.
        return discountOn(base, discountValue).min(base);
    }

    /** The discount on {@code base} (the whole booking, or just the food subtotal). */
    protected abstract BigDecimal discountOn(BigDecimal base, BigDecimal discountValue);

    /** Whole booking, or only the food subtotal for a FOOD_ONLY promotion. */
    private BigDecimal discountBase(BigDecimal originalPrice) {
        if (scope == DiscountScope.FOOD_ONLY && order != null) {
            return order.foodTotal().min(originalPrice);
        }
        return originalPrice;
    }

    @Override
    public List<String> unmetConditions() {
        List<String> missing = new ArrayList<>();
        if (order == null) {
            return missing;
        }
        if (minSeats != null && minSeats > 0 && order.seatCount() < minSeats) {
            missing.add("at least " + minSeats + " seats");
        }
        if (scope == DiscountScope.FOOD_ONLY && order.foodTotal().signum() <= 0) {
            missing.add("food in your order (it only discounts food)");
        }
        return missing;
    }
}
