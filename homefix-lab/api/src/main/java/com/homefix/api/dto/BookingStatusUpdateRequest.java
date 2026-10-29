package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BookingStatusUpdateRequest(
        @NotBlank String status,
        @Size(max = 500) String note,
        @Size(max = 120) String paymentReference,
        @Size(max = 6) String completionPin,
        @Pattern(regexp = "(?i)CASH|UPI", message = "Payment method must be CASH or UPI.")
        String paymentMethod,
        @DecimalMin(value = "0.01", message = "Cash received must be greater than zero.")
        @Digits(integer = 8, fraction = 2, message = "Cash received supports up to two decimal places.")
        BigDecimal cashReceivedAmount
) {
    public BookingStatusUpdateRequest(String status, String note) {
        this(status, note, null, null, null, null);
    }

    public BookingStatusUpdateRequest(String status, String note, String paymentReference) {
        this(status, note, paymentReference, null, null, null);
    }

    public BookingStatusUpdateRequest(
            String status, String note, String paymentReference, String completionPin) {
        this(status, note, paymentReference, completionPin, null, null);
    }
}
