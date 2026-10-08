package com.cinemahub.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plain data holder used only by the registration form - kept separate from
 * the {@link com.cinemahub.model.User} entity so the raw (unhashed) password
 * never accidentally gets persisted as-is.
 */
public class RegistrationDto implements PhoneNumbers {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    /** Primary phone number - this or {@link #phoneNumberAlt} must be filled in (PhoneNumberRule). */
    private String phoneNumber;

    /** Alternate phone number. */
    private String phoneNumberAlt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    @Override
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String getPhoneNumberAlt() {
        return phoneNumberAlt;
    }

    public void setPhoneNumberAlt(String phoneNumberAlt) {
        this.phoneNumberAlt = phoneNumberAlt;
    }
}
