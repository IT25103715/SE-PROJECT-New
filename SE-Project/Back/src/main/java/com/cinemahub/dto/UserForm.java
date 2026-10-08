package com.cinemahub.dto;

import com.cinemahub.model.Role;
import com.cinemahub.model.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Used by SYSTEM_ADMIN to create or edit any account (e.g. onboarding a new
 * CINEMA_STAFF member). Password is optional here - leaving it blank while
 * editing keeps the existing password.
 */
public class UserForm implements PhoneNumbers {

    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    @NotNull(message = "Status is required")
    private UserStatus status = UserStatus.ACTIVE;

    /** Primary phone number - this or {@link #phoneNumberAlt} must be filled in (PhoneNumberRule). */
    private String phoneNumber;

    /** Alternate phone number. */
    private String phoneNumberAlt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
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
