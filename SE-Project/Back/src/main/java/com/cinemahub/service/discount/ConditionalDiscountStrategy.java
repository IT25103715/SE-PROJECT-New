package com.cinemahub.service.discount;

import java.util.List;

/**
 * A {@link DiscountStrategy} whose discount only applies when the booking meets some conditions.
 * When {@link #unmetConditions()} is not empty, calculateDiscount returns zero and
 * PromotionService reports the code as not applicable - the booking still goes through at
 * full price, the same "an unusable promo never blocks the booking" rule used everywhere.
 */
public interface ConditionalDiscountStrategy extends DiscountStrategy {

    /** Human-readable list of what the booking is missing, e.g. "at least 2 seats" (empty = all met). */
    List<String> unmetConditions();
}
