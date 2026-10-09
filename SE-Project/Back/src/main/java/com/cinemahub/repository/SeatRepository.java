package com.cinemahub.repository;

import com.cinemahub.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByCinemaHall_IdOrderByRowLabelAscSeatNumberAsc(Long cinemaHallId);
}
