package com.cinemahub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Core account entity used for authentication. Every person who signs in
 * (customer, staff, manager, CRE, admin) is a row in this table; the
 * {@link Role} field is what Spring Security uses to decide what they can
 * access (see SecurityConfig).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Column(nullable = false, unique = true)
    private String email;

    // Stored as a BCrypt hash - never the raw password. See SecurityConfig#passwordEncoder().
    @NotBlank
    @Size(min = 60, max = 100, message = "Password must be hashed before saving")
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Basic contact details, folded in directly from the old, now-removed
    // Profile entity (Function 4 was repurposed to Payment & Transaction
    // Management - see PaymentController/PaymentService). Optional, so
    // accounts created before this change simply have them as null.
    // Phone numbers: a customer gives at least one of the two when registering or editing their
    // account (PhoneNumberRule) - both nullable here so older accounts with neither still load.
    // The primary number keeps the original "phone" column, so numbers saved before still show.
    @Column(name = "phone")
    private String phoneNumber;

    @Column(name = "phone_number_alt")
    private String phoneNumberAlt;

    private String address;

    // Membership program: set automatically once a customer has MembershipService.BOOKINGS_FOR_MEMBERSHIP
    // confirmed bookings (see MembershipService#recordConfirmedBooking). Members can use
    // "Members Only" promotions. The column default keeps existing accounts as non-members.
    @Column(name = "is_member", nullable = false, columnDefinition = "boolean default false")
    private boolean member = false;

    /** When the customer became a member (null for non-members). */
    private LocalDateTime memberSince;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneNumberAlt() {
        return phoneNumberAlt;
    }

    public void setPhoneNumberAlt(String phoneNumberAlt) {
        this.phoneNumberAlt = phoneNumberAlt;
    }

    /**
     * The number to contact this user on (e.g. for an SMS): the primary number if there is one,
     * otherwise the alternate - so a customer who only filled in the second field is still reachable.
     * Null if the account has no phone number at all.
     */
    public String getContactPhone() {
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            return phoneNumber;
        }
        return (phoneNumberAlt != null && !phoneNumberAlt.isBlank()) ? phoneNumberAlt : null;
    }

    /** Older name for the primary number, kept so existing code/templates using "phone" still work. */
    public String getPhone() {
        return phoneNumber;
    }

    public void setPhone(String phone) {
        this.phoneNumber = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isMember() {
        return member;
    }

    public void setMember(boolean member) {
        this.member = member;
    }

    public LocalDateTime getMemberSince() {
        return memberSince;
    }

    public void setMemberSince(LocalDateTime memberSince) {
        this.memberSince = memberSince;
    }
}
