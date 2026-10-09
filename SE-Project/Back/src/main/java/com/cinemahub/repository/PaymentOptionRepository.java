package com.cinemahub.repository;

import com.cinemahub.model.PaymentOption;
import com.cinemahub.model.PaymentOptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentOptionRepository extends JpaRepository<PaymentOption, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<PaymentOption> findAllByOrderByDateAddedDesc();

    List<PaymentOption> findByStatus(PaymentOptionStatus status);
}
