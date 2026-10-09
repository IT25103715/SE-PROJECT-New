package com.cinemahub.model;

/**
 * Kind of payment option a Payment Manager configures (see PaymentOption). The type decides
 * which provider-specific fields the Create/Edit form asks for:
 * <ul>
 *   <li>{@link #APPLE_PAY} - Merchant ID + Merchant Display Name</li>
 *   <li>{@link #PAYPAL} - public Client ID only (the Client Secret lives only in
 *       config/application.properties, never in the database)</li>
 *   <li>{@link #BANK_TRANSFER} - full bank account details shown to the customer</li>
 * </ul>
 * {@link #DIGITAL_WALLET} and {@link #CARD_NETWORK} are the original generic types, kept so
 * options created before the type-specific fields existed (e.g. Google Pay, Mastercard) still
 * load and can be edited - they keep using the free-text "Other details" field.
 */
public enum PaymentOptionType {
    APPLE_PAY("Apple Pay"),
    PAYPAL("PayPal"),
    BANK_TRANSFER("Bank Transfer"),
    DIGITAL_WALLET("Other digital wallet"),
    CARD_NETWORK("Card network");

    private final String displayLabel;

    PaymentOptionType(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    /** The original generic types - their only details field is the legacy free-text one. */
    public boolean isLegacy() {
        return this == DIGITAL_WALLET || this == CARD_NETWORK;
    }
}
