package com.homefix.api.service;

import com.homefix.api.dto.ServiceCatalogItem;

import java.util.List;
import java.util.Locale;

public final class ServiceCatalog {

    private static final List<ServiceCatalogItem> ITEMS = List.of(
            new ServiceCatalogItem(
                    "electrician",
                    "Electrician",
                    "ELECTRICAL",
                    "Safe help with wiring, lighting, switches, and electrical repairs.",
                    500
            ),
            new ServiceCatalogItem(
                    "plumber",
                    "Plumber",
                    "PLUMBING",
                    "Help with leaking pipes, taps, drains, and plumbing fixes.",
                    300
            ),
            new ServiceCatalogItem(
                    "ac-repair",
                    "AC Repair",
                    "AC REPAIR",
                    "AC troubleshooting, servicing, and cooling repairs.",
                    700
            ),
            new ServiceCatalogItem(
                    "cleaning",
                    "Cleaning",
                    "CLEANING",
                    "Professional home cleaning for a fresh, tidy space.",
                    999
            ),
            new ServiceCatalogItem(
                    "carpenter",
                    "Carpenter",
                    "CARPENTER",
                    "Help with furniture, fittings, doors, and home carpentry repairs.",
                    400
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
                .map(ServiceCatalogItem::startingPrice)
                .findFirst()
                .orElse(null);
        if (catalogPrice != null) {
            return catalogPrice;
        }
        return switch (normalized) {
            case "AIR CONDITIONING", "AC SERVICE", "AC REPAIR SERVICE" -> 700;
            case "HOME CLEANING" -> 999;
            case "PAINTING" -> 1500;
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
