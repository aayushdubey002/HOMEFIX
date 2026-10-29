package com.homefix.api.dto;

import java.math.BigDecimal;

public record ProviderMatchResponse(
        Long providerId,
        String providerName,
        String serviceCategory,
        String experience,
        String serviceArea,
        BigDecimal startingPrice,
        double distanceKm
) {
}
