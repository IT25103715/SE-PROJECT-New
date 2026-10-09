package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.PaymentOption;
import com.cinemahub.model.PaymentOptionStatus;
import com.cinemahub.model.PaymentOptionType;
import com.cinemahub.repository.PaymentOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * System-level configuration of which payment options the cinema accepts
 * (PAYMENT_MANAGER's entire scope). Not to be confused with
 * {@link com.cinemahub.service.PaymentService}, which handles individual
 * customer transactions (Function 4).
 */
@Service
public class PaymentOptionService {

    private final PaymentOptionRepository paymentOptionRepository;

    public PaymentOptionService(PaymentOptionRepository paymentOptionRepository) {
        this.paymentOptionRepository = paymentOptionRepository;
    }

    public List<PaymentOption> findAll() {
        return paymentOptionRepository.findAllByOrderByDateAddedDesc();
    }

    public List<PaymentOption> findActive() {
        return paymentOptionRepository.findByStatus(PaymentOptionStatus.ACTIVE);
    }

    /**
     * The bank account shown to customers on the Bank Transfer receipt-upload screen: the ACTIVE
     * Bank Transfer option with complete details (newest first). Empty if none is set up yet.
     */
    public Optional<PaymentOption> findActiveBankTransferDetails() {
        return findActive().stream()
                .filter(option -> option.getType() == PaymentOptionType.BANK_TRANSFER)
                .sorted(Comparator.comparing((PaymentOption option) -> !option.hasBankDetails())
                        .thenComparing(PaymentOption::getDateAdded, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    public PaymentOption findById(Long id) {
        return paymentOptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment option not found: " + id));
    }

    /** Create. */
    @Transactional
    public PaymentOption create(PaymentOption paymentOption) {
        if (paymentOptionRepository.existsByNameIgnoreCase(paymentOption.getName())) {
            throw new IllegalArgumentException("A payment option named '" + paymentOption.getName() + "' already exists");
        }
        paymentOption.setId(null);
        paymentOption.setStatus(PaymentOptionStatus.ACTIVE);
        keepOnlyFieldsForType(paymentOption);
        return paymentOptionRepository.save(paymentOption);
    }

    /** Update - only the editable fields; status changes through deactivate/reactivate instead. */
    @Transactional
    public PaymentOption update(Long id, PaymentOption updated) {
        PaymentOption paymentOption = findById(id);

        if (!paymentOption.getName().equalsIgnoreCase(updated.getName())
                && paymentOptionRepository.existsByNameIgnoreCase(updated.getName())) {
            throw new IllegalArgumentException("A payment option named '" + updated.getName() + "' already exists");
        }

        paymentOption.setName(updated.getName());
        paymentOption.setType(updated.getType());
        paymentOption.setProviderDetails(updated.getProviderDetails());
        paymentOption.setMerchantId(updated.getMerchantId());
        paymentOption.setMerchantDisplayName(updated.getMerchantDisplayName());
        paymentOption.setClientId(updated.getClientId());
        paymentOption.setBankName(updated.getBankName());
        paymentOption.setBranchName(updated.getBranchName());
        paymentOption.setAccountHolderName(updated.getAccountHolderName());
        paymentOption.setAccountNumber(updated.getAccountNumber());
        paymentOption.setBranchCode(updated.getBranchCode());
        paymentOption.setSwiftCode(updated.getSwiftCode());
        paymentOption.setPaymentReferenceInstructions(updated.getPaymentReferenceInstructions());
        keepOnlyFieldsForType(paymentOption);
        return paymentOptionRepository.save(paymentOption);
    }

    /**
     * The module's "Delete" action: flips the payment option to DISABLED
     * instead of removing it, so it stops appearing as a selectable option
     * on the customer checkout screen but its history isn't lost.
     * Soft-delete only, per team's consistent pattern across all modules.
     */
    @Transactional
    public void deactivate(Long id) {
        PaymentOption paymentOption = findById(id);
        paymentOption.setStatus(PaymentOptionStatus.DISABLED);
        paymentOptionRepository.save(paymentOption);
    }

    /**
     * Trims every provider field and clears the ones that don't belong to the selected type, so
     * switching an option from (say) Bank Transfer to Apple Pay never leaves old bank details behind.
     */
    private void keepOnlyFieldsForType(PaymentOption option) {
        PaymentOptionType type = option.getType();
        option.setProviderDetails(type != null && type.isLegacy() ? trim(option.getProviderDetails()) : null);
        boolean applePay = type == PaymentOptionType.APPLE_PAY;
        option.setMerchantId(applePay ? trim(option.getMerchantId()) : null);
        option.setMerchantDisplayName(applePay ? trim(option.getMerchantDisplayName()) : null);
        option.setClientId(type == PaymentOptionType.PAYPAL ? trim(option.getClientId()) : null);
        boolean bank = type == PaymentOptionType.BANK_TRANSFER;
        option.setBankName(bank ? trim(option.getBankName()) : null);
        option.setBranchName(bank ? trim(option.getBranchName()) : null);
        option.setAccountHolderName(bank ? trim(option.getAccountHolderName()) : null);
        option.setAccountNumber(bank ? trim(option.getAccountNumber()) : null);
        option.setBranchCode(bank ? trim(option.getBranchCode()) : null);
        String swift = bank ? trim(option.getSwiftCode()) : null;
        option.setSwiftCode(swift == null ? null : swift.toUpperCase());
        option.setPaymentReferenceInstructions(bank ? trim(option.getPaymentReferenceInstructions()) : null);
    }

    /** Trimmed value, or null for blank input (so optional fields like SWIFT are stored as null). */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public void reactivate(Long id) {
        PaymentOption paymentOption = findById(id);
        paymentOption.setStatus(PaymentOptionStatus.ACTIVE);
        paymentOptionRepository.save(paymentOption);
    }
}
