package com.homefix.api.repository;

import com.homefix.api.model.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {
    List<BookingStatusHistory> findByBooking_IdOrderByChangedAtAsc(Long bookingId);
}
