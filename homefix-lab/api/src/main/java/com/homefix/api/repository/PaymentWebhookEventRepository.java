package com.homefix.api.repository;

import com.homefix.api.model.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByGatewayEventId(String gatewayEventId);
}
