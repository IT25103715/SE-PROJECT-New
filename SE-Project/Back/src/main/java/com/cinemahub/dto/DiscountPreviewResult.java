package com.cinemahub.dto;

import java.math.BigDecimal;

/** Plain result holder returned by {@code PromotionService#previewDiscount}, shown on the test-coupon page. */
public class DiscountPreviewResult {

    private final String code;
    private final String discountType;
    private final BigDecimal originalAmount;
    private final BigDecimal discountAmount;
    private final BigDecimal finalAmount;

    public DiscountPreviewResult(String code, String discountType, BigDecimal originalAmount,
                                  BigDecimal discountAmount, BigDecimal finalAmount) {
        this.code = code;
        this.discountType = discountType;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
    }

    public String getCode() {
        return code;
    }

    public String getDiscountType() {
        return discountType;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }
}
