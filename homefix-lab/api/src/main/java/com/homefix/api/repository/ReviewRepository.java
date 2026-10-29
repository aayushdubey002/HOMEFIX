package com.homefix.api.repository;

import com.homefix.api.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProvider_IdAndVisibleTrueOrderByCreatedAtDesc(Long providerId);
    List<Review> findAllByOrderByCreatedAtDesc();
    boolean existsByBooking_IdAndCustomer_Id(Long bookingId, Long customerId);
    long countByVisibleTrue();
}
