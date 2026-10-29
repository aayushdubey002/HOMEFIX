package com.homefix.api.repository;

import com.homefix.api.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUser_IdOrderByPrimaryAddressDescCreatedAtDesc(Long userId);
    Optional<Address> findByIdAndUser_Id(Long id, Long userId);
    long countByUser_Id(Long userId);
}
