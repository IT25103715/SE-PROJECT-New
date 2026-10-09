package com.cinemahub.repository;

import com.cinemahub.model.ApprovalStatus;
import com.cinemahub.model.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Promotion> findAllByOrderByCreatedAtDesc();

    List<Promotion> findByApprovalStatus(ApprovalStatus approvalStatus);
}
