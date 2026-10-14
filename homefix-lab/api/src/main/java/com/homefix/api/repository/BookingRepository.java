package com.homefix.api.repository;

import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.User;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    // Customer: uski apni bookings
    List<BookingEntity> findByUserOrderByIdDesc(User user);

    // Provider: usko assign hui bookings
    List<BookingEntity> findByProviderOrderByIdDesc(User provider);

    // Provider: unassigned pending requests
    List<BookingEntity> findByProviderIsNullAndStatusIgnoreCaseOrderByIdDesc(
            String status
    );

    // Lock booking row while a provider accepts it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BookingEntity b WHERE b.id = :id")
    Optional<BookingEntity> findByIdForUpdate(@Param("id") Long id);

    // Provider: assigned bookings count
    long countByProvider(User provider);

    // Customer: total bookings
    long countByUser(User user);

    // Customer: pending bookings count
    long countByUserAndStatusIgnoreCase(User user, String status);

    // Search by booking ID
    Optional<BookingEntity> findByBookingId(String bookingId);
}