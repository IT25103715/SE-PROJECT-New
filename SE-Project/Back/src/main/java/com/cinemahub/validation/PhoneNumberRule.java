package com.cinemahub.validation;

import com.cinemahub.dto.PhoneNumbers;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * The one "at least one phone number" rule, used by every form that edits a user's phone
 * numbers: registration, the customer's account edit page and the admin user form.
 * Neither field is required on its own; the form is only rejected when BOTH are blank.
 * <p>
 * As a Spring {@link Validator} it runs alongside the normal @Valid annotations (attached by
 * {@link PhoneNumbersBinderAdvice}), so the error shows next to the phone fields. UserService also
 * calls {@link #requireAtLeastOne} before saving, as a second line of defence.
 * <p>
 * It is only checked when one of those forms is submitted - existing accounts with no phone
 * numbers can keep logging in and using the site.
 */
public class PhoneNumberRule implements Validator {

    public static final String MESSAGE = "Please provide at least one phone number.";

    public static boolean hasAtLeastOne(String phoneNumber, String phoneNumberAlt) {
        return !isBlank(phoneNumber) || !isBlank(phoneNumberAlt);
    }

    /** Service-level check: throws IllegalArgumentException with {@link #MESSAGE} if both are blank. */
    public static void requireAtLeastOne(PhoneNumbers form) {
        if (!hasAtLeastOne(form.getPhoneNumber(), form.getPhoneNumberAlt())) {
            throw new IllegalArgumentException(MESSAGE);
        }
    }

    /** Trims a submitted number; blank becomes null so the database never stores "". */
    public static String clean(String phone) {
        return isBlank(phone) ? null : phone.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return PhoneNumbers.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        PhoneNumbers form = (PhoneNumbers) target;
        if (!hasAtLeastOne(form.getPhoneNumber(), form.getPhoneNumberAlt())) {
            // Shown under the phone fields (templates read the 'phoneNumber' field error).
            errors.rejectValue("phoneNumber", "phone.atLeastOne", MESSAGE);
        }
    }
}
