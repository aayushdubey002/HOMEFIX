package com.homefix.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_webhook_event_id",
                columnNames = "gateway_event_id"),
        indexes = @Index(name = "idx_payment_webhook_received", columnList = "received_at"))
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway_event_id", nullable = false, length = 100)
    private String gatewayEventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    protected PaymentWebhookEvent() {
    }

    public PaymentWebhookEvent(String gatewayEventId, String eventType, String payloadSha256) {
        this.gatewayEventId = gatewayEventId;
        this.eventType = eventType;
        this.payloadSha256 = payloadSha256;
    }

    public Long getId() {
        return id;
    }

    public String getGatewayEventId() {
        return gatewayEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadSha256() {
        return payloadSha256;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
