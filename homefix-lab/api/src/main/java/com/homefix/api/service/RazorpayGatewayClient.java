package com.homefix.api.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface RazorpayGatewayClient {
    JsonNode createOrder(String keyId, String keySecret, Map<String, Object> request);

    JsonNode fetchPayment(String keyId, String keySecret, String paymentId);

    JsonNode createRefund(
            String keyId, String keySecret, String paymentId, Map<String, Object> request);
}
