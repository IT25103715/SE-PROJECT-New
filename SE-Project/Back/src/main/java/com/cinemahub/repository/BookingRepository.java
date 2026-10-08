package com.cinemahub.repository;

import com.cinemahub.model.Booking;
import com.cinemahub.model.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /** Parking slots already taken for a showtime: bookings in the given status that added parking. */
    /** Membership program: how many bookings a customer has in a status (MembershipService counts CONFIRMED ones). */
    long countByUser_IdAndStatus(Long userId, BookingStatus status);

    long countByShowtime_IdAndStatusAndParkingSelectedTrue(Long showtimeId, BookingStatus status);

    /** Function 6 performance tracking (US-35): total coupon discount handed out since a given point in time. */
    @Query("SELECT COALESCE(SUM(b.discountAmount), 0) FROM Booking b WHERE b.discountAmount IS NOT NULL AND b.createdAt >= :since")
    BigDecimal sumDiscountGivenSince(@Param("since") LocalDateTime since);

    /**
     * Loads one booking with a database row lock (SELECT ... FOR UPDATE) held until the
     * transaction ends. Used by everything that moves a PENDING booking on (completing a
     * payment, expiring it, uploading a bank-transfer receipt) so two of them can never run
     * on the same booking at the same time - whichever gets the lock first wins, and the
     * other then sees the new status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    /** PENDING bookings whose payment deadline has passed - picked up by BookingExpiryScheduler. */
    @Query("SELECT b.id FROM Booking b WHERE b.status = com.cinemahub.model.BookingStatus.PENDING "
            + "AND b.expiresAt IS NOT NULL AND b.expiresAt < :now ORDER BY b.expiresAt")
    List<Long> findOverduePendingIds(@Param("now") LocalDateTime now);

    /**
     * A customer's unpaid Apple Pay / PayPal seat holds for one showtime: PENDING, with a
     * deadline, and no Payment row yet (a Bank Transfer booking always has one).
     */
    @Query("SELECT b.id FROM Booking b WHERE b.user.id = :userId AND b.showtime.id = :showtimeId "
            + "AND b.status = com.cinemahub.model.BookingStatus.PENDING AND b.expiresAt IS NOT NULL "
            + "AND NOT EXISTS (SELECT p.id FROM Payment p WHERE p.booking = b)")
    List<Long> findUnpaidHoldIds(@Param("userId") Long userId, @Param("showtimeId") Long showtimeId);
}
