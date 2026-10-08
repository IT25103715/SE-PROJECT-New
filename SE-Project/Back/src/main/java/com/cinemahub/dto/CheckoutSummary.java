package com.cinemahub.dto;

import com.cinemahub.model.Seat;
import com.cinemahub.model.Showtime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Read-only order summary shown on the payment-details screen, between seat
 * selection and the actual {@code POST /bookings} that creates the booking
 * and processes payment. Nothing here is persisted - it mirrors the same
 * price/promo math {@code BookingService#createBooking} runs for real, so
 * what the customer sees on this screen matches what they're about to pay.
 */
public class CheckoutSummary {

    private final Showtime showtime;
    private final List<Seat> seats;
    private final BigDecimal originalPrice;
    private final BigDecimal discountAmount;
    private final String promoCode;
    private final BigDecimal totalPrice;
    private final String promoWarning;
    // Add-ons (food & parking). originalPrice = seatsTotal + foodTotal + parkingFee.
    private final BigDecimal seatsTotal;
    private final List<FoodLine> foodLines;
    private final BigDecimal foodTotal;
    private final String parkingLabel;
    private final BigDecimal parkingFee;

    public CheckoutSummary(Showtime showtime, List<Seat> seats, BigDecimal originalPrice,
                            BigDecimal discountAmount, String promoCode, BigDecimal totalPrice,
                            String promoWarning) {
        this(showtime, seats, originalPrice, discountAmount, promoCode, totalPrice, promoWarning,
                originalPrice, List.of(), BigDecimal.ZERO, null, BigDecimal.ZERO);
    }

    public CheckoutSummary(Showtime showtime, List<Seat> seats, BigDecimal originalPrice,
                            BigDecimal discountAmount, String promoCode, BigDecimal totalPrice,
                            String promoWarning, BigDecimal seatsTotal, List<FoodLine> foodLines,
                            BigDecimal foodTotal, String parkingLabel, BigDecimal parkingFee) {
        this.seatsTotal = seatsTotal;
        this.foodLines = foodLines == null ? List.of() : List.copyOf(foodLines);
        this.foodTotal = Objects.requireNonNullElse(foodTotal, BigDecimal.ZERO);
        this.parkingLabel = parkingLabel;
        this.parkingFee = Objects.requireNonNullElse(parkingFee, BigDecimal.ZERO);
        this.showtime = showtime;
        this.seats = seats;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.promoCode = promoCode;
        this.totalPrice = totalPrice;
        this.promoWarning = promoWarning;
    }

    public Showtime getShowtime() {
        return showtime;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public String getPromoWarning() {
        return promoWarning;
    }

    public BigDecimal getSeatsTotal() {
        return seatsTotal;
    }

    public List<FoodLine> getFoodLines() {
        return foodLines;
    }

    public BigDecimal getFoodTotal() {
        return foodTotal;
    }

    /** Null when parking wasn't added. */
    public String getParkingLabel() {
        return parkingLabel;
    }

    public BigDecimal getParkingFee() {
        return parkingFee;
    }

    public boolean isParkingAdded() {
        return parkingLabel != null;
    }
}
