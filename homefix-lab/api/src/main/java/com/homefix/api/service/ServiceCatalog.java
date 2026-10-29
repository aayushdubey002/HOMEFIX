package com.homefix.api.service;

import com.homefix.api.dto.ServiceCatalogItem;

import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;

public final class ServiceCatalog {

    private static final List<ServiceCatalogItem> ITEMS = List.of(
            new ServiceCatalogItem(
                    "electrician",
                    "Electrician",
                    "ELECTRICAL",
                    "Safe help with wiring, lighting, switches, and electrical repairs.",
                    BigDecimal.valueOf(500),
                    "electrical"
            ),
            new ServiceCatalogItem(
                    "plumber",
                    "Plumber",
                    "PLUMBING",
                    "Help with leaking pipes, taps, drains, and plumbing fixes.",
                    BigDecimal.valueOf(300),
                    "plumbing"
            ),
            new ServiceCatalogItem(
                    "ac-repair",
                    "AC Repair",
                    "AC REPAIR",
                    "AC troubleshooting, servicing, and cooling repairs.",
                    BigDecimal.valueOf(700),
                    "ac-repair"
            ),
            new ServiceCatalogItem(
                    "cleaning",
                    "Cleaning",
                    "CLEANING",
                    "Professional home cleaning for a fresh, tidy space.",
                    BigDecimal.valueOf(999),
                    "cleaning"
            ),
            new ServiceCatalogItem(
                    "carpenter",
                    "Carpenter",
                    "CARPENTER",
                    "Help with furniture, fittings, doors, and home carpentry repairs.",
                    BigDecimal.valueOf(400),
                    "carpentry"
            ),
            new ServiceCatalogItem(
                    "appliance-repair",
                    "Appliance Repair",
                    "APPLIANCE REPAIR",
                    "Home appliance diagnosis and repair.",
                    BigDecimal.valueOf(600),
                    "appliance-repair"
            ),
            new ServiceCatalogItem(
                    "painting",
                    "Painting",
                    "PAINTING",
                    "Interior and exterior home painting.",
                    BigDecimal.valueOf(1500),
                    "painting"
            )
    );

    private ServiceCatalog() {
    }

    public static List<ServiceCatalogItem> items() {
        return ITEMS;
    }

    public static Integer startingPrice(String service) {
        if (service == null) {
            return null;
        }
        String normalized = normalize(service);
        Integer catalogPrice = ITEMS.stream()
                .filter(item -> normalize(item.name()).equals(normalized)
                        || normalize(item.service()).equals(normalized)
                        || item.slug().equals(normalized.toLowerCase(Locale.ROOT)))
                .map(item -> item.startingPrice().intValueExact())
                .findFirst()
                .orElse(null);
        if (catalogPrice != null) {
            return catalogPrice;
        }
        return switch (normalized) {
            case "AIR CONDITIONING", "AC SERVICE", "AC REPAIR SERVICE" -> 700;
            case "HOME CLEANING" -> 999;
            default -> null;
        };
    }

    private static String normalize(String value) {
        return value.trim()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}
