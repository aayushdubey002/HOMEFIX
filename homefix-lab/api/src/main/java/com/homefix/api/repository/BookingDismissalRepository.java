package com.homefix.api.repository;

import com.homefix.api.model.BookingDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingDismissalRepository
        extends JpaRepository<BookingDismissal, Long> {

    List<BookingDismissal> findByProvider_Id(Long providerId);

    boolean existsByBooking_IdAndProvider_Id(Long bookingId, Long providerId);
}
