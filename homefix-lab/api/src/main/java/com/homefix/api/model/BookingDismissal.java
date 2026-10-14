package com.homefix.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "booking_dismissals",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booking_dismissal_booking_provider",
                columnNames = {"booking_id", "provider_id"}
        )
)
public class BookingDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @ManyToOne(optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    protected BookingDismissal() {
    }

    public BookingDismissal(BookingEntity booking, User provider) {
        this.booking = booking;
        this.provider = provider;
    }

    public Long getId() {
        return id;
    }

    public BookingEntity getBooking() {
        return booking;
    }

    public User getProvider() {
        return provider;
    }
}
