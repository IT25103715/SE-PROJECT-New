package com.cinemahub.model;

/**
 * The five account roles supported by the system. Spring Security expects
 * role names to be prefixed with "ROLE_" when checking authorities, which is
 * handled in {@link com.cinemahub.model.User#getAuthorities()}.
 */
public enum Role {
    CUSTOMER,
    CINEMA_STAFF,
    MANAGER,
    CUSTOMER_RELATIONS_EXECUTIVE,
    SYSTEM_ADMIN,
    PROMOTION_MANAGER,
    PAYMENT_MANAGER
}
