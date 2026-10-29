package com.homefix.api.repository;

import com.homefix.api.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();
    long countByStatusIgnoreCase(String status);
}
