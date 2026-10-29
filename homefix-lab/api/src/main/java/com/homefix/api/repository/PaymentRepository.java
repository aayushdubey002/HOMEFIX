package com.homefix.api.repository;

import com.homefix.api.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByBooking_Id(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.booking.id = :bookingId")
    Optional<Payment> findByBooking_IdForUpdate(@Param("bookingId") Long bookingId);

    List<Payment> findAllByOrderByUpdatedAtDesc();
    long countByStatusIgnoreCase(String status);
}
