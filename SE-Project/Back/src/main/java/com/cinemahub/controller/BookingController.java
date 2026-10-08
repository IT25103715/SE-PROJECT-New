package com.cinemahub.controller;

import com.cinemahub.config.PayPalConfig;
import com.cinemahub.dto.BookingAddOns;
import com.cinemahub.dto.CheckoutSummary;
import com.cinemahub.dto.SelectablePaymentMethod;
import com.cinemahub.exception.BookingExpiredException;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.Booking;
import com.cinemahub.model.BookingStatus;
import com.cinemahub.model.Payment;
import com.cinemahub.model.PaymentMethod;
import com.cinemahub.model.PaymentOption;
import com.cinemahub.model.PaymentOptionType;
import com.cinemahub.model.PaymentStatus;
import com.cinemahub.model.Seat;
import com.cinemahub.model.Showtime;
import com.cinemahub.model.Ticket;
import com.cinemahub.model.User;
import com.cinemahub.service.BookingService;
import com.cinemahub.service.FoodComboService;
import com.cinemahub.service.FoodItemService;
import com.cinemahub.service.ParkingOptionService;
import com.cinemahub.service.HumanVerificationService;
import com.cinemahub.service.PaymentOptionService;
import com.cinemahub.service.PaymentService;
import com.cinemahub.service.QrCodeService;
import com.cinemahub.service.ShowtimeService;
import com.cinemahub.service.TicketPdfService;
import com.cinemahub.service.TicketService;
import com.cinemahub.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Function 3 (seat selection + booking history/cancellation) and the
 * customer-facing half of Function 6 (showing the QR ticket once booked).
 * Every action here operates on "the current user's own booking" - the id
 * is resolved from the logged-in Authentication, never trusted from the URL.
 */
@Controller
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ShowtimeService showtimeService;
    private final TicketService ticketService;
    private final QrCodeService qrCodeService;
    private final UserService userService;
    private final PaymentOptionService paymentOptionService;
    private final PaymentService paymentService;
    private final TicketPdfService ticketPdfService;
    private final HumanVerificationService humanVerificationService;
    private final PayPalConfig payPalConfig;
    private final FoodItemService foodItemService;
    private final FoodComboService foodComboService;
    private final ParkingOptionService parkingOptionService;

    public BookingController(BookingService bookingService, ShowtimeService showtimeService,
                              TicketService ticketService, QrCodeService qrCodeService,
                              UserService userService, PaymentOptionService paymentOptionService,
                              PaymentService paymentService, TicketPdfService ticketPdfService,
                              HumanVerificationService humanVerificationService,
                              PayPalConfig payPalConfig, FoodItemService foodItemService,
                              ParkingOptionService parkingOptionService, FoodComboService foodComboService) {
        this.bookingService = bookingService;
        this.showtimeService = showtimeService;
        this.ticketService = ticketService;
        this.qrCodeService = qrCodeService;
        this.userService = userService;
        this.paymentOptionService = paymentOptionService;
        this.paymentService = paymentService;
        this.ticketPdfService = ticketPdfService;
        this.humanVerificationService = humanVerificationService;
        this.payPalConfig = payPalConfig;
        this.foodItemService = foodItemService;
        this.parkingOptionService = parkingOptionService;
        this.foodComboService = foodComboService;
    }

    @GetMapping("/new")
    public String seatMap(@RequestParam Long showtimeId, Model model, HttpSession session, Authentication authentication) {
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        Showtime showtime = showtimeService.findApprovedById(showtimeId);
        // Opening the seat map = starting a new booking: free any unpaid seat
        // hold this customer left behind for this showtime, so their own seats aren't shown taken.
        if (authentication != null) {
            bookingService.releaseUnpaidHolds(currentUser(authentication).getId(), showtimeId);
        }
        addBookingSeatMapAttributes(model, showtime, session);
        return "booking/seatmap";
    }

    /**
     * Card-payment leg only (see seatmap.html): renders the mock card-details
     * screen with a read-only order summary. Nothing is booked or charged
     * here - that still only happens on the {@link #create} POST below,
     * which this page's "Pay Now" button submits to.
     */
    @PostMapping("/checkout")
    public String checkout(@RequestParam Long showtimeId,
                            @RequestParam List<Long> seatIds,
                            @RequestParam(required = false) String promoCode,
                            // Optional add-ons from the seat map: food=<foodItemId>:<quantity> (repeated), parking=true
                            @RequestParam(required = false) List<String> food,
                            // Food combo packs: foodCombo=<foodComboId>:<quantity> (repeated)
                            @RequestParam(required = false) List<String> foodCombo,
                            @RequestParam(required = false) Boolean parking,
                            // The 10-minute seat hold made on the seat map's pay step - its countdown continues here.
                            @RequestParam(required = false) Long holdId,
                            Authentication authentication,
                            Model model,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        return renderPaymentDetails(showtimeId, seatIds, promoCode, BookingAddOns.fromForm(food, foodCombo, parking),
                holdId, authentication, model, session, redirectAttributes);
    }

    private String renderPaymentDetails(Long showtimeId, List<Long> seatIds, String promoCode, BookingAddOns addOns,
                                         Long holdId, Authentication authentication,
                                         Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        // Payment timeout: the card screen is only shown while the seat hold is still running.
        Booking hold = bookingService.findActiveHold(holdId, currentUser(authentication).getId()).orElse(null);
        if (hold == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Your seat reservation has expired - the seats were released. Please choose your seats again.");
            return "redirect:/bookings/new?showtimeId=" + showtimeId;
        }
        model.addAttribute("holdId", hold.getId());
        model.addAttribute("holdSecondsLeft", hold.secondsUntilExpiry());
        try {
            CheckoutSummary summary = bookingService.buildCheckoutSummary(showtimeId, seatIds, promoCode, addOns);
            model.addAttribute("summary", summary);
            model.addAttribute("showtimeId", showtimeId);
            model.addAttribute("seatIds", seatIds);
            model.addAttribute("promoCode", promoCode);
            // Re-sent as hidden fields so "Pay Now" books exactly the same food & parking.
            model.addAttribute("foodValues", addOns.toFormValues());
            model.addAttribute("comboValues", addOns.toComboFormValues());
            model.addAttribute("parkingAdded", addOns.parking());
            // Fresh CAPTCHA code on every render of this page - see HumanVerificationService.
            humanVerificationService.newChallenge(session);
            return "booking/payment-details";
        } catch (ResourceNotFoundException | IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/bookings/new?showtimeId=" + showtimeId;
        }
    }

    @PostMapping
    public String create(@RequestParam Long showtimeId,
                          @RequestParam List<Long> seatIds,
                          @RequestParam(required = false) PaymentMethod paymentMethod,
                          @RequestParam(required = false) String promoCode,
                          // Set from the selected option's PaymentOptionType (see seatmap.html's hidden
                          // field) - null for the raw-enum fallback, so it can never be confused with a
                          // customer literally picking "Cash" when no Payment Manager options exist.
                          @RequestParam(required = false) PaymentOptionType paymentOptionType,
                          // The characters the customer typed from the CAPTCHA image (GET /bookings/captcha).
                          @RequestParam(required = false) String humanToken,
                          @RequestParam(required = false) String customerName,
                          @RequestParam(required = false) String paymentOptionLabel,
                          // Optional add-ons: food=<foodItemId>:<quantity> (repeated), parking=true
                          @RequestParam(required = false) List<String> food,
                          // Food combo packs: foodCombo=<foodComboId>:<quantity> (repeated)
                          @RequestParam(required = false) List<String> foodCombo,
                          @RequestParam(required = false) Boolean parking,
                          // Every payment method: the seat hold made when the customer reached the pay step
                          // (POST /bookings/hold). Its 10-minute deadline is re-checked here on the server.
                          @RequestParam(required = false) Long holdId,
                          // The consent checkbox (fragments/terms.html) - an unticked box isn't submitted at all.
                          @RequestParam(defaultValue = "false") boolean termsAccepted,
                          Authentication authentication,
                          Model model,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        // Checkout gate - the ONE shared check for every payment method (card, Apple Pay,
        // PayPal, Bank Transfer all submit here). Two independent checks, both required:
        // the CAPTCHA code AND the Terms & Conditions consent box. Either one failing sends
        // the customer back to the screen they submitted from (with a fresh CAPTCHA image,
        // since verify() always uses up the old code) and nothing is
        // booked or charged.
        // PayPal is paid for real on PayPal's Sandbox (PayPalCheckoutController) - it must never be
        // booked here through the simulated flow. Apple Pay / Bank Transfer / Card are unaffected.
        if (payPalConfig.isEnabled() && isPayPalOption(paymentOptionType, paymentOptionLabel)) {
            Showtime showtime = showtimeService.findById(showtimeId);
            addBookingSeatMapAttributes(model, showtime, session);
            model.addAttribute("errorMessage", "Please pay with the PayPal button to complete a PayPal booking.");
            return "booking/seatmap";
        }
        boolean humanVerified = humanVerificationService.verify(session, humanToken);
        if (!humanVerified || !termsAccepted) {
            if (!humanVerified) {
                model.addAttribute("verificationError",
                        "The characters you typed didn't match the image. Please try the new code.");
            }
            if (!termsAccepted) {
                model.addAttribute("termsError",
                        "Please tick this box to confirm your booking and accept the Terms & Conditions.");
            }
            // Keep the box ticked on the retry if the customer had already ticked it.
            model.addAttribute("termsAccepted", termsAccepted);
            if (paymentMethod == PaymentMethod.CARD) {
                return renderPaymentDetails(showtimeId, seatIds, promoCode, BookingAddOns.fromForm(food, foodCombo, parking),
                        holdId, authentication, model, session, redirectAttributes);
            }
            // Wallet / bank transfer submit straight from the seat map's review step - put the
            // customer back on that step with their seats, coupon and payment choice kept.
            Showtime showtime = showtimeService.findById(showtimeId);
            addBookingSeatMapAttributes(model, showtime, session);
            model.addAttribute("retrySeatIds", seatIds);
            model.addAttribute("retryPaymentMethod", paymentMethod);
            model.addAttribute("retryPaymentOptionType", paymentOptionType);
            model.addAttribute("retryPromoCode", promoCode);
            model.addAttribute("retryCustomerName", customerName);
            model.addAttribute("retryPaymentOptionLabel", paymentOptionLabel);
            model.addAttribute("retryFood", BookingAddOns.fromForm(food, foodCombo, parking).toFormValues());
            model.addAttribute("retryFoodCombos", BookingAddOns.fromForm(food, foodCombo, parking).toComboFormValues());
            model.addAttribute("retryParking", Boolean.TRUE.equals(parking));
            // Keep the same seat hold (and its original deadline) for the retry,
            // and show its seats as free for THIS customer so they can be re-selected on the page.
            if (holdId != null) {
                bookingService.findActiveHold(holdId, currentUser(authentication).getId()).ifPresent(hold -> {
                    model.addAttribute("retryHoldId", hold.getId());
                    model.addAttribute("retryHoldSecondsLeft", hold.secondsUntilExpiry());
                    model.addAttribute("bookedSeatIds", bookingService.getBookedSeatIdsExcludingBooking(showtimeId, hold.getId()));
                });
            }
            return "booking/seatmap";
        }

        // Payment timeout for EVERY payment method: the booking is made from the 10-minute seat
        // hold created on the pay step. No hold (or an expired one) = refused, so the countdown
        // can't be skipped by submitting the form directly.
        User user = currentUser(authentication);
        boolean bankTransfer = paymentOptionType == PaymentOptionType.BANK_TRANSFER;
        return completeHeldBooking(holdId, user, showtimeId, seatIds, paymentMethod, promoCode,
                BookingAddOns.fromForm(food, foodCombo, parking), bankTransfer, model, session, redirectAttributes);
    }

    /**
     * Pays for the seat hold made when the customer reached the pay step (every payment method;
     * PayPal only when the real Sandbox checkout is off). The hold's deadline is checked on the
     * server with the booking row locked - a late submission after the countdown hit zero is
     * refused. Bank Transfer isn't paid here: the booking stays PENDING with a 24-hour deadline
     * to upload the slip, so the customer is sent to the upload screen.
     */
    private String completeHeldBooking(Long holdId, User user, Long showtimeId, List<Long> seatIds,
                                       PaymentMethod paymentMethod, String promoCode, BookingAddOns addOns,
                                       boolean bankTransfer,
                                       Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            Booking booking = bookingService.completeHeldBooking(holdId, user.getId(), seatIds, paymentMethod, promoCode,
                    addOns, bankTransfer);
            // An invalid/expired coupon doesn't fail the booking (see BookingService) - it still
            // goes through at full price, so just carry the warning to the confirmation page.
            if (booking.getPromoWarning() != null) {
                redirectAttributes.addFlashAttribute("promoWarning", booking.getPromoWarning());
            }
            if (booking.getStatus() == BookingStatus.PENDING) {
                return "redirect:/bookings/" + booking.getId() + "/upload-receipt";
            }
            return "redirect:/bookings/" + booking.getId();
        } catch (IllegalStateException | IllegalArgumentException | ResourceNotFoundException ex) {
            // Expired: the seats are already gone. Anything else (e.g. parking just filled up):
            // drop the hold so the seat map below shows the customer's seats as free again.
            if (!(ex instanceof BookingExpiredException) && holdId != null) {
                bookingService.releaseHold(holdId, user.getId());
            }
            Showtime showtime = showtimeService.findById(showtimeId);
            addBookingSeatMapAttributes(model, showtime, session);
            model.addAttribute("errorMessage", ex.getMessage());
            return "booking/seatmap";
        }
    }

    /**
     * Step 2 of the seat map (any payment method): hold the chosen seats for 10 minutes
     * (a PENDING booking with expiresAt). Returns the hold id and the seconds left, which the
     * page's countdown runs from.
     */
    @PostMapping(value = "/hold", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> holdSeats(@RequestParam Long showtimeId,
                                                         @RequestParam List<Long> seatIds,
                                                         Authentication authentication) {
        try {
            Booking hold = bookingService.holdSeats(currentUser(authentication).getId(), showtimeId, seatIds);
            return ResponseEntity.ok(holdBody(hold));
        } catch (IllegalStateException | IllegalArgumentException | ResourceNotFoundException ex) {
            return holdError(ex);
        }
    }

    /** Seats changed on the pay step: swap the held seats, keeping the original deadline. */
    @PostMapping(value = "/hold/{holdId}/seats", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateHoldSeats(@PathVariable Long holdId,
                                                               @RequestParam List<Long> seatIds,
                                                               Authentication authentication) {
        try {
            Booking hold = bookingService.updateHoldSeats(holdId, currentUser(authentication).getId(), seatIds);
            return ResponseEntity.ok(holdBody(hold));
        } catch (IllegalStateException | IllegalArgumentException | ResourceNotFoundException ex) {
            return holdError(ex);
        }
    }

    /** Customer left the pay step (Back / closed the page): free the seats now instead of in 10 minutes. */
    @PostMapping("/hold/{holdId}/release")
    @ResponseBody
    public ResponseEntity<Void> releaseHold(@PathVariable Long holdId, Authentication authentication) {
        bookingService.releaseHold(holdId, currentUser(authentication).getId());
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> holdBody(Booking hold) {
        Map<String, Object> body = new HashMap<>();
        body.put("holdId", hold.getId());
        body.put("secondsLeft", hold.secondsUntilExpiry());
        return body;
    }

    private static ResponseEntity<Map<String, Object>> holdError(RuntimeException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());
        body.put("expired", ex instanceof BookingExpiredException);
        return ResponseEntity.status(ex instanceof BookingExpiredException ? 410 : 409).body(body);
    }

    /**
     * The CAPTCHA picture for fragments/human-check.html - the session's current code drawn
     * as a distorted PNG. {@code refresh=true} ("New code" button) issues a fresh code first.
     * Never cached, so the picture always matches the code the server will check.
     */
    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public ResponseEntity<byte[]> captcha(@RequestParam(defaultValue = "false") boolean refresh,
                                          HttpSession session) {
        if (refresh) {
            humanVerificationService.newChallenge(session);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .contentType(MediaType.IMAGE_PNG)
                .body(humanVerificationService.renderImage(session));
    }

    /**
     * The new-booking seat map (not edit-seats): its review step is where Apple Pay / PayPal /
     * Bank Transfer submit their payment, so it carries a fresh CAPTCHA. That
     * check is only shown (and only required) when a non-card method is picked - card
     * payments get their own fresh check on the card-details screen instead.
     */
    private void addBookingSeatMapAttributes(Model model, Showtime showtime, HttpSession session) {
        addSeatMapAttributes(model, showtime);
        // "Select a Showtime" picker above the seat map - read-only, same data as the movie page.
        model.addAttribute("showtimesByDate",
                showtimeService.findUpcomingApprovedByMovieGroupedByDate(showtime.getMovie().getId()));
        humanVerificationService.newChallenge(session);
        // Real PayPal Sandbox button (seatmap.html) - only the public client ID reaches the page.
        model.addAttribute("paypalEnabled", payPalConfig.isEnabled());
        model.addAttribute("paypalClientId", payPalConfig.getClientId());
        model.addAttribute("paypalCurrency", payPalConfig.getCurrency());
        model.addAttribute("paypalLkrPerUsd", payPalConfig.getLkrPerUsd());
        // Optional add-ons (step 2): approved + available food items, and parking if it still has
        // a free slot for THIS showtime (CONFIRMED bookings with parking vs totalSlots).
        model.addAttribute("foodItems", foodItemService.findOrderable());
        // Food combo packs (approved, available, every included item on the menu) - bundle price.
        model.addAttribute("foodCombos", foodComboService.findOrderable());
        parkingOptionService.findOrderable().ifPresent(option -> model.addAttribute("parkingOption", option));
        model.addAttribute("parkingSlotsLeft", parkingOptionService.slotsLeft(showtime.getId()));
    }

    /** The seat map's "PayPal" option: type PAYPAL, or an older generic option named "PayPal". */
    private static boolean isPayPalOption(PaymentOptionType paymentOptionType, String paymentOptionLabel) {
        return paymentOptionType == PaymentOptionType.PAYPAL
                || (paymentOptionLabel != null && paymentOptionLabel.trim().equalsIgnoreCase("PayPal"));
    }

    private void addSeatMapAttributes(Model model, Showtime showtime) {
        Map<String, List<Seat>> seatsByRow = bookingService.getHallSeats(showtime).stream()
                .collect(Collectors.groupingBy(Seat::getRowLabel, LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("showtime", showtime);
        model.addAttribute("seatsByRow", seatsByRow);
        model.addAttribute("bookedSeatIds", bookingService.getBookedSeatIds(showtime.getId()));
        model.addAttribute("paymentMethodOptions", resolvePaymentMethodOptions());
    }

    /**
     * Payment Manager's system-accepted options (Apple Pay, PayPal, etc) drive this
     * dropdown's labels - only ACTIVE ones are ever offered. Each option still has to
     * submit one of the three {@link PaymentMethod} enum values underneath, since
     * that's what PaymentService/the Payment & Transaction module actually records
     * (no real per-provider gateway exists to route to - see mapToPaymentMethod).
     * Falls back to the raw enum if a Payment Manager has disabled every option, so
     * booking never becomes entirely impossible.
     */
    private List<SelectablePaymentMethod> resolvePaymentMethodOptions() {
        List<PaymentOption> active = paymentOptionService.findActive();
        if (active.isEmpty()) {
            // Fallback path: no PaymentOptionType exists here at all, so optionType is null -
            // a raw "Cash" pick can never be mistaken for a Bank Transfer option (see
            // SelectablePaymentMethod's javadoc for why that distinction matters).
            return Arrays.stream(PaymentMethod.values())
                    .map(m -> new SelectablePaymentMethod(m.getDisplayLabel(), m.name(), null))
                    .toList();
        }
        return active.stream()
                .map(option -> new SelectablePaymentMethod(option.getName(), mapToPaymentMethod(option.getType()).name(),
                        option.getType().name()))
                .toList();
    }

    private PaymentMethod mapToPaymentMethod(PaymentOptionType type) {
        return switch (type) {
            case CARD_NETWORK -> PaymentMethod.CARD;
            case APPLE_PAY, PAYPAL, DIGITAL_WALLET -> PaymentMethod.SIMULATED_GATEWAY;
            case BANK_TRANSFER -> PaymentMethod.CASH;
        };
    }

    @GetMapping
    public String history(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        List<Booking> bookings = bookingService.findByUser(user.getId());
        model.addAttribute("bookings", bookings);
        // Every booking has exactly one Payment row created at booking time (see
        // BookingService#createBooking / #initiateBankTransferPayment) - looked up per-row here
        // (rather than relying on Booking.payment, which is the unused inverse side of the
        // relationship - see PaymentService) so the table can show "Awaiting Review" instead of
        // a bare PENDING badge for bank-transfer bookings.
        // A seat hold that was never paid (still PENDING, or EXPIRED) has no
        // Payment row - it maps to null and the page shows the booking status instead.
        Map<Long, Payment> paymentsByBookingId = new HashMap<>();
        for (Booking b : bookings) {
            paymentsByBookingId.put(b.getId(), paymentService.findOptionalByBookingId(b.getId()).orElse(null));
        }
        model.addAttribute("paymentsByBookingId", paymentsByBookingId);
        return "booking/history";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication authentication, Model model) {
        // Owner only - a booking (and its ticket) is private to the customer who made it.
        Booking booking = ownedBooking(id, authentication);
        model.addAttribute("booking", booking);
        // Null for a seat hold that was never paid.
        model.addAttribute("payment", paymentService.findOptionalByBookingId(id).orElse(null));
        model.addAttribute("foodLines", bookingService.findFoodLines(id));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            model.addAttribute("ticket", ticketService.findByBookingId(id));
        }
        return "booking/detail";
    }

    /**
     * Bank Transfer only: shown right after a booking is created with that option, and
     * reachable again from booking history/detail while still AWAITING_RECEIPT (so a customer
     * who navigated away can come back and upload, or re-upload, before an admin reviews it).
     */
    @GetMapping("/{id}/upload-receipt")
    public String uploadReceiptForm(@PathVariable Long id, Authentication authentication, Model model) {
        Booking booking = ownedBooking(id, authentication);
        Payment payment = paymentService.findOptionalByBookingId(id).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.AWAITING_RECEIPT
                || booking.getStatus() != BookingStatus.PENDING) {
            return "redirect:/bookings/" + id;
        }
        model.addAttribute("booking", booking);
        model.addAttribute("payment", payment);
        addBankTransferDetails(model);
        return "booking/upload-receipt";
    }

    @PostMapping("/{id}/upload-receipt")
    public String uploadReceipt(@PathVariable Long id,
                                 @RequestParam("receiptFile") MultipartFile receiptFile,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        Booking booking = ownedBooking(id, authentication);
        try {
            // Locked against the expiry job; refused after the 24-hour window. A successful upload
            // clears the deadline, so the booking then waits for the admin however long that takes.
            bookingService.attachBankTransferReceipt(id, receiptFile);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Receipt uploaded - your booking is pending admin review.");
            return "redirect:/bookings/" + id;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            model.addAttribute("booking", booking);
            model.addAttribute("payment", paymentService.findByBookingId(id));
            model.addAttribute("errorMessage", ex.getMessage());
            addBankTransferDetails(model);
            return "booking/upload-receipt";
        } catch (IOException ex) {
            model.addAttribute("booking", booking);
            model.addAttribute("payment", paymentService.findByBookingId(id));
            model.addAttribute("errorMessage", "Could not save the uploaded file, please try again.");
            addBankTransferDetails(model);
            return "booking/upload-receipt";
        }
    }

    /**
     * Bank account the customer should transfer to - read from the ACTIVE Bank Transfer payment
     * option the Payment Manager set up (never hardcoded in the template). Absent if none exists yet.
     */
    private void addBankTransferDetails(Model model) {
        paymentOptionService.findActiveBankTransferDetails()
                .ifPresent(option -> model.addAttribute("bankOption", option));
    }

    /**
     * Downloadable PDF ticket (Proposal Document's "Automated Booking Receipt" minor
     * function) - only once the booking is CONFIRMED, i.e. payment succeeded and a real
     * ticket/QR already exists. Reuses ownedBooking's IDOR-safe pattern like every other
     * action here, and the exact same QR PNG bytes the on-screen ticket already uses.
     */
    @GetMapping("/{id}/download-ticket")
    @ResponseBody
    public ResponseEntity<byte[]> downloadTicket(@PathVariable Long id, Authentication authentication) {
        Booking booking = ownedBooking(id, authentication);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResourceNotFoundException("Ticket not available for booking: " + id);
        }
        Ticket ticket = ticketService.findByBookingId(id);
        byte[] qrPng = qrCodeService.generateQrCodePng(ticket.getQrCodeData());
        byte[] pdf = ticketPdfService.buildTicketPdf(booking, ticket, qrPng, bookingService.findFoodLines(id));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ticket-" + id + ".pdf\"")
                .body(pdf);
    }

    @GetMapping(value = "/{id}/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] qrCode(@PathVariable Long id, Authentication authentication) {
        ownedBooking(id, authentication);   // the QR code *is* the ticket - never hand it to anyone but the owner
        Ticket ticket = ticketService.findByBookingId(id);
        return qrCodeService.generateQrCodePng(ticket.getQrCodeData());
    }

    @GetMapping("/{id}/edit")
    public String editSeatsForm(@PathVariable Long id, Authentication authentication, Model model) {
        Booking booking = ownedBooking(id, authentication);
        addEditSeatMapAttributes(model, booking);
        return "booking/edit-seats";
    }

    @PostMapping("/{id}/edit")
    public String editSeats(@PathVariable Long id,
                             @RequestParam List<Long> seatIds,
                             Authentication authentication,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User user = currentUser(authentication);
        try {
            bookingService.updateSeats(id, user.getId(), seatIds);
            redirectAttributes.addFlashAttribute("successMessage", "Seats updated.");
            return "redirect:/bookings/" + id;
        } catch (IllegalStateException | IllegalArgumentException | ResourceNotFoundException ex) {
            Booking booking = ownedBooking(id, authentication);
            addEditSeatMapAttributes(model, booking);
            model.addAttribute("errorMessage", ex.getMessage());
            return "booking/edit-seats";
        }
    }

    /** Loads a booking and confirms it belongs to the logged-in user, 404-ing otherwise (never trust the id in the URL alone). */
    private Booking ownedBooking(Long bookingId, Authentication authentication) {
        User user = currentUser(authentication);
        Booking booking = bookingService.findById(bookingId);
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Booking not found: " + bookingId);
        }
        return booking;
    }

    private void addEditSeatMapAttributes(Model model, Booking booking) {
        Showtime showtime = booking.getShowtime();
        addSeatMapAttributes(model, showtime);
        model.addAttribute("bookedSeatIds", bookingService.getBookedSeatIdsExcludingBooking(showtime.getId(), booking.getId()));
        model.addAttribute("booking", booking);
        Set<Long> currentSeatIds = booking.getBookingSeats().stream()
                .map(bs -> bs.getSeat().getId())
                .collect(Collectors.toSet());
        model.addAttribute("currentSeatIds", currentSeatIds);
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication authentication, Model model) {
        User user = currentUser(authentication);
        try {
            bookingService.cancelBooking(id, user.getId());
        } catch (IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/bookings";
    }

    private User currentUser(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }
}
