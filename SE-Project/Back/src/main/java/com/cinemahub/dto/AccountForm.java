package com.cinemahub.dto;

/**
 * Backing object for the customer "my account" self-service page - name plus
 * the basic contact fields that live directly on {@link com.cinemahub.model.User}
 * now that the standalone Profile entity (old Function 4) has been removed.
 */
public class AccountForm implements PhoneNumbers {

    private String name;
    /** Primary phone number - this or {@link #phoneNumberAlt} must be filled in (PhoneNumberRule). */
    private String phoneNumber;

    /** Alternate phone number. */
    private String phoneNumberAlt;
    private String address;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
