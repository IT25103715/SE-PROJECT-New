package com.cinemahub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * A system-level payment option the cinema accepts (e.g. "Apple Pay", "PayPal",
 * "Visa Bank Transfer") - managed by PAYMENT_MANAGER via PaymentManagerController. This is
 * deliberately separate from the {@link PaymentMethod} enum on {@link Payment}: that enum records
 * which method a customer used for one specific transaction (Function 4), while this entity
 * configures which options the system accepts at all.
 *
 * <p>Provider details are type-specific - only the fields for this option's {@link #type} are
 * filled in, the rest stay null (see PaymentManagerController#validateTypeFields):</p>
 * <ul>
 *   <li>APPLE_PAY - {@link #merchantId}, {@link #merchantDisplayName}</li>
 *   <li>PAYPAL - {@link #clientId} (the PUBLIC Client ID only). There is deliberately no field for
 *       PayPal's Client Secret: it lives only in the git-ignored config/application.properties and
 *       is never stored in a database table any role can browse.</li>
 *   <li>BANK_TRANSFER - bank, branch, account holder, account number, branch code, optional SWIFT
 *       code and the payment reference instructions shown to the customer on the receipt-upload page</li>
 *   <li>DIGITAL_WALLET / CARD_NETWORK (original generic types) - the legacy free-text
 *       {@link #providerDetails}, kept so options created before these fields existed still load.</li>
 * </ul>
 */
@Entity
@Table(name = "payment_options")
public class PaymentOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @NotNull(message = "Please choose a type")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentOptionType type;

    /** Legacy free-text details - only used by the generic DIGITAL_WALLET / CARD_NETWORK types now. */
    @Size(max = 500)
    @Column(length = 500)
    private String providerDetails;

    // ---------- Apple Pay ----------
    @Size(max = 100)
    @Column(length = 100)
    private String merchantId;

    @Size(max = 100)
    @Column(length = 100)
    private String merchantDisplayName;

    // ---------- PayPal (public Client ID only - never the secret) ----------
    @Size(max = 200)
    @Column(length = 200)
    private String clientId;

    // ---------- Bank Transfer ----------
    @Size(max = 100)
    @Column(length = 100)
    private String bankName;

    @Size(max = 100)
    @Column(length = 100)
    private String branchName;

    @Size(max = 150)
    @Column(length = 150)
    private String accountHolderName;

    @Size(max = 34)
    @Column(length = 34)
    private String accountNumber;

    @Size(max = 20)
    @Column(length = 20)
    private String branchCode;

    /** Optional - only needed for international transfers. */
    @Size(max = 11)
    @Column(length = 11)
    private String swiftCode;

    @Size(max = 500)
    @Column(length = 500)
    private String paymentReferenceInstructions;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOptionStatus status = PaymentOptionStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateAdded = LocalDateTime.now();

    /** "****1234" - only the last 4 digits of the account number, for the dashboard list. */
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.isBlank()) {
            return null;
        }
        String digits = accountNumber.replaceAll("\\s+", "");
        return "****" + (digits.length() <= 4 ? digits : digits.substring(digits.length() - 4));
    }

    /** True when every required Bank Transfer field is filled in (SWIFT code is optional). */
    public boolean hasBankDetails() {
        return notBlank(bankName) && notBlank(branchName) && notBlank(accountHolderName)
                && notBlank(accountNumber) && notBlank(branchCode) && notBlank(paymentReferenceInstructions);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

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

    public PaymentOptionType getType() {
        return type;
    }

    public void setType(PaymentOptionType type) {
        this.type = type;
    }

    public String getProviderDetails() {
        return providerDetails;
    }

    public void setProviderDetails(String providerDetails) {
        this.providerDetails = providerDetails;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getMerchantDisplayName() {
        return merchantDisplayName;
    }

    public void setMerchantDisplayName(String merchantDisplayName) {
        this.merchantDisplayName = merchantDisplayName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public void setBranchCode(String branchCode) {
        this.branchCode = branchCode;
    }

    public String getSwiftCode() {
        return swiftCode;
    }

    public void setSwiftCode(String swiftCode) {
        this.swiftCode = swiftCode;
    }

    public String getPaymentReferenceInstructions() {
        return paymentReferenceInstructions;
    }

    public void setPaymentReferenceInstructions(String paymentReferenceInstructions) {
        this.paymentReferenceInstructions = paymentReferenceInstructions;
    }

    public PaymentOptionStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentOptionStatus status) {
        this.status = status;
    }

    public LocalDateTime getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(LocalDateTime dateAdded) {
        this.dateAdded = dateAdded;
    }
}
