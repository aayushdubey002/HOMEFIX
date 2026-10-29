package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyRazorpayPaymentRequest(
        @NotBlank @Size(max = 80)
        String razorpayOrderId,
        @NotBlank @Size(max = 80)
        String razorpayPaymentId,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}")
        String razorpaySignature
) {
}
