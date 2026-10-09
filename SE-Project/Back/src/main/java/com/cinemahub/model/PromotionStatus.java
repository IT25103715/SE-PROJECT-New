package com.cinemahub.model;

/**
 * Lifecycle state of a {@link Promotion}. This is the field the CRUD
 * "Delete" action flips (see {@code PromotionService#deactivate}) - coupon
 * rows are never hard-deleted, so usage history stays intact for reporting.
 */
public enum PromotionStatus {
    ACTIVE,
    EXPIRED,
    DISABLED
}
