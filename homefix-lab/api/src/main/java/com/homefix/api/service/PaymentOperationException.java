package com.homefix.api.service;

import org.springframework.http.HttpStatus;

public class PaymentOperationException extends RuntimeException {

    private final HttpStatus status;

    public PaymentOperationException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
