package com.cinemahub.controller;

import com.cinemahub.config.PayPalConfig;
import com.cinemahub.dto.BookingAddOns;
import com.cinemahub.dto.CheckoutSummary;
import com.cinemahub.exception.BookingExpiredException;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.BookingStatus;
import com.cinemahub.model.PaymentMethod;
import com.cinemahub.model.User;
import com.cinemahub.service.BookingService;
import com.cinemahub.service.HumanVerificationService;
import com.cinemahub.service.PayPalClient;
import com.cinemahub.service.PayPalClient.CaptureResult;
import com.cinemahub.service.PayPalClient.PayPalException;
import com.cinemahub.service.PaymentService;
import com.cinemahub.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real PayPal Sandbox checkout (Function 4), used only when the customer picks the "PayPal"
 * payment option on the seat map's review step. Apple Pay / Bank Transfer / Card keep using
 * the existing simulated flow in {@link BookingController#create} untouched.
 *
 * <ol>
 *   <li>{@code POST /payments/paypal/create-order} - checks the CAPTCHA and Terms box (same two
 *       checks every other method passes), re-prices the seats server-side (promo included),
 *       and creates a real Sandbox order. Nothing is booked yet.</li>
 *   <li>The buyer logs in and approves on PayPal's own Sandbox checkout window.</li>
 *   <li>{@code POST /payments/paypal/capture-order} - captures the order at PayPal. Only if PayPal
 *       reports the capture COMPLETED for the exact amount does it call the existing
 *       {@link BookingService#createBooking} (which runs PaymentService#processPayment, confirms
 *       the booking and issues the ticket). If our booking step then fails (e.g. the seat was
 *       taken meanwhile) the PayPal capture is refunded, so no one pays without a ticket.</li>
 * </ol>
 * The order details live in the server-side session, so the browser can't change seats or
 * price between approving and capturing.
 */
@RestController
@RequestMapping("/payments/paypal")
public class PayPalCheckoutController {

    private static final Logger log = LoggerFactory.getLogger(PayPalCheckoutController.class);
    private static final String SESSION_KEY = "PAYPAL_PENDING_CHECKOUTS";

    private final PayPalConfig payPalConfig;
    private final PayPalClient payPalClient;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final HumanVerificationService humanVerificationService;

    public PayPalCheckoutController(PayPalConfig payPalConfig, PayPalClient payPalClient,
                                    BookingService bookingService, PaymentService paymentService,
                                    UserService userService, HumanVerificationService humanVerificationService) {
        this.payPalConfig = payPalConfig;
        this.payPalClient = payPalClient;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.humanVerificationService = humanVerificationService;
    }

    /** What was agreed when the order was created - re-used at capture time instead of trusting the browser. */
    record PendingCheckout(Long userId, Long showtimeId, List<Long> seatIds, String promoCode,
                           BigDecimal lkrTotal, BigDecimal paypalAmount, String currency,
                           BookingAddOns addOns, Long holdId) implements Serializable {
    }

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestParam Long showtimeId,
                                                           @RequestParam List<Long> seatIds,
                                                           @RequestParam(required = false) String promoCode,
                                                           @RequestParam(required = false) String humanToken,
                                                           @RequestParam(defaultValue = "false") boolean termsAccepted,
                                                           // Optional add-ons, same form values as BookingController
                                                           @RequestParam(required = false) List<String> food,
                                                           @RequestParam(required = false) List<String> foodCombo,
                                                           @RequestParam(required = false) Boolean parking,
                                                           // The 10-minute seat hold made when the customer reached the pay step.
                                                           @RequestParam(required = false) Long holdId,
                                                           Authentication authentication,
                                                           HttpSession session) {
        if (!payPalConfig.isEnabled()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "PayPal Sandbox is not configured on this server yet.", false);
        }
        // Same two gates as every other payment method (BookingController#create). verify() uses up
        // the CAPTCHA code either way, so the page asks for a new image after this call.
        boolean humanVerified = humanVerificationService.verify(session, humanToken);
        if (!humanVerified) {
            return error(HttpStatus.BAD_REQUEST, "The characters you typed didn't match the image. Please try the new code.", true);
        }
        if (!termsAccepted) {
            return error(HttpStatus.BAD_REQUEST, "Please tick the box to accept the Terms & Conditions.", true);
        }

        User user = userService.findByEmail(authentication.getName());
        // Payment timeout: the seats must still be held for this customer (checked on the server -
        // the page's countdown alone is never trusted).
        if (holdId == null) {
            return error(HttpStatus.BAD_REQUEST, "Your seat reservation is missing - please go back and press Next again.", true);
        }
        try {
            bookingService.requireActiveHold(holdId, user.getId());
        } catch (BookingExpiredException ex) {
            return expired();
        } catch (IllegalStateException ex) {
            return error(HttpStatus.CONFLICT, ex.getMessage(), true);
        }

        CheckoutSummary summary;
        try {
            // The customer's own hold is the only thing allowed to be holding these seats.
            Set<Long> taken = bookingService.getBookedSeatIdsExcludingBooking(showtimeId, holdId);
            if (seatIds.stream().anyMatch(taken::contains)) {
                return error(HttpStatus.CONFLICT, "One of your seats was just booked by someone else - please pick another seat.", true);
            }
            summary = bookingService.buildCheckoutSummary(showtimeId, seatIds, promoCode, BookingAddOns.fromForm(food, foodCombo, parking));
        } catch (ResourceNotFoundException | IllegalStateException | IllegalArgumentException ex) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage(), true);
        }

        BigDecimal lkrTotal = summary.getTotalPrice();
        BigDecimal paypalAmount = payPalConfig.toPayPalAmount(lkrTotal);
        String currency = payPalConfig.getCurrency();
        try {
            String orderId = payPalClient.createOrder(paypalAmount, currency,
                    "SHOWTIME-" + showtimeId + "-" + System.currentTimeMillis(),
                    summary.getShowtime().getMovie().getTitle() + " - " + seatIds.size() + " seat(s) (Rs. " + lkrTotal + ")");
            pending(session).put(orderId, new PendingCheckout(user.getId(), showtimeId, List.copyOf(seatIds),
                    promoCode, lkrTotal, paypalAmount, currency, BookingAddOns.fromForm(food, foodCombo, parking), holdId));
            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("amount", paypalAmount.toPlainString());
            body.put("currency", currency);
            return ResponseEntity.ok(body);
        } catch (PayPalException ex) {
            return error(HttpStatus.BAD_GATEWAY, ex.getMessage(), true);
        }
    }

    @PostMapping("/capture-order")
    public ResponseEntity<Map<String, Object>> captureOrder(@RequestParam String orderId,
                                                            Authentication authentication,
                                                            HttpSession session) {
        // Removed up front (atomically) so the same order can never be captured/booked twice.
        PendingCheckout checkout = pending(session).remove(orderId);
        User user = userService.findByEmail(authentication.getName());
        if (checkout == null || !checkout.userId().equals(user.getId())) {
            return error(HttpStatus.BAD_REQUEST, "This PayPal payment session has expired. Nothing was charged - please try again.", true);
        }

        // Payment timeout, checked BEFORE any money moves: if the 10-minute hold has run out the
        // order is never captured (nothing is charged). If it is still valid, it gets a short
        // grace period so the expiry job can't release the seats while PayPal is capturing.
        try {
            bookingService.startCapture(checkout.holdId(), user.getId());
        } catch (BookingExpiredException ex) {
            return expired();
        } catch (IllegalStateException ex) {
            return error(HttpStatus.CONFLICT, ex.getMessage() + " Nothing was charged.", true);
        }

        CaptureResult capture;
        try {
            capture = payPalClient.captureOrder(orderId);
        } catch (PayPalException ex) {
            return error(HttpStatus.PAYMENT_REQUIRED, ex.getMessage() + " No ticket was issued.", true);
        }
        boolean amountMatches = capture.amount() != null
                && capture.amount().compareTo(checkout.paypalAmount()) == 0
                && checkout.currency().equals(capture.currency());
        if (!capture.isCompleted() || !amountMatches) {
            log.warn("PayPal order {} not completed/mismatched (status={}, capture={})", orderId,
                    capture.orderStatus(), capture.captureStatus());
            if (capture.isCompleted()) {
                safeRefund(capture.captureId());
            }
            return error(HttpStatus.PAYMENT_REQUIRED, "PayPal did not confirm the payment, so no ticket was issued.", true);
        }

        // PayPal confirmed the money - now run the exact same booking logic every other
        // method uses (PaymentService#processPayment -> CONFIRMED -> ticket issued).
        try {
            // Completes the held booking (seats already reserved) - locked against the expiry job.
            Booking booking = bookingService.completeHeldBooking(checkout.holdId(), checkout.userId(), checkout.seatIds(),
                    PaymentMethod.SIMULATED_GATEWAY, checkout.promoCode(), checkout.addOns());
            if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new IllegalStateException("Booking could not be confirmed");
            }
            paymentService.recordGatewayReference(booking.getId(), "PAYPAL-" + capture.captureId());
            Map<String, Object> body = new HashMap<>();
            body.put("bookingId", booking.getId());
            body.put("redirectUrl", "/bookings/" + booking.getId());
            return ResponseEntity.ok(body);
        } catch (RuntimeException ex) {
            log.warn("Booking failed after PayPal capture {} - refunding", capture.captureId());
            boolean refunded = safeRefund(capture.captureId());
            String message = (ex.getMessage() != null ? ex.getMessage() : "Your booking could not be completed")
                    + (refunded ? ". Your PayPal payment has been refunded." : ". Please contact support for a refund.");
            return error(HttpStatus.CONFLICT, message, true);
        }
    }

    private boolean safeRefund(String captureId) {
        try {
            payPalClient.refundCapture(captureId);
            return true;
        } catch (PayPalException ex) {
            log.error("PayPal refund for capture {} failed: {}", captureId, ex.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, PendingCheckout> pending(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, PendingCheckout>) map;
        }
        Map<String, PendingCheckout> created = new java.util.concurrent.ConcurrentHashMap<>();
        session.setAttribute(SESSION_KEY, created);
        return created;
    }

    /** The seat hold ran out - the page shows the "seats released" message and stops the payment. */
    private ResponseEntity<Map<String, Object>> expired() {
        ResponseEntity<Map<String, Object>> response = error(HttpStatus.GONE,
                BookingExpiredException.MESSAGE + " Nothing was charged.", false);
        response.getBody().put("expired", true);
        return response;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, boolean newCaptcha) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("newCaptcha", newCaptcha);
        return ResponseEntity.status(status).body(body);
    }
}
