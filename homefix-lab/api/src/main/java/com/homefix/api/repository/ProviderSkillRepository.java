package com.homefix.api.repository;

import com.homefix.api.model.ProviderSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderSkillRepository extends JpaRepository<ProviderSkill, Long> {
    List<ProviderSkill> findByCategory_Id(Long categoryId);
    List<ProviderSkill> findByCategory_IdAndProvider_VerificationStatusIgnoreCaseAndProvider_OnlineTrue(
            Long categoryId, String verificationStatus);
    List<ProviderSkill> findByProvider_Id(Long providerId);
}
