package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportResolutionRequest(
        @NotBlank String status,
        @Size(max = 3000) String resolution
) {
}
