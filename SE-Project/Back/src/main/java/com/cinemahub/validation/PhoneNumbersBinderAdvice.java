package com.cinemahub.validation;

import com.cinemahub.dto.PhoneNumbers;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Attaches {@link PhoneNumberRule} to every submitted form that implements {@link PhoneNumbers}
 * (registration, account edit, admin user form), so wherever such a form is checked with @Valid
 * the "at least one phone number" error lands in the same BindingResult as the other field errors.
 * Forms without phone fields are untouched.
 */
@ControllerAdvice
public class PhoneNumbersBinderAdvice {

    private static final PhoneNumberRule RULE = new PhoneNumberRule();

    @InitBinder
    public void addPhoneNumberRule(WebDataBinder binder) {
        // Spring 6.1+ may create the binder before the form object exists, so check the declared
        // target type as well as the target itself.
        Class<?> type = binder.getTarget() != null ? binder.getTarget().getClass()
                : (binder.getTargetType() != null ? binder.getTargetType().resolve() : null);
        if (type != null && PhoneNumbers.class.isAssignableFrom(type)) {
            binder.addValidators(RULE);
        }
    }
}
