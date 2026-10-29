package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportTicketRequest(
        Long bookingId,
        @NotBlank @Size(max = 120) String subject,
        @NotBlank @Size(max = 3000) String description
) {
}
