package com.homefix.api.repository;

import com.homefix.api.model.ProviderVerificationDocument;
import com.homefix.api.model.ProviderVerificationDocumentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderVerificationDocumentRepository
        extends JpaRepository<ProviderVerificationDocument, Long> {
    List<ProviderVerificationDocument> findByProvider_User_IdOrderByUploadedAtDescIdDesc(Long userId);

    List<ProviderVerificationDocument> findAllByOrderByUploadedAtDescIdDesc();

    Optional<ProviderVerificationDocument> findFirstByProvider_IdAndDocumentTypeOrderByUploadedAtDescIdDesc(
            Long providerId, ProviderVerificationDocumentType documentType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from ProviderVerificationDocument d
            where d.provider.id = :providerId and d.documentType = :documentType
            order by d.uploadedAt desc, d.id desc
            """)
    List<ProviderVerificationDocument> findLatestByProviderAndTypeForUpdate(
            @Param("providerId") Long providerId,
            @Param("documentType") ProviderVerificationDocumentType documentType);
}