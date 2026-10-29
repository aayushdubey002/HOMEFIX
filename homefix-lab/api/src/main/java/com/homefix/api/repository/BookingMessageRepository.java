package com.homefix.api.repository;

import com.homefix.api.model.BookingMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingMessageRepository extends JpaRepository<BookingMessage, Long> {
    List<BookingMessage> findByBooking_IdOrderBySentAtAscIdAsc(Long bookingId);
}
