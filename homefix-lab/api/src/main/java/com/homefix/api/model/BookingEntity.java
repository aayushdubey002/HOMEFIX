package com.homefix.api.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_customer_status", columnList = "user_id, status"),
        @Index(name = "idx_booking_provider_start", columnList = "provider_id, preferred_at, status"),
        @Index(name = "idx_booking_preferred_date", columnList = "preferred_date")
})
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String bookingId;
    private String customerName;
    private String service;
    private String providerName;
    private String status;

    private String address;
    private String preferredDate;
    @Column(name = "preferred_at")
    private LocalDateTime preferredAt;
    private String phone;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    @JsonIgnore
    private Double latitude;
    @JsonIgnore
    private Double longitude;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 2000)
    private String notes;

    @Lob
    @JsonIgnore
    @Column(name = "problem_image_data", columnDefinition = "MEDIUMBLOB")
    private byte[] problemImageData;

    @Column(name = "problem_image_content_type", length = 40)
    private String problemImageContentType;

    @JsonIgnore
    @Column(name = "completion_pin_hash", length = 100)
    private String completionPinHash;

    @JsonIgnore
    @Column(name = "completion_pin_encrypted", length = 100)
    private String completionPinEncrypted;

    @JsonIgnore
    @Column(name = "completion_pin_attempts", nullable = false)
    private int completionPinAttempts;

    @JsonIgnore
    @Column(name = "completion_pin_used_at")
    private Instant completionPinUsedAt;

    @Transient
    private String customerCompletionPin;

    @Transient
    private String paymentMethod;

    @Transient
    private String paymentStatus;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal paymentCollectedAmount;

    @Transient
    private String paymentReference;

    @Transient
    private String merchantUpiId;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String providerExperience;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String providerServiceArea;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String providerProfessionalSummary;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String providerPhone;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean problemImageAvailable;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String providerRequestIssue;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal proposedPrice;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal serviceSubtotal;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal discountAmount;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal taxAmount;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal taxableAmount;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String priceProposalExplanation;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long pendingPriceProposalId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "provider_id")
    private User provider;

    @jakarta.persistence.PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public BookingEntity() {
    }

    public BookingEntity(
            String bookingId,
            String customerName,
            String service,
            String providerName,
            String status,
            User user
    ) {
        this.bookingId = bookingId;
        this.customerName = customerName;
        this.service = service;
        this.providerName = providerName;
        this.status = status;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPreferredDate() {
        return preferredDate;
    }

    public void setPreferredDate(String preferredDate) {
        this.preferredDate = preferredDate;
    }

    public LocalDateTime getPreferredAt() {
        return preferredAt;
    }

    public void setPreferredAt(LocalDateTime preferredAt) {
        this.preferredAt = preferredAt;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setPrice(Integer price) {
        this.price = price == null ? null : BigDecimal.valueOf(price);
    }

    public BigDecimal getServiceSubtotal() {
        return serviceSubtotal;
    }

    public void setServiceSubtotal(BigDecimal serviceSubtotal) {
        this.serviceSubtotal = serviceSubtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public void setTaxableAmount(BigDecimal taxableAmount) {
        this.taxableAmount = taxableAmount;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @JsonIgnore
    public byte[] getProblemImageData() {
        return problemImageData;
    }

    public void setProblemImageData(byte[] problemImageData) {
        this.problemImageData = problemImageData;
    }

    public String getProblemImageContentType() {
        return problemImageContentType;
    }

    public void setProblemImageContentType(String problemImageContentType) {
        this.problemImageContentType = problemImageContentType;
    }

    @JsonIgnore
    public String getCompletionPinHash() {
        return completionPinHash;
    }

    public void setCompletionPinHash(String completionPinHash) {
        this.completionPinHash = completionPinHash;
    }

    @JsonIgnore
    public String getCompletionPinEncrypted() {
        return completionPinEncrypted;
    }

    public void setCompletionPinEncrypted(String completionPinEncrypted) {
        this.completionPinEncrypted = completionPinEncrypted;
    }

    @JsonIgnore
    public int getCompletionPinAttempts() {
        return completionPinAttempts;
    }

    public void incrementCompletionPinAttempts() {
        completionPinAttempts++;
    }

    public void resetCompletionPinAttempts() {
        completionPinAttempts = 0;
    }

    @JsonIgnore
    public Instant getCompletionPinUsedAt() {
        return completionPinUsedAt;
    }

    public void markCompletionPinUsed() {
        completionPinUsedAt = Instant.now();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getCompletionPin() {
        return customerCompletionPin;
    }

    public void setCompletionPin(String completionPin) {
        this.customerCompletionPin = completionPin;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public BigDecimal getPaymentCollectedAmount() {
        return paymentCollectedAmount;
    }

    public void setPaymentCollectedAmount(BigDecimal paymentCollectedAmount) {
        this.paymentCollectedAmount = paymentCollectedAmount;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getMerchantUpiId() {
        return merchantUpiId;
    }

    public void setMerchantUpiId(String merchantUpiId) {
        this.merchantUpiId = merchantUpiId;
    }

    public String getProviderExperience() {
        return providerExperience;
    }

    public void setProviderExperience(String providerExperience) {
        this.providerExperience = providerExperience;
    }

    public String getProviderServiceArea() {
        return providerServiceArea;
    }

    public void setProviderServiceArea(String providerServiceArea) {
        this.providerServiceArea = providerServiceArea;
    }

    public String getProviderProfessionalSummary() {
        return providerProfessionalSummary;
    }

    public void setProviderProfessionalSummary(String providerProfessionalSummary) {
        this.providerProfessionalSummary = providerProfessionalSummary;
    }

    public String getProviderPhone() {
        return providerPhone;
    }

    public void setProviderPhone(String providerPhone) {
        this.providerPhone = providerPhone;
    }

    public Boolean getProblemImageAvailable() {
        return problemImageAvailable;
    }

    public void setProblemImageAvailable(Boolean problemImageAvailable) {
        this.problemImageAvailable = problemImageAvailable;
    }

    public String getProviderRequestIssue() {
        return providerRequestIssue;
    }

    public void setProviderRequestIssue(String providerRequestIssue) {
        this.providerRequestIssue = providerRequestIssue;
    }

    public BigDecimal getProposedPrice() {
        return proposedPrice;
    }

    public void setProposedPrice(BigDecimal proposedPrice) {
        this.proposedPrice = proposedPrice;
    }

    public String getPriceProposalExplanation() {
        return priceProposalExplanation;
    }

    public void setPriceProposalExplanation(String priceProposalExplanation) {
        this.priceProposalExplanation = priceProposalExplanation;
    }

    public Long getPendingPriceProposalId() {
        return pendingPriceProposalId;
    }

    public void setPendingPriceProposalId(Long pendingPriceProposalId) {
        this.pendingPriceProposalId = pendingPriceProposalId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getProvider() {
        return provider;
    }

    public void setProvider(User provider) {
        this.provider = provider;
    }
}