package com.cinemahub.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Function 4 (repurposed): "Payment and Transaction Management". One row
 * per {@link Booking} - created automatically the moment a booking is paid
 * for (see PaymentService#processPayment), then updated in place if the
 * booking is later cancelled/refunded. Rows are never hard-deleted: the
 * "Delete" sub-function (Transaction Record Archiving) only ever flips
 * {@link #archived} to true, since financial records shouldn't disappear.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionDate = LocalDateTime.now();

    // Simulated gateway reference, e.g. "TXN-4F2A9C1B" - stands in for whatever
    // reference a real payment gateway would return.
    @Column(nullable = false, unique = true, length = 100)
    private String transactionRef;

    // Soft-delete flag for Transaction Record Archiving - archived rows are
    // hidden from the default transaction log but never physically removed.
    @Column(nullable = false)
    private boolean archived = false;

    // Bank Transfer only (status AWAITING_RECEIPT/RECEIPT_REJECTED): the uploaded
    // receipt file, stored on disk by ReceiptStorageService - see
    // PaymentService#attachReceipt. All null until a receipt is uploaded.
    @Column(length = 500)
    private String receiptFilePath;

    @Column(length = 255)
    private String receiptOriginalFilename;

    @Column(length = 100)
    private String receiptContentType;

    private LocalDateTime receiptUploadedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    /** Convenience accessor - the FK id, without forcing callers to fetch the whole Booking. */
    public Long getBookingId() {
        return booking != null ? booking.getId() : null;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String getReceiptFilePath() {
        return receiptFilePath;
    }

    public void setReceiptFilePath(String receiptFilePath) {
        this.receiptFilePath = receiptFilePath;
    }

    public String getReceiptOriginalFilename() {
        return receiptOriginalFilename;
    }

    public void setReceiptOriginalFilename(String receiptOriginalFilename) {
        this.receiptOriginalFilename = receiptOriginalFilename;
    }

    public String getReceiptContentType() {
        return receiptContentType;
    }

    public void setReceiptContentType(String receiptContentType) {
        this.receiptContentType = receiptContentType;
    }

    public LocalDateTime getReceiptUploadedAt() {
        return receiptUploadedAt;
    }

    public void setReceiptUploadedAt(LocalDateTime receiptUploadedAt) {
        this.receiptUploadedAt = receiptUploadedAt;
    }

    /** True once a receipt file has actually been uploaded (as opposed to merely being expected). */
    public boolean hasReceipt() {
        return receiptFilePath != null;
    }
}
