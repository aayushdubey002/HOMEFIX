package com.homefix.api.repository;

import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    List<BookingEntity> findByUserOrderByIdDesc(User user);

    long countByUser(User user);

    long countByUserAndStatus(User user, String status);

    Optional<BookingEntity> findByBookingId(String bookingId);
}