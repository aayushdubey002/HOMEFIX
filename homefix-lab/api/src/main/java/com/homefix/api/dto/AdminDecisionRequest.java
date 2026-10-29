package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminDecisionRequest(
        @NotBlank String status,
        @Size(max = 1000) String note
) {
}
