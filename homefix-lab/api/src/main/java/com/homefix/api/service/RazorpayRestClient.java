package com.homefix.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class RazorpayRestClient implements RazorpayGatewayClient {

    private final RestClient client = RestClient.builder()
            .baseUrl("https://api.razorpay.com/v1")
            .build();

    @Override
    public JsonNode createOrder(String keyId, String keySecret, Map<String, Object> request) {
        return client.post()
                .uri("/orders")
                .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    public JsonNode fetchPayment(String keyId, String keySecret, String paymentId) {
        return client.get()
                .uri("/payments/{paymentId}", paymentId)
                .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    public JsonNode createRefund(
            String keyId, String keySecret, String paymentId, Map<String, Object> request) {
        return client.post()
                .uri("/payments/{paymentId}/refund", paymentId)
                .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }
}
