package com.cinemahub.service;

import com.cinemahub.dto.BookingAddOns;
import com.cinemahub.dto.CheckoutSummary;
import com.cinemahub.dto.FoodLine;
import com.cinemahub.dto.DiscountPreviewResult;
import com.cinemahub.exception.BookingExpiredException;
import com.cinemahub.exception.ResourceNotFoundException;
import com.cinemahub.model.*;
import com.cinemahub.repository.BookingFoodItemRepository;
import com.cinemahub.repository.BookingRepository;
import com.cinemahub.repository.BookingSeatRepository;
import com.cinemahub.repository.SeatRepository;
import com.cinemahub.service.discount.ComboOrder;
import com.cinemahub.service.notification.NotificationFactory;
import com.cinemahub.service.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Function 3: seat selection and booking. The one rule this class exists to
 * enforce is "the same seat can never be double-booked for the same
 * showtime" - see {@link #createBooking}, which re-checks availability
 * inside the same transaction right before saving. Payment itself
 * (Function 4) is delegated to {@link PaymentService}: a booking starts out
 * PENDING and only flips to CONFIRMED once its payment completes.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** Every payment method: how long the seats stay held once the customer reaches the pay step. */
    public static final Duration INSTANT_PAYMENT_WINDOW = Duration.ofMinutes(10);
    /** Bank Transfer: how long the booking waits for a receipt before its seats are released. */
    public static final Duration BANK_TRANSFER_WINDOW = Duration.ofHours(24);
    /** Extra time a hold gets while PayPal is capturing the money, so it can't expire mid-capture. */
    private static final Duration CAPTURE_GRACE = Duration.ofMinutes(3);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeService showtimeService;
    private final TicketService ticketService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final PromotionService promotionService;
    private final FoodItemService foodItemService;
    private final FoodComboService foodComboService;
    private final ParkingOptionService parkingOptionService;
    private final BookingFoodItemRepository bookingFoodItemRepository;

    public BookingService(BookingRepository bookingRepository,
                           BookingSeatRepository bookingSeatRepository,
                           SeatRepository seatRepository,
                           ShowtimeService showtimeService,
                           TicketService ticketService,
                           UserService userService,
                           PaymentService paymentService,
                           PromotionService promotionService,
                           FoodItemService foodItemService,
                           FoodComboService foodComboService,
                           ParkingOptionService parkingOptionService,
                           BookingFoodItemRepository bookingFoodItemRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.seatRepository = seatRepository;
        this.showtimeService = showtimeService;
        this.ticketService = ticketService;
        this.userService = userService;
        this.paymentService = paymentService;
        this.promotionService = promotionService;
        this.foodItemService = foodItemService;
        this.foodComboService = foodComboService;
        this.parkingOptionService = parkingOptionService;
        this.bookingFoodItemRepository = bookingFoodItemRepository;
    }

    /**
     * Food, food combos & parking chosen at checkout, priced against the current menu / combo
     * prices / parking option. {@code foodItems} and {@code foodCombos} run parallel to
     * {@code foodLines}: for each line exactly one of them is set (the other is null).
     * {@code foodTotal} is the food subtotal - items at menu price plus combos at bundle price.
     * {@code includedFood} counts every food item ordered, including the items inside combo
     * packs (used for promotion conditions like "requires a Large Popcorn").
     */
    private record PricedAddOns(List<FoodLine> foodLines, List<FoodItem> foodItems, List<FoodCombo> foodCombos,
                                BigDecimal foodTotal, ParkingOption parking, BigDecimal parkingFee,
                                Map<Long, Integer> includedFood) {
    }

    /**
     * Prices the optional add-ons. Food items must be approved and available; a food combo must
     * be orderable (approved, available, all its items on the menu) and is charged its bundle
     * price - not the sum of its items; parking must be offered (approved + available) and still
     * have a free slot for this showtime. Anything that isn't orderable any more is rejected with
     * a clear message rather than silently dropped.
     */
    private PricedAddOns priceAddOns(Showtime showtime, BookingAddOns addOns) {
        BookingAddOns selection = addOns == null ? BookingAddOns.none() : addOns;
        List<FoodLine> lines = new ArrayList<>();
        List<FoodItem> items = new ArrayList<>();
        List<FoodCombo> combos = new ArrayList<>();
        Map<Long, Integer> includedFood = new java.util.LinkedHashMap<>();
        BigDecimal foodTotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : selection.foodQuantities().entrySet()) {
            FoodItem item = foodItemService.findById(entry.getKey());
            if (!item.isOrderable()) {
                throw new IllegalArgumentException(item.getName() + " is no longer available - please remove it from your order");
            }
            int quantity = Math.min(entry.getValue(), BookingAddOns.MAX_QUANTITY_PER_ITEM);
            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));
            lines.add(new FoodLine(item.getId(), item.getName(), quantity, item.getPrice(), lineTotal));
            items.add(item);
            combos.add(null);
            includedFood.merge(item.getId(), quantity, Integer::sum);
            foodTotal = foodTotal.add(lineTotal);
        }
        for (Map.Entry<Long, Integer> entry : selection.comboQuantities().entrySet()) {
            FoodCombo combo = foodComboService.findById(entry.getKey());
            if (!combo.isOrderable()) {
                throw new IllegalArgumentException(combo.getName() + " is no longer available - please remove it from your order");
            }
            int quantity = Math.min(entry.getValue(), BookingAddOns.MAX_QUANTITY_PER_ITEM);
            BigDecimal lineTotal = combo.getBundlePrice().multiply(BigDecimal.valueOf(quantity));
            lines.add(new FoodLine(combo.getId(), combo.getName(), quantity, combo.getBundlePrice(), lineTotal, true));
            items.add(null);
            combos.add(combo);
            combo.getItems().forEach(part ->
                    includedFood.merge(part.getFoodItem().getId(), part.getQuantity() * quantity, Integer::sum));
            foodTotal = foodTotal.add(lineTotal);
        }
        ParkingOption parking = null;
        BigDecimal parkingFee = BigDecimal.ZERO;
        if (selection.parking()) {
            parking = parkingOptionService.findOrderable()
                    .orElseThrow(() -> new IllegalStateException("Parking isn't available right now - please untick parking"));
            if (parkingOptionService.slotsLeft(showtime.getId()) <= 0) {
                throw new IllegalStateException("Parking is full for this showtime - please untick parking");
            }
            parkingFee = parking.getPrice();
        }
        return new PricedAddOns(lines, items, combos, foodTotal, parking, parkingFee, includedFood);
    }

    /**
     * The booking facts promotions are checked against: seat count, every food item ordered
     * (combo contents included), parking, and the food subtotal (for food-only discounts).
     */
    private static ComboOrder orderFacts(int seatCount, PricedAddOns priced) {
        return new ComboOrder(seatCount, priced.includedFood(), priced.parking() != null, priced.foodTotal());
    }

    /** Food lines saved for a booking (empty for bookings without food). */
    public List<BookingFoodItem> findFoodLines(Long bookingId) {
        return bookingFoodItemRepository.findByBooking_IdOrderByIdAsc(bookingId);
    }

    /** All physical seats in the showtime's hall, plus the subset already taken for that showtime. */
    public List<Seat> getHallSeats(Showtime showtime) {
        return seatRepository.findByCinemaHall_IdOrderByRowLabelAscSeatNumberAsc(showtime.getCinemaHall().getId());
    }

    public Set<Long> getBookedSeatIds(Long showtimeId) {
        return bookingSeatRepository.findActiveByShowtimeId(showtimeId).stream()
                .map(bookingSeat -> bookingSeat.getSeat().getId())
                .collect(Collectors.toSet());
    }

    /** Same as {@link #getBookedSeatIds(Long)}, but leaves out the given booking's own seats - used to render the edit-seats screen. */
    public Set<Long> getBookedSeatIdsExcludingBooking(Long showtimeId, Long bookingId) {
        return bookingSeatRepository.findActiveByShowtimeIdExcludingBooking(showtimeId, bookingId).stream()
                .map(bookingSeat -> bookingSeat.getSeat().getId())
                .collect(Collectors.toSet());
    }

    /** A showtime that has already started can no longer be booked. */
    private void requireNotStarted(Showtime showtime) {
        if (!showtime.getDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("This showtime has already started, bookings are closed");
        }
    }

    /** Every chosen seat must physically exist in the hall this showtime is screened in. */
    private void requireSeatsInHall(Showtime showtime, List<Seat> seats) {
        Set<Long> hallSeatIds = getHallSeats(showtime).stream()
                .map(Seat::getId).collect(Collectors.toSet());
        for (Seat seat : seats) {
            if (!hallSeatIds.contains(seat.getId())) {
                throw new IllegalArgumentException("Selected seat does not belong to this showtime's hall");
            }
        }
    }

    /**
     * Read-only preview of what a booking would cost, shown on the
     * payment-details screen between seat selection and the real
     * {@link #createBooking}. Deliberately not {@code @Transactional} and
     * never calls {@link PromotionService#recordUsage} - it must be safe to
     * call more than once (e.g. the customer reloads the payment page)
     * without double-counting a coupon's usage count.
     */
    public CheckoutSummary buildCheckoutSummary(Long showtimeId, List<Long> seatIds, String promoCode) {
        return buildCheckoutSummary(showtimeId, seatIds, promoCode, BookingAddOns.none());
    }

    /** Same preview, including any food and parking - the promo applies to seats + food + parking. */
    public CheckoutSummary buildCheckoutSummary(Long showtimeId, List<Long> seatIds, String promoCode, BookingAddOns addOns) {
        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        Showtime showtime = showtimeService.findApprovedById(showtimeId);
        requireNotStarted(showtime);
        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("One or more selected seats do not exist");
        }
        requireSeatsInHall(showtime, seats);

        BookingAddOns selection = addOns == null ? BookingAddOns.none() : addOns;
        PricedAddOns priced = priceAddOns(showtime, selection);
        BigDecimal seatsTotal = showtime.getPrice().multiply(BigDecimal.valueOf(seats.size()));
        BigDecimal originalPrice = seatsTotal.add(priced.foodTotal()).add(priced.parkingFee());
        BigDecimal totalPrice = originalPrice;
        BigDecimal discountAmount = null;
        String appliedPromoCode = null;
        String promoWarning = null;

        if (promoCode != null && !promoCode.isBlank()) {
            try {
                DiscountPreviewResult preview = promotionService.previewDiscount(promoCode.trim(), originalPrice,
                        orderFacts(seats.size(), priced));
                discountAmount = preview.getDiscountAmount();
                totalPrice = originalPrice.subtract(discountAmount);
                appliedPromoCode = preview.getCode();
            } catch (IllegalArgumentException ex) {
                promoWarning = ex.getMessage();
            }
        }

        return new CheckoutSummary(showtime, seats, originalPrice, discountAmount, appliedPromoCode, totalPrice, promoWarning,
                seatsTotal, priced.foodLines(), priced.foodTotal(),
                priced.parking() != null ? priced.parking().getLabel() : null, priced.parkingFee());
    }

    @Transactional
    public Booking createBooking(Long userId, Long showtimeId, List<Long> seatIds) {
        return createBooking(userId, showtimeId, seatIds, PaymentMethod.SIMULATED_GATEWAY, null);
    }

    @Transactional
    public Booking createBooking(Long userId, Long showtimeId, List<Long> seatIds, PaymentMethod paymentMethod) {
        return createBooking(userId, showtimeId, seatIds, paymentMethod, null);
    }

    @Transactional
    public Booking createBooking(Long userId, Long showtimeId, List<Long> seatIds, PaymentMethod paymentMethod, String promoCode) {
        return createBooking(userId, showtimeId, seatIds, paymentMethod, promoCode, false);
    }

    /**
     * Seat selection, optional coupon code, then simulated payment: the
     * booking is inserted as PENDING, {@link PaymentService#processPayment}
     * is run against it, and only a successfully processed payment flips it
     * to CONFIRMED (and issues the ticket). If the payment fails, the whole
     * transaction rolls back so no half-paid booking is ever left behind.
     * An invalid/expired/exhausted coupon does NOT block the purchase - the
     * booking still goes through at full price, with a warning surfaced on
     * the confirmation page (see {@link Booking#getPromoWarning()} and
     * {@code BookingController#create}).
     *
     * <p>{@code bankTransfer} (true only when the customer picked a PAYMENT_MANAGER
     * option of type BANK_TRANSFER) skips straight-through payment entirely: the
     * booking is saved as PENDING with its seats held (same as every booking,
     * mid-transaction), a Payment row is created as AWAITING_RECEIPT instead of being
     * processed, and no ticket is issued yet - see PaymentService#initiateBankTransferPayment
     * and #confirmBankTransferBooking/#rejectBankTransferBooking below for what happens
     * once an admin reviews the uploaded receipt. This is, deliberately, the first case in
     * this codebase where a PENDING booking is ever actually committed to the database -
     * every other path either reaches CONFIRMED or rolls back entirely within the same
     * transaction (see the IllegalStateException a few lines below).</p>
     */
    @Transactional
    public Booking createBooking(Long userId, Long showtimeId, List<Long> seatIds, PaymentMethod paymentMethod,
                                  String promoCode, boolean bankTransfer) {
        return createBooking(userId, showtimeId, seatIds, paymentMethod, promoCode, bankTransfer, BookingAddOns.none());
    }

    /**
     * Full version, with the optional food and parking add-ons: they are priced inside the same
     * transaction, added to the total BEFORE the promo code is applied, saved on the booking
     * (parking + food total) and as BookingFoodItem lines, and a COMBO promo code is checked
     * against them.
     */
    @Transactional
    public Booking createBooking(Long userId, Long showtimeId, List<Long> seatIds, PaymentMethod paymentMethod,
                                  String promoCode, boolean bankTransfer, BookingAddOns addOns) {
        BookingAddOns selection = addOns == null ? BookingAddOns.none() : addOns;
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.SIMULATED_GATEWAY;
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat");
        }

        // Admin approval required before Promotion Manager / Manager submissions go live - does not apply to Admin's own creations
        Showtime showtime = showtimeService.findApprovedById(showtimeId);
        requireNotStarted(showtime);
        User user = userService.findById(userId);

        // Re-read the currently taken seats inside this transaction to guard against
        // two customers submitting for the same seat at (almost) the same time.
        Set<Long> alreadyBooked = getBookedSeatIds(showtimeId);
        for (Long seatId : seatIds) {
            if (alreadyBooked.contains(seatId)) {
                throw new IllegalStateException("Seat is already booked for this showtime, please pick another seat");
            }
        }

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("One or more selected seats do not exist");
        }
        requireSeatsInHall(showtime, seats);

        PricedAddOns priced = priceAddOns(showtime, selection);
        BigDecimal originalPrice = showtime.getPrice().multiply(BigDecimal.valueOf(seats.size()))
                .add(priced.foodTotal()).add(priced.parkingFee());
        PromoResult promo = applyPromo(promoCode, originalPrice, orderFacts(seats.size(), priced));

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShowtime(showtime);
        booking.setStatus(BookingStatus.PENDING);
        applyPricing(booking, promo, priced);

        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setSeat(seat);
            booking.getBookingSeats().add(bookingSeat);
        }
        if (bankTransfer) {
            // Bank Transfer gets the long window: time to make the transfer and upload the
            // receipt. Once a receipt is uploaded the deadline is cleared (see
            // attachBankTransferReceipt) and only the admin's decision moves it on.
            booking.setExpiresAt(LocalDateTime.now().plus(BANK_TRANSFER_WINDOW));
        }

        Booking saved = bookingRepository.save(booking);
        saveFoodLines(saved, priced);

        if (bankTransfer) {
            // Do NOT process payment, confirm the booking, or issue a ticket here - all of
            // that only happens once an admin approves the receipt the customer is about to
            // upload (see confirmBankTransferBooking below). The booking is returned still
            // PENDING; BookingController#create reads that status to redirect to the upload
            // screen instead of the normal confirmation page.
            paymentService.initiateBankTransferPayment(saved, paymentMethod);
            log.info("Booking #{} created PENDING (Bank Transfer) - expires at {} unless a receipt is uploaded",
                    saved.getId(), saved.getExpiresAt());
            return saved;
        }

        return payAndConfirm(saved, paymentMethod, user, showtime, seats.size());
    }

    /** Result of checking a coupon code against a booking's total. */
    private record PromoResult(BigDecimal totalPrice, BigDecimal discountAmount, String appliedCode, String warning) {
    }

    // Function 6 integration: a service-to-service call to PromotionService
    // (no promotionId FK on Booking - the schema isn't finalized, so this
    // stays a lightweight call using the code string, not a database join).
    // An invalid/expired/exhausted code does NOT block the purchase - it
    // proceeds at full price with a clear warning on the confirmation page.
    //
    // previewDiscount() is deliberately used here (not a single combined
    // "redeem" method) because it isn't @Transactional: if it throws,
    // catching it here is enough - the exception never touches this
    // method's own transaction. recordUsage() only runs once the code is
    // already confirmed valid, so it never has anything to throw either.
    private PromoResult applyPromo(String promoCode, BigDecimal originalPrice, ComboOrder order) {
        BigDecimal totalPrice = originalPrice;
        BigDecimal discountAmount = null;
        String appliedPromoCode = null;
        String promoWarning = null;
        if (promoCode != null && !promoCode.isBlank()) {
            DiscountPreviewResult preview = null;
            try {
                preview = promotionService.previewDiscount(promoCode.trim(), originalPrice, order);
            } catch (IllegalArgumentException ex) {
                promoWarning = ex.getMessage();
            }
            if (preview != null) {
                discountAmount = preview.getDiscountAmount();
                totalPrice = originalPrice.subtract(discountAmount);
                appliedPromoCode = preview.getCode();
                promotionService.recordUsage(appliedPromoCode);
            }
        }
        return new PromoResult(totalPrice, discountAmount, appliedPromoCode, promoWarning);
    }

    /** Copies the final price, coupon and add-on totals onto the booking. */
    private void applyPricing(Booking booking, PromoResult promo, PricedAddOns priced) {
        booking.setTotalPrice(promo.totalPrice());
        booking.setPromoCode(promo.appliedCode());
        booking.setDiscountAmount(promo.discountAmount());
        booking.setPromoWarning(promo.warning());
        booking.setFoodTotal(priced.foodLines().isEmpty() ? null : priced.foodTotal());
        booking.setParkingSelected(priced.parking() != null);
        booking.setParkingFee(priced.parking() != null ? priced.parkingFee() : null);
        booking.setParkingLabel(priced.parking() != null ? priced.parking().getLabel() : null);
    }

    /** Saves the food lines (price snapshot) for an already-saved booking. */
    private void saveFoodLines(Booking saved, PricedAddOns priced) {
        for (int i = 0; i < priced.foodLines().size(); i++) {
            FoodLine line = priced.foodLines().get(i);
            BookingFoodItem foodLine = new BookingFoodItem();
            foodLine.setBooking(saved);
            foodLine.setFoodItem(priced.foodItems().get(i));
            foodLine.setFoodCombo(priced.foodCombos().get(i));
            foodLine.setItemName(line.name());
            foodLine.setQuantity(line.quantity());
            foodLine.setUnitPrice(line.unitPrice());
            bookingFoodItemRepository.save(foodLine);
        }
    }

    /**
     * Simulated payment for a saved PENDING booking: only a COMPLETED payment confirms it and
     * issues the ticket; otherwise the whole transaction rolls back.
     */
    private Booking payAndConfirm(Booking saved, PaymentMethod paymentMethod, User user, Showtime showtime, int seatCount) {
        Payment payment = paymentService.processPayment(saved, paymentMethod);
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Payment could not be processed, please try again");
        }

        saved.setStatus(BookingStatus.CONFIRMED);
        saved.setExpiresAt(null);   // paid - no deadline any more
        saved = bookingRepository.save(saved);
        ticketService.issueTicket(saved);

        NotificationFactory.create(NotificationType.BOOKING_CONFIRMATION)
                .send(user, "Showtime on " + showtime.getDateTime() + ", " + seatCount + " seat(s), total Rs. " + saved.getTotalPrice());

        return saved;
    }

    // ---------------------------------------------------------------------------------------
    // Seat holds (10-minute payment window) - every payment method since 2026-10-03
    // ---------------------------------------------------------------------------------------

    /**
     * Holds the chosen seats for {@link #INSTANT_PAYMENT_WINDOW} while the customer pays (any
     * payment method - the pay step's countdown runs from this): saved as a PENDING booking with {@code expiresAt} set and no Payment
     * row yet. The seats show as taken for everyone else until it is paid
     * ({@link #completeHeldBooking}), released by the customer ({@link #releaseHold}), or
     * expired ({@link #expireIfOverdue}). Prices, coupon and add-ons are only worked out when
     * the payment is actually made, so a coupon's usage isn't counted for an abandoned hold.
     * A customer has at most one hold per showtime - any older one is released first.
     */
    @Transactional
    public Booking holdSeats(Long userId, Long showtimeId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat");
        }
        Showtime showtime = showtimeService.findApprovedById(showtimeId);
        requireNotStarted(showtime);
        User user = userService.findById(userId);
        releaseUnpaidHolds(userId, showtimeId);

        List<Seat> seats = loadAvailableSeats(showtime, seatIds, null);
        Booking hold = new Booking();
        hold.setUser(user);
        hold.setShowtime(showtime);
        hold.setStatus(BookingStatus.PENDING);
        hold.setTotalPrice(showtime.getPrice().multiply(BigDecimal.valueOf(seats.size())));
        hold.setExpiresAt(LocalDateTime.now().plus(INSTANT_PAYMENT_WINDOW));
        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(hold);
            bookingSeat.setSeat(seat);
            hold.getBookingSeats().add(bookingSeat);
        }
        Booking saved = bookingRepository.save(hold);
        log.info("Booking #{} created PENDING (seat hold) - {} seat(s), expires at {}",
                saved.getId(), seats.size(), saved.getExpiresAt());
        return saved;
    }

    /**
     * The customer changed seats on the pay step: swap the held seats, keeping the SAME deadline
     * (changing seats never buys more time).
     */
    @Transactional
    public Booking updateHoldSeats(Long holdId, Long userId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat");
        }
        Booking hold = lockActiveHold(holdId, userId);
        List<Seat> seats = loadAvailableSeats(hold.getShowtime(), seatIds, holdId);
        hold.getBookingSeats().clear();
        for (Seat seat : seats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(hold);
            bookingSeat.setSeat(seat);
            hold.getBookingSeats().add(bookingSeat);
        }
        hold.setTotalPrice(hold.getShowtime().getPrice().multiply(BigDecimal.valueOf(seats.size())));
        return bookingRepository.save(hold);
    }

    /** Throws {@link BookingExpiredException} unless the hold can still be paid for; returns it otherwise. */
    @Transactional
    public Booking requireActiveHold(Long holdId, Long userId) {
        return lockActiveHold(holdId, userId);
    }

    /** The hold, if it still belongs to this customer and can still be paid for (used to restore it after a failed security check). */
    @Transactional(readOnly = true)
    public Optional<Booking> findActiveHold(Long holdId, Long userId) {
        if (holdId == null) {
            return Optional.empty();
        }
        return bookingRepository.findById(holdId)
                .filter(b -> b.getUser().getId().equals(userId))
                .filter(this::isUnpaidHold)
                .filter(b -> !b.isPastExpiry());
    }

    /**
     * PayPal only, called just before the money is captured: makes sure the hold is still valid
     * and, if it has less than {@link #CAPTURE_GRACE} left, stretches it to that so the expiry
     * job can't release the seats while PayPal is taking the payment.
     */
    @Transactional
    public void startCapture(Long holdId, Long userId) {
        Booking hold = lockActiveHold(holdId, userId);
        LocalDateTime graceEnd = LocalDateTime.now().plus(CAPTURE_GRACE);
        if (hold.getExpiresAt().isBefore(graceEnd)) {
            hold.setExpiresAt(graceEnd);
            bookingRepository.save(hold);
        }
    }

    /**
     * Pays for a held booking (any method; PayPal only after the capture succeeded). Locks the hold
     * and re-checks its deadline on the server - the browser countdown alone is never trusted.
     * The submitted seats must match the held ones. Then it is priced (food, parking, coupon)
     * and paid exactly like {@link #createBooking}.
     */
    @Transactional
    public Booking completeHeldBooking(Long holdId, Long userId, List<Long> seatIds, PaymentMethod paymentMethod,
                                       String promoCode, BookingAddOns addOns) {
        return completeHeldBooking(holdId, userId, seatIds, paymentMethod, promoCode, addOns, false);
    }

    /**
     * Same, for every payment method. With {@code bankTransfer} the held booking is NOT paid here:
     * it stays PENDING with an AWAITING_RECEIPT payment and its deadline moves from the 10-minute
     * hold to {@link #BANK_TRANSFER_WINDOW} - the time the customer has to upload the slip. The
     * upload clears the deadline (attachBankTransferReceipt), which stops the countdown.
     */
    @Transactional
    public Booking completeHeldBooking(Long holdId, Long userId, List<Long> seatIds, PaymentMethod paymentMethod,
                                       String promoCode, BookingAddOns addOns, boolean bankTransfer) {
        BookingAddOns selection = addOns == null ? BookingAddOns.none() : addOns;
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.SIMULATED_GATEWAY;
        }
        Booking hold = lockActiveHold(holdId, userId);
        Showtime showtime = hold.getShowtime();
        requireNotStarted(showtime);

        Set<Long> heldSeatIds = new HashSet<>();
        hold.getBookingSeats().forEach(bs -> heldSeatIds.add(bs.getSeat().getId()));
        if (seatIds != null && !heldSeatIds.equals(new HashSet<>(seatIds))) {
            throw new IllegalStateException("Your seat selection changed while you were paying - please try again");
        }
        int seatCount = heldSeatIds.size();

        PricedAddOns priced = priceAddOns(showtime, selection);
        BigDecimal originalPrice = showtime.getPrice().multiply(BigDecimal.valueOf(seatCount))
                .add(priced.foodTotal()).add(priced.parkingFee());
        PromoResult promo = applyPromo(promoCode, originalPrice, orderFacts(seatCount, priced));
        applyPricing(hold, promo, priced);
        if (bankTransfer) {
            hold.setExpiresAt(LocalDateTime.now().plus(BANK_TRANSFER_WINDOW));
        }
        Booking saved = bookingRepository.save(hold);
        saveFoodLines(saved, priced);

        if (bankTransfer) {
            paymentService.initiateBankTransferPayment(saved, paymentMethod);
            log.info("Booking #{} PENDING (Bank Transfer, from seat hold) - expires at {} unless a receipt is uploaded",
                    saved.getId(), saved.getExpiresAt());
            return saved;
        }
        return payAndConfirm(saved, paymentMethod, hold.getUser(), showtime, seatCount);
    }

    /**
     * The customer left the pay step (Back, or closed the page): the unpaid hold is removed so
     * the seats are free straight away. Never touches anything that is no longer an unpaid hold.
     */
    @Transactional
    public boolean releaseHold(Long holdId, Long userId) {
        Booking hold = bookingRepository.findByIdForUpdate(holdId).orElse(null);
        if (hold == null || !hold.getUser().getId().equals(userId) || !isUnpaidHold(hold)) {
            return false;
        }
        bookingRepository.delete(hold);
        log.info("Seat hold #{} released by the customer before paying", holdId);
        return true;
    }

    /** Releases every unpaid hold this customer still has for the showtime (they are starting over). */
    @Transactional
    public void releaseUnpaidHolds(Long userId, Long showtimeId) {
        for (Long holdId : bookingRepository.findUnpaidHoldIds(userId, showtimeId)) {
            releaseHold(holdId, userId);
        }
    }

    /** PENDING, has a deadline, and no Payment row yet - i.e. an unpaid seat hold. */
    private boolean isUnpaidHold(Booking booking) {
        return booking.getStatus() == BookingStatus.PENDING
                && booking.getExpiresAt() != null
                && paymentService.findOptionalByBookingId(booking.getId()).isEmpty();
    }

    /** Locks the hold and checks it is this customer's, still unpaid and not past its deadline. */
    private Booking lockActiveHold(Long holdId, Long userId) {
        if (holdId == null) {
            throw new BookingExpiredException();
        }
        Booking hold = bookingRepository.findByIdForUpdate(holdId).orElseThrow(BookingExpiredException::new);
        if (!hold.getUser().getId().equals(userId)) {
            throw new IllegalStateException("This seat reservation belongs to another account");
        }
        if (hold.getStatus() == BookingStatus.CONFIRMED) {
            throw new IllegalStateException("This booking has already been paid");
        }
        if (!isUnpaidHold(hold) || hold.isPastExpiry()) {
            throw new BookingExpiredException();
        }
        return hold;
    }

    /** Loads the chosen seats and checks they exist, are in the hall, and aren't taken (ignoring one booking's own seats). */
    private List<Seat> loadAvailableSeats(Showtime showtime, List<Long> seatIds, Long excludingBookingId) {
        Set<Long> taken = excludingBookingId == null
                ? getBookedSeatIds(showtime.getId())
                : getBookedSeatIdsExcludingBooking(showtime.getId(), excludingBookingId);
        for (Long seatId : seatIds) {
            if (taken.contains(seatId)) {
                throw new IllegalStateException("Seat is already booked for this showtime, please pick another seat");
            }
        }
        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResourceNotFoundException("One or more selected seats do not exist");
        }
        requireSeatsInHall(showtime, seats);
        return seats;
    }

    // ---------------------------------------------------------------------------------------
    // Expiry
    // ---------------------------------------------------------------------------------------

    /**
     * Called by BookingExpiryScheduler for each overdue PENDING booking. Inside one transaction,
     * with the booking row locked, it re-checks that the booking is STILL PENDING and past its
     * deadline - so a payment that completed a moment earlier always wins and is never
     * overwritten. Then: status -> EXPIRED and the seats are released through
     * {@link #releaseSeats} (the same path a cancellation uses). No refund - nothing was paid.
     * A Bank Transfer booking that already has an uploaded receipt is never expired (the admin
     * decides it); its deadline is just cleared.
     */
    @Transactional
    public boolean expireIfOverdue(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.PENDING || !booking.isPastExpiry()) {
            return false;
        }
        Optional<Payment> payment = paymentService.findOptionalByBookingId(bookingId);
        if (payment.isPresent() && payment.get().hasReceipt()) {
            booking.setExpiresAt(null);
            bookingRepository.save(booking);
            return false;
        }

        releaseSeats(booking, BookingStatus.EXPIRED);
        if (payment.isPresent()) {
            // Bank Transfer with no receipt after the full window: close the AWAITING_RECEIPT payment.
            paymentService.closeExpiredBankTransfer(bookingId);
        }
        String kind = payment.isPresent() ? "Bank Transfer, no receipt uploaded" : "seat hold, not paid";
        log.info("Booking #{} EXPIRED ({}) - payment deadline {} passed, seats released", bookingId, kind, booking.getExpiresAt());
        NotificationFactory.create(NotificationType.BOOKING_CANCELLATION)
                .send(booking.getUser(), "Booking #" + bookingId + " expired because payment wasn't completed in time. "
                        + "Your reserved seats have been released and nothing was charged.");
        return true;
    }

    /**
     * Bank Transfer: the customer uploads (or replaces) their receipt. Locked against the expiry
     * job; refused once the 24-hour window has passed. After a successful upload the deadline is
     * cleared, so the booking now waits for the admin's decision however long that takes.
     */
    @Transactional
    public void attachBankTransferReceipt(Long bookingId, MultipartFile file) throws IOException {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        if (booking.getStatus() == BookingStatus.EXPIRED || (booking.getStatus() == BookingStatus.PENDING && booking.isPastExpiry())) {
            throw new BookingExpiredException();
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("This booking isn't awaiting a receipt");
        }
        paymentService.attachReceipt(bookingId, file);
        booking.setExpiresAt(null);
        bookingRepository.save(booking);
    }

    /**
     * Frees a booking's seats by moving it out of PENDING/CONFIRMED (BookingSeatRepository's
     * "active seats" query only counts those two statuses) and voids any ticket. Shared by
     * customer cancellation, a rejected bank-transfer receipt and expiry.
     */
    private void releaseSeats(Booking booking, BookingStatus endStatus) {
        booking.setStatus(endStatus);
        bookingRepository.save(booking);
        ticketService.voidTicketForBooking(booking.getId());
    }

    /**
     * Admin approves an uploaded bank-transfer receipt (/admin/receipts): only now does the
     * booking actually become CONFIRMED and get a real, scannable ticket - reusing the exact
     * same {@link TicketService#issueTicket} call every other payment method already goes
     * through inside {@link #createBooking}, just triggered later, from here, instead.
     */
    @Transactional
    public Booking confirmBankTransferBooking(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("This booking is not awaiting bank transfer confirmation");
        }
        paymentService.approveAwaitingReceipt(bookingId);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        Booking saved = bookingRepository.save(booking);
        ticketService.issueTicket(saved);

        NotificationFactory.create(NotificationType.BOOKING_CONFIRMATION)
                .send(saved.getUser(), "Your bank transfer receipt was approved - Showtime on "
                        + saved.getShowtime().getDateTime() + ", total Rs. " + saved.getTotalPrice());
        return saved;
    }

    /**
     * Admin rejects an uploaded bank-transfer receipt (/admin/receipts): cancels the booking
     * and frees its seats the exact same way {@link #cancelBooking} already does for a
     * customer-initiated cancellation - setting status to CANCELLED is what excludes it from
     * {@link com.cinemahub.repository.BookingSeatRepository#findActiveByShowtimeId}, so no
     * separate "seat freeing" step is needed. No payment was ever completed here, so there's
     * nothing to refund (unlike cancelBooking).
     */
    @Transactional
    public Booking rejectBankTransferBooking(Long bookingId) {
        Booking booking = findById(bookingId);
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("This booking is not awaiting bank transfer confirmation");
        }
        paymentService.rejectAwaitingReceipt(bookingId);
        releaseSeats(booking, BookingStatus.CANCELLED);   // voids the ticket too - a no-op here (none exists yet)
        Booking saved = booking;

        NotificationFactory.create(NotificationType.BOOKING_CANCELLATION)
                .send(saved.getUser(), "Your bank transfer receipt for booking #" + bookingId
                        + " was rejected, so this booking has been cancelled - please try booking again.");
        return saved;
    }

    public List<Booking> findByUser(Long userId) {
        return bookingRepository.findByUser_IdOrderByCreatedAtDesc(userId);
    }

    /** Function 6 performance tracking (US-35): total coupon discount applied to bookings so far this calendar month. */
    public BigDecimal getDiscountGivenThisMonth() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return bookingRepository.sumDiscountGivenSince(monthStart);
    }

    public Booking findById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + id));
    }

    /**
     * Update: change which physical seats are attached to an already-confirmed
     * booking (e.g. the customer wants a different row). The seat *count*
     * must stay the same as the original booking - this only reassigns which
     * seats are held, not how many, so the price already paid, the promo
     * record, and the issued ticket all stay untouched.
     */
    @Transactional
    public Booking updateSeats(Long bookingId, Long requestingUserId, List<Long> newSeatIds) {
        Booking booking = findById(bookingId);
        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new IllegalStateException("You can only edit your own bookings");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed booking can be edited");
        }
        if (!booking.getShowtime().getDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("This showtime has already started, seats can no longer be changed");
        }
        if (newSeatIds == null || newSeatIds.isEmpty()) {
            throw new IllegalArgumentException("Select at least one seat");
        }
        int originalSeatCount = booking.getBookingSeats().size();
        if (newSeatIds.size() != originalSeatCount) {
            throw new IllegalArgumentException("Select exactly " + originalSeatCount + " seat(s) to match your original booking");
        }

        List<Seat> newSeats = seatRepository.findAllById(newSeatIds);
        if (newSeats.size() != newSeatIds.size()) {
            throw new ResourceNotFoundException("One or more selected seats do not exist");
        }
        requireSeatsInHall(booking.getShowtime(), newSeats);

        // Re-check availability inside this transaction, excluding this booking's own current seats.
        Set<Long> takenByOthers = getBookedSeatIdsExcludingBooking(booking.getShowtime().getId(), bookingId);
        for (Long seatId : newSeatIds) {
            if (takenByOthers.contains(seatId)) {
                throw new IllegalStateException("Seat is already booked for this showtime, please pick another seat");
            }
        }

        booking.getBookingSeats().clear();
        for (Seat seat : newSeats) {
            BookingSeat bookingSeat = new BookingSeat();
            bookingSeat.setBooking(booking);
            bookingSeat.setSeat(seat);
            booking.getBookingSeats().add(bookingSeat);
        }

        return bookingRepository.save(booking);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long requestingUserId) {
        Booking booking = findById(bookingId);
        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new IllegalStateException("You can only cancel your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("This booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new IllegalStateException("This booking has expired - its seats were already released");
        }

        // Basic refund rule: only bookings cancelled before the showtime starts are refundable
        boolean eligibleForRefund = booking.getShowtime().getDateTime().isAfter(LocalDateTime.now());
        booking.setRefunded(eligibleForRefund);
        releaseSeats(booking, BookingStatus.CANCELLED);
        if (eligibleForRefund) {
            // Payment & Transaction Management, Update sub-function: flip the payment
            // Completed -> Refunded now that the booking behind it is cancelled.
            paymentService.markRefunded(bookingId);
        }

        NotificationFactory.create(NotificationType.BOOKING_CANCELLATION)
                .send(booking.getUser(), "Booking #" + booking.getId() + " cancelled. Refund issued: " + eligibleForRefund);
    }
}
