package com.homefix.api.repository;

import com.homefix.api.model.ProviderProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, Long> {
    Optional<ProviderProfile> findByUser_Id(Long userId);
    List<ProviderProfile> findByVerificationStatusOrderByCreatedAtAsc(String verificationStatus);
    long countByVerificationStatusIgnoreCase(String verificationStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProviderProfile p WHERE p.user.id = :userId")
    Optional<ProviderProfile> findByUserIdForUpdate(@Param("userId") Long userId);
}
