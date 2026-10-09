package com.cinemahub.service.discount;

import com.cinemahub.model.DiscountType;
import com.cinemahub.model.Promotion;

/**
 * Factory pattern, mirroring {@code service.notification.NotificationFactory}:
 * callers ask for a {@link DiscountType} and get back the matching
 * {@link DiscountStrategy}, without needing to know the concrete class.
 * Adding a new discount type later only means one enum value and one case here.
 */
public class DiscountStrategyFactory {

    private DiscountStrategyFactory() {
    }

    /** Percentage / fixed strategies (a COMBO from here can't see the booking, so conditions aren't checked). */
    public static DiscountStrategy get(DiscountType type) {
        return switch (type) {
            case PERCENTAGE -> new PercentageDiscountStrategy();
            case FIXED_AMOUNT -> new FixedAmountDiscountStrategy();
            case COMBO -> new ComboDiscountStrategy(null, null, null, false, null);
        };
    }

    /**
     * The strategy for a specific promotion and booking. Percentage and Fixed promotions apply
     * their scope (whole booking / food only) and optional minimum seats; COMBO promotions check
     * their seat / food / parking conditions. {@code order} may be null for a plain preview
     * without a booking, in which case no condition can be checked.
     */
    public static DiscountStrategy get(Promotion promotion, ComboOrder order) {
        return switch (promotion.getDiscountType()) {
            case PERCENTAGE -> new PercentageDiscountStrategy(promotion.scope(), promotion.getMinSeats(), order);
            case FIXED_AMOUNT -> new FixedAmountDiscountStrategy(promotion.scope(), promotion.getMinSeats(), order);
            case COMBO -> new ComboDiscountStrategy(
                    promotion.getMinSeats(),
                    promotion.getRequiredFoodItem() != null ? promotion.getRequiredFoodItem().getId() : null,
                    promotion.getRequiredFoodItem() != null ? promotion.getRequiredFoodItem().getName() : null,
                    promotion.parkingRequired(),
                    order);
        };
    }
}
