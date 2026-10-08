package com.cinemahub.repository;

import com.cinemahub.model.BookingFoodItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingFoodItemRepository extends JpaRepository<BookingFoodItem, Long> {

    List<BookingFoodItem> findByBooking_IdOrderByIdAsc(Long bookingId);
}
