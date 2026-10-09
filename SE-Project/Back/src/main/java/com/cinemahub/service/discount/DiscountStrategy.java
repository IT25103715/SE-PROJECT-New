package com.cinemahub.service.discount;

import java.math.BigDecimal;

/**
 * Strategy pattern (Function 6): each way of turning a coupon's stored
 * {@code discountValue} into an actual discount amount gets its own class
 * implementing this one method, instead of an if/else on discount type
 * scattered through the service. {@link DiscountStrategyFactory} picks the
 * right implementation at runtime based on the promotion's
 * {@link com.cinemahub.model.DiscountType}.
 */
public interface DiscountStrategy {

    /**
     * @param originalPrice the price before any discount is applied
     * @param discountValue the coupon's stored value (e.g. 20 for "20%", or 500 for "Rs. 500 off")
     * @return the amount of money to subtract from originalPrice
     */
    BigDecimal calculateDiscount(BigDecimal originalPrice, BigDecimal discountValue);
}
