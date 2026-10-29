package com.homefix.api.repository;

import com.homefix.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    long countByRoleIgnoreCase(String role);

    List<User> findAllByOrderByCreatedAtDesc();

    long countByRoleIgnoreCaseAndActiveTrue(String role);

    long countByRoleIgnoreCaseAndProviderApprovedTrueAndActiveTrue(String role);
}