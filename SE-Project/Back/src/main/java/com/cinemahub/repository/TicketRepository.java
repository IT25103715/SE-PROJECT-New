package com.cinemahub.repository;

import com.cinemahub.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByQrCodeData(String qrCodeData);

    Optional<Ticket> findByBooking_Id(Long bookingId);
}
