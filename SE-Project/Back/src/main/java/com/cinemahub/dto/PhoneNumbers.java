package com.cinemahub.dto;

/**
 * A form that carries the two phone number fields (registration, the customer's own account
 * edit, and the admin user form). Implementing this is what makes the shared "at least one phone
 * number" rule apply to a form - see {@link com.cinemahub.validation.PhoneNumberRule}.
 */
public interface PhoneNumbers {

    /** Primary phone number. */
    String getPhoneNumber();

    /** Secondary / alternate phone number. */
    String getPhoneNumberAlt();
}
