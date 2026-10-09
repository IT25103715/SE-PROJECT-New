package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    List<FoodItem> findAllByOrderByCategoryAscNameAsc();

    List<FoodItem> findByApprovalStatus(ApprovalStatus approvalStatus);

    List<FoodItem> findByAvailableTrueAndApprovalStatusOrderByCategoryAscNameAsc(ApprovalStatus approvalStatus);
}
