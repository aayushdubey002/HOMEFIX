package com.homefix.api.dto;

public record HomeServiceResponse(
        Long id,
        String name,
        String category,
        double price,
        String location,
        String availability,
        double rating,
        String providerName,
        String status
) {
}
