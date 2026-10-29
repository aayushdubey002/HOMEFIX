package com.homefix.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "booking_status_history",
        indexes = @Index(name = "idx_booking_status_history_booking_time",
                columnList = "booking_id, changed_at"))
public class BookingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "previous_status", length = 24)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 24)
    private String newStatus;

    @Column(length = 500)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt = Instant.now();

    public BookingStatusHistory() {
    }

    public BookingStatusHistory(BookingEntity booking, User actor, String previousStatus,
                                String newStatus, String note) {
        this.booking = booking;
        this.actor = actor;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public BookingEntity getBooking() {
        return booking;
    }

    public User getActor() {
        return actor;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
