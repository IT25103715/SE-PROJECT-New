package com.cinemahub.repository;

import com.cinemahub.model.Payment;
import com.cinemahub.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBooking_Id(Long bookingId);

    /** Admin's receipt-review queue at /admin/receipts - oldest submission first, like the approval queue. */
    List<Payment> findByStatusOrderByTransactionDateAsc(PaymentStatus status);

    /** A customer's own payment history, most recent first. */
    List<Payment> findByBooking_User_IdOrderByTransactionDateDesc(Long userId);

    /** The manager/admin transaction log - archived rows are hidden by default. */
    List<Payment> findByArchivedFalseOrderByTransactionDateDesc();

    List<Payment> findAllByOrderByTransactionDateDesc();

    /**
     * Revenue totals grouped "branch-wise". Done as a database-side
     * aggregate (rather than loading every Payment and walking
     * booking -> showtime -> cinemaHall in Java) both for efficiency and to
     * avoid touching lazy associations once the request's transaction/session
     * has already closed (spring.jpa.open-in-view=false). Each row is
     * {@code [cinemaHallName, totalAmount]}; only COMPLETED, non-archived
     * payments count towards revenue. See PaymentService#getRevenueByBranch
     * for why CinemaHall stands in for "branch" here.
     */
    @Query("SELECT p.booking.showtime.cinemaHall.name, SUM(p.amount) " +
           "FROM Payment p " +
           "WHERE p.status = com.cinemahub.model.PaymentStatus.COMPLETED AND p.archived = false " +
           "GROUP BY p.booking.showtime.cinemaHall.name " +
           "ORDER BY p.booking.showtime.cinemaHall.name")
    List<Object[]> sumRevenueByBranch();
}
