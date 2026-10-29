package com.homefix.api.controller;

import com.homefix.api.service.PaymentConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentConfigurationController {

    private final PaymentConfigurationService paymentConfigurationService;

    public PaymentConfigurationController(PaymentConfigurationService paymentConfigurationService) {
        this.paymentConfigurationService = paymentConfigurationService;
    }

    @GetMapping("/config")
    public Map<String, Object> configuration() {
        return paymentConfigurationService.publicConfiguration();
    }
}