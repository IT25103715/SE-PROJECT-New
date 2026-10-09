package com.cinemahub.model;

/**
 * Lifecycle state of a {@link PaymentOption}. This is the field the CRUD
 * "Delete" action flips (see {@code PaymentOptionService#deactivate}) -
 * payment option rows are never hard-deleted, matching every other module
 * in this app.
 */
public enum PaymentOptionStatus {
    ACTIVE,
    DISABLED
}
