package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.ParkingOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParkingOptionRepository extends JpaRepository<ParkingOption, Long> {

    List<ParkingOption> findAllByOrderByIdAsc();

    List<ParkingOption> findByApprovalStatus(ApprovalStatus approvalStatus);

    List<ParkingOption> findByAvailableTrueAndApprovalStatusOrderByIdAsc(ApprovalStatus approvalStatus);
}
