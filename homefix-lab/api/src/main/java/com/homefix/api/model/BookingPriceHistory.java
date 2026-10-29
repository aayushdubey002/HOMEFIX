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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "booking_price_history", indexes = {
        @Index(name = "idx_price_history_booking_time", columnList = "booking_id, created_at"),
        @Index(name = "idx_price_history_booking_status", columnList = "booking_id, status")
})
public class BookingPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private BookingEntity booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "experience_tier", length = 32)
    private String experienceTier;

    @Column(name = "experience_multiplier", nullable = false, precision = 5, scale = 2)
    private BigDecimal experienceMultiplier = BigDecimal.ONE;

    @Column(name = "weekend_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal weekendSurcharge = BigDecimal.ZERO;

    @Column(name = "urgent_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal urgentSurcharge = BigDecimal.ZERO;

    @Column(name = "late_night_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal lateNightSurcharge = BigDecimal.ZERO;

    @Column(name = "additional_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal additionalAmount = BigDecimal.ZERO;

    @Column(name = "proposed_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal proposedTotal;

    @Column(name = "service_subtotal", precision = 10, scale = 2)
    private BigDecimal serviceSubtotal;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "taxable_amount", precision = 10, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "discount_rule", length = 100)
    private String discountRule;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(length = 500)
    private String explanation;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;

    public Long getId() {
        return id;
    }

    public BookingEntity getBooking() {
        return booking;
    }

    public void setBooking(BookingEntity booking) {
        this.booking = booking;
    }

    public User getActor() {
        return actor;
    }

    public void setActor(User actor) {
        this.actor = actor;
    }

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public String getExperienceTier() {
        return experienceTier;
    }

    public void setExperienceTier(String experienceTier) {
        this.experienceTier = experienceTier;
    }

    public BigDecimal getExperienceMultiplier() {
        return experienceMultiplier;
    }

    public void setExperienceMultiplier(BigDecimal experienceMultiplier) {
        this.experienceMultiplier = experienceMultiplier;
    }

    public BigDecimal getWeekendSurcharge() {
        return weekendSurcharge;
    }

    public void setWeekendSurcharge(BigDecimal weekendSurcharge) {
        this.weekendSurcharge = weekendSurcharge;
    }

    public BigDecimal getUrgentSurcharge() {
        return urgentSurcharge;
    }

    public void setUrgentSurcharge(BigDecimal urgentSurcharge) {
        this.urgentSurcharge = urgentSurcharge;
    }

    public BigDecimal getLateNightSurcharge() {
        return lateNightSurcharge;
    }

    public void setLateNightSurcharge(BigDecimal lateNightSurcharge) {
        this.lateNightSurcharge = lateNightSurcharge;
    }

    public BigDecimal getAdditionalAmount() {
        return additionalAmount;
    }

    public void setAdditionalAmount(BigDecimal additionalAmount) {
        this.additionalAmount = additionalAmount;
    }

    public BigDecimal getProposedTotal() {
        return proposedTotal;
    }

    public void setProposedTotal(BigDecimal proposedTotal) {
        this.proposedTotal = proposedTotal;
    }

    public BigDecimal getServiceSubtotal() {
        return serviceSubtotal == null ? proposedTotal : serviceSubtotal;
    }

    public void setServiceSubtotal(BigDecimal serviceSubtotal) {
        this.serviceSubtotal = serviceSubtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount == null ? BigDecimal.ZERO : discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount == null ? proposedTotal : taxableAmount;
    }

    public void setTaxableAmount(BigDecimal taxableAmount) {
        this.taxableAmount = taxableAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount == null ? BigDecimal.ZERO : taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public String getDiscountRule() {
        return discountRule == null ? "NONE" : discountRule;
    }

    public void setDiscountRule(String discountRule) {
        this.discountRule = discountRule;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        if ("APPROVED".equals(status) || "REJECTED".equals(status)) {
            resolvedAt = Instant.now();
        }
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
