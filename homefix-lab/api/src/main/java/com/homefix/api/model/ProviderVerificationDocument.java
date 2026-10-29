package com.homefix.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "provider_verification_documents")
public class ProviderVerificationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderProfile provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProviderVerificationDocumentType documentType;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 80, unique = true)
    private String storageKey;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 24)
    private String status = "PENDING";

    @Column(length = 1000)
    private String reviewNote;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    protected ProviderVerificationDocument() {
    }

    public ProviderVerificationDocument(
            ProviderProfile provider,
            ProviderVerificationDocumentType documentType,
            String originalFilename,
            String storageKey,
            String contentType,
            long sizeBytes) {
        this.provider = provider;
        this.documentType = documentType;
        this.originalFilename = originalFilename;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public ProviderProfile getProvider() {
        return provider;
    }

    public ProviderVerificationDocumentType getDocumentType() {
        return documentType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.reviewedAt = Instant.now();
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}