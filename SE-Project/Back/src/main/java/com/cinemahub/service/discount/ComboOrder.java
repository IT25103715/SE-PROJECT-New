package com.cinemahub.service.discount;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What is in the booking being priced - the facts a promotion's conditions are checked against:
 * a COMBO promotion's seat / food / parking conditions (ComboDiscountStrategy), and the minimum
 * seats + food-only scope any Percentage / Fixed promotion can have (ScopedDiscountStrategy).
 *
 * @param seatCount       number of seats (tickets) in the booking
 * @param foodQuantities  food item id -> quantity ordered (items inside a food combo pack count too)
 * @param parking         whether car parking was added
 * @param foodTotal       the food subtotal (food items + food combos), used by FOOD_ONLY promotions
 */
public record ComboOrder(int seatCount, Map<Long, Integer> foodQuantities, boolean parking, BigDecimal foodTotal) {

    public ComboOrder {
        foodQuantities = foodQuantities == null ? Map.of() : Map.copyOf(foodQuantities);
        foodTotal = foodTotal == null ? BigDecimal.ZERO : foodTotal;
    }

    /** Without a food subtotal (kept for existing callers - a food-only discount then has nothing to apply to). */
    public ComboOrder(int seatCount, Map<Long, Integer> foodQuantities, boolean parking) {
        this(seatCount, foodQuantities, parking, BigDecimal.ZERO);
    }

    public boolean contains(Long foodItemId) {
        return foodItemId != null && foodQuantities.getOrDefault(foodItemId, 0) > 0;
    }
}
