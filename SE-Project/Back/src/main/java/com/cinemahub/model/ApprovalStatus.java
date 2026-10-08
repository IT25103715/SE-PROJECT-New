package com.cinemahub.model;

/**
 * Whether a Promotion/Movie/Showtime submitted by PROMOTION_MANAGER or
 * MANAGER has been reviewed by SYSTEM_ADMIN yet. This is separate from each
 * entity's own operational status (e.g. Promotion's ACTIVE/DISABLED) -
 * PENDING/REJECTED always hides an item from customers regardless of that
 * other status; only once APPROVED does the normal status field govern
 * visibility. SYSTEM_ADMIN's own submissions skip this queue entirely and
 * are saved as APPROVED immediately.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
