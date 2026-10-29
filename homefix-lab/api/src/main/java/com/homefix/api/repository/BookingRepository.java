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
import java.time.LocalDateTime;
import java.math.BigDecimal;

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

    long countByProviderAndStatusIgnoreCase(User provider, String status);

    // Customer: total bookings
    long countByUser(User user);

    // Customer: pending bookings count
    long countByUserAndStatusIgnoreCase(User user, String status);

    // Search by booking ID
    Optional<BookingEntity> findByBookingId(String bookingId);

    List<BookingEntity> findAllByOrderByCreatedAtDesc();

    long countByStatusIgnoreCase(String status);

    @Query("SELECT COALESCE(SUM(b.price), 0) FROM BookingEntity b "
            + "WHERE UPPER(b.status) = 'COMPLETED'")
    BigDecimal sumCompletedBookingPrices();

    @Query("SELECT COALESCE(SUM(b.price), 0) FROM BookingEntity b "
            + "WHERE b.provider.id = :providerId AND UPPER(b.status) = 'COMPLETED'")
    BigDecimal sumProviderCompletedBookingPrices(@Param("providerId") Long providerId);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
            FROM BookingEntity b
            WHERE b.provider.id = :providerId
              AND b.preferredAt < :requestedEnd
              AND b.preferredAt > :requestedStartMinusDuration
              AND UPPER(b.status) NOT IN ('CANCELLED', 'REJECTED')
            """)
    boolean hasOverlappingAppointment(
            @Param("providerId") Long providerId,
            @Param("requestedEnd") LocalDateTime requestedEnd,
            @Param("requestedStartMinusDuration") LocalDateTime requestedStartMinusDuration);
}