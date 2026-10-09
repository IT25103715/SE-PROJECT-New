package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.FoodCombo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodComboRepository extends JpaRepository<FoodCombo, Long> {

    List<FoodCombo> findAllByOrderByNameAsc();

    List<FoodCombo> findByApprovalStatus(ApprovalStatus approvalStatus);

    List<FoodCombo> findByAvailableTrueAndApprovalStatusOrderByNameAsc(ApprovalStatus approvalStatus);
}
