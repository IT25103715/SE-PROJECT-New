package com.cinemahub.dto;

import java.math.BigDecimal;

/**
 * One priced food line of a checkout summary, e.g. 2 x Large Popcorn @ Rs. 650 = Rs. 1300, or a
 * food combo pack (combo = true; foodItemId is then the FoodCombo id and unitPrice its bundle price).
 */
public record FoodLine(Long foodItemId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                       boolean combo) {

    /** A plain food item line. */
    public FoodLine(Long foodItemId, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this(foodItemId, name, quantity, unitPrice, lineTotal, false);
    }
}
