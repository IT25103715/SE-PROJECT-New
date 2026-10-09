package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.CinemaHall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CinemaHallRepository extends JpaRepository<CinemaHall, Long> {

    List<CinemaHall> findByApprovalStatus(ApprovalStatus approvalStatus);
}
