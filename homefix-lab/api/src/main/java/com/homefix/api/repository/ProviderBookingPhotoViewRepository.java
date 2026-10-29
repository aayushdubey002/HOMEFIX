package com.homefix.api.repository;

import com.homefix.api.model.ProviderBookingPhotoView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderBookingPhotoViewRepository
        extends JpaRepository<ProviderBookingPhotoView, Long> {

    boolean existsByBooking_IdAndProvider_Id(Long bookingId, Long providerId);
}
