package com.homefix.api.repository;

import com.homefix.api.model.HomeService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HomeServiceRepository extends JpaRepository<HomeService, Long> {

    long countByStatusIgnoreCase(String status);
}