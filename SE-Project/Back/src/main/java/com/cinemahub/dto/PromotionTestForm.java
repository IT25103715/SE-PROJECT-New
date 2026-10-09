package com.cinemahub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Backs the small "try a coupon" calculator on the promotion manager screen -
 * lets a Promotion Manager type in a code and an amount to see the Strategy
 * pattern (see {@link com.cinemahub.service.discount.DiscountStrategyFactory})
 * work without needing a real booking.
 */
public class PromotionTestForm {

    @NotBlank(message = "Enter a coupon code")
    private String code;

    @NotNull(message = "Enter an amount")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
