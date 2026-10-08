package com.cinemahub.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@EntityListeners(BookingStatusListener.class)   // membership program: reacts when a booking becomes CONFIRMED
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // EAGER for the same reason as Showtime.movie/cinemaHall above - booking
    // confirmation/history/ticket-scan views all need showtime (and,
    // transitively, movie/hall) details after the session has closed.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    /** Status as last read from / written to the database - lets BookingStatusListener spot the change to CONFIRMED. Not stored. */
    @Transient
    private BookingStatus statusWhenLoaded;

    public BookingStatus getStatusWhenLoaded() {
        return statusWhenLoaded;
    }

    /** Called by BookingStatusListener after a load / insert / update. */
    public void rememberStatus() {
        this.statusWhenLoaded = status;
    }

    @Column(nullable = false)
    private BigDecimal totalPrice;

    // Set only when a coupon (Function 6) was applied at checkout - null/zero otherwise.
    private String promoCode;

    private BigDecimal discountAmount;

    /**
     * Not persisted - a same-request signal from BookingService to
     * BookingController when a submitted coupon code was invalid/expired/
     * exhausted, so the controller can pass it along to the confirmation
     * page. The booking still goes through at full price; this is only ever
     * read once, right after creation, never reloaded from the database.
     */
    @Transient
    private String promoWarning;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Simple refund flag used once a CONFIRMED booking is cancelled (Function 3 requirement)
    private boolean refunded = false;

    // ---------- Optional add-ons chosen at checkout (Food & Parking) ----------
    // Nullable so bookings made before add-ons existed still load (null = none / Rs. 0).

    /** Sum of this booking's food lines (see BookingFoodItem). */
    @Column(precision = 10, scale = 2)
    private BigDecimal foodTotal;

    /** True when car parking was added to this booking. */
    private Boolean parkingSelected;

    /** Parking fee charged (copied from the ParkingOption at booking time). */
    @Column(precision = 10, scale = 2)
    private BigDecimal parkingFee;

    /** Parking option name at booking time, e.g. "Standard Parking". */
    @Column(length = 100)
    private String parkingLabel;

    // Payment deadline while the booking is still PENDING - after this the seats are released
    // (status -> EXPIRED) by BookingExpiryScheduler. Set when the PENDING booking is created:
    // 10 minutes for Apple Pay / PayPal (the seat hold made when the customer reaches the pay
    // step), 24 hours for Bank Transfer. Cleared (null) once the booking is CONFIRMED or a Bank
    // Transfer receipt has been uploaded (an admin decides from then on). Null on every booking
    // made before this feature existed, so those are never expired.
    private LocalDateTime expiresAt;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private Ticket ticket;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private Payment payment;

    public BigDecimal getFoodTotal() {
        return foodTotal;
    }

    public void setFoodTotal(BigDecimal foodTotal) {
        this.foodTotal = foodTotal;
    }

    /** Null-safe: bookings from before add-ons existed count as "no parking". */
    public boolean isParkingSelected() {
        return Boolean.TRUE.equals(parkingSelected);
    }

    public void setParkingSelected(Boolean parkingSelected) {
        this.parkingSelected = parkingSelected;
    }

    public BigDecimal getParkingFee() {
        return parkingFee;
    }

    public void setParkingFee(BigDecimal parkingFee) {
        this.parkingFee = parkingFee;
    }

    public String getParkingLabel() {
        return parkingLabel;
    }

    public void setParkingLabel(String parkingLabel) {
        this.parkingLabel = parkingLabel;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** Whole seconds left before {@link #expiresAt} (0 once it has passed, -1 when there is no deadline). */
    public long secondsUntilExpiry() {
        if (expiresAt == null) {
            return -1;
        }
        return Math.max(0, java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds());
    }

    /** True when this booking has a payment deadline and it has passed. */
    public boolean isPastExpiry() {
        return expiresAt != null && !expiresAt.isAfter(LocalDateTime.now());
    }

    /** True when food or parking was added (for the history / confirmation pages). */
    public boolean hasAddOns() {
        return isParkingSelected() || (foodTotal != null && foodTotal.signum() > 0);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public void setShowtime(Showtime showtime) {
        this.showtime = showtime;
    }

    public List<BookingSeat> getBookingSeats() {
        return bookingSeats;
    }

    public void setBookingSeats(List<BookingSeat> bookingSeats) {
        this.bookingSeats = bookingSeats;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public String getPromoWarning() {
        return promoWarning;
    }

    public void setPromoWarning(String promoWarning) {
        this.promoWarning = promoWarning;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRefunded() {
        return refunded;
    }

    public void setRefunded(boolean refunded) {
        this.refunded = refunded;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }
}
