package com.homefix.api.repository;

import com.homefix.api.model.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {
    List<ServiceCategory> findByActiveTrueOrderByNameAsc();
    Optional<ServiceCategory> findBySlugIgnoreCase(String slug);
    Optional<ServiceCategory> findByServiceCodeIgnoreCase(String serviceCode);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsByServiceCodeIgnoreCase(String serviceCode);
}
