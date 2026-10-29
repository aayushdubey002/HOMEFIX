package com.homefix.api.dto;

import java.math.BigDecimal;

public record ServiceCatalogItem(
        String slug,
        String name,
        String service,
        String description,
        BigDecimal startingPrice,
        String imageKey,
        String imageUrl
) {
    public ServiceCatalogItem(
            String slug,
            String name,
            String service,
            String description,
            BigDecimal startingPrice) {
        this(slug, name, service, description, startingPrice, null, null);
    }

    public ServiceCatalogItem(
            String slug,
            String name,
            String service,
            String description,
            BigDecimal startingPrice,
            String imageKey) {
        this(slug, name, service, description, startingPrice, imageKey, null);
    }
}
