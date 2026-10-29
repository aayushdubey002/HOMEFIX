package com.homefix.api.repository;

import com.homefix.api.model.BookingPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingPriceHistoryRepository extends JpaRepository<BookingPriceHistory, Long> {
    List<BookingPriceHistory> findByBooking_IdOrderByCreatedAtAsc(Long bookingId);
    Optional<BookingPriceHistory> findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(
            Long bookingId, String status);
    Optional<BookingPriceHistory> findByIdAndBooking_Id(Long id, Long bookingId);
    boolean existsByBooking_IdAndStatus(Long bookingId, String status);
}
