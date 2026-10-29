package com.homefix.api.repository;

import com.homefix.api.model.ProviderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface ProviderAvailabilityRepository extends JpaRepository<ProviderAvailability, Long> {
    List<ProviderAvailability> findByProvider_IdAndDayOfWeekAndEnabledTrue(
            Long providerId, DayOfWeek dayOfWeek);
    List<ProviderAvailability> findByProvider_IdOrderByDayOfWeekAscStartTimeAsc(Long providerId);

    void deleteByProvider_Id(Long providerId);
}
