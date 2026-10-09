package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    List<Showtime> findByMovie_IdOrderByDateTimeAsc(Long movieId);

    List<Showtime> findByMovie_IdAndApprovalStatusOrderByDateTimeAsc(Long movieId, ApprovalStatus approvalStatus);

    List<Showtime> findAllByOrderByDateTimeAsc();

    List<Showtime> findByApprovalStatusOrderByDateTimeAsc(ApprovalStatus approvalStatus);

    List<Showtime> findByApprovalStatus(ApprovalStatus approvalStatus);

    List<Showtime> findByCinemaHall_Id(Long cinemaHallId);
}
