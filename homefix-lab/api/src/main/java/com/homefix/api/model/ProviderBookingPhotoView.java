package com.homefix.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "provider_booking_photo_views",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_provider_booking_photo_view",
                columnNames = {"booking_id", "provider_id"}
        )
)
public class ProviderBookingPhotoView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false, updatable = false)
    private Instant viewedAt;

    protected ProviderBookingPhotoView() {
    }

    public ProviderBookingPhotoView(BookingEntity booking, User provider) {
        this.booking = booking;
        this.provider = provider;
    }

    @PrePersist
    void setViewedAt() {
        if (viewedAt == null) {
            viewedAt = Instant.now();
        }
    }
}
