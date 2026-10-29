package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookingMessageRequest(
        @NotBlank(message = "Message cannot be empty.")
        @Size(max = 2000, message = "Message must be 2000 characters or fewer.")
        String message
) {
}
