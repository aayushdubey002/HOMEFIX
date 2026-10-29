package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PriceProposalDecisionRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED")
        String decision
) {
}
