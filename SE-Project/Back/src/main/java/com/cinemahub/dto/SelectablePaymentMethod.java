package com.cinemahub.dto;

/**
 * One entry in the seat-map's "Payment method" dropdown - pairs a
 * customer-facing label (normally a Payment Manager {@code PaymentOption}'s
 * name, e.g. "Apple Pay") with the actual {@code PaymentMethod} enum value
 * the booking form submits (see BookingController#resolvePaymentMethodOptions).
 *
 * {@code optionType} carries the underlying {@code PaymentOptionType} (e.g. BANK_TRANSFER)
 * alongside the collapsed 3-value {@code PaymentMethod}, null for the raw-enum fallback used
 * when a Payment Manager has disabled every option. It exists only so the booking form can
 * tell "the customer picked a Bank Transfer option" apart from "the customer picked the raw
 * Cash enum value" - both submit the same PaymentMethod (CASH) underneath, but only the
 * former should trigger the receipt-upload flow (see seatmap.html's hidden paymentOptionType
 * field and BookingController#create).
 */
public class SelectablePaymentMethod {

    private final String label;
    private final String value;
    private final String optionType;

    public SelectablePaymentMethod(String label, String value, String optionType) {
        this.label = label;
        this.value = value;
        this.optionType = optionType;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getOptionType() {
        return optionType;
    }
}
