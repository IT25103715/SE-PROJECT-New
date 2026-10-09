package com.cinemahub.service;

import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.Payment;
import com.cinemahub.model.PaymentMethod;
import com.cinemahub.model.PaymentStatus;
import com.cinemahub.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Function 4: "Payment and Transaction Management" - a simulated payment
 * flow (no real gateway, see the proposal's System Limitations &
 * Constraints) built around four sub-functions:
 * <ul>
 *   <li>Create - {@link #processPayment} auto-generates a Payment the
 *       moment a booking is paid for.</li>
 *   <li>Read - {@link #findByUser} (customer history) and
 *       {@link #findActive}/{@link #getRevenueByBranch} (manager/admin
 *       transaction logs and revenue totals).</li>
 *   <li>Update - {@link #markRefunded} and {@link #updateStatus} for
 *       refund/status changes.</li>
 *   <li>Delete - {@link #archive}, a soft-delete via the "archived" flag,
 *       since financial records shouldn't disappear.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReceiptStorageService receiptStorageService;

    public PaymentService(PaymentRepository paymentRepository, ReceiptStorageService receiptStorageService) {
        this.paymentRepository = paymentRepository;
        this.receiptStorageService = receiptStorageService;
    }

    /**
     * Create & Read: Payment Processing & Initialization. Called right after
     * a booking is saved, so every booking ends up with exactly one Payment
     * row. Since there's no real gateway, the "processing" is simulated and
     * always succeeds unless the amount is invalid.
     */
    @Transactional
    public Payment processPayment(Booking booking, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setMethod(method);
        payment.setTransactionRef(generateTransactionRef());
        payment.setStatus(isValidAmount(booking.getTotalPrice()) ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
        return paymentRepository.save(payment);
    }

    /**
     * Bank Transfer only: called instead of {@link #processPayment} - never auto-completes.
     * Creates the Payment row as {@code AWAITING_RECEIPT} so the seat map/getBookedSeatIds
     * still treats the booking as held while it waits on {@link #attachReceipt} and then an
     * admin's {@link #approveAwaitingReceipt}/{@link #rejectAwaitingReceipt} decision.
     */
    @Transactional
    public Payment initiateBankTransferPayment(Booking booking, PaymentMethod method) {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalPrice());
        payment.setMethod(method);
        payment.setTransactionRef(generateTransactionRef());
        payment.setStatus(PaymentStatus.AWAITING_RECEIPT);
        return paymentRepository.save(payment);
    }

    /**
     * Bank Transfer only: attaches (or replaces) the customer's uploaded receipt file on a
     * payment that's still waiting for one. Storage itself is delegated to
     * {@link ReceiptStorageService}, which also does the file-type/size validation.
     */
    @Transactional
    public Payment attachReceipt(Long bookingId, MultipartFile file) throws IOException {
        Payment payment = findByBookingId(bookingId);
        if (payment.getStatus() != PaymentStatus.AWAITING_RECEIPT) {
            throw new IllegalStateException("This booking isn't awaiting a receipt");
        }
        ReceiptStorageService.StoredReceipt stored = receiptStorageService.store(file, payment.getId());
        payment.setReceiptFilePath(stored.path());
        payment.setReceiptOriginalFilename(stored.originalFilename());
        payment.setReceiptContentType(stored.contentType());
        payment.setReceiptUploadedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }

    /** Admin's receipt-review queue at /admin/receipts - every payment still waiting on a decision. */
    public List<Payment> findAwaitingReceipt() {
        return paymentRepository.findByStatusOrderByTransactionDateAsc(PaymentStatus.AWAITING_RECEIPT);
    }

    /**
     * Admin approves an uploaded receipt. Payment-only concern (status flip); the booking
     * confirmation + ticket issuance this triggers lives in
     * {@link BookingService#confirmBankTransferBooking} instead, to avoid this service having
     * to depend back on BookingService (which already depends on this one).
     */
    @Transactional
    public Payment approveAwaitingReceipt(Long bookingId) {
        Payment payment = findByBookingId(bookingId);
        if (payment.getStatus() != PaymentStatus.AWAITING_RECEIPT) {
            throw new IllegalStateException("This payment isn't awaiting a receipt review");
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        return paymentRepository.save(payment);
    }

    /** Admin rejects an uploaded receipt - see {@link BookingService#rejectBankTransferBooking} for the booking-side effects. */
    @Transactional
    public Payment rejectAwaitingReceipt(Long bookingId) {
        Payment payment = findByBookingId(bookingId);
        if (payment.getStatus() != PaymentStatus.AWAITING_RECEIPT) {
            throw new IllegalStateException("This payment isn't awaiting a receipt review");
        }
        payment.setStatus(PaymentStatus.RECEIPT_REJECTED);
        return paymentRepository.save(payment);
    }

    /**
     * Real-gateway payments only (PayPal Sandbox, see PayPalCheckoutController): after the normal
     * {@link #processPayment} row is created, replace its generated TXN-... reference with the
     * gateway's own ID (e.g. "PAYPAL-&lt;capture id&gt;") so the transaction log can be matched
     * against the PayPal Sandbox dashboard.
     */
    @Transactional
    public Payment recordGatewayReference(Long bookingId, String reference) {
        Payment payment = findByBookingId(bookingId);
        payment.setTransactionRef(reference);
        return paymentRepository.save(payment);
    }

    private boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private String generateTransactionRef() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Read: a customer's own payment history. */
    public List<Payment> findByUser(Long userId) {
        return paymentRepository.findByBooking_User_IdOrderByTransactionDateDesc(userId);
    }

    /** Read: manager/admin transaction log - archived rows hidden by default. */
    public List<Payment> findActive() {
        return paymentRepository.findByArchivedFalseOrderByTransactionDateDesc();
    }

    public List<Payment> findAll() {
        return paymentRepository.findAllByOrderByTransactionDateDesc();
    }

    public Payment findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    /** The booking's Payment row, if it has one yet (an unpaid Apple Pay / PayPal seat hold has none). */
    public java.util.Optional<Payment> findOptionalByBookingId(Long bookingId) {
        return paymentRepository.findByBooking_Id(bookingId);
    }

    /**
     * A Bank Transfer booking ran out of time (24 hours, no receipt uploaded): the payment that
     * was only ever AWAITING_RECEIPT is closed as FAILED so it leaves the admin's receipt queue
     * and can't be uploaded to any more. Nothing was ever paid, so this is NOT a refund.
     */
    @Transactional
    public void closeExpiredBankTransfer(Long bookingId) {
        paymentRepository.findByBooking_Id(bookingId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.AWAITING_RECEIPT) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        });
    }

    public Payment findByBookingId(Long bookingId) {
        return paymentRepository.findByBooking_Id(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for booking: " + bookingId));
    }

    /**
     * Read: revenue totals grouped "branch-wise". This system only models a
     * single cinema location broken into {@link com.cinemahub.model.CinemaHall}s
     * rather than a separate multi-branch entity, so each hall stands in as
     * a "branch" for reporting purposes. Only COMPLETED, non-archived
     * payments count towards revenue.
     */
    public Map<String, BigDecimal> getRevenueByBranch() {
        Map<String, BigDecimal> revenue = new LinkedHashMap<>();
        for (Object[] row : paymentRepository.sumRevenueByBranch()) {
            revenue.put((String) row[0], (BigDecimal) row[1]);
        }
        return revenue;
    }

    public BigDecimal getTotalRevenue() {
        return getRevenueByBranch().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Update: Refund & Transaction Status Update. Called automatically when
     * a CONFIRMED booking with a completed payment is cancelled.
     */
    @Transactional
    public void markRefunded(Long bookingId) {
        paymentRepository.findByBooking_Id(bookingId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                payment.setStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
            }
        });
    }

    /** Update: manual status override (e.g. a manager correcting a stuck PENDING/FAILED row). */
    @Transactional
    public Payment updateStatus(Long id, PaymentStatus status) {
        Payment payment = findById(id);
        payment.setStatus(status);
        return paymentRepository.save(payment);
    }

    /** Delete (soft): Transaction Record Archiving. */
    @Transactional
    public void archive(Long id) {
        Payment payment = findById(id);
        payment.setArchived(true);
        paymentRepository.save(payment);
    }
}
