package com.cinemahub.repository;

import com.cinemahub.model.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    /**
     * All seats currently held (PENDING or CONFIRMED) for a showtime - used
     * both to render the seat map and to guard against double-booking.
     * A PENDING booking whose payment deadline (expiresAt) has passed no longer holds its
     * seats, even in the minute or so before BookingExpiryScheduler marks it EXPIRED.
     */
    default List<BookingSeat> findActiveByShowtimeId(Long showtimeId) {
        return findActiveByShowtimeIdAt(showtimeId, LocalDateTime.now());
    }

    /** Same as {@link #findActiveByShowtimeId}, but leaves out one booking's own seats - used when editing that booking's seats. */
    default List<BookingSeat> findActiveByShowtimeIdExcludingBooking(Long showtimeId, Long excludingBookingId) {
        return findActiveByShowtimeIdExcludingBookingAt(showtimeId, excludingBookingId, LocalDateTime.now());
    }

    @Query("select bs from BookingSeat bs " +
            "where bs.booking.showtime.id = :showtimeId " +
            "and bs.booking.status in ('PENDING', 'CONFIRMED') " +
            "and (bs.booking.expiresAt is null or bs.booking.expiresAt > :now)")
    List<BookingSeat> findActiveByShowtimeIdAt(@Param("showtimeId") Long showtimeId, @Param("now") LocalDateTime now);

    @Query("select bs from BookingSeat bs " +
            "where bs.booking.showtime.id = :showtimeId " +
            "and bs.booking.status in ('PENDING', 'CONFIRMED') " +
            "and (bs.booking.expiresAt is null or bs.booking.expiresAt > :now) " +
            "and bs.booking.id <> :excludingBookingId")
    List<BookingSeat> findActiveByShowtimeIdExcludingBookingAt(@Param("showtimeId") Long showtimeId,
                                                               @Param("excludingBookingId") Long excludingBookingId,
                                                               @Param("now") LocalDateTime now);

    List<BookingSeat> findByBooking_Id(Long bookingId);
}
