package com.homefix.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record ProviderProfileUpdateRequest(
        @Size(max = 2000) String professionalSummary,
        @DecimalMin("1.0") @DecimalMax("200.0") Double serviceRadiusKm,
        @Size(max = 40) String serviceCategory,
        @Size(max = 40) String experience,
        @Size(max = 100) String serviceArea,
        Boolean online
) {
}
