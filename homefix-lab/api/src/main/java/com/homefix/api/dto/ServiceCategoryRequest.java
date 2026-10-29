package com.homefix.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ServiceCategoryRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 80) String slug,
        @NotBlank @Size(max = 40) String serviceCode,
        @Size(max = 500) String description,
        @Size(max = 40) String imageKey,
        @Size(max = 255) String imageUrl,
        @DecimalMin("0.00") BigDecimal startingPrice,
        boolean active
) {
}
