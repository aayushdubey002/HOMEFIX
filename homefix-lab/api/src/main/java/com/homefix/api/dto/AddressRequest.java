package com.homefix.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 500) String streetAddress,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        Double latitude,
        Double longitude,
        boolean primaryAddress
) {
}
