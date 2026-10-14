package com.homefix.api.dto;

public record ServiceCatalogItem(
        String slug,
        String name,
        String service,
        String description,
        int startingPrice
) {
}
