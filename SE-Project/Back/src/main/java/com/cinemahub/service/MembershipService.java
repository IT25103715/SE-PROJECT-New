package com.cinemahub.service;

import com.cinemahub.model.BookingStatus;
import com.cinemahub.model.User;
import com.cinemahub.repository.BookingRepository;
import com.cinemahub.repository.UserRepository;
import com.cinemahub.service.notification.NotificationFactory;
import com.cinemahub.service.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * Membership program for regular customers. A customer becomes a member automatically once they
 * have {@link #BOOKINGS_FOR_MEMBERSHIP} CONFIRMED bookings; members can use "Members Only"
 * promotions (PromotionService). The threshold lives only here - the account page and the
 * promotion messages all read it from this constant.
 */
@Service
public class MembershipService {

    /** Confirmed bookings needed to become a member. Change it here and everything follows. */
    public static final int BOOKINGS_FOR_MEMBERSHIP = 5;

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public MembershipService(BookingRepository bookingRepository, UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    /** For templates: the threshold, e.g. "book 5 times". */
    public int getBookingsForMembership() {
        return BOOKINGS_FOR_MEMBERSHIP;
    }

    public long countConfirmedBookings(Long userId) {
        return bookingRepository.countByUser_IdAndStatus(userId, BookingStatus.CONFIRMED);
    }

    /** How many more confirmed bookings this customer needs (0 once they are a member). */
    public long bookingsUntilMembership(User user) {
        if (user.isMember()) {
            return 0;
        }
        return Math.max(0, BOOKINGS_FOR_MEMBERSHIP - countConfirmedBookings(user.getId()));
    }

    /**
     * Runs after a booking has become CONFIRMED (card / Apple Pay / PayPal payment, or an admin
     * approving a bank-transfer receipt) - see BookingStatusListener - once that transaction has
     * committed, so a booking that ends up rolled back never counts. Uses its own transaction.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        userRepository.findById(event.userId()).ifPresent(this::recordConfirmedBooking);
    }

    /**
     * Checks a customer after a confirmed booking (and on their account page, as a catch-up for
     * customers who already had enough bookings before membership existed). If they have reached
     * the threshold and aren't a member yet, they become one and get a MEMBERSHIP_UPGRADE
     * notification (same Notification/Factory pattern as the booking confirmation).
     *
     * @return true if this call upgraded the customer
     */
    @Transactional
    public boolean recordConfirmedBooking(User customer) {
        User user = userRepository.findById(customer.getId()).orElse(null);
        if (user == null || user.isMember()) {
            return false;
        }
        long confirmed = countConfirmedBookings(user.getId());
        if (confirmed < BOOKINGS_FOR_MEMBERSHIP) {
            return false;
        }
        user.setMember(true);
        user.setMemberSince(LocalDateTime.now());
        userRepository.save(user);
        log.info("User #{} became a member after {} confirmed bookings", user.getId(), confirmed);

        NotificationFactory.create(NotificationType.MEMBERSHIP_UPGRADE)
                .send(user, "You've made " + confirmed + " bookings with CinemaX Lanka, so you're now a member! "
                        + "Members Only promotions on the homepage are now unlocked for you.");
        return true;
    }

    /**
     * Whether the customer making the current request is a member - used when a promo code is
     * checked during checkout, which always runs in the signed-in customer's own request.
     * Signed out (or no request, e.g. a background job) = not a member.
     */
    public boolean currentUserIsMember() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        return userRepository.findByEmail(auth.getName()).map(User::isMember).orElse(false);
    }
}
